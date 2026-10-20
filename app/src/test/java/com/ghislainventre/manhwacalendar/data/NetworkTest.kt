package com.ghislainventre.manhwacalendar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkTest {

    @Test
    fun noConnectionIsSaidPlainly() {
        assertEquals(Network.NO_CONNECTION, Network.describe(UnknownHostException("Unable to resolve host \"api.mangadex.org\"")))
        assertEquals(Network.NO_CONNECTION, Network.describe(IOException("wrapper", UnknownHostException())))
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
