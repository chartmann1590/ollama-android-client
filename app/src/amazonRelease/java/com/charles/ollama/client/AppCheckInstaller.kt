package com.charles.ollama.client

/**
 * Amazon Appstore release builds run on Fire devices without Google Play services, so the Play
 * Integrity App Check provider is not used (it lives in src/playRelease). Debug builds keep the
 * debug provider from src/debug.
 */
object AppCheckInstaller {
    fun install() {
        // No Play Integrity provider on Amazon builds.
    }
}
