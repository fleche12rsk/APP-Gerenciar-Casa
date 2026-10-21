package dev.micio.casa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.WindPower
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.micio.casa.core.WeatherPlace
import dev.micio.casa.home.WeatherState
import dev.micio.casa.integrations.weather.DayForecast
import dev.micio.casa.integrations.weather.HourForecast
import dev.micio.casa.integrations.weather.Weather
import dev.micio.casa.integrations.weather.WeatherCodes
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.AnimatedWeatherIcon
import dev.micio.casa.ui.components.EmptyState
import dev.micio.casa.ui.components.GlassCard
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.components.weatherIcon
import dev.micio.casa.ui.components.weatherTint
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun WeatherScreen(vm: MainViewModel, wide: Boolean) {
    val state by vm.weather.state.collectAsStateWithLifecycle()
    var changing by rememberSaveable { mutableStateOf(false) }
    val hPad = if (wide) 32.dp else 16.dp
    val pick: (WeatherPlace) -> Unit = { place ->
        vm.updateConfig { it.copy(weather = place) }
        changing = false
    }

    if (state.place == null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = hPad, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                icon = Icons.Rounded.WbSunny,
                title = "Previsão do tempo",
                message = "Escolha a sua cidade para ver temperatura, chuva, umidade, vento, índice UV e os próximos 7 dias.",
            )
            CityPicker(vm, pick, Modifier.widthIn(max = 520.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = hPad, vertical = 24.dp)) {
        PlaceHeader(state, onChange = { changing = !changing }, onRefresh = vm.weather::refresh)
        AnimatedVisibility(changing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            CityPicker(vm, pick, Modifier.widthIn(max = 520.dp).padding(top = 8.dp))
        }

        val weather = state.weather
        when {
            weather != null -> WeatherContent(weather, wide)
            state.error != null -> ErrorBanner(state.error.orEmpty())
            else -> Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Ember)
            }
        }
        Attribution()
    }
}

@Composable
private fun PlaceHeader(state: WeatherState, onChange: () -> Unit, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Palette.Ember, modifier = Modifier.size(20.dp))
                Text(state.place?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = Palette.Ivory, modifier = Modifier.padding(start = 6.dp))
            }
            val updated = state.weather?.current?.time?.format(hourFormat)
            Text(
                listOfNotNull(state.place?.region, updated?.let { "atualizado às $it" }).joinToString(" · "),
                color = Palette.Mist,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onChange) { Text("Trocar cidade", color = Palette.Ember) }
        if (state.loading) {
            CircularProgressIndicator(color = Palette.Mist, strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(20.dp))
        } else {
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar previsão", tint = Palette.Mist) }
        }
    }
}

@Composable
private fun WeatherContent(weather: Weather, wide: Boolean) {
    val now = weather.current
    val today = weather.today

    // Destaque: condição atual.
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AnimatedWeatherIcon(now.code, now.isDay, size = if (wide) 112.dp else 80.dp)
        Column {
            Text(
                "${now.temperature.roundToInt()}°",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = if (wide) 96.sp else 72.sp),
                color = Palette.Ivory,
            )
            Text(WeatherCodes.describe(now.code), style = MaterialTheme.typography.titleLarge, color = Palette.Ivory)
            Text(
                listOfNotNull(
                    now.apparentTemperature?.let { "Sensação ${it.roundToInt()}°" },
                    today?.let { "Máx ${it.max.roundToInt()}°" },
                    today?.let { "Mín ${it.min.roundToInt()}°" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Mist,
            )
        }
    }

    SectionTitle("Próximas horas")
    HourlyStrip(weather.hourly)

    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) { DailyList(weather.daily) }
            Column(Modifier.weight(1f)) { Metrics(weather) }
        }
    } else {
        DailyList(weather.daily)
        Metrics(weather)
    }
}

@Composable
private fun HourlyStrip(hours: List<HourForecast>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(hours, key = { _, h -> h.time.toString() }) { index, hour ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (index == 0) Palette.Ivory.copy(alpha = 0.12f) else Palette.GlassFill)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (index == 0) "Agora" else "${hour.time.hour}h", color = Palette.Mist, style = MaterialTheme.typography.labelMedium)
                Icon(weatherIcon(hour.code, hour.isDay), contentDescription = WeatherCodes.describe(hour.code), tint = weatherTint(hour.code, hour.isDay), modifier = Modifier.size(24.dp))
                Text("${hour.temperature.roundToInt()}°", color = Palette.Ivory, style = MaterialTheme.typography.titleMedium)
                Text(
                    hour.precipitationProbability?.takeIf { it >= 10 }?.let { "$it%" } ?: " ",
                    color = Palette.Sky,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DailyList(days: List<DayForecast>) {
    if (days.isEmpty()) return
    SectionTitle("Próximos 7 dias")
    val weekMin = days.minOf { it.min }
    val weekMax = days.maxOf { it.max }
    GlassCard(Modifier.fillMaxWidth()) {
        days.forEachIndexed { index, day ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {
                    contentDescription = "${dayName(day.date, index)}: ${WeatherCodes.describe(day.code)}, mínima ${day.min.roundToInt()}, máxima ${day.max.roundToInt()}"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dayName(day.date, index), color = Palette.Ivory, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(weatherIcon(day.code, true), contentDescription = null, tint = weatherTint(day.code, true), modifier = Modifier.size(22.dp))
                    day.precipitationProbability?.takeIf { it >= 10 }?.let {
                        Text("$it%", color = Palette.Sky, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("${day.min.roundToInt()}°", color = Palette.Mist, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(40.dp).padding(start = 8.dp))
                TemperatureBar(day.min, day.max, weekMin, weekMax, Modifier.weight(1f).padding(horizontal = 8.dp), delayMs = index * 60)
                Text("${day.max.roundToInt()}°", color = Palette.Ivory, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(36.dp))
            }
        }
    }
}

/** Faixa da mínima à máxima do dia, posicionada na escala da semana inteira. */
@Composable
private fun TemperatureBar(min: Double, max: Double, weekMin: Double, weekMax: Double, modifier: Modifier, delayMs: Int) {
    val span = (weekMax - weekMin).takeIf { it > 0 } ?: 1.0
    val start = ((min - weekMin) / span).toFloat()
    val end = ((max - weekMin) / span).toFloat()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        grow.animateTo(1f, tween(600))
    }
    BoxWithConstraints(modifier.height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
        val width = maxWidth
        Box(
            Modifier
                .offset(x = width * start)
                .width(width * (end - start).coerceAtLeast(0.04f) * grow.value)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(tempColor(min), tempColor(max)))),
        )
    }
}

private fun tempColor(celsius: Double): Color = when {
    celsius < 12 -> Palette.Sky
    celsius < 20 -> Palette.Lagoon
    celsius < 27 -> Palette.Ember
    else -> Palette.Coral
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metrics(weather: Weather) {
    val now = weather.current
    val today = weather.today
    SectionTitle("Detalhes de agora")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        now.humidity?.let { MetricTile(Icons.Rounded.Opacity, "Umidade", "$it%", Palette.Sky) }
        now.windSpeed?.let { speed ->
            MetricTile(Icons.Rounded.Air, "Vento", "${speed.roundToInt()} km/h", Palette.Lagoon) {
                now.windDirection?.let { dir ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // A direção meteorológica diz de onde o vento vem; a seta aponta para onde ele vai.
                        Icon(Icons.Rounded.Navigation, contentDescription = null, tint = Palette.Lagoon, modifier = Modifier.size(14.dp).rotate(dir + 180f))
                        Text(" de ${WeatherCodes.windDirection(dir)}", color = Palette.Mist, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        now.windGusts?.let { MetricTile(Icons.Rounded.WindPower, "Rajadas", "${it.roundToInt()} km/h", Palette.Lagoon) }
        now.apparentTemperature?.let { MetricTile(Icons.Rounded.Thermostat, "Sensação", "${it.roundToInt()}°", Palette.Ember) }
        today?.let { day ->
            MetricTile(Icons.Rounded.WaterDrop, "Chuva hoje", day.precipitationProbability?.let { "$it%" } ?: "—", Palette.Sky) {
                day.precipitationSum?.let { Text("%.1f mm previstos".format(ptBR, it), color = Palette.Mist, style = MaterialTheme.typography.bodySmall) }
            }
        }
        (now.uvIndex ?: today?.uvIndexMax)?.let { uv ->
            MetricTile(Icons.Rounded.WbSunny, "Índice UV", uv.roundToInt().toString(), Palette.Ember) {
                Text(WeatherCodes.uvLevel(uv), color = Palette.Mist, style = MaterialTheme.typography.bodySmall)
            }
        }
        now.pressure?.let { MetricTile(Icons.Rounded.Compress, "Pressão", "${it.roundToInt()} hPa", Palette.Mist) }
        now.cloudCover?.let { MetricTile(Icons.Rounded.Cloud, "Nebulosidade", "$it%", Palette.Mist) }
        today?.sunrise?.let { MetricTile(Icons.Rounded.WbTwilight, "Nascer do sol", it.format(hourFormat), Palette.Ember) }
        today?.sunset?.let { MetricTile(Icons.Rounded.WbTwilight, "Pôr do sol", it.format(hourFormat), Palette.Coral) }
    }
}

@Composable
private fun MetricTile(icon: ImageVector, label: String, value: String, accent: Color, extra: @Composable (() -> Unit)? = null) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.GlassFill)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(label.uppercase(ptBR), color = Palette.Mist, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
        }
        Text(value, color = Palette.Ivory, style = MaterialTheme.typography.headlineSmall)
        extra?.invoke()
    }
}

/** Busca de cidade com atraso curto entre as teclas para não consultar a cada letra. */
@Composable
fun CityPicker(vm: MainViewModel, onPick: (WeatherPlace) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(400)
        searching = true
        runCatching { vm.weather.search(query) }
            .onSuccess { results = it; failure = null }
            .onFailure { failure = it.message ?: "Não foi possível buscar"; results = emptyList() }
        searching = false
    }

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar cidade (ex.: Curitiba)") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = { if (searching) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        results?.let { list ->
            if (list.isEmpty()) {
                Text(failure ?: "Nenhuma cidade encontrada.", color = Palette.Mist, modifier = Modifier.padding(12.dp))
            }
            list.forEach { place ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClickLabel = "Escolher ${place.name}") { onPick(place) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Palette.Mist, modifier = Modifier.size(18.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(place.name, color = Palette.Ivory, style = MaterialTheme.typography.titleSmall)
                        place.region?.let { Text(it, color = Palette.Mist, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Attribution() {
    val text = buildAnnotatedString {
        append("Dados meteorológicos: ")
        withLink(
            LinkAnnotation.Url(
                "https://open-meteo.com/",
                TextLinkStyles(SpanStyle(color = Palette.Mist, textDecoration = TextDecoration.Underline)),
            )
        ) { append("Open-Meteo.com") }
        append(" (CC BY 4.0)")
    }
    Spacer(Modifier.height(24.dp))
    Text(text, color = Palette.Mist, style = MaterialTheme.typography.bodySmall)
}

private fun dayName(date: LocalDate, index: Int): String = when (index) {
    0 -> "Hoje"
    else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, ptBR).trimEnd('.').replaceFirstChar { it.titlecase(ptBR) }
}

/** Selo compacto da tela inicial: toque abre a previsão completa. */
@Composable
fun WeatherBadge(state: WeatherState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val weather = state.weather
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.GlassFill)
            .clickable(role = Role.Button, onClickLabel = "Abrir previsão do tempo", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (weather == null) {
            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Palette.Ember)
            Text(if (state.place == null) "Adicionar previsão do tempo" else "Carregando previsão…", color = Palette.Ivory, style = MaterialTheme.typography.labelLarge)
            return@Row
        }
        AnimatedWeatherIcon(weather.current.code, weather.current.isDay, size = 36.dp)
        Text("${weather.current.temperature.roundToInt()}°", color = Palette.Ivory, style = MaterialTheme.typography.headlineMedium)
        Column {
            Text(WeatherCodes.describe(weather.current.code), color = Palette.Ivory, style = MaterialTheme.typography.labelLarge)
            Text(
                listOfNotNull(
                    weather.today?.let { "↑${it.max.roundToInt()}° ↓${it.min.roundToInt()}°" },
                    weather.current.humidity?.let { "$it% umidade" },
                ).joinToString(" · "),
                color = Palette.Mist,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

