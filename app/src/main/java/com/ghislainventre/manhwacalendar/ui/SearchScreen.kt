package com.ghislainventre.manhwacalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghislainventre.manhwacalendar.data.MangaSummary
import com.ghislainventre.manhwacalendar.data.Site
import com.ghislainventre.manhwacalendar.data.Source
import com.ghislainventre.manhwacalendar.data.TitleMatch

@Composable
fun SearchScreen(
    vm: MainViewModel,
    followedIds: Set<String>,
    followedTitles: Set<String>,
    sites: List<Site>,
    onSettings: () -> Unit,
) {
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { vm.loadRecommendations() }
    val search = {
        focus.clearFocus()
        vm.search()
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            ScreenHeader("Rechercher") { SettingsAction(onSettings) }
        }
        item(key = "field") {
            TextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Titre d'un manhwa…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (vm.siteResults.isEmpty()) {
            item(key = "hint") {
                val enabled = sites.filter { it.enabled }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        if (enabled.isEmpty()) {
                            "Aucun site activé pour la recherche."
                        } else {
                            "Recherche sur " + enabled.joinToString(", ") { it.name }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onSettings, contentPadding = PaddingValues(0.dp)) { Text("Gérer les sites") }
                }
            }
            recommendationItems(vm, followedIds)
        }
        if (vm.siteResults.isNotEmpty()) {
            val groups = vm.seriesResults
            item(key = "progress") { SearchProgress(vm.siteResults, empty = groups.isEmpty()) }
            // Une ligne par série : l'appli choisit elle-même le site qui a le plus de chapitres, puis le plus rapide.
            items(groups, key = { "series-${it.key}" }) { group ->
                val followed = group.hits.any { it.manga.id in followedIds } ||
                    TitleMatch.normalize(group.title) in followedTitles
                SearchResultRow(
                    group.best.copy(coverUrl = group.coverUrl),
                    followed = followed,
                    onFollow = { vm.follow(group) },
                    reason = if (group.hits.size > 1) {
                        "Sur ${group.hits.size} sites · lu sur ${group.best.sourceLabel}"
                    } else {
                        "Sur ${group.best.sourceLabel}"
                    },
                )
            }
        }
    }
}

/** « Pour toi » : manhwa populaires qui partagent les genres des séries suivies. */
private fun LazyListScope.recommendationItems(vm: MainViewModel, followedIds: Set<String>) {
    item(key = "reco-title") {
        Column {
            SectionTitle("Pour toi") {
                if (followedIds.isNotEmpty()) {
                    IconButton(onClick = { vm.loadRecommendations(force = true) }, enabled = !vm.recommendationsLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser les suggestions")
                    }
                }
            }
            val note = when {
                followedIds.isEmpty() -> "Suis quelques séries : des suggestions apparaîtront ici selon leurs genres."
                vm.recommendationsLoading -> null
                vm.recommendationsError != null -> "Suggestions indisponibles : ${vm.recommendationsError}"
                vm.recommendations.isEmpty() -> "Pas de suggestion pour l'instant."
                else -> "D'après les genres de tes séries, parmi les manhwa les plus suivis sur MangaDex"
            }
            if (vm.recommendationsLoading) {
                CircularProgressIndicator(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).size(24.dp), strokeWidth = 2.dp)
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
            }
        }
    }
    if (!vm.recommendationsLoading) {
        items(vm.recommendations, key = { "reco-${it.manga.id}" }) { reco ->
            SearchResultRow(
                reco.manga,
                followed = reco.manga.id in followedIds,
                onFollow = { vm.follow(reco.manga) },
                reason = "Comme tes séries : " + reco.reasons.joinToString(", ") { tagLabel(it) },
            )
        }
    }
}

/** Avancement de la recherche : combien de sites ont répondu, et combien sont en panne. */
@Composable
private fun SearchProgress(results: List<SiteResult>, empty: Boolean) {
    val done = results.count { !it.loading }
    val failed = results.count { it.error != null }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val searching = done < results.size
        if (searching) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            listOfNotNull(
                when {
                    searching -> "Recherche sur ${results.size} sites… ($done/${results.size})"
                    empty -> "Aucun résultat"
                    else -> "Résultats de ${results.size - failed} sites"
                },
                failed.takeIf { it > 0 }?.let { plural(it, "site indisponible", "sites indisponibles") },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultRow(manga: MangaSummary, followed: Boolean, onFollow: () -> Unit, reason: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(manga.coverUrl, manga.title, Modifier.size(width = 52.dp, height = 74.dp), corner = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                if (manga.source == Source.WEB) {
                    listOfNotNull(manga.latestChapter).joinToString(" · ")
                } else {
                    listOfNotNull(manga.year?.toString(), statusLabel(manga.status), manga.originalLanguage?.uppercase())
                        .joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (reason != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    reason,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (followed) {
            FilledTonalButton(onClick = {}, enabled = false, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Suivi")
            }
        } else {
            Button(onClick = onFollow, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Suivre") }
        }
    }
}
