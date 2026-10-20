package com.ghislainventre.manhwacalendar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.ghislainventre.manhwacalendar.tr
import com.ghislainventre.manhwacalendar.update.AppUpdater

/** Propose la nouvelle version trouvée sur GitHub, puis montre le téléchargement. */
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val progress = vm.updateProgress
    AlertDialog(
        onDismissRequest = { if (progress == null) vm.postponeUpdate() },
        title = { Text(tr("Nouvelle version disponible", "New version available")) },
        text = {
            Column {
                Text(
                    if (progress == null) {
                        tr(
                            "Une mise à jour de Manhwa Calendar est prête. Tes séries suivies sont conservées.",
                            "A Manhwa Calendar update is ready. Your followed series are kept.",
                        )
                    } else {
                        tr("Téléchargement…", "Downloading…") + " ${(progress * 100).toInt()} %"
                    },
                )
                if (progress != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::installUpdate, enabled = progress == null) { Text(tr("Mettre à jour", "Update")) }
        },
        dismissButton = {
            TextButton(onClick = vm::postponeUpdate, enabled = progress == null) { Text(tr("Plus tard", "Later")) }
        },
    )
}

/** Petit formulaire de retour : le texte part par mail à Ghislain, depuis l'appli mail du téléphone. */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Envoyer un retour", "Send feedback")) },
        text = {
            Column {
                Text(
                    tr(
                        "Un bug, une idée, un site qui ne marche pas ? Écris-le ici, ton appli mail s'ouvrira avec le message prêt à partir.",
                        "A bug, an idea, a site that doesn't work? Write it here and your mail app will open with the message ready to send.",
                    ),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(tr("Ton message", "Your message")) },
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
            ) { Text(tr("Envoyer", "Send")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler", "Cancel")) } },
    )
}

/** Les retours arrivent par mail chez Ghislain ; le préfixe du sujet sert au filtre Gmail. */
private const val FEEDBACK_ADDRESS = "ghislain.ventre@gmail.com"
private const val FEEDBACK_SUBJECT = "[Manhwa Calendar] Retour"

private fun Context.shareFeedback(text: String) {
    val body = "$text\n\n— ${AppUpdater.deviceSummary(this)}"
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_ADDRESS))
        .putExtra(Intent.EXTRA_SUBJECT, FEEDBACK_SUBJECT)
        .putExtra(Intent.EXTRA_TEXT, body)
    // Sans appli mail, on passe par le menu de partage avec l'adresse dans le texte.
    val share = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_ADDRESS))
        .putExtra(Intent.EXTRA_SUBJECT, FEEDBACK_SUBJECT)
        .putExtra(Intent.EXTRA_TEXT, tr("À envoyer à", "Send to") + " $FEEDBACK_ADDRESS\n\n$body")
    runCatching { startActivity(mail) }
        .recoverCatching { startActivity(Intent.createChooser(share, tr("Envoyer le retour avec…", "Send feedback with…"))) }
}
