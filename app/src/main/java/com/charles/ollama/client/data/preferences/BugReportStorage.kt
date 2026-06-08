package com.charles.ollama.client.data.preferences

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class LocalBugReport(
    val issueNumber: Int,
    val title: String,
    val status: String, // "open" or "closed"
    val createdAt: String,
    val htmlUrl: String
)

@Singleton
class BugReportStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBugReports(): List<LocalBugReport> {
        val json = prefs.getString(KEY_BUG_REPORTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LocalBugReport>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveBugReports(reports: List<LocalBugReport>) {
        val json = gson.toJson(reports)
        prefs.edit().putString(KEY_BUG_REPORTS, json).apply()
    }

    fun addOrUpdateBugReport(report: LocalBugReport) {
        val reports = getBugReports().toMutableList()
        val index = reports.indexOfFirst { it.issueNumber == report.issueNumber }
        if (index >= 0) {
            reports[index] = report
        } else {
            reports.add(0, report) // Prepend new reports
        }
        saveBugReports(reports)
    }

    companion object {
        private const val PREFS_NAME = "bug_reports_prefs"
        private const val KEY_BUG_REPORTS = "bug_reports_list"
    }
}
