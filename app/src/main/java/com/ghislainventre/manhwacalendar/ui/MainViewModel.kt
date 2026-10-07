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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ManhwaCalendarApp).repository

    val followed = repository.followed
    val language = repository.language

    var refreshing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set
    var manhwaOnly by mutableStateOf(true)
        private set
    var searching by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<MangaSummary>>(emptyList())
        private set
    private var searchJob: Job? = null

    init {
        if (followed.value.isNotEmpty()) refresh()
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            message = try {
                val events = repository.refreshAll()
                if (events.isEmpty()) null else "${events.size} nouveau(x) chapitre(s)"
            } catch (e: Exception) {
                "Impossible de joindre MangaDex : ${e.message}"
            }
            refreshing = false
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun toggleManhwaOnly() {
        manhwaOnly = !manhwaOnly
        search()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching = true
            try {
                results = repository.search(q, manhwaOnly)
                if (results.isEmpty()) message = "Aucun résultat pour « $q »"
            } catch (e: Exception) {
                message = "Recherche impossible : ${e.message}"
            } finally {
                searching = false
            }
        }
    }

    fun follow(manga: MangaSummary) {
        viewModelScope.launch {
            repository.follow(manga)
            message = "${manga.title} ajouté à vos séries"
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
