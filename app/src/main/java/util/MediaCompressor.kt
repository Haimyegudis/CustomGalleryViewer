package com.example.customgalleryviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object MediaCompressor {

    private const val TAG = "MediaCompressor"

    fun compressImage(
        context: Context,
        uri: Uri,
        quality: Int = 80,
        maxWidth: Int = 1920,
        maxHeight: Int = 1080,
        onProgress: (Float) -> Unit = {}
    ): File? {
        try {
            onProgress(0.1f)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            var sampleSize = 1
            while (origWidth / sampleSize > maxWidth * 2 || origHeight / sampleSize > maxHeight * 2) {
                sampleSize *= 2
            }

            onProgress(0.3f)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val stream2 = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions) ?: return null
            stream2.close()

            onProgress(0.5f)
            val scaledBitmap = if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
                val newW = (bitmap.width * ratio).toInt()
                val newH = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else bitmap

            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Compressed"
            )
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, "compressed_${System.currentTimeMillis()}.jpg")

            onProgress(0.7f)
            FileOutputStream(outputFile).use { fos ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }

            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            bitmap.recycle()

            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)
            onProgress(1f)
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            return null
        }
    }

    fun compressVideo(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): File? {
        // Fallback: copy the file (real transcoding requires MediaCodec pipeline)
        try {
            onProgress(0.1f)
            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Compressed"
            )
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress((totalRead.toFloat() / (totalRead + 1)).coerceAtMost(0.95f))
                    }
                }
            }

            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)
            onProgress(1f)
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing video", e)
            return null
        }
    }
}
