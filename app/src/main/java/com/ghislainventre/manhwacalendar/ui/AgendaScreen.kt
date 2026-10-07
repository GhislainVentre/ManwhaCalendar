package com.ghislainventre.manhwacalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghislainventre.manhwacalendar.data.FollowedSeries
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Écran d'accueil : la semaine en un coup d'œil, les derniers chapitres, puis les sorties à venir jour par jour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    followed: List<FollowedSeries>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (FollowedSeries) -> Unit,
    onSearch: () -> Unit,
) {
    val today = LocalDate.now()
    val now = Instant.now()
    val fresh = followed
        .filter { s -> s.hasNew || s.latestChapter?.let { Duration.between(it.readableAt, now).toDays() < 7 } == true }
        .sortedWith(compareByDescending<FollowedSeries> { it.hasNew }.thenByDescending { it.latestChapter?.readableAt })
    val byDay = followed
        .filter { it.nextEstimate != null }
        .sortedBy { it.nextEstimate }
        .groupBy { it.nextEstimate!!.localDate() }
    val unknown = followed.filter { it.nextEstimate == null }.sortedBy { it.title.lowercase() }
    val thisWeek = byDay.filterKeys { it.isBefore(today.plusDays(7)) }.values.sumOf { it.size }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showUnknown by rememberSaveable { mutableStateOf(false) }

    // Position de chaque jour dans la liste, pour y sauter depuis le bandeau de la semaine.
    var index = 2 + (if (fresh.isNotEmpty()) 2 else 0) + (if (byDay.isNotEmpty()) 1 else 0)
    val dayIndex = byDay.keys.associateWith { index++ }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                ScreenHeader(
                    "Agenda",
                    subtitle = when {
                        followed.isEmpty() -> null
                        thisWeek == 0 -> "Rien de prévu cette semaine"
                        else -> plural(thisWeek, "sortie prévue", "sorties prévues") + " cette semaine"
                    },
                ) {
                    RefreshAction(refreshing, onRefresh)
                    SettingsAction(onSettings)
                }
            }
            if (followed.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.DateRange,
                        title = "Ton agenda est vide",
                        body = "Suis tes manhwa pour voir ici leurs prochaines sorties et être prévenu à chaque nouveau chapitre.",
                        action = "Trouver une série",
                        onAction = onSearch,
                    )
                }
                return@LazyColumn
            }
            item(key = "week") {
                WeekStrip(today, byDay.mapValues { it.value.size }) { date ->
                    dayIndex[date]?.let { scope.launch { listState.animateScrollToItem(it) } }
                }
            }
            if (fresh.isNotEmpty()) {
                item(key = "fresh-title") { SectionTitle("Nouveaux chapitres") }
                item(key = "fresh") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(fresh, key = { it.id }) { FreshCard(it, onOpen) }
                    }
                }
            }
            if (byDay.isNotEmpty()) {
                item(key = "upcoming-title") { SectionTitle("À venir") }
            }
            byDay.forEach { (date, list) ->
                item(key = "day-$date") { DayBlock(date, today, list, onOpen) }
            }
            if (unknown.isNotEmpty()) {
                item(key = "unknown-title") {
                    SectionTitle("Sans date prévue") {
                        TextButton(onClick = { showUnknown = !showUnknown }) {
                            Text(if (showUnknown) "Masquer" else "Voir (${unknown.size})")
                            Icon(
                                if (showUnknown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (showUnknown) {
                    items(unknown, key = { "unknown-${it.id}" }) { s ->
                        SeriesCard(
                            s,
                            headline = unknownReason(s),
                            meta = s.latestChapter?.let { "Dernier : ${chapterLabel(it.number)} · ${relativePast(it.readableAt)}" },
                            onOpen = onOpen,
                            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, counts: Map<LocalDate, Int>, onDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (offset in 0L until 7L) {
            val date = today.plusDays(offset)
            val count = counts[date] ?: 0
            val isToday = offset == 0L
            val content = if (isToday) colors.onPrimary else colors.onSurface
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isToday) colors.primary else colors.surfaceContainer)
                    .clickable(enabled = count > 0) { onDay(date) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = longDate(date) + ", " +
                            if (count == 0) "aucune sortie" else plural(count, "sortie prévue", "sorties prévues")
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekdayShort(date), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = content)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                count == 0 -> Color.Transparent
                                isToday -> colors.onPrimary
                                else -> colors.primary
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun FreshCard(series: FollowedSeries, onOpen: (FollowedSeries) -> Unit) {
    val chapter = series.latestChapter
    Column(
        Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen(series) },
    ) {
        Box {
            Cover(series.coverUrl, series.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), corner = 14.dp)
            if (chapter != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 8.dp),
                ) {
                    Text(chapterLabel(chapter.number), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Text(
                        relativePast(chapter.readableAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            if (series.hasNew) NewBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            series.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayBlock(date: LocalDate, today: LocalDate, list: List<FollowedSeries>, onOpen: (FollowedSeries) -> Unit) {
    val isToday = date == today
    val accent = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
        Column(Modifier.width(56.dp).padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                weekdayShort(date).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, color = accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                dayCaption(date, today),
                style = MaterialTheme.typography.labelLarge,
                color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            list.forEach { s ->
                SeriesCard(
                    s,
                    headline = nextChapterLabel(s),
                    meta = listOfNotNull(rhythm(s.intervalDays)?.replaceFirstChar { it.uppercase() }, s.sourceLabel)
                        .joinToString(" · "),
                    onOpen = onOpen,
                )
            }
        }
    }
}
