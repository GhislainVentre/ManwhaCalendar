package com.ghislainventre.manhwacalendar.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ghislainventre.manhwacalendar.update.AppUpdater

/** Propose la nouvelle version trouvée sur GitHub, puis montre le téléchargement. */
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val progress = vm.updateProgress
    AlertDialog(
        onDismissRequest = { if (progress == null) vm.postponeUpdate() },
        title = { Text("Nouvelle version disponible") },
        text = {
            Column {
                Text(
                    if (progress == null) {
                        "Une mise à jour de Manhwa Calendar est prête. Tes séries suivies sont conservées."
                    } else {
                        "Téléchargement… ${(progress * 100).toInt()} %"
                    },
                )
                if (progress != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::installUpdate, enabled = progress == null) { Text("Mettre à jour") }
        },
        dismissButton = {
            TextButton(onClick = vm::postponeUpdate, enabled = progress == null) { Text("Plus tard") }
        },
    )
}

/** Petit formulaire de retour : le texte part par l'appli de messagerie choisie (SMS, WhatsApp, mail…). */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Envoyer un retour") },
        text = {
            Column {
                Text("Un bug, une idée, un site qui ne marche pas ? Écris-le ici, puis choisis comment l'envoyer.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Ton message") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.shareFeedback(text.trim())
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text("Envoyer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun Context.shareFeedback(text: String) {
    val body = "$text\n\n— ${AppUpdater.deviceSummary(this)}"
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Retour sur Manhwa Calendar")
        .putExtra(Intent.EXTRA_TEXT, body)
    runCatching { startActivity(Intent.createChooser(send, "Envoyer le retour avec…")) }
}
