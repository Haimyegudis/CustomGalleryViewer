package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.example.customgalleryviewer.data.GalleryItem
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaCacheManager
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.data.PlaylistItemEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URLDecoder

class FileScanner(
    private val context: Context,
    private val cacheManager: MediaCacheManager,
    private val showHidden: Boolean = false
) {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "dng", "cr2", "nef", "arw", "rw2", "orf", "srw", "pef", "raf", "tiff", "tif", "svg", "ico", "avif", "jxl")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpg", "mpeg", "vob", "m3u8", "m3u", "f4v", "ogv", "divx", "asf", "rm", "rmvb", "m2ts", "mts", "rec", "mxf")
    // Extensions that are HLS/DASH segments — hidden when m3u8 is present
    private val hlsSegmentExtensions = setOf("ts")

    companion object {
        private const val BATCH_SIZE = 150
        private const val BATCH_DELAY_MS = 30L
    }

    suspend fun scanPlaylistItemsFlow(
        items: List<PlaylistItemEntity>,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        Log.w("FileScanner", "Starting scan for ${items.size} items, filter: $filter")

        for (item in items) {
            if (!currentCoroutineContext().isActive) break

            val uri = Uri.parse(item.uriString)

            if (item.type == ItemType.FILE) {
                Log.w("FileScanner", "Emitting single file: $uri")
                emit(listOf(uri))
            } else {
                val cachedFiles = cacheManager.getCachedFiles(uri)
                if (cachedFiles != null) {
                    // Emit ALL cached files at once — no chunking delay
                    Log.w("FileScanner", "CACHE HIT: ${cachedFiles.size} files from $uri")
                    emit(cachedFiles)

                    // Background delta scan — only emit new files
                    val cachedSet = cachedFiles.toHashSet()
                    val freshFiles = mutableListOf<Uri>()
                    scanFolderProgressive(uri, item.isRecursive, filter).collect { batch ->
                        val delta = batch.filter { it !in cachedSet }
                        if (delta.isNotEmpty()) {
                            emit(delta)
                        }
                        freshFiles.addAll(batch)
                    }
                    // Update cache with fresh results
                    if (freshFiles.isNotEmpty()) {
                        cacheManager.cacheFiles(uri, freshFiles)
                        Log.w("FileScanner", "Cache updated: ${cachedFiles.size} -> ${freshFiles.size} files for $uri")
                    }
                } else {
                    Log.w("FileScanner", "CACHE MISS - scanning $uri")
                    val scanned = mutableListOf<Uri>()
                    scanFolderProgressive(uri, item.isRecursive, filter).collect { batch ->
                        Log.w("FileScanner", "Scanned batch: ${batch.size} files")
                        scanned.addAll(batch)
                        emit(batch)
                    }
                    if (scanned.isNotEmpty()) {
                        cacheManager.cacheFiles(uri, scanned)
                        Log.w("FileScanner", "Cached ${scanned.size} files for $uri")
                    } else {
                        Log.w("FileScanner", "No files found in $uri with filter $filter")
                    }
                }
            }
        }
        Log.w("FileScanner", "Scan complete")
    }

    private suspend fun scanFolderProgressive(
        folderUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        var foundAny = false

        val physicalPath = getPhysicalPath(folderUri)
        if (physicalPath != null) {
            val folder = File(physicalPath)
            if (folder.exists() && folder.isDirectory) {
                Log.w("FileScanner", "Physical scan for: $physicalPath")
                scanPhysicalFolderProgressive(folder, recursive, filter).collect { batch ->
                    foundAny = true
                    emit(batch)
                }
            }
        }

        if (!foundAny) {
            Log.w("FileScanner", "Using SAF for: $folderUri")
            scanViaSAFProgressive(folderUri, recursive, filter).collect { emit(it) }
        }
    }

    private suspend fun scanPhysicalFolderProgressive(
        folder: File,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        val batch = mutableListOf<Uri>()
        // Track directories that contain m3u8 files
        val dirsWithM3u8 = mutableSetOf<String>()
        // Track directories that ARE m3u8 containers (dir named *.m3u8)
        // Maps dir path -> playlist file path
        val hlsContainerDirs = mutableMapOf<String, File?>()

        // First pass: find directories with m3u8 files AND directories named *.m3u8
        suspend fun findM3u8Dirs(dir: File) {
            val dirNameLower = dir.name.lowercase()
            // Directory itself named .m3u8 — it's an HLS container
            if (dirNameLower.endsWith(".m3u8") || dirNameLower.endsWith(".m3u")) {
                val playlistFile = findPlaylistFileInDir(dir)
                hlsContainerDirs[dir.absolutePath] = playlistFile
                // Mark this dir and all subdirs as having m3u8
                dirsWithM3u8.add(dir.absolutePath)
                dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                    dirsWithM3u8.add(sub.absolutePath)
                }
                return // Don't scan inside further
            }

            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory && recursive) {
                    findM3u8Dirs(file)
                } else {
                    val ext = file.name.substringAfterLast('.', "").lowercase()
                    if (ext == "m3u8" || ext == "m3u") {
                        dirsWithM3u8.add(file.parent ?: "")
                    }
                }
            }
        }
        findM3u8Dirs(folder)

        suspend fun scanDir(dir: File) {
            if (!currentCoroutineContext().isActive) return

            // If this dir is an HLS container (named *.m3u8), emit only the playlist file
            val hlsPlaylist = hlsContainerDirs[dir.absolutePath]
            if (hlsPlaylist != null) {
                batch.add(Uri.fromFile(hlsPlaylist))
                if (batch.size >= BATCH_SIZE) {
                    emit(batch.toList())
                    batch.clear()
                    delay(BATCH_DELAY_MS)
                }
                return // Don't scan inside — all segments are part of this playlist
            }
            // Skip subdirs of HLS containers entirely
            if (hlsContainerDirs.keys.any { dir.absolutePath.startsWith(it) }) return

            val files = dir.listFiles() ?: return
            val dirPath = dir.absolutePath
            val hasM3u8 = dirPath in dirsWithM3u8

            for (file in files) {
                if (!currentCoroutineContext().isActive) return
                // Skip hidden files/folders unless showHidden is enabled
                if (!showHidden && file.name.startsWith(".")) continue

                if (file.isDirectory) {
                    if (recursive) scanDir(file)
                } else {
                    val ext = file.name.substringAfterLast('.', "").lowercase()
                    // Skip .ts segment files in directories that have m3u8 playlists
                    if (hasM3u8 && ext in hlsSegmentExtensions) continue

                    if (isMediaFile(file.name, null, filter)) {
                        batch.add(Uri.fromFile(file))
                        if (batch.size >= BATCH_SIZE) {
                            emit(batch.toList())
                            batch.clear()
                            delay(BATCH_DELAY_MS)
                        }
                    }
                }
            }
        }

        scanDir(folder)
        if (batch.isNotEmpty()) {
            emit(batch.toList())
        }
    }

    /**
     * Find the actual HLS playlist file inside a directory named *.m3u8.
     * Checks for files with m3u8/m3u extension, files named "playlist*", or files starting with #EXTM3U.
     */
    private fun findPlaylistFileInDir(dir: File): File? {
        val files = dir.listFiles() ?: return null

        // 1. Check for files with .m3u8 or .m3u extension
        files.firstOrNull {
            it.isFile && (it.extension.equals("m3u8", true) || it.extension.equals("m3u", true))
        }?.let { return it }

        // 2. Check for files with "m3u8" or "m3u" or "playlist" in the name
        files.firstOrNull {
            it.isFile && !it.name.startsWith(".") && it.name.lowercase().let { name ->
                name.contains("m3u8") || name.contains("m3u") || name.contains("playlist")
            }
        }?.let { return it }

        // 3. Check file content for #EXTM3U header
        for (file in files) {
            if (!file.isFile || file.name.startsWith(".") || file.isDirectory) continue
            if (file.extension.equals("ts", true)) continue // Skip segments
            try {
                val header = file.bufferedReader().use { it.readLine() }
                if (header?.trim() == "#EXTM3U") return file
            } catch (_: Exception) { }
        }

        return null
    }

    private suspend fun scanViaSAFProgressive(
        treeUri: Uri,
        recursive: Boolean,
        filter: MediaFilterType
    ): Flow<List<Uri>> = flow {
        val batch = mutableListOf<Uri>()
        val visited = mutableSetOf<String>()
        // Track parent docIds that have m3u8 files
        val dirsWithM3u8 = mutableSetOf<String>()

        // First pass: find dirs with m3u8
        suspend fun findM3u8Dirs(parentDocId: String) {
            if (parentDocId in visited) return
            visited.add(parentDocId)
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ), null, null, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    if (idIdx == -1) return
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idIdx) ?: continue
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                        val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive) findM3u8Dirs(docId)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext == "m3u8" || ext == "m3u") {
                                dirsWithM3u8.add(parentDocId)
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        findM3u8Dirs(rootDocId)
        visited.clear()

        suspend fun scanDir(parentDocId: String) {
            if (!currentCoroutineContext().isActive) return
            if (parentDocId in visited) return
            visited.add(parentDocId)
            val hasM3u8 = parentDocId in dirsWithM3u8

            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    if (idIdx == -1) return

                    while (cursor.moveToNext() && currentCoroutineContext().isActive) {
                        val documentId = cursor.getString(idIdx) ?: continue
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                        val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive) {
                                scanDir(documentId)
                            }
                        } else {
                            // Skip .ts segments when m3u8 exists in same directory
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (hasM3u8 && ext in hlsSegmentExtensions) continue

                            if (isMediaFile(name, mime, filter)) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                                batch.add(fileUri)
                                if (batch.size >= BATCH_SIZE) {
                                    emit(batch.toList())
                                    batch.clear()
                                    delay(BATCH_DELAY_MS)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScanner", "Error scanning docId=$parentDocId", e)
            }
        }

        scanDir(rootDocId)
        if (batch.isNotEmpty()) {
            emit(batch.toList())
        }
    }

    private fun getPhysicalPath(uri: Uri): String? {
        val uriString = uri.toString()
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath

        if (uriString.contains("primary%3A") || uriString.contains("primary:")) {
            val decoded = URLDecoder.decode(uriString, "UTF-8")
            val parts = decoded.split(Regex("primary[:%]"))
            if (parts.size > 1) return "$externalStorage/${parts[1]}"
        }
        if (uri.scheme == "file") return uri.path
        return null
    }

    private fun isMediaFile(name: String?, mime: String?, filter: MediaFilterType): Boolean {
        val safeName = name?.lowercase() ?: ""
        val safeMime = mime?.lowercase() ?: ""
        val ext = safeName.substringAfterLast('.', "")

        val isImage = imageExtensions.contains(ext) || safeMime.startsWith("image/")
        val isVideo = videoExtensions.contains(ext) || safeMime.startsWith("video/")
            || safeName.contains("m3u8") || safeName.contains("m3u")

        return when (filter) {
            MediaFilterType.PHOTOS_ONLY -> isImage
            MediaFilterType.VIDEO_ONLY -> isVideo
            MediaFilterType.MIXED -> isImage || isVideo
        }
    }

    /**
     * Browse a single directory level — returns folders and media files (not recursive).
     * Used for folder navigation in the gallery view.
     */
    fun browseFolderLevel(
        folderUri: Uri,
        filter: MediaFilterType
    ): List<GalleryItem> {
        val physicalPath = getPhysicalPath(folderUri)
        if (physicalPath != null) {
            val folder = File(physicalPath)
            if (folder.exists() && folder.isDirectory) {
                return browsePhysicalFolder(folder, filter)
            }
        }

        // Fallback to SAF
        return browseSAFFolder(folderUri, filter)
    }

    private fun browsePhysicalFolder(folder: File, filter: MediaFilterType): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()
        val files = folder.listFiles() ?: return items

        // Check if this dir has m3u8
        val hasM3u8 = files.any {
            val ext = it.name.substringAfterLast('.', "").lowercase()
            ext == "m3u8" || ext == "m3u"
        }

        val sortedFiles = files.sortedBy { it.name.lowercase() }

        for (file in sortedFiles) {
            // Skip hidden files/folders
            if (!showHidden && file.name.startsWith(".")) continue

            if (file.isDirectory) {
                val dirNameLower = file.name.lowercase()
                // Directory named *.m3u8 — show as single playable item, not a folder
                if (dirNameLower.endsWith(".m3u8") || dirNameLower.endsWith(".m3u")) {
                    val playlistFile = findPlaylistFileInDir(file)
                    if (playlistFile != null) {
                        items.add(GalleryItem.MediaFile(Uri.fromFile(playlistFile)))
                        continue
                    }
                }

                // Count media children (quick check, just immediate children)
                val childCount = file.listFiles()?.count { child ->
                    !child.isDirectory && isMediaFile(child.name, null, MediaFilterType.MIXED)
                } ?: 0
                val hasSubfolders = file.listFiles()?.any { it.isDirectory } ?: false

                // Show folder if it has media or subfolders
                if (childCount > 0 || hasSubfolders) {
                    // Find first media file as thumbnail
                    val thumbFile = file.listFiles()?.firstOrNull { child ->
                        !child.isDirectory && isMediaFile(child.name, null, MediaFilterType.MIXED)
                    }
                    items.add(
                        GalleryItem.Folder(
                            uri = Uri.fromFile(file),
                            name = file.name,
                            thumbnailUri = thumbFile?.let { Uri.fromFile(it) },
                            childCount = childCount
                        )
                    )
                }
            } else {
                val ext = file.name.substringAfterLast('.', "").lowercase()
                // Skip .ts segments when m3u8 exists
                if (hasM3u8 && ext in hlsSegmentExtensions) continue

                if (isMediaFile(file.name, null, filter)) {
                    items.add(GalleryItem.MediaFile(Uri.fromFile(file)))
                }
            }
        }

        return items
    }

    private fun browseSAFFolder(treeUri: Uri, filter: MediaFilterType): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)

            // Check for m3u8 first
            var hasM3u8 = false
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""
                    if (mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext == "m3u8" || ext == "m3u") {
                            hasM3u8 = true
                            break
                        }
                    }
                }
            }

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idIdx == -1) return items

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val nameLower = name.lowercase()
                        // Directory named *.m3u8 — show as single playable item, not a folder
                        if (nameLower.endsWith(".m3u8") || nameLower.endsWith(".m3u")) {
                            val physPath = getPhysicalPath(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                            if (physPath != null) {
                                val playlistFile = findPlaylistFileInDir(File(physPath))
                                if (playlistFile != null) {
                                    items.add(GalleryItem.MediaFile(Uri.fromFile(playlistFile)))
                                    continue
                                }
                            }
                            // Even if we can't find the playlist, don't show as navigable folder
                            continue
                        }
                        val folderDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        items.add(
                            GalleryItem.Folder(
                                uri = folderDocUri,
                                name = name,
                                childCount = 0
                            )
                        )
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (hasM3u8 && ext in hlsSegmentExtensions) continue

                        if (isMediaFile(name, mime, filter)) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            items.add(GalleryItem.MediaFile(fileUri))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "Error browsing SAF folder: $treeUri", e)
        }

        return items
    }

    /**
     * Browse a subfolder within a tree URI using its document ID.
     */
    fun browseSubfolder(
        treeUri: Uri,
        docId: String,
        filter: MediaFilterType
    ): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()

        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            // Check for m3u8 first
            var hasM3u8 = false
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""
                    if (mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext == "m3u8" || ext == "m3u") {
                            hasM3u8 = true
                            break
                        }
                    }
                }
            }

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idIdx == -1) return items

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idIdx) ?: continue
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) ?: "" else ""

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val nameLower = name.lowercase()
                        // Directory named *.m3u8 — show as single playable item
                        if (nameLower.endsWith(".m3u8") || nameLower.endsWith(".m3u")) {
                            val physPath = getPhysicalPath(DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId))
                            if (physPath != null) {
                                val playlistFile = findPlaylistFileInDir(File(physPath))
                                if (playlistFile != null) {
                                    items.add(GalleryItem.MediaFile(Uri.fromFile(playlistFile)))
                                    continue
                                }
                            }
                            continue // Don't show as navigable folder
                        }
                        val folderDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        items.add(
                            GalleryItem.Folder(
                                uri = folderDocUri,
                                name = name,
                                childCount = 0
                            )
                        )
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (hasM3u8 && ext in hlsSegmentExtensions) continue

                        if (isMediaFile(name, mime, filter)) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            items.add(GalleryItem.MediaFile(fileUri))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileScanner", "Error browsing subfolder docId=$docId", e)
        }

        return items
    }
}
