package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.customgalleryviewer.data.VaultDao
import com.example.customgalleryviewer.data.VaultEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao
) {
    private val vaultDir: File
        get() {
            val dir = File(context.filesDir, "vault")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    suspend fun moveToVault(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourcePath = getPathFromUri(uri)
            val sourceFile = if (sourcePath != null) File(sourcePath) else null

            // Determine filename
            val fileName = sourceFile?.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
            val destFile = File(vaultDir, "${System.currentTimeMillis()}_$fileName")

            if (sourceFile != null && sourceFile.exists()) {
                // File-based copy + delete
                sourceFile.copyTo(destFile, overwrite = true)
                sourceFile.delete()
                // Also remove from MediaStore
                try {
                    context.contentResolver.delete(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        "${android.provider.MediaStore.MediaColumns.DATA}=?",
                        arrayOf(sourcePath)
                    )
                } catch (_: Exception) {}
            } else {
                // Content URI fallback — copy via InputStream
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext false
                // Try to delete source via content resolver
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            }

            if (!destFile.exists()) return@withContext false

            val isVideo = fileName.lowercase().let {
                it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".avi") ||
                        it.endsWith(".mov") || it.endsWith(".webm") || it.endsWith(".3gp")
            }

            vaultDao.insert(
                VaultEntity(
                    originalPath = sourcePath ?: uri.toString(),
                    vaultPath = destFile.absolutePath,
                    fileName = fileName,
                    isVideo = isVideo
                )
            )
            Log.i("VaultManager", "Moved to vault: $fileName -> ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("VaultManager", "Error moving to vault", e)
            false
        }
    }

    suspend fun restoreFromVault(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val item = vaultDao.getById(id) ?: return@withContext false
            val vaultFile = File(item.vaultPath)
            if (!vaultFile.exists()) return@withContext false

            val destFile = File(item.originalPath)
            destFile.parentFile?.mkdirs()

            vaultFile.copyTo(destFile, overwrite = true)
            vaultFile.delete()

            vaultDao.deleteById(id)
            true
        } catch (e: Exception) {
            Log.e("VaultManager", "Error restoring from vault", e)
            false
        }
    }

    suspend fun deleteFromVault(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val item = vaultDao.getById(id) ?: return@withContext false
            File(item.vaultPath).delete()
            vaultDao.deleteById(id)
            true
        } catch (e: Exception) {
            Log.e("VaultManager", "Error deleting from vault", e)
            false
        }
    }

    fun getVaultFileUri(item: VaultEntity): Uri {
        return Uri.fromFile(File(item.vaultPath))
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
