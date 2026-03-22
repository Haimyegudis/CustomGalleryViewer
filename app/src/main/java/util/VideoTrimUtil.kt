package com.example.customgalleryviewer.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object VideoTrimUtil {
    private const val TAG = "VideoTrimUtil"

    fun trimVideo(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit = {}
    ): File? {
        try {
            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Trimmed"
            )
            if (!outputDir.exists()) outputDir.mkdirs()

            val outputFile = File(outputDir, "trimmed_${System.currentTimeMillis()}.mp4")

            val extractor = MediaExtractor()
            val fd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return null
            extractor.setDataSource(fd.fileDescriptor)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val muxerTrackIndex = muxer.addTrack(format)
                trackIndexMap[i] = muxerTrackIndex
            }

            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val totalDuration = endUs - startUs
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            for (trackIndex in 0 until extractor.trackCount) {
                extractor.selectTrack(trackIndex)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val sampleTime = extractor.sampleTime
                    if (sampleTime > endUs) break

                    if (sampleTime >= startUs) {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = sampleTime - startUs
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(trackIndexMap[trackIndex]!!, buffer, bufferInfo)

                        if (totalDuration > 0) {
                            onProgress(((sampleTime - startUs).toFloat() / totalDuration).coerceIn(0f, 1f))
                        }
                    }

                    extractor.advance()
                }
                extractor.unselectTrack(trackIndex)
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            fd.close()

            // Notify MediaStore
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                null, null
            )

            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming video", e)
            return null
        }
    }
}
