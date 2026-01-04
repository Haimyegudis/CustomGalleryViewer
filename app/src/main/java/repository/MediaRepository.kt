// MediaRepository.kt
// גרסה פשוטה - עובד עם או בלי database

package com.example.customgalleryviewer.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.util.FileScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaCacheManager: MediaCacheManager
) {
    // מפה זמנית לשמירת folderUri לפי playlistId
    // אם יש לך database, תחליף את זה!
    private val playlistFolders = mutableMapOf<Long, Uri>()

    /**
     * רשום folderUri עבור playlistId
     * קרא לזה לפני loadPlaylist אם אין לך database
     */
    fun registerPlaylistFolder(playlistId: Long, folderUri: Uri) {
        playlistFolders[playlistId] = folderUri
    }

    /**
     * קבל קבצי מדיה עבור playlist ID
     */
    fun getMediaFilesFlow(playlistId: Long): Flow<List<Uri>> = flow {
        val folderUri = playlistFolders[playlistId]

        if (folderUri == null) {
            Log.e("MediaRepository", "No folder URI for playlist $playlistId. Call registerPlaylistFolder first!")
            return@flow
        }

        emitAll(getMediaFilesFlow(folderUri))
    }.flowOn(Dispatchers.IO)

    /**
     * קבל קבצי מדיה עבור תיקייה - עם Cache!
     */
    fun getMediaFilesFlow(folderUri: Uri): Flow<List<Uri>> = flow {
        Log.d("MediaRepository", "Loading files for folder: $folderUri")

        // נסה cache קודם!
        val cachedFiles = mediaCacheManager.getCachedFiles(folderUri)

        if (cachedFiles != null && cachedFiles.isNotEmpty()) {
            Log.d("MediaRepository", "✅ Using cache: ${cachedFiles.size} files")
            cachedFiles.chunked(200).forEach { batch ->
                emit(batch)
            }
            return@flow
        }

        // סרוק מחדש
        Log.d("MediaRepository", "❌ No valid cache - scanning folder...")

        val startTime = System.currentTimeMillis()
        val allFiles = withContext(Dispatchers.IO) {
            FileScanner.scanMediaFiles(context, folderUri)
        }
        val scanDuration = System.currentTimeMillis() - startTime

        Log.d("MediaRepository", "⚡ Scanned ${allFiles.size} files in ${scanDuration}ms")

        if (allFiles.isEmpty()) {
            Log.w("MediaRepository", "No media files found")
            return@flow
        }

        // שמור ב-cache
        withContext(Dispatchers.IO) {
            mediaCacheManager.cacheFiles(folderUri, allFiles)
        }

        // פלוט batches
        allFiles.chunked(200).forEach { batch ->
            emit(batch)
        }

        Log.d("MediaRepository", "✅ Completed: ${allFiles.size} files")
    }.flowOn(Dispatchers.IO)

    fun clearAllCache() {
        mediaCacheManager.clearAllCache()
    }
}