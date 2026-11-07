package uz.phoneboost.app.data.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import uz.phoneboost.app.core.util.startSafely
import java.io.File

/** PDF'ni vaqtinchalik keshga yozadi va Android Share orqali ulashadi (FileProvider, ruxsat kerak emas). */
object ReportFiles {
    private const val DIR = "reports"
    const val FILE_NAME = "PHONEBOOST_report.pdf"

    fun writeToCache(context: Context, bytes: ByteArray): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File(dir, FILE_NAME).apply { writeBytes(bytes) }
    }

    fun share(context: Context, file: File, chooserTitle: String): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startSafely(Intent.createChooser(send, chooserTitle))
    }.getOrDefault(false)
}
