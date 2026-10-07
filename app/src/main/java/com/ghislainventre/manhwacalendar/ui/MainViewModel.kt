package com.ghislainventre.manhwacalendar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghislainventre.manhwacalendar.ManhwaCalendarApp
import com.ghislainventre.manhwacalendar.data.ChapterLanguage
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
    var message by mutableStateOf<String?>(null)
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

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val events = repository.refreshAll()
                if (events.isNotEmpty()) message = "${events.size} nouveau(x) chapitre(s)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Vérification impossible : ${e.message}"
            } finally {
                refreshing = false
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    /** Interroge tous les sites activés en parallèle. */
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        val sources = repository.searchSources()
        if (sources.isEmpty()) {
            message = "Aucun site activé (Réglages → Sites de recherche)"
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
        if (!repository.addSite(address)) message = "Adresse invalide ou site déjà présent"
    }

    fun removeSite(name: String) = repository.removeSite(name)

    fun follow(manga: MangaSummary) {
        message = "${manga.title} ajouté à vos séries"
        viewModelScope.launch {
            repository.follow(manga)
        }
    }

    fun unfollow(id: String) = repository.unfollow(id)

    fun markSeen(id: String) = repository.markSeen(id)

    fun setLanguage(language: ChapterLanguage) {
        if (language == this.language.value) return
        repository.setLanguage(language)
        refresh()
    }

    fun consumeMessage() {
        message = null
    }
}

data class SiteResult(
    val site: String,
    val loading: Boolean = true,
    val items: List<MangaSummary> = emptyList(),
    val error: String? = null,
)
