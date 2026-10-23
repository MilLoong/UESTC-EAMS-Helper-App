package edu.uestc.eams.helper.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerOriginTest {
    @Test fun acceptsRootHttps() {
        assertEquals("https://server.example", ServerOrigin.validate("https://server.example/", false))
    }
    @Test fun requiresExplicitLanConsent() {
        assertEquals("http://192.168.1.2:8000", ServerOrigin.validate("http://192.168.1.2:8000", true))
        assertThrows(IllegalArgumentException::class.java) { ServerOrigin.validate("http://192.168.1.2:8000", false) }
        assertThrows(IllegalArgumentException::class.java) { ServerOrigin.validate("http://8.8.8.8", true) }
    }
    @Test fun rejectsCredentialsAndUrlExtras() {
        listOf("https://u:p@server.example", "https://server.example/path", "https://server.example?token=1", "https://server.example#fragment").forEach {
            assertThrows(IllegalArgumentException::class.java) { ServerOrigin.validate(it, false) }
        }
    }
}
