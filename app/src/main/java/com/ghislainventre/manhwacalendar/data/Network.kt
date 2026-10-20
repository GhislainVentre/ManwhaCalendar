package com.ghislainventre.manhwacalendar.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ghislainventre.manhwacalendar.tr
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** État de la connexion et messages d'erreur réseau lisibles. */
object Network {

    /** Vrai si le téléphone a une connexion internet (Wi-Fi ou données mobiles). */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Message à montrer pour une erreur : « Unable to resolve host api.mangadex.org… » ne dit rien
     * à l'utilisateur, « pas de connexion internet » si.
     */
    fun describe(error: Throwable): String {
        var cause: Throwable? = error
        while (cause != null) {
            when (cause) {
                is UnknownHostException, is ConnectException -> return noConnection
                is SocketTimeoutException -> return tr("le serveur met trop de temps à répondre", "the server is taking too long to respond")
                is SSLException -> return tr("connexion sécurisée impossible", "secure connection failed")
            }
            cause = cause.cause
        }
        return error.message?.takeIf { it.isNotBlank() } ?: tr("erreur inconnue", "unknown error")
    }

    val noConnection: String get() = tr("pas de connexion internet", "no internet connection")
}
