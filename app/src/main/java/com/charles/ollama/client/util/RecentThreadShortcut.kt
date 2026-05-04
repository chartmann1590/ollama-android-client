package com.charles.ollama.client.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.charles.ollama.client.MainActivity
import com.charles.ollama.client.R

/**
 * Maintains a single dynamic launcher shortcut, "Resume last chat", that
 * deep-links straight into the most recently opened chat thread. The shortcut
 * is updated each time a chat is opened from anywhere in the app.
 *
 * Long-pressing the launcher icon shows the shortcut so users can return to
 * their last conversation in one tap without going through the threads list.
 */
object RecentThreadShortcut {

    const val SHORTCUT_ID = "resume_last_thread"
    const val EXTRA_THREAD_ID = "threadId"

    fun update(context: Context, threadId: Long, title: String) {
        if (threadId <= 0L) return

        val displayLabel = title.ifBlank { "Resume last chat" }
        val intent = Intent(context.applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_THREAD_ID, threadId)
        }

        val shortcut = ShortcutInfoCompat.Builder(context.applicationContext, SHORTCUT_ID)
            .setShortLabel(displayLabel.take(20))
            .setLongLabel("Resume: ${displayLabel.take(40)}")
            .setIcon(IconCompat.createWithResource(context.applicationContext, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()

        try {
            ShortcutManagerCompat.pushDynamicShortcut(context.applicationContext, shortcut)
        } catch (e: Exception) {
            android.util.Log.w("RecentThreadShortcut", "Failed to push shortcut", e)
        }
    }
}
