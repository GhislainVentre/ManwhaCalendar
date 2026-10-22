package com.ghislainventre.manhwacalendar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghislainventre.manhwacalendar.ManhwaCalendarApp
import com.ghislainventre.manhwacalendar.data.Chapter
import com.ghislainventre.manhwacalendar.data.ChapterLanguage
import com.ghislainventre.manhwacalendar.data.FollowedSeries
import com.ghislainventre.manhwacalendar.data.MangaSummary
import com.ghislainventre.manhwacalendar.data.Network
import com.ghislainventre.manhwacalendar.data.Recommendation
import com.ghislainventre.manhwacalendar.data.RecommendationFilter
import com.ghislainventre.manhwacalendar.data.SearchGrouping
import com.ghislainventre.manhwacalendar.data.SeriesGroup
import com.ghislainventre.manhwacalendar.tr
import com.ghislainventre.manhwacalendar.update.AppUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private const val STARTUP_REFRESH_AFTER_MIN = 60L
/** Après « Plus tard », on ne repropose la mise à jour qu'au bout de ce délai. */
private const val UPDATE_SNOOZE_HOURS = 24L
/** Suggestions affichées au plus, et séries dont on compte les chapitres en même temps. */
private const val MAX_RECOMMENDATIONS = 20
private const val COUNT_BATCH = 3

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ManhwaCalendarApp).repository

    val followed = repository.followed
    val language = repository.language
    val sites = repository.sites

    var refreshing by mutableStateOf(false)
        private set
    var message by mutableStateOf<UiMessage?>(null)
        private set

    var query by mutableStateOf("")
        private set
    /** Résultats par site, dans l'ordre des sites ; chaque site se remplit dès qu'il répond. */
    var siteResults by mutableStateOf<List<SiteResult>>(emptyList())
        private set
    val searching: Boolean get() = siteResults.any { it.loading }
    /** Chaque série une seule fois, tous sites confondus, avec le meilleur site déjà choisi. */
    val seriesResults: List<SeriesGroup>
        get() = SearchGrouping.group(
            siteResults.filter { !it.loading && it.error == null }.map { it.items to it.elapsedMs },
            preferFrench = language.value == ChapterLanguage.FR,
        )
    private var searchJob: Job? = null

    var recommendations by mutableStateOf<List<Recommendation>>(emptyList())
        private set
    var recommendationsLoading by mutableStateOf(false)
        private set
    var recommendationsError by mutableStateOf<String?>(null)
        private set
    /** Séries suivies et langue au moment du dernier calcul : on ne recalcule que s'ils ont changé. */
    private var recommendationsKey: Set<String>? = null
    var recommendationFilter by mutableStateOf(RecommendationFilter())
        private set
    /** Nombre de chapitres par série proposée ; une clé présente avec null : inconnu (aucun chapitre, ou erreur). */
    var recommendationChapters by mutableStateOf<Map<String, Int?>>(emptyMap())
        private set
    var countingChapters by mutableStateOf(false)
        private set
    private var countingJob: Job? = null

    /** Suggestions qui passent les filtres, dans l'ordre du classement. */
    val visibleRecommendations: List<Recommendation>
        get() = recommendations
            .filter { recommendationFilter.matches(it.manga, recommendationChapters[it.manga.id]) }
            .take(MAX_RECOMMENDATIONS)

    /** versionCode d'une version plus récente publiée sur GitHub, tant qu'elle est proposée. */
    var availableUpdate by mutableStateOf<Long?>(null)
        private set
    /** Avancement du téléchargement (0..1) pendant qu'il a lieu, sinon null. */
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    private val updatePrefs = application.getSharedPreferences("app_update", android.content.Context.MODE_PRIVATE)

    init {
        checkForUpdate()
        // À l'ouverture, seulement si la dernière vérification date : la tâche de fond vérifie déjà toutes les 6 h,
        // et une vérification complète à chaque lancement occupait le téléphone pendant les recherches.
        val stale = Instant.now().minus(Duration.ofMinutes(STARTUP_REFRESH_AFTER_MIN))
        if (followed.value.any { !it.done && (it.lastCheckedAt == null || it.lastCheckedAt.isBefore(stale)) }) refresh()
    }

    /** [userInitiated] : l'utilisateur a demandé la vérification, on lui confirme aussi quand rien n'a changé. */
    fun refresh(userInitiated: Boolean = false) {
        if (refreshing) return
        // Hors ligne, chaque série échouerait : on ne lance rien, et on ne le dit que si on l'a demandé.
        if (!Network.isOnline(getApplication<Application>())) {
            if (userInitiated) message = UiMessage(tr("Pas de connexion internet", "No internet connection"))
            return
        }
        refreshing = true
        viewModelScope.launch {
            try {
                val events = repository.refreshAll()
                when {
                    events.isNotEmpty() -> message = UiMessage(
                        plural(events.size, tr("nouveau chapitre", "new chapter"), tr("nouveaux chapitres", "new chapters")) + tr(" !", "!"),
                    )
                    userInitiated -> message = UiMessage(tr("Tout est à jour", "Everything is up to date"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = UiMessage(tr("Vérification impossible : ", "Couldn't check: ") + Network.describe(e))
            } finally {
                refreshing = false
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
        // Champ vidé : on revient aux suggestions plutôt que de laisser d'anciens résultats.
        if (value.isBlank()) {
            searchJob?.cancel()
            siteResults = emptyList()
        }
    }

    /** Interroge tous les sites activés en parallèle. */
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        if (!Network.isOnline(getApplication<Application>())) {
            message = UiMessage(tr("Pas de connexion internet", "No internet connection"))
            return
        }
        val sources = repository.searchSources()
        if (sources.isEmpty()) {
            message = UiMessage(tr("Aucun site activé (Réglages → Sites de recherche)", "No site enabled (Settings → Search sites)"))
            return
        }
        searchJob?.cancel()
        siteResults = sources.map { SiteResult(it.name) }
        searchJob = viewModelScope.launch {
            sources.forEach { source ->
                launch {
                    val start = System.currentTimeMillis()
                    val result = try {
                        val items = source.search(q)
                        SiteResult(source.name, loading = false, items = items, elapsedMs = System.currentTimeMillis() - start)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SiteResult(source.name, loading = false, error = Network.describe(e))
                    }
                    siteResults = siteResults.map { if (it.site == source.name) result else it }
                }
            }
        }
    }

    fun loadRecommendations(force: Boolean = false) {
        val key = followed.value.map { it.id }.toSet() + "lang:${language.value.name}"
        if (recommendationsLoading || (!force && key == recommendationsKey)) return
        recommendationsKey = key
        if (followed.value.isEmpty()) {
            recommendations = emptyList()
            return
        }
        recommendationsLoading = true
        recommendationsError = null
        viewModelScope.launch {
            try {
                recommendations = repository.recommendations()
                // La langue a pu changer : les nombres de chapitres sont à recompter.
                countingJob?.cancel()
                recommendationChapters = emptyMap()
                if (recommendationFilter.chapters != null) countChapters()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recommendationsError = Network.describe(e)
                recommendationsKey = null
            } finally {
                recommendationsLoading = false
            }
        }
    }

    fun setRecommendationFilter(filter: RecommendationFilter) {
        recommendationFilter = filter
        if (filter.chapters != null) countChapters() else countingJob?.cancel()
    }

    /**
     * Compte les chapitres des suggestions (une requête MangaDex par série), dans l'ordre du classement et
     * par petits lots pour ménager MangaDex, jusqu'à avoir de quoi remplir la liste filtrée.
     */
    private fun countChapters() {
        countingJob?.cancel()
        countingJob = viewModelScope.launch {
            countingChapters = true
            try {
                val pending = recommendations.map { it.manga }
                    .filter { recommendationFilter.matchesStatus(it) && it.id !in recommendationChapters }
                for (batch in pending.chunked(COUNT_BATCH)) {
                    if (visibleRecommendations.size >= MAX_RECOMMENDATIONS) break
                    val counts = batch.map { manga ->
                        async {
                            manga.id to try {
                                repository.chapterCount(manga.id)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll()
                    recommendationChapters = recommendationChapters + counts
                }
            } finally {
                // Un comptage annulé pour en relancer un autre ne doit pas éteindre l'indicateur du nouveau.
                if (countingJob === coroutineContext[Job]) countingChapters = false
            }
        }
    }

    fun setSiteEnabled(name: String, enabled: Boolean) = repository.setSiteEnabled(name, enabled)

    fun addSite(address: String) {
        if (!repository.addSite(address)) message = UiMessage(tr("Adresse invalide ou site déjà présent", "Invalid address or site already listed"))
    }

    fun removeSite(name: String) = repository.removeSite(name)

    /** Suit la série sur son meilleur site, en gardant les autres en secours. */
    fun follow(group: SeriesGroup) {
        message = UiMessage(tr("${group.title} ajouté à tes séries", "${group.title} added to your series") + " (${group.best.sourceLabel})")
        viewModelScope.launch {
            repository.follow(group.best, group.alternates)
        }
    }

    fun follow(manga: MangaSummary) {
        message = UiMessage(tr("${manga.title} ajouté à tes séries", "${manga.title} added to your series"))
        viewModelScope.launch {
            repository.follow(manga)
        }
    }

    /** Retire la série tout de suite ; la snackbar propose d'annuler. */
    fun unfollow(series: FollowedSeries) {
        repository.unfollow(series.id)
        message = UiMessage(tr("${series.title} retiré de tes séries", "${series.title} removed from your series"), action = tr("Annuler", "Undo")) { repository.restore(series) }
    }

    fun markSeen(id: String) = repository.markSeen(id)

    fun markAllSeen() = repository.markAllSeen()

    fun setDone(series: FollowedSeries, done: Boolean) {
        repository.setDone(series.id, done)
        message = if (done) {
            UiMessage(tr("${series.title} rangé dans les terminés", "${series.title} moved to finished"), action = tr("Annuler", "Undo")) { repository.setDone(series.id, false) }
        } else {
            UiMessage(tr("${series.title} de nouveau suivi", "Following ${series.title} again"))
        }
    }

    fun setRead(series: FollowedSeries, chapter: Chapter, read: Boolean) = repository.setRead(series.id, chapter, read)

    fun markAllRead(series: FollowedSeries) = repository.markAllRead(series.id)

    fun setLanguage(language: ChapterLanguage) {
        if (language == this.language.value) return
        repository.setLanguage(language)
        refresh()
    }

    /** [userInitiated] : demandé depuis les réglages, on répond même quand l'appli est à jour. */
    fun checkForUpdate(userInitiated: Boolean = false) {
        val app = getApplication<Application>()
        if (!userInitiated && System.currentTimeMillis() < updatePrefs.getLong("snoozed_until", 0)) return
        viewModelScope.launch {
            try {
                val latest = AppUpdater.latestVersionCode()
                when {
                    latest != null && latest > AppUpdater.installedVersionCode(app) -> availableUpdate = latest
                    userInitiated -> message = UiMessage(tr("Tu as déjà la dernière version", "You already have the latest version"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (userInitiated) message = UiMessage(tr("Vérification impossible : ", "Couldn't check: ") + Network.describe(e))
            }
        }
    }

    fun postponeUpdate() {
        availableUpdate = null
        updatePrefs.edit().putLong("snoozed_until", System.currentTimeMillis() + UPDATE_SNOOZE_HOURS * 3_600_000).apply()
    }

    fun installUpdate() {
        if (updateProgress != null) return
        val app = getApplication<Application>()
        updateProgress = 0f
        viewModelScope.launch {
            try {
                val apk = AppUpdater.download(app) { updateProgress = it }
                availableUpdate = null
                AppUpdater.install(app, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = UiMessage(tr("Téléchargement impossible : ", "Download failed: ") + Network.describe(e))
            } finally {
                updateProgress = null
            }
        }
    }

    fun consumeMessage() {
        message = null
    }
}

/** Message en bas d'écran, avec une action facultative (« Annuler »). */
data class UiMessage(val text: String, val action: String? = null, val onAction: () -> Unit = {})

data class SiteResult(
    val site: String,
    val loading: Boolean = true,
    val items: List<MangaSummary> = emptyList(),
    val error: String? = null,
    val elapsedMs: Long = 0,
)
