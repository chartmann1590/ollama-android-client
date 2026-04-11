package com.charles.ollama.client.data.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {

    @Test
    fun `catalog exposes both Gemma 4 bundles`() {
        val ids = LocalModelCatalog.entries.map { it.id }
        assertTrue("E2B missing", ids.contains(LocalModelCatalog.GEMMA4_E2B))
        assertTrue("E4B missing", ids.contains(LocalModelCatalog.GEMMA4_E4B))
    }

    @Test
    fun `catalog exposes the expanded litert-community set`() {
        val ids = LocalModelCatalog.entries.map { it.id }.toSet()
        val required = setOf(
            LocalModelCatalog.GEMMA3_270M,
            LocalModelCatalog.GEMMA3_1B,
            LocalModelCatalog.QWEN3_0_6B,
            LocalModelCatalog.QWEN2_5_1_5B,
            LocalModelCatalog.DEEPSEEK_R1_QWEN_1_5B,
            LocalModelCatalog.GEMMA4_E2B,
            LocalModelCatalog.GEMMA4_E4B,
            LocalModelCatalog.PHI4_MINI
        )
        assertEquals(required, ids.intersect(required))
    }

    @Test
    fun `every entry id is unique`() {
        val ids = LocalModelCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every entry has a litertlm download URL from litert-community`() {
        LocalModelCatalog.entries.forEach { entry ->
            assertTrue(
                "download url for ${entry.id} should be a HF litert-community URL",
                entry.downloadUrl.startsWith("https://huggingface.co/litert-community/")
            )
            assertTrue(
                "download url for ${entry.id} should point at a .litertlm file",
                entry.downloadUrl.contains(".litertlm")
            )
            assertTrue("size for ${entry.id} must be positive", entry.approximateSizeBytes > 0L)
            assertFalse("display name for ${entry.id} must be set", entry.displayName.isBlank())
        }
    }

    @Test
    fun `Gemma 4 entries resolve to the expected HF paths`() {
        val e2b = LocalModelCatalog.byId(LocalModelCatalog.GEMMA4_E2B)!!
        assertTrue(e2b.downloadUrl.contains("gemma-4-E2B-it-litert-lm"))
        assertEquals("gemma-4-E2B-it.litertlm", e2b.fileName)

        val e4b = LocalModelCatalog.byId(LocalModelCatalog.GEMMA4_E4B)!!
        assertTrue(e4b.downloadUrl.contains("gemma-4-E4B-it-litert-lm"))
        assertEquals("gemma-4-E4B-it.litertlm", e4b.fileName)
    }

    @Test
    fun `threadModelName round-trips through fromThreadModelName`() {
        val entry = LocalModelCatalog.byId(LocalModelCatalog.GEMMA4_E2B)
        assertNotNull(entry)
        val name = entry!!.threadModelName
        assertEquals("litert/${LocalModelCatalog.GEMMA4_E2B}", name)
        assertEquals(entry, LocalModelCatalog.fromThreadModelName(name))
    }

    @Test
    fun `fromThreadModelName rejects non-litert names`() {
        assertNull(LocalModelCatalog.fromThreadModelName(null))
        assertNull(LocalModelCatalog.fromThreadModelName(""))
        assertNull(LocalModelCatalog.fromThreadModelName("llama3.2"))
        assertNull(LocalModelCatalog.fromThreadModelName("litert/"))
    }

    @Test
    fun `legacy gemma3n IDs resolve to the current gemma4 entries`() {
        // A brief intermediate build used "litert/gemma3n_*" IDs. Threads
        // created during that build must still resolve after the revert to
        // Gemma 4, or the user loses chat history wiring.
        val e2b = LocalModelCatalog.fromThreadModelName("litert/gemma3n_e2b")
        assertNotNull(e2b)
        assertEquals(LocalModelCatalog.GEMMA4_E2B, e2b!!.id)

        val e4b = LocalModelCatalog.fromThreadModelName("litert/gemma3n_e4b")
        assertNotNull(e4b)
        assertEquals(LocalModelCatalog.GEMMA4_E4B, e4b!!.id)
    }
}
