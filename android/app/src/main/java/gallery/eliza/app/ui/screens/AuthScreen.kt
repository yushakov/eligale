package gallery.eliza.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.RequestCodeBody
import gallery.eliza.app.data.VerifyCodeBody
import kotlinx.coroutines.launch

@Composable
fun AuthDialog(
    onTokenReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1 = email, 2 = code
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 1) "Введите email" else "Введите код") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == 1) {
                    Text("Мы отправим 6-значный код для подтверждения.")
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Код отправлен на $email")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6) { code = it; error = null } },
                        label = { Text("Код") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            if (step == 1) {
                                Api.service.requestCode(RequestCodeBody(email.trim()))
                                step = 2
                            } else {
                                val resp = Api.service.verifyCode(VerifyCodeBody(email.trim(), code.trim()))
                                onTokenReceived(resp.token)
                            }
                        } catch (e: Exception) {
                            error = if (step == 1) "Ошибка отправки. Проверьте email." else "Неверный или истёкший код."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && (if (step == 1) email.isNotBlank() else code.length == 6)
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (step == 1) "Отправить код" else "Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
