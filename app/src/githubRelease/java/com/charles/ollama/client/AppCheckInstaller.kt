package com.charles.ollama.client

/**
 * Direct-download (github) release builds are not installed from Google Play, so the
 * Play Integrity App Check provider is not used here. The Play flavor's copy lives in
 * src/playRelease; debug builds keep the debug provider from src/debug.
 */
object AppCheckInstaller {
    fun install() {
        // No Play Integrity provider for direct-download builds.
    }
}
