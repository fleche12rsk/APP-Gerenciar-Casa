package dev.micio.casa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.micio.casa.ui.theme.Palette

/** Abre o reconhecimento de voz do sistema e devolve a frase entendida. */
@Composable
expect fun rememberVoiceInput(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit

/** Botão/gesto "voltar" do sistema (só existe no Android). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Converte JPEG/PNG em imagem do Compose. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/**
 * Campo para falar ou digitar um comando. No iPhone/iPad o ditado do teclado
 * (ícone de microfone) transcreve a fala direto aqui.
 */
@Composable
fun CommandDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (text.isNotBlank()) onSubmit(text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.NightHigh,
        title = { Text("O que você quer fazer?", color = Palette.Ivory) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Ex.: apagar as luzes da sala") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    "Para falar, toque no microfone do teclado.",
                    color = Palette.Mist,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = submit, enabled = text.isNotBlank()) { Text("Enviar", color = Palette.Ember) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.Mist) } },
    )
}
