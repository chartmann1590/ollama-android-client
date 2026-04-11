package com.charles.ollama.client.data.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LitertConstantsTest {

    @Test
    fun `litert-local sentinel matches canonical form`() {
        assertTrue(LitertConstants.isLitertLocalBaseUrl(LitertConstants.LOCAL_BASE_URL))
    }

    @Test
    fun `litert-local sentinel is case-insensitive`() {
        assertTrue(LitertConstants.isLitertLocalBaseUrl("LITERT-LOCAL://"))
        assertTrue(LitertConstants.isLitertLocalBaseUrl("Litert-Local://"))
    }

    @Test
    fun `whitespace around sentinel is tolerated`() {
        assertTrue(LitertConstants.isLitertLocalBaseUrl("  litert-local://  "))
    }

    @Test
    fun `remote ollama URLs are not flagged as litert-local`() {
        assertFalse(LitertConstants.isLitertLocalBaseUrl("http://localhost:11434"))
        assertFalse(LitertConstants.isLitertLocalBaseUrl("https://ollama.example.com"))
        assertFalse(LitertConstants.isLitertLocalBaseUrl(""))
    }

    @Test
    fun `ServerBackend fromStored defaults to OLLAMA for unknown or null`() {
        assertEquals(ServerBackend.OLLAMA, ServerBackend.fromStored(null))
        assertEquals(ServerBackend.OLLAMA, ServerBackend.fromStored(""))
        assertEquals(ServerBackend.OLLAMA, ServerBackend.fromStored("not-a-backend"))
    }

    @Test
    fun `ServerBackend fromStored round-trips known names`() {
        assertEquals(ServerBackend.OLLAMA, ServerBackend.fromStored("OLLAMA"))
        assertEquals(ServerBackend.LITERT_LOCAL, ServerBackend.fromStored("LITERT_LOCAL"))
    }
}
