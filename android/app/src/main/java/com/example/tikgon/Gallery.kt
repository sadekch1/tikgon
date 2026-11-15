package com.example.tikgon

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** حفظ نسخة في معرض الهاتف (Movies/TikGon) + مشاركة ملف */
object Gallery {

    /** ينسخ ملف الفيديو إلى Movies/TikGon عبر MediaStore. يعيد اسم الملف المحفوظ. */
    fun saveCopy(ctx: Context, src: File, displayName: String): String {
        val cr = ctx.contentResolver
        val name = if (displayName.endsWith(".mp4", true)) displayName else "$displayName.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/TikGon")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
                ?: throw IOException("تعذّر إنشاء الملف في المعرض")
            try {
                val out = cr.openOutputStream(uri) ?: throw IOException("تعذّر فتح الملف للكتابة")
                out.use { o -> src.inputStream().use { it.copyTo(o, 256 * 1024) } }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                cr.update(uri, done, null, null)
            } catch (e: Exception) {
                try { cr.delete(uri, null, null) } catch (_: Exception) {}
                throw e
            }
            return name
        }
        // أندرويد قديم: مجلد عام + مسح وسائط (يحتاج صلاحية الكتابة التي تُطلب عند الإقلاع)
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "TikGon")
            .apply { mkdirs() }
        var dst = File(dir, name)
        var i = 1
        while (dst.exists()) { dst = File(dir, name.replace(".mp4", " (${i++}).mp4")) }
        src.inputStream().use { inp -> dst.outputStream().use { inp.copyTo(it, 256 * 1024) } }
        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(dst.path), arrayOf("video/mp4"), null)
        return dst.name
    }

    /** Intent مشاركة قياسي للملف (عبر FileProvider) */
    fun shareIntent(ctx: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
