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
import gallery.eliza.app.data.SetNameBody
import gallery.eliza.app.data.VerifyCodeBody
import kotlinx.coroutines.launch

@Composable
fun AuthDialog(
    onTokenReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1=email, 2=code, 3=name
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (step) {
                1 -> "Введите email"
                2 -> "Введите код"
                else -> "Как к Вам обращаться?"
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    1 -> {
                        Text("Мы отправим 6-значный код для подтверждения.")
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; error = null },
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
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
                    3 -> {
                        Text("Чьим именем подписывать комментарии?")
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; error = null },
                            label = { Text("Ваше имя") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                            when (step) {
                                1 -> {
                                    Api.service.requestCode(RequestCodeBody(email.trim()))
                                    step = 2
                                }
                                2 -> {
                                    val resp = Api.service.verifyCode(VerifyCodeBody(email.trim(), code.trim()))
                                    token = resp.token
                                    if (resp.has_name) {
                                        onTokenReceived(resp.token)
                                    } else {
                                        step = 3
                                    }
                                }
                                3 -> {
                                    Api.service.setName("Token $token", SetNameBody(name.trim()))
                                    onTokenReceived(token)
                                }
                            }
                        } catch (e: Exception) {
                            error = when (step) {
                                1 -> "Ошибка отправки. Проверьте email."
                                2 -> "Неверный или истёкший код."
                                else -> "Ошибка. Попробуйте ещё раз."
                            }
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && when (step) {
                    1 -> email.isNotBlank()
                    2 -> code.length == 6
                    else -> name.isNotBlank()
                }
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(when (step) {
                    1 -> "Отправить код"
                    2 -> "Подтвердить"
                    else -> "Сохранить"
                })
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
