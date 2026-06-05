package com.charles.ollama.client.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.charles.ollama.client.MainActivity
import com.charles.ollama.client.R

/**
 * Static-style launcher shortcuts (New chat / Models / Servers) registered as
 * dynamic shortcuts so they pick up the correct flavor applicationId and can
 * carry a destination extra. Coexists with [RecentThreadShortcut]'s
 * "Resume last chat" dynamic shortcut.
 */
object AppShortcuts {

    fun refresh(context: Context) {
        val app = context.applicationContext
        val shortcuts = listOf(
            build(app, "new_chat", MainActivity.DEST_NEW_CHAT,
                app.getString(R.string.shortcut_new_chat_short),
                app.getString(R.string.shortcut_new_chat_long)),
            build(app, "models", MainActivity.DEST_MODELS,
                app.getString(R.string.shortcut_models_short),
                app.getString(R.string.shortcut_models_long)),
            build(app, "servers", MainActivity.DEST_SERVERS,
                app.getString(R.string.shortcut_servers_short),
                app.getString(R.string.shortcut_servers_long)),
        )
        try {
            ShortcutManagerCompat.addDynamicShortcuts(app, shortcuts)
        } catch (e: Exception) {
            android.util.Log.w("AppShortcuts", "Failed to add shortcuts", e)
        }
    }

    private fun build(
        context: Context,
        id: String,
        dest: String,
        shortLabel: String,
        longLabel: String
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_DEST, dest)
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
    }
}
