package dev.micio.casa.home

import androidx.annotation.VisibleForTesting
import dev.micio.casa.core.ConfigRepository
import dev.micio.casa.core.RiverCity
import dev.micio.casa.core.WeatherPlace
import dev.micio.casa.integrations.river.RiverMonitor
import dev.micio.casa.integrations.river.RiverReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sqrt

data class RiverState(val city: RiverCity? = null, val report: RiverReport? = null, val error: String? = null) {
    val enabled get() = city != null
}

/** Atualiza o nível dos rios a cada 5 minutos (mesmo ritmo do site) enquanto o app está aberto. */
class RiverRepository(
    private val configRepo: ConfigRepository,
    private val scope: CoroutineScope,
    private val monitor: RiverMonitor = RiverMonitor(),
) {
    private val _state = MutableStateFlow(RiverState(city = configRepo.current.river))
    val state: StateFlow<RiverState> = _state.asStateFlow()

    private var active = false
    private var job: Job? = null

    init {
        scope.launch {
            configRepo.config.map { it.river }.distinctUntilChanged().collect { city ->
                _state.value = RiverState(city = city)
                restart()
            }
        }
    }

    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        restart()
    }

    fun refresh() {
        scope.launch { load() }
    }

    suspend fun cities(): List<RiverCity> = withContext(Dispatchers.IO) { monitor.cities() }

    /**
     * Ao escolher a cidade da previsão do tempo, liga o nível do rio se aquela cidade
     * tiver painel da Defesa Civil — sem sobrescrever uma escolha feita pela pessoa.
     */
    fun suggestFor(place: WeatherPlace) {
        if (configRepo.current.river != null) return
        scope.launch {
            val match = runCatching { cities() }.getOrDefault(emptyList()).firstOrNull { city ->
                VoiceCommandParser.normalize(city.name) == VoiceCommandParser.normalize(place.name) &&
                    (city.latitude == null || city.longitude == null ||
                        distanceKm(city.latitude, city.longitude, place.latitude, place.longitude) < 40)
            } ?: return@launch
            configRepo.update { if (it.river == null) it.copy(river = match) else it }
        }
    }

    @VisibleForTesting
    internal fun setForTest(state: RiverState) {
        job?.cancel()
        _state.value = state
    }

    private fun restart() {
        job?.cancel()
        if (!active || _state.value.city == null) return
        job = scope.launch {
            while (isActive) {
                val ok = load()
                delay(if (ok) 5 * 60_000L else 60_000L)
            }
        }
    }

    private suspend fun load(): Boolean {
        val city = _state.value.city ?: return false
        return try {
            val report = withContext(Dispatchers.IO) { monitor.load(city.id) }
            if (_state.value.city == city) _state.value = _state.value.copy(report = report, error = null)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_state.value.city == city) _state.value = _state.value.copy(error = e.message ?: "Nível do rio indisponível")
            false
        }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Aproximação equiretangular: suficiente para distinguir cidades homônimas.
        val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val y = Math.toRadians(lat2 - lat1)
        return sqrt(x * x + y * y) * 6371
    }
}
