package com.charles.ollama.client.data.litert

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Regression coverage for the `UnsatisfiedLinkError: litertlm_jni` crash on
 * app open. The previous `init { Engine.setNativeMinLogSeverity(...) }` block
 * forced the JNI .so to load the moment Hilt instantiated the singleton —
 * killing devices where the lib is missing for the device's ABI even if they
 * never used the on-device backend.
 *
 * On a JVM unit test the litertlm_jni library is not loadable. If anyone
 * re-introduces an eager LiteRT native call in `init` or in field initializers,
 * this test fails with `UnsatisfiedLinkError` at construction time.
 */
class LiteRtChatServiceTest {

    @Test
    fun `construction does not load the litertlm native library`() {
        val context = mock(Context::class.java)
        val service = LiteRtChatService(context)
        assertNotNull(service)
    }
}
