package com.stayfocused.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkText

@Composable
fun SettingsBackupCard(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Encrypted Backup & Restore",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkText
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Export or restore app limits, blocked domains, and profiles via AES-256 encrypted file. Recovery codes and active strict mode are excluded.",
                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 12.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkText)
                ) {
                    Text("Export", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonkEmber,
                        contentColor = MonkInk
                    )
                ) {
                    Text("Import", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ExportPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonkCard,
        title = {
            Text(
                text = "Encrypt Backup",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkText
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose a strong passphrase to encrypt your settings backup. You will need this passphrase to restore your settings.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Passphrase (min 6 characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MonkEmber,
                        unfocusedBorderColor = MonkLine,
                        focusedTextColor = MonkText,
                        unfocusedTextColor = MonkText,
                        focusedLabelColor = MonkEmber,
                        unfocusedLabelColor = MonkMuted
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MonkEmber,
                        unfocusedBorderColor = MonkLine,
                        focusedTextColor = MonkText,
                        unfocusedTextColor = MonkText,
                        focusedLabelColor = MonkEmber,
                        unfocusedLabelColor = MonkMuted
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MonkDanger,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.length < 6) {
                        errorMessage = "Passphrase must be at least 6 characters"
                    } else if (password != confirmPassword) {
                        errorMessage = "Passphrases do not match"
                    } else {
                        onConfirm(password)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonkEmber,
                    contentColor = MonkInk
                )
            ) {
                Text("Export File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MonkMuted)
            }
        }
    )
}

@Composable
fun ImportPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonkCard,
        title = {
            Text(
                text = "Decrypt Backup",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkText
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the passphrase used to encrypt this backup file.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MonkEmber,
                        unfocusedBorderColor = MonkLine,
                        focusedTextColor = MonkText,
                        unfocusedTextColor = MonkText,
                        focusedLabelColor = MonkEmber,
                        unfocusedLabelColor = MonkMuted
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MonkDanger,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isEmpty()) {
                        errorMessage = "Passphrase cannot be empty"
                    } else {
                        onConfirm(password)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonkEmber,
                    contentColor = MonkInk
                )
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MonkMuted)
            }
        }
    )
}
