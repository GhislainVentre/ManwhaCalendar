package com.ghislainventre.manhwacalendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghislainventre.manhwacalendar.data.FollowedSeries

/** Bibliothèque : les séries suivies en mosaïque de couvertures, les nouveautés en premier. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    followed: List<FollowedSeries>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (FollowedSeries) -> Unit,
    onSearch: () -> Unit,
    onMarkAllSeen: () -> Unit,
) {
    val sorted = followed.sortedWith(compareByDescending<FollowedSeries> { it.hasNew }.thenBy { it.title.lowercase() })
    val newCount = followed.count { it.hasNew }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                ScreenHeader(
                    "Mes séries",
                    subtitle = when {
                        followed.isEmpty() -> null
                        newCount > 0 -> plural(followed.size, "série", "séries") + " · " +
                            plural(newCount, "nouveauté", "nouveautés")
                        else -> plural(followed.size, "série suivie", "séries suivies")
                    },
                    start = 4.dp,
                    end = 0.dp,
                ) {
                    RefreshAction(refreshing, onRefresh)
                    SettingsAction(onSettings)
                }
            }
            if (newCount > 0) {
                item(key = "mark-all", span = { GridItemSpan(maxLineSpan) }) {
                    Row {
                        TextButton(onClick = onMarkAllSeen) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tout marquer comme vu")
                        }
                    }
                }
            }
            if (followed.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Default.Favorite,
                        title = "Aucune série suivie",
                        body = "Ajoute tes manhwa préférés depuis la recherche, ils apparaîtront ici.",
                        action = "Trouver une série",
                        onAction = onSearch,
                    )
                }
            }
            items(sorted, key = { it.id }) { LibraryTile(it, onOpen) }
        }
    }
}

@Composable
private fun LibraryTile(series: FollowedSeries, onOpen: (FollowedSeries) -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(series) }) {
        Box {
            Cover(series.coverUrl, series.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), corner = 14.dp)
            if (series.hasNew) NewBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            series.nextEstimate?.let {
                Pill(
                    shortUntil(it.localDate()),
                    container = Color.Black.copy(alpha = 0.7f),
                    content = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            series.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(
                series.latestChapter?.let { chapterLabel(it.number) } ?: "Aucun chapitre",
                series.unreadCount?.let { if (it == 0) "à jour" else plural(it, "non lu", "non lus") },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
