package dev.micio.casa.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.micio.casa.model.Device
import dev.micio.casa.model.DeviceKind
import dev.micio.casa.ui.theme.Palette

fun DeviceKind.icon(): ImageVector = when (this) {
    DeviceKind.LIGHT -> Icons.Rounded.Lightbulb
    DeviceKind.SWITCH -> Icons.Rounded.ToggleOn
    DeviceKind.OUTLET -> Icons.Rounded.Power
    DeviceKind.FAN -> Icons.Rounded.Air
    DeviceKind.COVER -> Icons.Rounded.VerticalSplit
    DeviceKind.VACUUM -> Icons.Rounded.CleaningServices
    DeviceKind.TV -> Icons.Rounded.Tv
    DeviceKind.SPEAKER -> Icons.Rounded.Speaker
    DeviceKind.CAMERA -> Icons.Rounded.Videocam
    DeviceKind.CLIMATE -> Icons.Rounded.AcUnit
    DeviceKind.SENSOR -> Icons.Rounded.Thermostat
    DeviceKind.OTHER -> Icons.Rounded.DevicesOther
}

fun DeviceKind.accent(): Color = when (this) {
    DeviceKind.LIGHT -> Palette.Ember
    DeviceKind.SWITCH, DeviceKind.OUTLET, DeviceKind.FAN -> Palette.Sage
    DeviceKind.TV, DeviceKind.SPEAKER, DeviceKind.CLIMATE, DeviceKind.SENSOR -> Palette.Sky
    DeviceKind.VACUUM, DeviceKind.COVER -> Palette.Lagoon
    DeviceKind.CAMERA -> Palette.Coral
    DeviceKind.OTHER -> Palette.Mist
}

fun DeviceKind.label(): String = when (this) {
    DeviceKind.LIGHT -> "Luz"
    DeviceKind.SWITCH -> "Interruptor"
    DeviceKind.OUTLET -> "Tomada"
    DeviceKind.FAN -> "Ventilador"
    DeviceKind.COVER -> "Cortina"
    DeviceKind.VACUUM -> "Aspirador"
    DeviceKind.TV -> "TV"
    DeviceKind.SPEAKER -> "Alto-falante"
    DeviceKind.CAMERA -> "Câmera"
    DeviceKind.CLIMATE -> "Clima"
    DeviceKind.SENSOR -> "Sensor"
    DeviceKind.OTHER -> "Aparelho"
}

/** Linha de estado curta exibida embaixo do nome do aparelho. */
fun Device.statusLine(): String {
    if (!online) return "Sem resposta"
    status?.let { text -> return battery?.let { "$text · $it%" } ?: text }
    val on = isOn ?: return kind.label()
    return when (kind) {
        DeviceKind.LIGHT -> if (on) brightness?.let { "Acesa · ${(it * 100).toInt()}%" } ?: "Acesa" else "Apagada"
        DeviceKind.COVER -> if (on) "Aberta" else "Fechada"
        DeviceKind.TV, DeviceKind.SPEAKER -> if (on) "Ligada" else "Desligada"
        DeviceKind.OUTLET -> if (on) "Ligada" else "Desligada"
        DeviceKind.VACUUM -> if (on) "Limpando" else "Parado"
        else -> if (on) "Ligado" else "Desligado"
    }
}
