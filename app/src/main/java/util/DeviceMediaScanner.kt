package com.example.customgalleryviewer.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.customgalleryviewer.data.SearchFilters
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceMediaScanner"

data class MediaFolder(
    val bucketId: Long,
    val name: String,
    val path: String,
    val thumbnailUri: Uri?,
    val mediaCount: Int,
    val isExternal: Boolean = false
)

@Singleton
class DeviceMediaScanner @Inject constructor(@ApplicationContext private val context: Context) {

    // Maps synthetic bucket IDs to filesystem paths for folders not in MediaStore
    private val syntheticFolderPaths = mutableMapOf<Long, String>()

    // Cache folder paths (both synthetic and MediaStore-resolved) to avoid repeated queries
    private val folderPathCache = mutableMapOf<Long, String>()

    // In-memory cache of last scan result for instant access
    @Volatile
    private var cachedFolders: List<MediaFolder>? = null

    fun getCachedFolders(): List<MediaFolder>? = cachedFolders

    fun getAllMediaFolders(): List<MediaFolder> {
        val folders = mutableMapOf<Long, MutableMediaFolder>()

        // Query images
        queryMedia(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media._ID
        ) { bucketId, bucketName, data, contentUri ->
            val folder = folders.getOrPut(bucketId) {
                MutableMediaFolder(bucketId, bucketName, data.substringBeforeLast('/'), null, 0)
            }
            folder.count++
            if (folder.thumbnailUri == null) folder.thumbnailUri = contentUri
        }

        // Query videos
        queryMedia(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media._ID
        ) { bucketId, bucketName, data, contentUri ->
            val folder = folders.getOrPut(bucketId) {
                MutableMediaFolder(bucketId, bucketName, data.substringBeforeLast('/'), null, 0)
            }
            folder.count++
            if (folder.thumbnailUri == null) folder.thumbnailUri = contentUri
        }

        // Adjust counts for folders with m3u8 files (subtract .ts segments, add m3u8 entries)
        for ((_, folder) in folders) {
            val dir = File(folder.path)
            if (dir.exists()) {
                val allFiles = dir.listFiles() ?: continue
                val hasM3u8 = allFiles.any {
                    it.isFile && (it.extension.equals("m3u8", true) || it.extension.equals("m3u", true))
                }
                if (hasM3u8) {
                    val tsCount = allFiles.count { it.isFile && it.extension.equals("ts", true) }
                    val m3u8Count = allFiles.count {
                        it.isFile && (it.extension.equals("m3u8", true) || it.extension.equals("m3u", true))
                    }
                    folder.count = (folder.count - tsCount + m3u8Count).coerceAtLeast(1)
                }
            }
        }

        // Mark MediaStore folders as external if their path is not under primary storage
        val primaryPath = android.os.Environment.getExternalStorageDirectory().absolutePath
        for ((_, folder) in folders) {
            if (!folder.path.startsWith(primaryPath)) {
                folder.isExternal = true
            }
        }

        // Also scan filesystem for folders not indexed by MediaStore
        // (e.g., folders with .nomedia, or files not yet scanned)
        val knownPaths = folders.values.map { it.path }.toSet()
        scanFilesystemFolders(knownPaths, folders)

        val result = folders.values
            .map { MediaFolder(it.bucketId, it.name, it.path, it.thumbnailUri, it.count, it.isExternal) }
            .sortedByDescending { it.mediaCount }
        Log.w(TAG, "getAllMediaFolders: found ${result.size} folders: ${result.map { "${it.name}(${it.mediaCount})" }}")
        // Cache result and populate path cache for instant getFolderPath() lookups
        cachedFolders = result
        result.forEach { folderPathCache[it.bucketId] = it.path }
        return result
    }

    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif",
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v",
        "mpg", "mpeg", "m3u8", "m3u", "m2ts", "mts"
    )

    private fun scanFilesystemFolders(
        knownPaths: Set<String>,
        folders: MutableMap<Long, MutableMediaFolder>
    ) {
        val extStorage = android.os.Environment.getExternalStorageDirectory()
        // Scan common top-level directories
        val dirsToScan = mutableListOf(
            File(extStorage, "Download"),
            File(extStorage, "Movies"),
            File(extStorage, "DCIM"),
            File(extStorage, "Pictures"),
            File(extStorage, "Video"),
            File(extStorage, "Videos"),
            File(extStorage, "Media")
        )

        // Scan USB/external volumes (OTG drives, SD cards)
        try {
            val storageManager = context.getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                storageManager.storageVolumes.forEach { volume ->
                    try {
                        var volumeDir: File? = null
                        // API 30+ has getDirectory()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            volumeDir = volume.directory
                        }
                        // Fallback: reflection for older APIs
                        if (volumeDir == null) {
                            val getPathMethod = volume.javaClass.getMethod("getPath")
                            val volumePath = getPathMethod.invoke(volume) as? String
                            if (volumePath != null) volumeDir = File(volumePath)
                        }
                        if (volumeDir != null && !volume.isPrimary && volumeDir.exists() && volumeDir.canRead()) {
                            Log.i(TAG, "Found external volume: ${volume.getDescription(context)} at ${volumeDir.absolutePath}")
                            dirsToScan.add(volumeDir)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error reading volume: ${volume.getDescription(context)}", e)
                    }
                }
            }
            // Also check common USB mount points
            val usbMounts = listOf(
                File("/storage"),
                File("/mnt/media_rw")
            )
            for (mountPoint in usbMounts) {
                if (mountPoint.exists() && mountPoint.isDirectory) {
                    mountPoint.listFiles()?.forEach { volumeDir ->
                        if (volumeDir.isDirectory && volumeDir.canRead()
                            && volumeDir.absolutePath != extStorage.absolutePath
                            && !volumeDir.name.equals("emulated", ignoreCase = true)
                            && !volumeDir.name.equals("self", ignoreCase = true)
                        ) {
                            if (dirsToScan.none { it.absolutePath == volumeDir.absolutePath }) {
                                Log.i(TAG, "Found USB/external mount: ${volumeDir.absolutePath}")
                                dirsToScan.add(volumeDir)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning external volumes", e)
        }

        for (topDir in dirsToScan) {
            if (!topDir.exists() || !topDir.isDirectory) continue
            // Use deeper scan for USB/external volumes (not under primary storage)
            val isExternal = !topDir.absolutePath.startsWith(extStorage.absolutePath)
            scanDirForMediaFolders(topDir, knownPaths, folders, maxDepth = if (isExternal) 5 else 3, currentDepth = 0, isExternal = isExternal)
        }
    }

    private fun scanDirForMediaFolders(
        dir: File,
        knownPaths: Set<String>,
        folders: MutableMap<Long, MutableMediaFolder>,
        maxDepth: Int,
        currentDepth: Int,
        isExternal: Boolean = false
    ) {
        if (currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return

        // Count media files in this directory
        var mediaCount = 0
        var firstMediaFile: File? = null
        val hasM3u8Dir = files.any {
            it.isDirectory && it.name.lowercase().let { n -> n.endsWith(".m3u8") || n.endsWith(".m3u") }
        }

        for (file in files) {
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in mediaExtensions) {
                    mediaCount++
                    if (firstMediaFile == null) firstMediaFile = file
                }
            }
        }

        // Count .m3u8 directories as single media items
        if (hasM3u8Dir) {
            for (file in files) {
                if (file.isDirectory && file.name.lowercase().let { it.endsWith(".m3u8") || it.endsWith(".m3u") }) {
                    mediaCount++
                    if (firstMediaFile == null) {
                        // Use first .ts file as thumbnail
                        val segDir = File(file, "video_segments")
                        val tsFile = (if (segDir.exists()) segDir else file).listFiles()?.firstOrNull {
                            it.isFile && it.extension.equals("ts", true)
                        }
                        if (tsFile != null) firstMediaFile = tsFile
                    }
                }
            }
        }

        // Add this directory if it has media and isn't already known
        val dirPath = dir.absolutePath
        if (mediaCount > 0 && dirPath !in knownPaths) {
            val syntheticBucketId = dirPath.hashCode().toLong()
            if (syntheticBucketId !in folders) {
                val thumbUri = firstMediaFile?.let { Uri.fromFile(it) }
                folders[syntheticBucketId] = MutableMediaFolder(
                    syntheticBucketId, dir.name, dirPath, thumbUri, mediaCount, isExternal = isExternal
                )
                syntheticFolderPaths[syntheticBucketId] = dirPath
            }
        }

        // Recurse into subdirectories
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirForMediaFolders(file, knownPaths, folders, maxDepth, currentDepth + 1, isExternal = isExternal)
            }
        }
    }

    fun getFolderPath(bucketId: Long): String? {
        // Check in-memory path cache first (populated by getAllMediaFolders)
        folderPathCache[bucketId]?.let { return it }

        val syntheticPath = syntheticFolderPaths[bucketId]
        if (syntheticPath != null) {
            folderPathCache[bucketId] = syntheticPath
            return syntheticPath
        }

        // Query MediaStore for the folder path
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA),
                "${MediaStore.Images.Media.BUCKET_ID}=?",
                arrayOf(bucketId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    if (dataIdx >= 0) {
                        val path = cursor.getString(dataIdx)
                        if (!path.isNullOrBlank()) {
                            val folderPath = path.substringBeforeLast('/')
                            folderPathCache[bucketId] = folderPath
                            return folderPath
                        }
                    }
                }
            }
            // Try videos
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media.DATA),
                "${MediaStore.Video.Media.BUCKET_ID}=?",
                arrayOf(bucketId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    if (dataIdx >= 0) {
                        val path = cursor.getString(dataIdx)
                        if (!path.isNullOrBlank()) {
                            val folderPath = path.substringBeforeLast('/')
                            folderPathCache[bucketId] = folderPath
                            return folderPath
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getFilesInFolder(bucketId: Long): List<Uri> {
        // Check if this is a filesystem-scanned folder (not in MediaStore)
        val syntheticPath = syntheticFolderPaths[bucketId]
        if (syntheticPath != null) {
            return getFilesFromFilesystem(syntheticPath)
        }

        val files = mutableListOf<Uri>()
        var folderPath: String? = null

        // Images in folder
        queryFilesInBucketWithPath(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            bucketId
        ) { uri, path ->
            files.add(uri)
            if (folderPath.isNullOrBlank() && path.isNotBlank()) folderPath = path.substringBeforeLast('/')
        }

        // Videos in folder
        queryFilesInBucketWithPath(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED,
            bucketId
        ) { uri, path ->
            files.add(uri)
            if (folderPath.isNullOrBlank() && path.isNotBlank()) folderPath = path.substringBeforeLast('/')
        }

        Log.w(TAG, "getFilesInFolder: bucketId=$bucketId, files=${files.size}, folderPath=$folderPath")

        // Fallback: resolve folderPath from file URIs when DATA column returned null/empty
        if (folderPath.isNullOrBlank()) {
            val resolvedPaths = files.mapNotNull { getPathFromUri(it) }
            val directories = resolvedPaths.map { it.substringBeforeLast('/') }
                .filter { it.contains('/') }.toSet()
            for (dirPath in directories) {
                val dir = File(dirPath)
                if (dir.exists() && dir.isDirectory) {
                    val m3u8Files = dir.listFiles()?.filter {
                        it.isFile && (it.extension.equals("m3u8", true) || it.extension.equals("m3u", true))
                    } ?: emptyList()
                    if (m3u8Files.isNotEmpty()) {
                        folderPath = dirPath
                        break
                    }
                }
            }
        }

        // Check for m3u8 files in the folder and handle HLS content
        var dirHasM3u8 = false
        folderPath?.let { dirPath ->
            val dir = File(dirPath)
            val dirExists = dir.exists()
            val dirIsDir = dir.isDirectory
            val allDirFiles = dir.listFiles()
            Log.w(TAG, "m3u8 check: dirPath=$dirPath, exists=$dirExists, isDir=$dirIsDir, listFiles=${allDirFiles?.size ?: "NULL"}")
            if (allDirFiles != null) {
                allDirFiles.forEach { f ->
                    Log.w(TAG, "  file: ${f.name} ext=${f.extension}")
                }
            }
            if (dirExists && dirIsDir) {
                val m3u8Files = allDirFiles?.filter {
                    it.isFile && (it.extension.equals("m3u8", true) || it.extension.equals("m3u", true))
                } ?: emptyList()
                Log.w(TAG, "m3u8Files found: ${m3u8Files.size} -> ${m3u8Files.map { it.name }}")

                if (m3u8Files.isNotEmpty()) {
                    dirHasM3u8 = true
                    // Remove .ts segment files from the list
                    files.removeAll { uri ->
                        val path = getPathFromUri(uri)
                        path != null && path.substringAfterLast('.').equals("ts", true)
                    }

                    // Add m3u8 files as file:// URIs
                    m3u8Files.forEach { m3u8File ->
                        val m3u8Uri = Uri.fromFile(m3u8File)
                        if (files.none { it.toString() == m3u8Uri.toString() }) {
                            files.add(0, m3u8Uri) // Add at top
                        }
                    }
                }
            }
        }

        // Also check subdirectories for m3u8 files
        if (!dirHasM3u8 && folderPath != null) {
            val subDirs = File(folderPath!!).listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (subDir in subDirs) {
                val subM3u8 = subDir.listFiles()?.filter {
                    it.isFile && (it.extension.equals("m3u8", true) || it.extension.equals("m3u", true))
                } ?: emptyList()
                if (subM3u8.isNotEmpty()) {
                    files.removeAll { uri ->
                        val path = getPathFromUri(uri)
                        path != null && path.substringAfterLast('.').equals("ts", true)
                            && path.startsWith(subDir.absolutePath)
                    }
                    subM3u8.forEach { m3u8File ->
                        val m3u8Uri = Uri.fromFile(m3u8File)
                        if (files.none { it.toString() == m3u8Uri.toString() }) {
                            files.add(0, m3u8Uri)
                        }
                    }
                }
            }
        }

        return files
    }

    private fun getFilesFromFilesystem(dirPath: String): List<Uri> {
        val result = mutableListOf<Uri>()
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return result

        val files = dir.listFiles() ?: return result

        // Check for m3u8 directories
        for (file in files) {
            if (file.isDirectory) {
                val nameLower = file.name.lowercase()
                if (nameLower.endsWith(".m3u8") || nameLower.endsWith(".m3u")) {
                    // Find playlist file inside
                    val playlistFile = findPlaylistFileInDir(file)
                    if (playlistFile != null) {
                        result.add(Uri.fromFile(playlistFile))
                    }
                    continue
                }
            }
            if (file.isFile && !file.name.startsWith(".")) {
                val ext = file.extension.lowercase()
                if (ext in mediaExtensions) {
                    result.add(Uri.fromFile(file))
                }
            }
        }
        return result
    }

    private fun findPlaylistFileInDir(dir: File): File? {
        val files = dir.listFiles() ?: return null
        // Check for files with .m3u8/.m3u extension
        files.firstOrNull {
            it.isFile && (it.extension.equals("m3u8", true) || it.extension.equals("m3u", true))
        }?.let { return it }
        // Check for files with m3u8/playlist in the name
        files.firstOrNull {
            it.isFile && !it.name.startsWith(".") && it.name.lowercase().let { name ->
                name.contains("m3u8") || name.contains("playlist")
            }
        }?.let { return it }
        // Check file content for #EXTM3U header
        for (file in files) {
            if (!file.isFile || file.name.startsWith(".") || file.extension.equals("ts", true)) continue
            try {
                val header = file.bufferedReader().use { it.readLine() }
                if (header?.trim() == "#EXTM3U") return file
            } catch (_: Exception) { }
        }
        return null
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx >= 0) {
                        val data = cursor.getString(dataIdx)
                        if (!data.isNullOrBlank()) return data
                    }
                    // Fallback to DISPLAY_NAME (just filename, enough for extension checking)
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) return cursor.getString(nameIdx)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun queryMedia(
        contentUri: Uri,
        bucketIdCol: String,
        bucketNameCol: String,
        dataCol: String,
        idCol: String,
        onRow: (Long, String, String, Uri) -> Unit
    ) {
        try {
            // Include RELATIVE_PATH as fallback when DATA is null (Android 10+)
            val projection = mutableListOf(bucketIdCol, bucketNameCol, dataCol, idCol)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            context.contentResolver.query(
                contentUri,
                projection.toTypedArray(),
                null, null,
                "$bucketNameCol ASC"
            )?.use { cursor ->
                val bucketIdIdx = cursor.getColumnIndex(bucketIdCol)
                val bucketNameIdx = cursor.getColumnIndex(bucketNameCol)
                val dataIdx = cursor.getColumnIndex(dataCol)
                val idIdx = cursor.getColumnIndex(idCol)
                val relPathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                if (bucketIdIdx == -1 || idIdx == -1) return

                val extStorage = android.os.Environment.getExternalStorageDirectory().absolutePath

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getLong(bucketIdIdx)
                    val bucketName = if (bucketNameIdx != -1) cursor.getString(bucketNameIdx) ?: "Unknown" else "Unknown"
                    var data = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    // Fallback: construct path from RELATIVE_PATH
                    if (data.isNullOrBlank() && relPathIdx >= 0) {
                        val relPath = cursor.getString(relPathIdx)
                        if (!relPath.isNullOrBlank()) {
                            data = "$extStorage/$relPath"
                        }
                    }
                    if (data.isNullOrBlank()) continue
                    val mediaId = cursor.getLong(idIdx)
                    val mediaUri = android.content.ContentUris.withAppendedId(contentUri, mediaId)
                    onRow(bucketId, bucketName, data, mediaUri)
                }
            }
        } catch (_: Exception) {}
    }

    private fun queryFilesInBucketWithPath(
        contentUri: Uri,
        bucketIdCol: String,
        idCol: String,
        dataCol: String,
        dateCol: String,
        bucketId: Long,
        onFile: (Uri, String) -> Unit
    ) {
        try {
            context.contentResolver.query(
                contentUri,
                arrayOf(idCol, dataCol),
                "$bucketIdCol = ?",
                arrayOf(bucketId.toString()),
                "$dateCol DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(idCol)
                val dataIdx = cursor.getColumnIndex(dataCol)
                if (idIdx == -1) return
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idIdx)
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) ?: "" else ""
                    onFile(android.content.ContentUris.withAppendedId(contentUri, mediaId), path)
                }
            }
        } catch (_: Exception) {}
    }

    fun searchMedia(filters: SearchFilters): List<Uri> {
        val results = mutableListOf<Uri>()
        if (filters.query.isBlank() && !filters.hasActiveFilters) return results

        searchMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, filters, results)
        searchMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, filters, results)

        return results
    }

    private fun searchMediaStore(contentUri: Uri, filters: SearchFilters, results: MutableList<Uri>) {
        try {
            val selection = StringBuilder()
            val selectionArgs = mutableListOf<String>()

            if (filters.query.isNotBlank()) {
                selection.append("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
                selectionArgs.add("%${filters.query}%")
            }

            if (filters.dateFrom != null) {
                if (selection.isNotEmpty()) selection.append(" AND ")
                selection.append("${MediaStore.MediaColumns.DATE_ADDED} >= ?")
                selectionArgs.add((filters.dateFrom / 1000).toString())
            }
            if (filters.dateTo != null) {
                if (selection.isNotEmpty()) selection.append(" AND ")
                selection.append("${MediaStore.MediaColumns.DATE_ADDED} <= ?")
                selectionArgs.add((filters.dateTo / 1000).toString())
            }

            if (filters.minSize != null) {
                if (selection.isNotEmpty()) selection.append(" AND ")
                selection.append("${MediaStore.MediaColumns.SIZE} >= ?")
                selectionArgs.add(filters.minSize.toString())
            }
            if (filters.maxSize != null) {
                if (selection.isNotEmpty()) selection.append(" AND ")
                selection.append("${MediaStore.MediaColumns.SIZE} <= ?")
                selectionArgs.add(filters.maxSize.toString())
            }

            context.contentResolver.query(
                contentUri,
                arrayOf(MediaStore.MediaColumns._ID),
                selection.toString().ifEmpty { null },
                if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    results.add(ContentUris.withAppendedId(contentUri, id))
                }
            }
        } catch (_: Exception) {}
    }

    fun getRecentlyAddedMedia(limit: Int = 20): List<Uri> {
        val results = mutableListOf<Uri>()

        // Images
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    results.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIdx)))
                    count++
                }
            }
        } catch (_: Exception) {}

        // Videos
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    results.add(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIdx)))
                    count++
                }
            }
        } catch (_: Exception) {}

        // Sort by date_added desc and limit
        return results.take(limit)
    }

    private data class MutableMediaFolder(
        val bucketId: Long,
        val name: String,
        val path: String,
        var thumbnailUri: Uri?,
        var count: Int,
        var isExternal: Boolean = false
    )
}
