package com.ghislainventre.manhwacalendar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ghislainventre.manhwacalendar.data.ChapterLanguage
import com.ghislainventre.manhwacalendar.data.FollowedSeries
import com.ghislainventre.manhwacalendar.data.MangaSummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private enum class Tab(val label: String) { CALENDAR("Calendrier"), SERIES("Mes séries"), SEARCH("Rechercher") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManhwaCalendarScreen(vm: MainViewModel = viewModel()) {
    val followed by vm.followed.collectAsState()
    val language by vm.language.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.CALENDAR) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val selected = followed.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }
    val openSeries: (FollowedSeries) -> Unit = {
        selectedId = it.id
        vm.markSeen(it.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.title ?: "Manhwa Calendar", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = { selectedId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !vm.refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Vérifier les sorties")
                    }
                    LanguageMenu(language, vm::setLanguage)
                },
            )
        },
        bottomBar = {
            if (selected == null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.CALENDAR -> Icons.Default.DateRange
                                        Tab.SERIES -> Icons.Default.Favorite
                                        Tab.SEARCH -> Icons.Default.Search
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                selected != null -> SeriesDetail(
                    series = selected,
                    onOpen = { context.openUrl(it) },
                    onUnfollow = {
                        vm.unfollow(selected.id)
                        selectedId = null
                    },
                )
                tab == Tab.SEARCH -> SearchScreen(vm, followed.map { it.id }.toSet())
                else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh) {
                    if (followed.isEmpty()) {
                        EmptyState { tab = Tab.SEARCH }
                    } else if (tab == Tab.CALENDAR) {
                        CalendarScreen(followed, openSeries)
                    } else {
                        SeriesList(followed, openSeries)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageMenu(current: ChapterLanguage, onSelect: (ChapterLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Langue des chapitres")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Langue des chapitres",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ChapterLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.label) },
                    leadingIcon = { if (lang == current) Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onSelect(lang)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onSearch: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp)) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(48.dp))
                Text("Aucune série suivie", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recherchez vos manhwa pour voir leurs prochaines sorties et être notifié des nouveaux chapitres.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSearch) { Text("Rechercher une série") }
            }
        }
    }
}

// --- Calendrier ---

@Composable
private fun CalendarScreen(followed: List<FollowedSeries>, onOpen: (FollowedSeries) -> Unit) {
    val now = Instant.now()
    val recent = followed
        .filter { s -> s.latestChapter?.let { Duration.between(it.readableAt, now).toDays() < 7 } == true }
        .sortedByDescending { it.latestChapter!!.readableAt }
    val upcoming = followed.filter { it.nextEstimate != null }.sortedBy { it.nextEstimate }
    val unknown = followed.filter { it.nextEstimate == null }.sortedBy { it.title.lowercase() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (recent.isNotEmpty()) {
            section("Sortis cette semaine")
            items(recent, key = { "recent-${it.id}" }) { s ->
                val c = s.latestChapter!!
                SeriesRow(s, chapterLabel(c.number) + " · " + relativePast(c.readableAt), null, onOpen)
            }
        }
        upcoming.groupBy { it.nextEstimate!!.localDate() }.forEach { (date, list) ->
            section(dayHeader(date))
            items(list, key = { "next-${it.id}" }) { s ->
                SeriesRow(s, "Prochain : ${nextChapterLabel(s)} (estimé)", rhythm(s.intervalDays), onOpen)
            }
        }
        if (unknown.isNotEmpty()) {
            section("Date inconnue")
            items(unknown, key = { "unknown-${it.id}" }) { s ->
                SeriesRow(s, unknownReason(s), s.latestChapter?.let { "Dernier : ${chapterLabel(it.number)}" }, onOpen)
            }
        }
    }
}

private fun LazyListScope.section(title: String) {
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

private fun chapterLabel(number: String?) = number?.let { "Ch. $it" } ?: "Oneshot"

private fun nextChapterLabel(s: FollowedSeries): String {
    val n = s.latestChapter?.number?.toDoubleOrNull() ?: return "nouveau chapitre"
    return "Ch. ${n.toInt() + 1}"
}

private fun unknownReason(s: FollowedSeries) = when {
    s.isFinished -> "Série terminée"
    s.lastCheckedAt == null -> "Pas encore vérifiée"
    s.recentChapters.size < 2 -> "Pas assez de chapitres traduits"
    else -> "En pause ou rythme irrégulier"
}

// --- Mes séries ---

@Composable
private fun SeriesList(followed: List<FollowedSeries>, onOpen: (FollowedSeries) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(followed.sortedBy { it.title.lowercase() }, key = { it.id }) { s ->
            val last = s.latestChapter?.let { "Dernier : ${chapterLabel(it.number)} · ${relativePast(it.readableAt)}" }
                ?: "Aucun chapitre trouvé"
            val next = s.nextEstimate?.let { "Prochain estimé : ${shortDate(it)}" } ?: unknownReason(s)
            SeriesRow(s, last, next, onOpen)
        }
    }
}

@Composable
private fun SeriesRow(series: FollowedSeries, line1: String, line2: String?, onOpen: (FollowedSeries) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(series) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(series.coverUrl, Modifier.size(width = 48.dp, height = 68.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(series.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line1, style = MaterialTheme.typography.bodyMedium)
            if (line2 != null) {
                Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (series.hasNew) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
        }
    }
}

@Composable
private fun Cover(url: String?, modifier: Modifier) {
    val shaped = modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = shaped)
    } else {
        Box(shaped)
    }
}

// --- Détail ---

@Composable
private fun SeriesDetail(series: FollowedSeries, onOpen: (String) -> Unit, onUnfollow: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row {
                Cover(series.coverUrl, Modifier.size(width = 110.dp, height = 156.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(series.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Statut : ${statusLabel(series.status)}", style = MaterialTheme.typography.bodyMedium)
                    rhythm(series.intervalDays)?.let { Text("Rythme : $it", style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        series.nextEstimate?.let { "Prochain chapitre estimé : ${dayHeader(it.localDate()).lowercase()}" }
                            ?: unknownReason(series),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    series.lastCheckedAt?.let {
                        Text("Vérifié ${relativePast(it)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(series.url) }) { Text("Ouvrir sur MangaDex") }
                OutlinedButton(onClick = onUnfollow) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Ne plus suivre")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Derniers chapitres", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }
        if (series.recentChapters.isEmpty()) {
            item { Text("Aucun chapitre traduit trouvé dans la langue choisie.") }
        }
        items(series.recentChapters, key = { it.id }) { c ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(c.url) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    chapterLabel(c.number) + (c.title?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${dayHeader(c.readableAt.localDate(), LocalDate.now())} · ${c.language.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

private fun statusLabel(status: String?) = when (status) {
    "ongoing" -> "en cours"
    "completed" -> "terminée"
    "hiatus" -> "en pause"
    "cancelled" -> "annulée"
    else -> "inconnu"
}

// --- Recherche ---

@Composable
private fun SearchScreen(vm: MainViewModel, followedIds: Set<String>) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            label = { Text("Titre du manhwa") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = vm::search) { Icon(Icons.Default.Search, contentDescription = "Rechercher") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        FilterChip(
            selected = vm.manhwaOnly,
            onClick = vm::toggleManhwaOnly,
            label = { Text("Manhwa (coréen) uniquement") },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (vm.searching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(vm.results, key = { it.id }) { manga ->
                SearchResult(manga, followed = manga.id in followedIds, onFollow = { vm.follow(manga) })
            }
        }
    }
}

@Composable
private fun SearchResult(manga: MangaSummary, followed: Boolean, onFollow: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(manga.coverUrl, Modifier.size(width = 48.dp, height = 68.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(manga.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(manga.year?.toString(), statusLabel(manga.status), manga.originalLanguage?.uppercase())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onFollow, enabled = !followed) {
                Icon(
                    if (followed) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (followed) "Déjà suivi" else "Suivre",
                )
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
