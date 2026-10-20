package com.ghislainventre.manhwacalendar.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

class NetworkTest {

    private val initial = Locale.getDefault()

    @Before
    fun french() = Locale.setDefault(Locale.FRENCH)

    @After
    fun restore() = Locale.setDefault(initial)

    @Test
    fun noConnectionIsSaidPlainly() {
        assertEquals("pas de connexion internet", Network.describe(UnknownHostException("Unable to resolve host \"api.mangadex.org\"")))
        assertEquals("pas de connexion internet", Network.describe(IOException("wrapper", UnknownHostException())))
    }

    @Test
    fun timeoutIsExplained() {
        assertEquals("le serveur met trop de temps à répondre", Network.describe(SocketTimeoutException("timeout")))
    }

    @Test
    fun otherErrorsKeepTheirMessage() {
        assertEquals("MangaDex a répondu 500", Network.describe(IOException("MangaDex a répondu 500")))
        assertEquals("erreur inconnue", Network.describe(IllegalStateException()))
    }
}
