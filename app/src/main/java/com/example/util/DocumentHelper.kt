package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File

object DocumentHelper {

    /** Extracts the real display name from a content Uri */
    fun getDisplayName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name?.takeIf { it.isNotBlank() } ?: "Document"
    }

    /** Extracts size from a content Uri if available */
    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0L
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index != -1) {
                            size = cursor.getLong(index)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return size
    }

    /** Formats bytes to readable size string, e.g. "1.2 MB" */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    /** Gets the MIME type from a file name or URL extension */
    fun getMimeType(fileNameOrUrl: String): String {
        val extension = fileNameOrUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
        return if (extension.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        } else {
            "*/*"
        }
    }

    /** Downloads a document to public Downloads directory using system DownloadManager */
    fun downloadDocument(context: Context, url: String, originalName: String?) {
        if (url.isBlank()) {
            Toast.makeText(context, "Document URL not available", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = originalName?.ifBlank { null } ?: "Document_${System.currentTimeMillis()}"
        try {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading $fileName")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                val mime = getMimeType(fileName)
                if (mime.isNotBlank() && mime != "*/*") {
                    setMimeType(mime)
                }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                dm.enqueue(request)
                Toast.makeText(context, "Downloading $fileName…", Toast.LENGTH_SHORT).show()
            } else {
                openDocumentInBrowser(context, url)
            }
        } catch (e: Exception) {
            openDocumentInBrowser(context, url)
        }
    }

    private fun openDocumentInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Unable to download document", Toast.LENGTH_SHORT).show()
        }
    }
}
