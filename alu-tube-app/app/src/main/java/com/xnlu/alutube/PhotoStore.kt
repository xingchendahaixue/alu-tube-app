package com.xnlu.alutube

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 记录照片的本地存储：filesDir/photos/<记录id>/xxxx.jpg
 */
object PhotoStore {

    fun dir(context: Context, recordId: Long): File =
        File(context.filesDir, "photos/$recordId")

    fun list(context: Context, recordId: Long): List<File> {
        val d = dir(context, recordId)
        if (!d.exists()) return emptyList()
        return d.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    }

    /** 把相册选中的图片复制到本记录目录，返回是否成功 */
    fun add(context: Context, recordId: Long, uri: Uri): Boolean {
        return try {
            val d = dir(context, recordId)
            d.mkdirs()
            val name = "p_${System.currentTimeMillis()}.jpg"
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(d, name).outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun delete(file: File): Boolean = file.exists() && file.delete()

    /** 按需采样解码，避免大图占内存 */
    fun decodeSampled(file: File, maxDim: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > maxDim * 2 || opts.outHeight / sample > maxDim * 2) {
                sample *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }
}
