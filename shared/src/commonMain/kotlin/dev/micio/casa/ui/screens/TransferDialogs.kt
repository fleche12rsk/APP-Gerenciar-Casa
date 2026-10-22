package dev.micio.casa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.micio.casa.core.AppConfig
import dev.micio.casa.core.ConfigTransfer
import dev.micio.casa.core.summary
import dev.micio.casa.ui.MainViewModel
import dev.micio.casa.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gera o código cifrado com toda a configuração deste aparelho. */
@Composable
fun ExportConfigDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val valid = password.length >= ConfigTransfer.MIN_PASSWORD && password == repeat

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.NightHigh,
        title = { Text(if (code == null) "Enviar configuração" else "Código pronto", color = Palette.Ivory) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = code
                if (current == null) {
                    Hint("Leva contas, TVs, câmeras, cômodos, nomes e favoritos para outro celular ou tablet. Crie uma senha: ela será pedida no outro aparelho.")
                    SecretField("Senha (mínimo ${ConfigTransfer.MIN_PASSWORD} caracteres)", password) { password = it }
                    SecretField("Repita a senha", repeat) { repeat = it }
                    if (repeat.isNotEmpty() && password != repeat) Hint("As senhas não são iguais.", warn = true)
                } else {
                    Hint("Mande o código para o outro aparelho e digite lá a mesma senha.")
                    SelectionContainer {
                        Text(
                            current,
                            color = Palette.Ivory,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.GlassFill)
                                .padding(12.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            clipboard.setText(AnnotatedString(current))
                            copied = true
                        }) {
                            Icon(if (copied) Icons.Rounded.CheckCircle else Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(if (copied) "Copiado" else "Copiar", modifier = Modifier.padding(start = 8.dp))
                        }
                        FilledTonalButton(onClick = {
                            vm.platform.shareText("Código de configuração do app Casa (a senha vai separada):\n\n$current")
                        }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Compartilhar", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Hint("Não mande a senha junto com o código. Quem tiver os dois consegue controlar a sua casa.", warn = true)
                }
            }
        },
        confirmButton = {
            if (code == null) {
                Button(
                    enabled = valid && !working,
                    onClick = {
                        working = true
                        scope.launch {
                            code = withContext(Dispatchers.Default) { ConfigTransfer.export(vm.config.current, password) }
                            working = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink),
                ) {
                    if (working) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = Palette.Ink)
                    else Text("Gerar código")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Pronto", color = Palette.Ember) }
            }
        },
        dismissButton = { if (code == null) TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.Mist) } },
    )
}

/** Recebe um código gerado em outro aparelho e aplica a configuração aqui. */
@Composable
fun ImportConfigDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<AppConfig?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.NightHigh,
        title = { Text(if (imported == null) "Receber configuração" else "Conferir e aplicar", color = Palette.Ivory) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val preview = imported
                if (preview == null) {
                    Hint("No aparelho já configurado, abra Ajustes › Outros aparelhos › Enviar configuração. Cole aqui o código recebido.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; error = null },
                        label = { Text("Código") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        trailingIcon = {
                            TextButton(onClick = { clipboard.getText()?.text?.let { code = it; error = null } }) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp), tint = Palette.Ember)
                                Text("Colar", color = Palette.Ember, modifier = Modifier.padding(start = 4.dp))
                            }
                        },
                    )
                    SecretField("Senha", password) { password = it; error = null }
                    error?.let { Hint(it, warn = true) }
                } else {
                    Hint("O código traz:")
                    preview.summary().ifEmpty { listOf("Nenhuma integração configurada") }.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Palette.Sage, modifier = Modifier.size(18.dp))
                            Text(item, color = Palette.Ivory, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Hint("Isso substitui a configuração atual deste aparelho.", warn = true)
                }
            }
        },
        confirmButton = {
            val preview = imported
            Button(
                enabled = !working && (preview != null || (code.isNotBlank() && password.isNotEmpty())),
                onClick = {
                    if (preview != null) {
                        vm.updateConfig { current -> preview.copy(keepScreenOn = current.keepScreenOn, immersive = current.immersive) }
                        vm.home.refreshAll()
                        vm.home.notify("Configuração recebida. Tudo pronto!")
                        onDismiss()
                    } else {
                        working = true
                        scope.launch {
                            val result = withContext(Dispatchers.Default) { runCatching { ConfigTransfer.import(code, password) } }
                            result.onSuccess { imported = it }.onFailure { error = it.message ?: "Não foi possível ler o código." }
                            working = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Ivory, contentColor = Palette.Ink),
            ) {
                if (working) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = Palette.Ink)
                else Text(if (preview == null) "Abrir código" else "Aplicar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.Mist) } },
    )
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String, warn: Boolean = false) {
    Text(text, color = if (warn) Palette.Ember else Palette.Mist, style = MaterialTheme.typography.bodyMedium)
}
