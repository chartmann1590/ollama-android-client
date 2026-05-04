package com.charles.ollama.client.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a chat thread (or a single message) as Markdown and shares it via
 * `ACTION_SEND`. Small payloads go inline as plain text; large payloads spill
 * to a temp file under `cacheDir/exports/` and ship as a content:// URI via
 * the app's `FileProvider`.
 *
 * Reasoning ("thinking") content is intentionally omitted so exported logs
 * mirror what the user actually saw in chat.
 */
object ThreadExporter {

    private const val INLINE_LIMIT_BYTES = 100 * 1024 // 100 KB
    private const val EXPORT_DIR = "exports"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    fun toMarkdown(thread: ChatThreadEntity, messages: List<ChatMessageEntity>): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(thread.updatedAt))
        sb.append("# ").append(thread.title.ifBlank { "Chat" }).append('\n')
        thread.model?.let { sb.append("- Model: `").append(it).append("`\n") }
        sb.append("- Updated: ").append(date).append("\n\n")
        sb.append("---\n\n")

        for (msg in messages) {
            if (msg.role == "system") continue
            val heading = when (msg.role) {
                "user" -> "### User"
                "assistant" -> "### Assistant"
                else -> "### " + msg.role.replaceFirstChar { it.uppercase(Locale.getDefault()) }
            }
            sb.append(heading).append('\n')
            val imageCount = msg.images?.size ?: 0
            if (imageCount > 0) {
                sb.append("_(").append(imageCount).append(" image")
                if (imageCount != 1) sb.append('s')
                sb.append(" attached)_\n\n")
            }
            val body = msg.content.trim()
            if (body.isNotEmpty()) sb.append(body).append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    fun shareThread(context: Context, thread: ChatThreadEntity, messages: List<ChatMessageEntity>) {
        val md = toMarkdown(thread, messages)
        share(context, md, defaultFileName(thread.title), thread.title.ifBlank { "Chat" })
    }

    fun shareMessage(context: Context, message: ChatMessageEntity, threadTitle: String?) {
        val md = singleMessageMarkdown(message)
        val subject = threadTitle?.takeIf { it.isNotBlank() } ?: "Message"
        share(context, md, defaultFileName(subject), subject)
    }

    private fun singleMessageMarkdown(message: ChatMessageEntity): String {
        val heading = when (message.role) {
            "user" -> "### User"
            "assistant" -> "### Assistant"
            else -> "### " + message.role.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }
        return buildString {
            append(heading).append('\n').append('\n')
            append(message.content.trim())
            append('\n')
        }
    }

    private fun share(context: Context, markdown: String, fileName: String, subject: String) {
        val intent = if (markdown.toByteArray(Charsets.UTF_8).size < INLINE_LIMIT_BYTES) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, markdown)
            }
        } else {
            val uri = writeToCache(context, markdown, fileName)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(intent, "Share chat")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun writeToCache(context: Context, content: String, fileName: String): android.net.Uri {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val outFile = File(dir, fileName)
        outFile.writeText(content, Charsets.UTF_8)
        val authority = context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    private fun defaultFileName(title: String): String {
        val safe = title
            .ifBlank { "chat" }
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "chat" }
        return "$safe.md"
    }
}
