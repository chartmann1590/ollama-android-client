package com.charles.ollama.client.data.update

import android.util.Log
import com.charles.ollama.client.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub for a newer release than the currently installed build.
 *
 * "Newer" is defined as `release.published_at` being strictly after
 * [BuildConfig.BUILD_COMMIT_EPOCH_SECONDS] — the epoch of the git commit this
 * APK was built from. Using the commit timestamp keeps the comparison stable
 * across Gradle incremental builds (see `gitCommitEpochSeconds()` in
 * `app/build.gradle.kts`).
 *
 * Results are throttled by [UpdatePreferences.lastCheckedMillis] so we hit
 * `api.github.com` at most once every few hours even if the user backgrounds
 * and reopens the app constantly.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferences: UpdatePreferences
) {
    /**
     * @param force bypass the throttle window and always hit the API.
     * @return the latest release if it is newer than the installed build and
     *   has not been dismissed by the user, otherwise `null`.
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - preferences.lastCheckedMillis < UpdatePreferences.CHECK_INTERVAL_MILLIS) {
            Log.d(TAG, "Throttled — last check was within the cooldown window")
            return@withContext null
        }
        preferences.lastCheckedMillis = now

        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ollama-android-client/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub releases API returned HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null

                val root = JsonParser.parseString(body).asJsonObject
                val tag = root.get("tag_name")?.asString.orEmpty()
                val name = root.get("name")?.asString?.takeIf { it.isNotBlank() } ?: tag
                val releaseUrl = root.get("html_url")?.asString.orEmpty()
                val publishedAtRaw = root.get("published_at")?.asString
                val bodyMarkdown = root.get("body")?.asString.orEmpty()

                val publishedAtEpoch = publishedAtRaw
                    ?.let { parseEpochSeconds(it) }
                    ?: 0L

                val apkAsset = root.getAsJsonArray("assets")
                    ?.mapNotNull { it.asJsonObject }
                    ?.firstOrNull { it.get("name")?.asString?.endsWith(".apk", ignoreCase = true) == true }
                val apkAssetUrl = apkAsset?.get("browser_download_url")?.asString

                if (tag.isBlank() || releaseUrl.isBlank()) {
                    Log.w(TAG, "Release payload missing tag/html_url — ignoring")
                    return@withContext null
                }

                if (preferences.dismissedTag == tag) {
                    Log.d(TAG, "Tag $tag was dismissed by the user; skipping prompt")
                    return@withContext null
                }

                val installedEpoch = BuildConfig.BUILD_COMMIT_EPOCH_SECONDS
                val isNewer = installedEpoch > 0L && publishedAtEpoch > installedEpoch
                if (!isNewer) {
                    Log.d(
                        TAG,
                        "No newer release (installed=$installedEpoch published=$publishedAtEpoch tag=$tag)"
                    )
                    return@withContext null
                }

                UpdateInfo(
                    tag = tag,
                    name = name,
                    releaseUrl = releaseUrl,
                    apkAssetUrl = apkAssetUrl,
                    publishedAtEpochSeconds = publishedAtEpoch,
                    bodyMarkdown = bodyMarkdown
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Update check failed: ${t.message}")
            null
        }
    }

    fun dismiss(tag: String) {
        preferences.dismissedTag = tag
    }

    /**
     * Parse GitHub's `published_at` ISO-8601 UTC timestamp (e.g.
     * `2026-04-11T21:11:51Z`) without using `java.time.Instant`, which
     * requires API 26 and minSdk here is 24.
     */
    private fun parseEpochSeconds(iso: String): Long? = try {
        isoFormatter.get()!!.parse(iso)?.time?.let { it / 1000L }
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val TAG = "UpdateChecker"

        private val isoFormatter: ThreadLocal<SimpleDateFormat> =
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue(): SimpleDateFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
            }
    }
}
