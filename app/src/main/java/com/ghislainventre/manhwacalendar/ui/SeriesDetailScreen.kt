package com.ghislainventre.manhwacalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onSetRead: (Chapter, Boolean) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "hero") { Hero(series, onBack) }
        item(key = "next") { NextReleaseCard(series, Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                // Le geste le plus fréquent, en un seul appui : reprendre au premier chapitre non lu,
                // ou lire le dernier chapitre.
                val latest = series.latestChapter
                val next = series.nextToRead
                Button(
                    onClick = { onOpenUrl(next?.url ?: latest?.url ?: series.url) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            next != null && next.id != latest?.id ->
                                if (next.number != null) "Continuer au chapitre ${next.number}" else "Continuer la lecture"
                            latest == null -> "Ouvrir sur ${series.sourceLabel}"
                            latest.number != null -> "Lire le chapitre ${latest.number}"
                            else -> "Lire le dernier chapitre"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (latest != null) {
                    OutlinedButton(
                        onClick = { onOpenUrl(series.url) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                    ) { Text("Voir la série sur ${series.sourceLabel}") }
                }
                TextButton(
                    onClick = onUnfollow,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Ne plus suivre") }
            }
        }
        item(key = "chapters-title") {
            SectionTitle("Chapitres") {
                val unread = series.unreadCount
                if (series.recentChapters.isNotEmpty() && unread != 0) {
                    TextButton(onClick = onMarkAllRead) { Text("Tout marquer lu") }
                } else series.lastCheckedAt?.let {
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
        items(series.recentChapters, key = { it.id }) { c ->
            val read = series.isRead(c)
            ChapterRow(c, read, onClick = { onOpenUrl(c.url) }, onToggleRead = { onSetRead(c, !read) })
        }
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
                "Tu seras quand même prévenu dès qu'un nouveau chapitre sort.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, read: Boolean, onClick: () -> Unit, onToggleRead: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReadToggle(read, onToggleRead)
        // Les chapitres lus passent au second plan.
        val dim = Modifier.alpha(if (read) 0.5f else 1f)
        Column(Modifier.weight(1f).padding(vertical = 10.dp).then(dim)) {
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
        Column(dim, horizontalAlignment = Alignment.End) {
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

/** Pastille « lu » : cochée quand le chapitre est lu ; un appui marque aussi les chapitres précédents. */
@Composable
private fun ReadToggle(read: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = onToggle) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .semantics { contentDescription = if (read) "Lu, appuyer pour marquer non lu" else "Marquer comme lu" }
                .then(
                    if (read) Modifier.background(colors.primary)
                    else Modifier.border(2.dp, colors.outline, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (read) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
