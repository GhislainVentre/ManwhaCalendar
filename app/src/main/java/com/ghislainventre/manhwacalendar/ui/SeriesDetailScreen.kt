package com.ghislainventre.manhwacalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ghislainventre.manhwacalendar.data.Chapter
import com.ghislainventre.manhwacalendar.data.FollowedSeries

/** Fiche d'une série : grande couverture, prochaine sortie mise en avant, bouton de lecture, chapitres. */
@Composable
fun SeriesDetailScreen(
    series: FollowedSeries,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onUnfollow: () -> Unit,
) {
    var confirmUnfollow by remember { mutableStateOf(false) }
    if (confirmUnfollow) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text("Ne plus suivre ?") },
            text = { Text("${series.title} sera retirée de vos séries et vous ne serez plus prévenu de ses sorties.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnfollow = false
                    onUnfollow()
                }) { Text("Retirer") }
            },
            dismissButton = { TextButton(onClick = { confirmUnfollow = false }) { Text("Annuler") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "hero") { Hero(series, onBack) }
        item(key = "next") { NextReleaseCard(series, Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Button(onClick = { onOpenUrl(series.url) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lire sur ${series.sourceLabel}", style = MaterialTheme.typography.titleSmall)
                }
                TextButton(
                    onClick = { confirmUnfollow = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Ne plus suivre") }
            }
        }
        item(key = "chapters-title") {
            SectionTitle("Chapitres") {
                series.lastCheckedAt?.let {
                    Text(
                        "Vérifié ${relativePast(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        if (series.recentChapters.isEmpty()) {
            item(key = "no-chapters") {
                Text(
                    "Aucun chapitre trouvé pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(series.recentChapters, key = { it.id }) { c -> ChapterRow(c) { onOpenUrl(c.url) } }
    }
}

@Composable
private fun Hero(series: FollowedSeries, onBack: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth()) {
        // La couverture floutée en fond, qui se fond dans l'arrière-plan.
        if (series.coverUrl != null) {
            AsyncImage(
                model = series.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(32.dp).alpha(0.5f),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0.2f), background))),
        )
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Cover(
                series.coverUrl,
                series.title,
                Modifier
                    .size(width = 150.dp, height = 214.dp)
                    .shadow(20.dp, RoundedCornerShape(16.dp)),
                corner = 16.dp,
            )
            Spacer(Modifier.height(20.dp))
            Text(series.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(
                    series.sourceLabel,
                    series.status?.let { statusLabel(it).replaceFirstChar { c -> c.uppercase() } },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
        }
    }
}

@Composable
private fun NextReleaseCard(series: FollowedSeries, modifier: Modifier = Modifier) {
    val next = series.nextEstimate
    val colors = MaterialTheme.colorScheme
    val content = if (next != null) colors.onPrimaryContainer else colors.onSurface
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (next != null) colors.primaryContainer else colors.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("PROCHAIN CHAPITRE", style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
        if (next != null) {
            val date = next.localDate()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    untilLabel(date),
                    style = MaterialTheme.typography.headlineSmall,
                    color = content,
                    modifier = Modifier.weight(1f),
                )
                Text(nextChapterLabel(series), style = MaterialTheme.typography.titleMedium, color = content)
            }
            Text(longDate(date), style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
            Text(
                "Estimé d'après le rythme " + (rhythm(series.intervalDays) ?: "des dernières sorties"),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.65f),
            )
        } else {
            Text(unknownReason(series), style = MaterialTheme.typography.titleMedium, color = content)
            Text(
                "Vous serez quand même prévenu dès qu'un nouveau chapitre sort.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(chapterLabel(chapter.number), style = MaterialTheme.typography.titleSmall)
            chapter.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                relativePast(chapter.readableAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                chapter.language.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
