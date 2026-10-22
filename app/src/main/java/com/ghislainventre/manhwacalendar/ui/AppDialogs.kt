package com.ghislainventre.manhwacalendar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ghislainventre.manhwacalendar.R
import com.ghislainventre.manhwacalendar.tr
import com.ghislainventre.manhwacalendar.update.AppRelease
import com.ghislainventre.manhwacalendar.update.AppUpdater

/**
 * Propose la nouvelle version trouvée sur GitHub avec ses nouveautés, puis montre le téléchargement
 * et l'ouverture de l'installeur. Les erreurs restent dans la fenêtre, avec « Réessayer ».
 */
@Composable
fun UpdateDialog(vm: MainViewModel, release: AppRelease) {
    val context = LocalContext.current
    val step = vm.updateStep
    val installed = remember { AppUpdater.installedVersionCode(context) }
    val changes = remember(release) { release.changesSince(installed) }
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = vm::postponeUpdate) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_update),
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column {
                    Text(tr("Nouvelle version", "New version"), style = MaterialTheme.typography.titleLarge)
                    val details = listOfNotNull(
                        release.versionName?.let { "Version $it" },
                        release.sizeBytes?.let(AppRelease::formatSize),
                    )
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }

                if (changes.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceContainerLowest)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(tr("Nouveautés", "What's new"), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        changes.forEachIndexed { index, note ->
                            if (index > 0) Spacer(Modifier.height(8.dp))
                            // Plusieurs versions sautées : chacune garde son numéro.
                            if (changes.size > 1 && note.versionName.isNotEmpty()) {
                                Text("Version ${note.versionName}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            }
                            Text(note.title, style = MaterialTheme.typography.titleSmall)
                            if (note.body.isNotEmpty()) Text(note.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                when (step) {
                    is UpdateStep.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (step.progress != null) {
                            LinearProgressIndicator(progress = { step.progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                        }
                        Text(
                            tr("Téléchargement…", "Downloading…") + (step.progress?.let { " ${(it * 100).toInt()} %" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    UpdateStep.Installing -> Text(
                        tr("Ouverture de l'installation…", "Opening the installer…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    is UpdateStep.Failed -> Text(step.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
                    UpdateStep.Idle -> Unit
                }

                Text(
                    tr(
                        "La première fois, Android demande d'autoriser Manhwa Calendar à installer des applications : accepte, puis confirme l'installation. Tes séries suivies sont conservées.",
                        "The first time, Android asks you to allow Manhwa Calendar to install apps: accept, then confirm the installation. Your followed series are kept.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                Button(
                    onClick = vm::installUpdate,
                    enabled = !step.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_update), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (step is UpdateStep.Failed) tr("Réessayer", "Try again") else tr("Mettre à jour", "Update"))
                }
                TextButton(onClick = vm::postponeUpdate, enabled = !step.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Plus tard", "Later"))
                }
            }
        }
    }
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
