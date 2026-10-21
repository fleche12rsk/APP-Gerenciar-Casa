package dev.micio.casa.home

import dev.micio.casa.core.AppConfig
import dev.micio.casa.core.ConfigRepository
import dev.micio.casa.core.DeviceOverride
import dev.micio.casa.integrations.DeviceProvider
import dev.micio.casa.integrations.ProviderSnapshot
import dev.micio.casa.integrations.camera.CameraProvider
import dev.micio.casa.integrations.ha.HomeAssistantProvider
import dev.micio.casa.integrations.lg.LgTvProvider
import dev.micio.casa.integrations.lg.WebOsClient
import dev.micio.casa.integrations.tuya.TuyaProvider
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceAction
import dev.micio.casa.model.Scene
import dev.micio.casa.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ponto único que junta todas as marcas: mantém uma integração por plataforma
 * configurada, atualiza o estado periodicamente e encaminha os comandos.
 */
class HomeRepository(
    private val configRepo: ConfigRepository,
    private val scope: CoroutineScope,
) {
    private val snapshots = MutableStateFlow<Map<Source, ProviderSnapshot>>(emptyMap())

    private val _errors = MutableStateFlow<Map<Source, String>>(emptyMap())
    val errors: StateFlow<Map<Source, String>> = _errors.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _providers = MutableStateFlow<Map<Source, DeviceProvider>>(emptyMap())
    val providers: StateFlow<Map<Source, DeviceProvider>> = _providers.asStateFlow()

    val tuya get() = _providers.value[Source.TUYA] as? TuyaProvider
    val homeAssistant get() = _providers.value[Source.HOME_ASSISTANT] as? HomeAssistantProvider
    val tv: WebOsClient? get() = (_providers.value[Source.LG] as? LgTvProvider)?.tv

    /** Todos os aparelhos, já com nome/cômodo/favorito personalizados. */
    val allDevices: StateFlow<List<Device>> = combine(snapshots, configRepo.config) { snaps, config ->
        Source.entries.flatMap { snaps[it]?.devices.orEmpty() }.map { it.withOverride(config.overrides[it.id]) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val devices: StateFlow<List<Device>> = allDevices.map { list -> list.filterNot { it.hidden } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val scenes: StateFlow<List<Scene>> = snapshots.map { snaps -> Source.entries.flatMap { snaps[it]?.scenes.orEmpty() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var active = false
    private val pollJobs = mutableMapOf<Source, Job>()

    init {
        watch(Source.TUYA, { it.tuya }) { config -> config?.let(::TuyaProvider) }
        watch(Source.HOME_ASSISTANT, { it.homeAssistant?.copy(alexaEntity = null) }) { config -> config?.let(::HomeAssistantProvider) }
        watch(Source.CAMERA, { it.cameras }) { cameras -> cameras.takeIf { it.isNotEmpty() }?.let(::CameraProvider) }
        watch(Source.LG, { config -> config.tv?.let { it.host to it.mac } }) { key ->
            (_providers.value[Source.LG] as? LgTvProvider)?.tv?.close()
            val initial = configRepo.current.tv
            if (key == null || initial == null) null
            else LgTvProvider(
                settings = { configRepo.current.tv ?: initial },
                tv = WebOsClient(
                    host = initial.host,
                    clientKey = initial.clientKey,
                    onClientKey = { newKey -> configRepo.update { c -> c.copy(tv = c.tv?.copy(clientKey = newKey)) } },
                ),
            )
        }
    }

    /** Recria a integração de [source] só quando a parte da configuração que ela usa muda. */
    private fun <T> watch(source: Source, selector: (AppConfig) -> T, build: (T) -> DeviceProvider?) {
        scope.launch {
            configRepo.config.map(selector).distinctUntilChanged().collect { value -> setProvider(source, build(value)) }
        }
    }

    private fun setProvider(source: Source, provider: DeviceProvider?) {
        pollJobs.remove(source)?.cancel()
        _providers.update { if (provider == null) it - source else it + (source to provider) }
        if (provider == null) {
            snapshots.update { it - source }
            _errors.update { it - source }
        } else if (active) {
            startPolling(provider)
        }
    }

    /** Só para testes e capturas de tela: injeta uma integração de exemplo. */
    fun attachForTest(provider: DeviceProvider) = setProvider(provider.source, provider)

    /** Liga/desliga a atualização automática junto com o ciclo de vida da tela. */
    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        pollJobs.values.forEach { it.cancel() }
        pollJobs.clear()
        if (isActive) _providers.value.values.forEach(::startPolling)
    }

    private fun startPolling(provider: DeviceProvider) {
        pollJobs[provider.source] = scope.launch {
            while (isActive) {
                refresh(provider)
                delay(provider.pollSeconds * 1000L)
            }
        }
    }

    fun refreshAll() {
        scope.launch {
            _loading.value = true
            _providers.value.values.map { launch { refresh(it) } }.forEach { it.join() }
            _loading.value = false
        }
    }

    private suspend fun refresh(provider: DeviceProvider) {
        try {
            val snapshot = withContext(Dispatchers.IO) { provider.fetch() }
            // Ignora respostas de uma integração que foi trocada enquanto buscava.
            if (_providers.value[provider.source] !== provider) return
            snapshots.update { it + (provider.source to snapshot) }
            _errors.update { it - provider.source }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_providers.value[provider.source] === provider) {
                _errors.update { it + (provider.source to (e.message ?: "Falha de conexão")) }
            }
        }
    }

    fun execute(device: Device, action: DeviceAction) {
        val provider = _providers.value[device.source] ?: return
        scope.launch {
            patch(device) {
                when (action) {
                    is DeviceAction.Power -> it.copy(isOn = action.on)
                    is DeviceAction.Brightness -> it.copy(brightness = action.level, isOn = true)
                    is DeviceAction.Vacuum -> it
                }
            }
            try {
                withContext(Dispatchers.IO) { provider.execute(device, action) }
                delay(1_500)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Não foi possível controlar ${device.name}.")
            }
            refresh(provider)
        }
    }

    fun runScene(scene: Scene) {
        val provider = _providers.value[scene.source] ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { provider.runScene(scene) }
                _messages.emit("Cena “${scene.name}” ativada")
                delay(2_000)
                refresh(provider)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Não foi possível ativar a cena.")
            }
        }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    /** Executa uma ação fora do fluxo de aparelhos (TV, IR, Alexa) com tratamento de erro padrão. */
    fun launchAction(failure: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.message ?: failure)
            }
        }
    }

    private fun patch(device: Device, transform: (Device) -> Device) {
        snapshots.update { snaps ->
            val snap = snaps[device.source] ?: return@update snaps
            snaps + (device.source to snap.copy(devices = snap.devices.map { if (it.id == device.id) transform(it) else it }))
        }
    }

    private fun Device.withOverride(override: DeviceOverride?): Device {
        if (override == null) return this
        return copy(
            name = override.name?.takeIf { it.isNotBlank() } ?: name,
            room = override.room ?: room,
            favorite = override.favorite,
            hidden = override.hidden,
        )
    }
}
