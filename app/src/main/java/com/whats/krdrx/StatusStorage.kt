package com.whats.krdrx

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.IOException

data class StatusFile(val uri: Uri, val name: String, val mimeType: String) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

object StatusStorage {
    private const val PREFS = "whats_krdrx_prefs"
    private const val KEY_TREE = "status_tree_uri"

    fun savedTree(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TREE, null)?.let(Uri::parse)

    fun rememberTree(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun list(context: Context, tree: Uri): List<StatusFile> {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val files = mutableListOf<StatusFile>()
        resolver.query(children, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val mime = c.getString(mimeCol) ?: continue
                if (!mime.startsWith("image/") && !mime.startsWith("video/")) continue
                val id = c.getString(idCol)
                files += StatusFile(
                    DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    c.getString(nameCol) ?: "WhatsStatus",
                    mime
                )
            }
        }
        return files.sortedByDescending { it.name.lowercase() }
    }

    @Throws(IOException::class)
    fun save(context: Context, item: StatusFile): Uri {
        val resolver = context.contentResolver
        val collection = if (item.isVideo)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mimeType)
        val stem = item.name.substringBeforeLast('.', item.name).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val filename = if (ext != null && !item.name.endsWith(".$ext", true)) "$stem.$ext" else item.name
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Whats-Krdrx")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: throw IOException("Gagal membuat file tujuan.")
        try {
            val input = resolver.openInputStream(item.uri) ?: throw IOException("File sumber tidak dapat dibaca.")
            input.use { source ->
                val output = resolver.openOutputStream(target, "w") ?: throw IOException("File tujuan tidak dapat ditulis.")
                output.use { source.copyTo(it) }
            }
            resolver.update(target, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return target
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
    }
}
