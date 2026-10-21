package dev.micio.casa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbIncandescent
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.micio.casa.core.CameraBrand
import dev.micio.casa.core.CameraConfig
import dev.micio.casa.core.HaConfig
import dev.micio.casa.core.LgTvConfig
import dev.micio.casa.core.TuyaConfig
import dev.micio.casa.core.TuyaRegion
import dev.micio.casa.core.WeatherPlace
import dev.micio.casa.integrations.camera.CameraTemplates
import dev.micio.casa.integrations.lg.LgTvProvider
import dev.micio.casa.integrations.lg.WebOsClient
import dev.micio.casa.model.Device
import dev.micio.casa.model.Source
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.components.GlassCard
import dev.micio.casa.ui.components.SectionTitle
import dev.micio.casa.ui.components.accent
import dev.micio.casa.ui.components.icon
import dev.micio.casa.ui.theme.Palette
import java.time.Year
import java.util.UUID

private const val PROJECT_YEAR = 2026

@Composable
fun SettingsScreen(vm: MainViewModel, wide: Boolean, onOpenDevice: (Device) -> Unit) {
    val config by vm.config.config.collectAsStateWithLifecycle()
    val errors by vm.home.errors.collectAsStateWithLifecycle()
    val allDevices by vm.home.allDevices.collectAsStateWithLifecycle()
    val hPad = if (wide) 32.dp else 16.dp

    LazyColumn(
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 24.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        val column = Modifier.widthIn(max = 820.dp).fillMaxWidth()
        item { Text("Ajustes", style = MaterialTheme.typography.headlineMedium, color = Palette.Ivory, modifier = column) }

        item { SectionTitle("Integrações", column) }
        item {
            TuyaCard(vm, config.tuya, errors[Source.TUYA], allDevices.count { it.source == Source.TUYA }, column)
        }
        item { TvCard(vm, config.tv, column) }
        item {
            HomeAssistantCard(vm, config.homeAssistant, errors[Source.HOME_ASSISTANT], allDevices.count { it.source == Source.HOME_ASSISTANT }, column)
        }

        item { SectionTitle("Clima", column) }
        item { WeatherCard(vm, config.weather, column) }

        item { SectionTitle("Câmeras", column) }
        item { CamerasCard(vm, config.cameras, config.rooms, column) }

        item { SectionTitle("Cômodos", column) }
        item { RoomsCard(vm, config.rooms, column) }

        if (allDevices.isNotEmpty()) {
            item { SectionTitle("Aparelhos (${allDevices.size})", column) }
            item { DevicesCard(allDevices, onOpenDevice, column) }
        }

        item { SectionTitle("Tela", column) }
        item {
            GlassCard(column) {
                ToggleRow("Manter a tela ligada", config.keepScreenOn, "Ideal para o tablet fixo na parede ou na base de carga.") { on ->
                    vm.updateConfig { it.copy(keepScreenOn = on) }
                }
                ToggleRow("Tela cheia", config.immersive, "Esconde as barras do Android. Deslize da borda para mostrá-las.") { on ->
                    vm.updateConfig { it.copy(immersive = on) }
                }
            }
        }

        item { Footer(column) }
    }
}

// ---------- Integrações ----------

@Composable
private fun TuyaCard(vm: MainViewModel, current: TuyaConfig?, error: String?, count: Int, modifier: Modifier) {
    var clientId by rememberSaveable(current) { mutableStateOf(current?.clientId.orEmpty()) }
    var secret by rememberSaveable(current) { mutableStateOf(current?.clientSecret.orEmpty()) }
    var region by rememberSaveable(current) { mutableStateOf(current?.region ?: TuyaRegion.AMERICA) }

    IntegrationCard(
        icon = Icons.Rounded.WbIncandescent,
        accent = Palette.Ember,
        title = "Smart Life / Tuya",
        brands = "Interruptores NovaDigital, Intelbras Izy e WAG · controle IR Intelbras",
        status = statusText(current != null, error, "$count aparelhos"),
        healthy = current != null && error == null,
        modifier = modifier,
    ) {
        Field("Access ID / Client ID", clientId) { clientId = it }
        Field("Access Secret", secret, secret = true) { secret = it }
        Label("Data center do projeto")
        ChoiceChips(TuyaRegion.entries, region, { it.label }) { region = it }
        Actions(
            saveEnabled = clientId.isNotBlank() && secret.isNotBlank(),
            onSave = {
                vm.updateConfig { it.copy(tuya = TuyaConfig(clientId.trim(), secret.trim(), region)) }
                vm.home.refreshAll()
            },
            onRemove = current?.let { { vm.updateConfig { c -> c.copy(tuya = null) } } },
        )
        Help(
            "Crie uma conta em iot.tuya.com › Cloud › Create Cloud Project (tipo Smart Home, data center “Western America” para contas do Brasil).",
            "Em Devices › Link Tuya App Account, leia o QR code com o app Smart Life (aba Eu › ícone de leitura no topo).",
            "Os apps de marca (Izy Smart, NovaDigital) costumam não aparecer para vínculo: nesse caso, adicione os mesmos aparelhos ao Smart Life — eles continuam funcionando com a Alexa.",
            "Em Service API, confira se estão ativas: IoT Core, Authorization Token Management, Smart Home Scene Linkage e IR Control Hub Open Service.",
            "Copie o Access ID e o Access Secret da aba Overview do projeto e cole aqui.",
            "O plano gratuito de testes da Tuya precisa ser renovado de tempos em tempos (é grátis, pelo mesmo site).",
        )
    }
}

@Composable
private fun TvCard(vm: MainViewModel, current: LgTvConfig?, modifier: Modifier) {
    val providers by vm.home.providers.collectAsStateWithLifecycle()
    val tv = (providers[Source.LG] as? LgTvProvider)?.tv
    val state = tv?.state?.collectAsStateWithLifecycle()?.value
    var host by rememberSaveable(current?.host) { mutableStateOf(current?.host.orEmpty()) }
    var mac by rememberSaveable(current?.mac) { mutableStateOf(current?.mac.orEmpty()) }
    var name by rememberSaveable(current?.name) { mutableStateOf(current?.name ?: "TV da sala") }

    IntegrationCard(
        icon = Icons.Rounded.Tv,
        accent = Palette.Sky,
        title = "TV LG (webOS)",
        brands = "Smart TV Mini LED QNED 4K · controle pela rede local",
        status = when {
            current == null -> "Não configurada"
            state == WebOsClient.State.CONNECTED -> "Conectada"
            state == WebOsClient.State.WAITING_APPROVAL -> "Aceite o pedido na tela da TV"
            tv?.clientKey == null -> "Falta parear"
            else -> "Pareada · desligada ou fora da rede"
        },
        healthy = state == WebOsClient.State.CONNECTED,
        modifier = modifier,
    ) {
        Field("Nome", name) { name = it }
        Field("IP da TV (ex.: 192.168.0.50)", host, keyboard = KeyboardType.Uri) { host = it }
        Field("MAC (para ligar a TV pelo app)", mac) { mac = it.uppercase() }
        Actions(
            saveEnabled = host.isNotBlank(),
            saveLabel = "Salvar",
            onSave = {
                vm.updateConfig { c ->
                    val keepKey = c.tv?.takeIf { it.host == host.trim() }?.clientKey
                    c.copy(tv = LgTvConfig(host.trim(), mac.trim(), keepKey, name.trim().ifEmpty { "TV LG" }))
                }
            },
            onRemove = current?.let { { vm.updateConfig { c -> c.copy(tv = null) } } },
            extra = if (tv != null && state != WebOsClient.State.CONNECTED) {
                {
                    TextButton(onClick = { vm.home.launchAction("A TV não respondeu.") { tv.connect() } }) {
                        Text(if (tv.clientKey == null) "Parear agora" else "Conectar", color = Palette.Ember)
                    }
                }
            } else null,
        )
        Help(
            "Ligue a TV e deixe o tablet na mesma rede Wi-Fi.",
            "IP e MAC ficam em Configurações › Geral › Rede › Wi-Fi › Configurações avançadas (ou em Sobre esta TV).",
            "Toque em Parear agora: a TV mostra um pedido de permissão — aceite com o controle remoto. Isso só é feito uma vez.",
            "Para ligar a TV pelo app, ative em Configurações › Geral › Dispositivos › “Ligar via Wi-Fi” (nome varia por modelo).",
            "Dica: reserve um IP fixo para a TV no roteador para o app sempre encontrá-la.",
        )
    }
}

@Composable
private fun HomeAssistantCard(vm: MainViewModel, current: HaConfig?, error: String?, count: Int, modifier: Modifier) {
    var url by rememberSaveable(current?.url) { mutableStateOf(current?.url ?: "http://homeassistant.local:8123") }
    var token by rememberSaveable(current?.token) { mutableStateOf(current?.token.orEmpty()) }

    IntegrationCard(
        icon = Icons.Rounded.Hub,
        accent = Palette.Lagoon,
        title = "Home Assistant",
        brands = "Aspirador Eufy · Alexa / Echo Spot · e qualquer outra marca",
        status = statusText(current != null, error, "$count entidades"),
        healthy = current != null && error == null,
        modifier = modifier,
    ) {
        Field("Endereço", url, keyboard = KeyboardType.Uri) { url = it }
        Field("Token de acesso de longa duração", token, secret = true) { token = it }
        Actions(
            saveEnabled = url.isNotBlank() && token.isNotBlank(),
            onSave = {
                vm.updateConfig { c -> c.copy(homeAssistant = HaConfig(url.trim(), token.trim(), c.homeAssistant?.alexaEntity)) }
                vm.home.refreshAll()
            },
            onRemove = current?.let { { vm.updateConfig { c -> c.copy(homeAssistant = null) } } },
        )
        Help(
            "Eufy e Amazon não oferecem acesso aberto aos aparelhos. O Home Assistant (gratuito, roda num Raspberry Pi ou mini PC) faz essa ponte com segurança, dentro de casa.",
            "Aspirador Eufy: instale pelo HACS a integração “Eufy RoboVac” (ou eufy-clean, conforme o modelo).",
            "Alexa: instale pelo HACS a integração “Alexa Media Player” e entre com a sua conta Amazon. O Echo Spot aparece na aba Alexa.",
            "Crie o token em Perfil › Segurança › Tokens de acesso de longa duração › Criar token.",
        )
    }
}

private fun statusText(configured: Boolean, error: String?, okText: String) = when {
    !configured -> "Não conectado"
    error != null -> error
    else -> "Conectado · $okText"
}

// ---------- Clima ----------

@Composable
private fun WeatherCard(vm: MainViewModel, place: WeatherPlace?, modifier: Modifier) {
    IntegrationCard(
        icon = Icons.Rounded.WbSunny,
        accent = Palette.Ember,
        title = "Previsão do tempo",
        brands = "Temperatura, chuva, umidade, vento e UV · Open-Meteo, grátis e sem cadastro",
        status = place?.let { listOfNotNull(it.name, it.region).joinToString(", ") } ?: "Nenhuma cidade escolhida",
        healthy = place != null,
        modifier = modifier,
    ) {
        CityPicker(vm, onPick = { picked -> vm.updateConfig { it.copy(weather = picked) } })
        if (place != null) {
            TextButton(onClick = { vm.updateConfig { it.copy(weather = null) } }) { Text("Remover previsão", color = Palette.Coral) }
        }
    }
}

// ---------- Câmeras ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CamerasCard(vm: MainViewModel, cameras: List<CameraConfig>, rooms: List<String>, modifier: Modifier) {
    var adding by rememberSaveable { mutableStateOf(false) }
    GlassCard(modifier.animateContentSize()) {
        if (cameras.isEmpty() && !adding) {
            Text("Nenhuma câmera adicionada.", color = Palette.Mist)
        }
        cameras.forEach { camera ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Videocam, Palette.Coral)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(camera.name, color = Palette.Ivory, style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(camera.brand.label, camera.room).joinToString(" · "), color = Palette.Mist, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { vm.updateConfig { c -> c.copy(cameras = c.cameras - camera) } }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remover ${camera.name}", tint = Palette.Mist)
                }
            }
        }
        AnimatedVisibility(adding, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            AddCameraForm(rooms, onCancel = { adding = false }) { camera ->
                vm.updateConfig { c -> c.copy(cameras = c.cameras + camera) }
                adding = false
            }
        }
        if (!adding) {
            TextButton(onClick = { adding = true }, modifier = Modifier.padding(top = 4.dp)) {
                Text("+ Adicionar câmera", color = Palette.Ember)
            }
        }
    }
}

@Composable
private fun AddCameraForm(rooms: List<String>, onCancel: () -> Unit, onSave: (CameraConfig) -> Unit) {
    var brand by rememberSaveable { mutableStateOf(CameraBrand.EZVIZ) }
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf<String?>(null) }
    var customUrl by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }

    val generated = if (host.isBlank()) null else CameraTemplates.build(brand, host, user, password)
    LaunchedEffect(generated?.main) { if (!advanced) customUrl = generated?.main.orEmpty() }

    Column(Modifier.padding(top = 8.dp)) {
        Label("Marca")
        ChoiceChips(CameraBrand.entries, brand, { it.label }) { brand = it }
        Field("Nome (ex.: Portão)", name) { name = it }
        Field("IP da câmera", host, keyboard = KeyboardType.Uri) { host = it }
        Field("Usuário", user) { user = it }
        Field(
            when (brand) {
                CameraBrand.EZVIZ -> "Código de verificação (etiqueta da câmera)"
                CameraBrand.ICSEE -> "Senha do app ICSee (vazia se nunca definiu)"
                CameraBrand.GENERIC -> "Senha"
            },
            password,
            secret = true,
        ) { password = it }
        Label("Cômodo")
        ChoiceChips(listOf<String?>(null) + rooms, room, { it ?: "Nenhum" }) { room = it }

        TextButton(onClick = { advanced = !advanced }) {
            Text(if (advanced) "Usar endereço automático" else "Editar endereço RTSP", color = Palette.Mist)
        }
        if (advanced) Field("Endereço RTSP", customUrl, keyboard = KeyboardType.Uri) { customUrl = it }

        Text(
            when (brand) {
                CameraBrand.EZVIZ -> "Se a imagem não abrir: no app EZVIZ, desative a “Criptografia de imagem” da câmera. Alternativa de endereço: rtsp://admin:CÓDIGO@IP:554/H.264"
                CameraBrand.ICSEE -> "Câmeras ICSee/XMEye usam a porta 554. O stream reduzido é usado na grade e o principal ao ampliar."
                CameraBrand.GENERIC -> "Use o endereço RTSP indicado no manual ou no app da câmera (ONVIF)."
            },
            color = Palette.Mist,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = customUrl.startsWith("rtsp://") && name.isNotBlank(),
                onClick = {
                    onSave(CameraConfig(
                        id = UUID.randomUUID().toString().take(8),
                        name = name.trim(),
                        brand = brand,
                        url = customUrl.trim(),
                        subUrl = generated?.sub.takeIf { !advanced },
                        room = room,
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink),
            ) { Text("Salvar câmera") }
            TextButton(onClick = onCancel) { Text("Cancelar", color = Palette.Mist) }
        }
    }
}

// ---------- Cômodos e aparelhos ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomsCard(vm: MainViewModel, rooms: List<String>, modifier: Modifier) {
    var newRoom by rememberSaveable { mutableStateOf("") }
    GlassCard(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rooms.forEach { room ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(room) },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remover $room",
                            modifier = Modifier.size(18.dp).clip(CircleShape).clickable(role = Role.Button) {
                                vm.updateConfig { c -> c.copy(rooms = c.rooms - room) }
                            },
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = InputChipDefaults.inputChipColors(containerColor = Palette.GlassFill, labelColor = Palette.Ivory, trailingIconColor = Palette.Mist),
                    border = null,
                )
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newRoom,
                onValueChange = { newRoom = it },
                placeholder = { Text("Novo cômodo") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = newRoom.isNotBlank(),
                onClick = {
                    val room = newRoom.trim()
                    vm.updateConfig { c -> if (room in c.rooms) c else c.copy(rooms = c.rooms + room) }
                    newRoom = ""
                },
            ) { Text("Adicionar", color = Palette.Ember) }
        }
    }
}

@Composable
private fun DevicesCard(devices: List<Device>, onOpen: (Device) -> Unit, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    GlassCard(modifier.animateContentSize()) {
        Text(
            "Toque para renomear, escolher o cômodo, favoritar ou ocultar.",
            color = Palette.Mist,
            style = MaterialTheme.typography.bodySmall,
        )
        val shown = if (expanded) devices else devices.take(6)
        shown.forEach { device ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = "Editar ${device.name}") { onOpen(device) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(device.kind.icon(), device.kind.accent())
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(device.name, color = Palette.Ivory, style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(device.room, device.source.label).joinToString(" · "), color = Palette.Mist, style = MaterialTheme.typography.bodySmall)
                }
                if (device.hidden) Icon(Icons.Rounded.VisibilityOff, contentDescription = "Oculto", tint = Palette.Mist, modifier = Modifier.size(18.dp))
            }
        }
        if (devices.size > 6) {
            TextButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.Ember)
                Text(if (expanded) "Mostrar menos" else "Mostrar todos", color = Palette.Ember)
            }
        }
    }
}

// ---------- Blocos reutilizáveis ----------

@Composable
private fun IntegrationCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    brands: String,
    status: String,
    healthy: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    GlassCard(modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = if (open) "Recolher" else "Configurar") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, accent)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ivory)
                Text(brands, style = MaterialTheme.typography.bodySmall, color = Palette.Mist)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (healthy) Palette.Sage else Palette.Mist.copy(alpha = 0.5f)))
                    Text(status, style = MaterialTheme.typography.labelMedium, color = if (healthy) Palette.Sage else Palette.Mist, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.Mist)
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, accent: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Mist, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ivory, selectedLabelColor = Palette.Ink, labelColor = Palette.Ivory),
            )
        }
    }
}

@Composable
private fun Actions(
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onRemove: (() -> Unit)?,
    saveLabel: String = "Salvar e conectar",
    extra: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSave, enabled = saveEnabled, colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink)) {
            Text(saveLabel)
        }
        extra?.invoke()
        onRemove?.let { TextButton(onClick = it) { Text("Desconectar", color = Palette.Coral) } }
    }
}

@Composable
private fun Help(vararg steps: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        TextButton(onClick = { open = !open }) {
            Text(if (open) "Ocultar passo a passo" else "Como configurar", color = Palette.Mist)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, step ->
                    Row {
                        Text("${index + 1}.", color = Palette.Ember, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 10.dp))
                        Text(step, color = Palette.Ivory, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Footer(modifier: Modifier) {
    val currentYear = Year.now().value
    val years = if (currentYear > PROJECT_YEAR) "$PROJECT_YEAR–$currentYear" else "$PROJECT_YEAR"
    val text = buildAnnotatedString {
        append("© $years ")
        withLink(
            LinkAnnotation.Url(
                url = "https://micio.dev/",
                styles = TextLinkStyles(style = SpanStyle(color = Palette.Ember, textDecoration = TextDecoration.Underline)),
            )
        ) { append("Mauricio Renato Sarda") }
        append(". Todos os direitos reservados.")
    }
    Column(modifier.padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Casa · versão 1.0", color = Palette.Mist, style = MaterialTheme.typography.labelMedium)
        Text(text, color = Palette.Mist, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

