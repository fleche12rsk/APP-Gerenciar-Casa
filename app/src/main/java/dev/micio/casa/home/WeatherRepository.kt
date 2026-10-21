package dev.micio.casa.home

import androidx.annotation.VisibleForTesting
import dev.micio.casa.core.ConfigRepository
import dev.micio.casa.core.WeatherPlace
import dev.micio.casa.integrations.weather.OpenMeteo
import dev.micio.casa.integrations.weather.Weather
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

data class WeatherState(
    val place: WeatherPlace? = null,
    val weather: Weather? = null,
    val error: String? = null,
    val loading: Boolean = false,
)

/** Mantém a previsão da cidade escolhida atualizada a cada 30 minutos com o app aberto. */
class WeatherRepository(
    private val configRepo: ConfigRepository,
    private val scope: CoroutineScope,
    private val api: OpenMeteo = OpenMeteo(),
) {
    private val _state = MutableStateFlow(WeatherState(place = configRepo.current.weather))
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private var active = false
    private var job: Job? = null

    init {
        scope.launch {
            configRepo.config.map { it.weather }.distinctUntilChanged().collect { place ->
                _state.value = WeatherState(place = place)
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

    suspend fun search(query: String): List<WeatherPlace> =
        withContext(Dispatchers.IO) { api.searchPlaces(query) }

    @VisibleForTesting
    internal fun setForTest(state: WeatherState) {
        job?.cancel()
        _state.value = state
    }

    private fun restart() {
        job?.cancel()
        if (!active || _state.value.place == null) return
        job = scope.launch {
            while (isActive) {
                // Depois de uma falha tenta de novo mais cedo.
                val ok = load()
                delay(if (ok) 30 * 60_000L else 2 * 60_000L)
            }
        }
    }

    private suspend fun load(): Boolean {
        val place = _state.value.place ?: return false
        _state.value = _state.value.copy(loading = true)
        return try {
            val weather = withContext(Dispatchers.IO) { api.forecast(place) }
            if (_state.value.place == place) _state.value = WeatherState(place, weather)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_state.value.place == place) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Previsão indisponível")
            }
            false
        }
    }
}
