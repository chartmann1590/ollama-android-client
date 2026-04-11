package com.charles.ollama.client.data.update

/**
 * Minimal snapshot of the "latest release" on GitHub that the app needs in
 * order to prompt the user to download an update.
 */
data class UpdateInfo(
    val tag: String,
    val name: String,
    val releaseUrl: String,
    /** Direct APK asset URL, or `null` if the release has no `.apk` attached. */
    val apkAssetUrl: String?,
    /** Release `published_at` as epoch seconds. */
    val publishedAtEpochSeconds: Long,
    val bodyMarkdown: String
)
