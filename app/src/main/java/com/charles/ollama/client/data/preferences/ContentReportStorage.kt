package com.charles.ollama.client.data.preferences

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records user reports of objectionable AI-generated content. Required by
 * Google Play's AI-Generated Content policy: apps that surface AI output must
 * give users an in-app way to flag offensive results.
 *
 * Reports are pushed to a Firebase Realtime Database `/contentReports` node so
 * they can be reviewed; if Firebase is unavailable the attempt is logged and
 * silently dropped (the user still gets local confirmation in the UI).
 */
@Singleton
class ContentReportStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun report(messageSnippet: String, reason: String?) {
        val snippet = messageSnippet.trim().take(1000)
        val entry = mapOf(
            "snippet" to snippet,
            "reason" to (reason?.take(200) ?: "unspecified"),
            "reportedAt" to System.currentTimeMillis(),
            "package" to context.packageName
        )
        try {
            FirebaseDatabase.getInstance()
                .getReference("contentReports")
                .push()
                .setValue(entry)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit content report", e)
        }
    }

    companion object {
        private const val TAG = "ContentReportStorage"
    }
}
