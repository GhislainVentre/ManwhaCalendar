package com.ghislainventre.manhwacalendar.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Charge une page dans une WebView invisible puis en extrait des données en JavaScript.
 *
 * Contrairement à une simple requête HTTP, la WebView exécute la vérification anti-robot
 * de Cloudflare comme un vrai navigateur, et garde ses cookies pour les visites suivantes.
 */
class WebPageScraper(context: Context) {

    private val appContext = context.applicationContext
    // Quelques pages en parallèle pour interroger plusieurs sites à la fois sans saturer le téléphone.
    private val permits = Semaphore(MAX_PARALLEL_PAGES)

    /**
     * [stateJs] doit renvoyer 2 quand la page est prête, 1 quand elle est chargée sans le contenu
     * attendu (acceptée après un délai de grâce), 0 sinon. [extractJs] doit renvoyer une chaîne.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun scrape(url: String, stateJs: String, extractJs: String, timeoutMs: Long = 40_000): String =
        permits.withPermit {
            withContext(Dispatchers.Main) {
                val webView = WebView(appContext)
                try {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.blockNetworkImage = true
                    // Site injoignable (domaine mort, connexion refusée) : on abandonne tout de suite
                    // au lieu d'attendre la fin du délai.
                    var unreachable: String? = null
                    webView.webViewClient = object : WebViewClient() {
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) unreachable = error.description?.toString() ?: "site injoignable"
                        }
                    }
                    webView.loadUrl(url)

                    var result: String? = null
                    var loadedTicks = 0
                    withTimeoutOrNull(timeoutMs) {
                        while (result == null) {
                            delay(1_000)
                            unreachable?.let { throw IOException("site injoignable ($it)") }
                            when (webView.eval(stateJs)) {
                                "2" -> result = webView.eval(extractJs)
                                "1" -> if (++loadedTicks >= GRACE_TICKS) result = webView.eval(extractJs)
                            }
                        }
                    }
                    decode(result ?: throw IOException("le site ne répond pas (vérification Cloudflare ?)"))
                } finally {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }

    private suspend fun WebView.eval(js: String): String = suspendCancellableCoroutine { cont ->
        evaluateJavascript(js) { cont.resume(it ?: "null") }
    }

    /** evaluateJavascript renvoie la valeur encodée en JSON : on décode la chaîne. */
    private fun decode(raw: String): String {
        if (raw == "null") throw IOException("page illisible")
        return JSONArray("[$raw]").getString(0)
    }

    private companion object {
        const val GRACE_TICKS = 8
        const val MAX_PARALLEL_PAGES = 6
    }
}
