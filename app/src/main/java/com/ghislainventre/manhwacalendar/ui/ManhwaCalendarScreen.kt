package com.ghislainventre.manhwacalendar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghislainventre.manhwacalendar.data.FollowedSeries

private enum class Tab(val label: String, val icon: ImageVector) {
    AGENDA("Agenda", Icons.Default.DateRange),
    LIBRARY("Mes séries", Icons.Default.Favorite),
    SEARCH("Rechercher", Icons.Default.Search),
}

@Composable
fun ManhwaCalendarScreen(vm: MainViewModel = viewModel()) {
    val followed by vm.followed.collectAsState()
    val sites by vm.sites.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.AGENDA) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }

    val selected = followed.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }
    BackHandler(enabled = selected == null && tab != Tab.AGENDA) { tab = Tab.AGENDA }
    val open: (FollowedSeries) -> Unit = {
        selectedId = it.id
        vm.markSeen(it.id)
    }
    val goSearch = { tab = Tab.SEARCH }
    val newCount = followed.count { it.hasNew }

    Scaffold(
        // Chaque écran gère lui-même la barre d'état, pour que la fiche d'une série passe dessous.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selected == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                if (t == Tab.LIBRARY && newCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$newCount") } }) {
                                        Icon(t.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(t.icon, contentDescription = null)
                                }
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar, if (selected != null) Modifier.navigationBarsPadding() else Modifier)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Crossfade(
                targetState = selected?.let { "series:${it.id}" } ?: "tab:${tab.name}",
                animationSpec = tween(200),
                label = "screen",
            ) { screen ->
                when (screen) {
                    "tab:${Tab.AGENDA.name}" -> AgendaScreen(
                        followed, vm.refreshing, vm::refresh, { showSettings = true }, open, goSearch,
                    )
                    "tab:${Tab.LIBRARY.name}" -> LibraryScreen(
                        followed, vm.refreshing, vm::refresh, { showSettings = true }, open, goSearch,
                    )
                    "tab:${Tab.SEARCH.name}" -> SearchScreen(
                        vm, followed.map { it.id }.toSet(), sites, onSettings = { showSettings = true },
                    )
                    else -> followed.firstOrNull { "series:${it.id}" == screen }?.let { series ->
                        SeriesDetailScreen(
                            series = series,
                            onBack = { selectedId = null },
                            onOpenUrl = { context.openUrl(it) },
                            onUnfollow = {
                                vm.unfollow(series.id)
                                selectedId = null
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
