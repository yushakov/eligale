package gallery.eliza.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.RequestCodeBody
import gallery.eliza.app.data.SetNameBody
import gallery.eliza.app.data.VerifyCodeBody
import kotlinx.coroutines.launch

@Composable
fun AccountDialog(
    token: String,
    onSignOut: () -> Unit,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var originalName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var loadingProfile by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val profile = Api.service.getProfile("Token $token")
            email = profile.email
            originalName = profile.display_name
            name = profile.display_name
        } catch (e: Exception) {
            error = "Не удалось загрузить профиль"
        } finally {
            loadingProfile = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Аккаунт", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (loadingProfile) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {},
                        label = { Text("Email") },
                        enabled = false,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text("Имя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))

                    if (name.trim() != originalName && name.isNotBlank()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    error = null
                                    try {
                                        val resp = Api.service.setName("Token $token", SetNameBody(name.trim()))
                                        originalName = resp.display_name
                                        name = resp.display_name
                                    } catch (e: Exception) {
                                        error = "Ошибка сохранения"
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Обновить")
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = { showSignOutConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Выйти") }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Удалить аккаунт") }
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Выйти?") },
            text = { Text("Вы уверены?") },
            confirmButton = {
                Button(onClick = { showSignOutConfirm = false; onSignOut() }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Отмена") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Все ваши данные будут удалены безвозвратно.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            deleting = true
                            try {
                                Api.service.deleteAccount("Token $token")
                                onDeleted()
                            } catch (e: Exception) {
                                showDeleteConfirm = false
                                error = "Ошибка удаления"
                                deleting = false
                            }
                        }
                    },
                    enabled = !deleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (deleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

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
