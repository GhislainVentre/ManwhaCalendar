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
import com.ghislainventre.manhwacalendar.data.Recommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    private var searchJob: Job? = null

    var recommendations by mutableStateOf<List<Recommendation>>(emptyList())
        private set
    var recommendationsLoading by mutableStateOf(false)
        private set
    var recommendationsError by mutableStateOf<String?>(null)
        private set
    /** Séries suivies et langue au moment du dernier calcul : on ne recalcule que s'ils ont changé. */
    private var recommendationsKey: Set<String>? = null

    init {
        if (followed.value.isNotEmpty()) refresh()
    }

    /** [userInitiated] : l'utilisateur a demandé la vérification, on lui confirme aussi quand rien n'a changé. */
    fun refresh(userInitiated: Boolean = false) {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val events = repository.refreshAll()
                when {
                    events.isNotEmpty() -> message = UiMessage(
                        plural(events.size, "nouveau chapitre", "nouveaux chapitres") + " !",
                    )
                    userInitiated -> message = UiMessage("Tout est à jour")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = UiMessage("Vérification impossible : ${e.message}")
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
        val sources = repository.searchSources()
        if (sources.isEmpty()) {
            message = UiMessage("Aucun site activé (Réglages → Sites de recherche)")
            return
        }
        searchJob?.cancel()
        siteResults = sources.map { SiteResult(it.name) }
        searchJob = viewModelScope.launch {
            sources.forEach { source ->
                launch {
                    val result = try {
                        SiteResult(source.name, loading = false, items = source.search(q))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SiteResult(source.name, loading = false, error = e.message ?: "erreur inconnue")
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recommendationsError = e.message ?: "erreur inconnue"
                recommendationsKey = null
            } finally {
                recommendationsLoading = false
            }
        }
    }

    fun setSiteEnabled(name: String, enabled: Boolean) = repository.setSiteEnabled(name, enabled)

    fun addSite(address: String) {
        if (!repository.addSite(address)) message = UiMessage("Adresse invalide ou site déjà présent")
    }

    fun removeSite(name: String) = repository.removeSite(name)

    fun follow(manga: MangaSummary) {
        message = UiMessage("${manga.title} ajouté à tes séries")
        viewModelScope.launch {
            repository.follow(manga)
        }
    }

    /** Retire la série tout de suite ; la snackbar propose d'annuler. */
    fun unfollow(series: FollowedSeries) {
        repository.unfollow(series.id)
        message = UiMessage("${series.title} retiré de tes séries", action = "Annuler") { repository.restore(series) }
    }

    fun markSeen(id: String) = repository.markSeen(id)

    fun markAllSeen() = repository.markAllSeen()

    fun setRead(series: FollowedSeries, chapter: Chapter, read: Boolean) = repository.setRead(series.id, chapter, read)

    fun markAllRead(series: FollowedSeries) = repository.markAllRead(series.id)

    fun setLanguage(language: ChapterLanguage) {
        if (language == this.language.value) return
        repository.setLanguage(language)
        refresh()
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
)
