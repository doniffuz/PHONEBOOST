package uz.phoneboost.app.data.storage

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LargeFileKind { IMAGE, VIDEO, DOCUMENT, OTHER }

data class LargeFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val modifiedMillis: Long?,
    val kind: LargeFileKind
)

/**
 * Null = bu kategoriya uchun Android ma’lumot bermadi (ruxsat yo‘q yoki API cheklovi).
 */
data class MediaBreakdown(
    val photosBytes: Long,
    val videosBytes: Long,
    val documentsBytes: Long?,
    val largeFiles: List<LargeFile>
)

object StoragePermissions {
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun granted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

class StorageRepository(private val context: Context) {

    /** Hujjatlarni Android 10 (API 29) gacha MediaStore ko‘rsatadi; keyinroq — yo‘q (scoped storage). */
    private val documentsVisible = Build.VERSION.SDK_INT <= 29

    suspend fun scanMedia(limit: Int = 25): MediaBreakdown? = withContext(Dispatchers.IO) {
        if (!StoragePermissions.granted(context)) return@withContext null
        runCatching { doScan(limit) }.getOrNull()
    }

    private fun doScan(limit: Int): MediaBreakdown {
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        var photos = 0L
        var videos = 0L
        var docs = 0L
        val top = ArrayList<LargeFile>(limit + 1)

        context.contentResolver.query(filesUri, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (c.moveToNext()) {
                val size = c.getLong(sizeCol)
                if (size <= 0) continue
                val mediaType = c.getInt(typeCol)
                val mime = c.getString(mimeCol)
                val kind = when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> LargeFileKind.IMAGE
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> LargeFileKind.VIDEO
                    MediaStore.Files.FileColumns.MEDIA_TYPE_NONE ->
                        if (documentsVisible && isDocumentMime(mime)) LargeFileKind.DOCUMENT else LargeFileKind.OTHER
                    else -> LargeFileKind.OTHER
                }
                when (kind) {
                    LargeFileKind.IMAGE -> photos += size
                    LargeFileKind.VIDEO -> videos += size
                    LargeFileKind.DOCUMENT -> docs += size
                    LargeFileKind.OTHER -> Unit
                }
                if (kind == LargeFileKind.OTHER) continue

                // Faqat eng kattalarning kichik ro‘yxatini saqlaymiz.
                if (top.size < limit || size > top.last().sizeBytes) {
                    val id = c.getLong(idCol)
                    val itemUri = when (kind) {
                        LargeFileKind.IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        LargeFileKind.VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        else -> ContentUris.withAppendedId(filesUri, id)
                    }
                    top += LargeFile(
                        uri = itemUri,
                        name = c.getString(nameCol) ?: "—",
                        sizeBytes = size,
                        mimeType = mime,
                        modifiedMillis = c.getLong(dateCol).takeIf { it > 0 }?.times(1000),
                        kind = kind
                    )
                    top.sortByDescending { it.sizeBytes }
                    if (top.size > limit) top.removeAt(top.size - 1)
                }
            }
        }
        return MediaBreakdown(
            photosBytes = photos,
            videosBytes = videos,
            documentsBytes = if (documentsVisible) docs else null,
            largeFiles = top
        )
    }

    private fun isDocumentMime(mime: String?): Boolean {
        if (mime == null) return false
        return mime == "application/pdf" ||
            mime.startsWith("text/") ||
            mime.startsWith("application/vnd.openxmlformats") ||
            mime.startsWith("application/vnd.ms-") ||
            mime == "application/msword" ||
            mime == "application/rtf" ||
            mime == "application/epub+zip"
    }
}
