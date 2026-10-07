package com.ghislainventre.manhwacalendar.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
            val result = snackbar.showSnackbar(
                it.text,
                actionLabel = it.action,
                duration = if (it.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) it.onAction()
            vm.consumeMessage()
        }
    }

    // Les notifications ne servent qu'une fois une série suivie : on les demande à ce moment-là,
    // plutôt qu'au tout premier lancement, quand la demande n'a pas encore de sens.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val followsSomething = followed.isNotEmpty()
    LaunchedEffect(followsSomething) {
        if (followsSomething && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    val userRefresh = { vm.refresh(userInitiated = true) }
    // Garde l'état de chaque écran (défilement, sections ouvertes) quand on ouvre une fiche puis qu'on revient.
    val screenStates = rememberSaveableStateHolder()

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
                screenStates.SaveableStateProvider(screen) {
                    when (screen) {
                        "tab:${Tab.AGENDA.name}" -> AgendaScreen(
                            followed, vm.refreshing, userRefresh, { showSettings = true }, open, goSearch,
                        )
                        "tab:${Tab.LIBRARY.name}" -> LibraryScreen(
                            followed, vm.refreshing, userRefresh, { showSettings = true }, open, goSearch,
                            onMarkAllSeen = vm::markAllSeen,
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
                                    vm.unfollow(series)
                                    selectedId = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
