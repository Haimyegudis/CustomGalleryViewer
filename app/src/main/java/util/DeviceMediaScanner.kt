package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
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
    val mediaCount: Int
)

@Singleton
class DeviceMediaScanner @Inject constructor(@ApplicationContext private val context: Context) {

    // Maps synthetic bucket IDs to filesystem paths for folders not in MediaStore
    private val syntheticFolderPaths = mutableMapOf<Long, String>()

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

        // Also scan filesystem for folders not indexed by MediaStore
        // (e.g., folders with .nomedia, or files not yet scanned)
        val knownPaths = folders.values.map { it.path }.toSet()
        scanFilesystemFolders(knownPaths, folders)

        val result = folders.values
            .map { MediaFolder(it.bucketId, it.name, it.path, it.thumbnailUri, it.count) }
            .sortedByDescending { it.mediaCount }
        Log.w(TAG, "getAllMediaFolders: found ${result.size} folders: ${result.map { "${it.name}(${it.mediaCount})" }}")
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
        val dirsToScan = listOf(
            File(extStorage, "Download"),
            File(extStorage, "Movies"),
            File(extStorage, "DCIM"),
            File(extStorage, "Pictures"),
            File(extStorage, "Video"),
            File(extStorage, "Videos"),
            File(extStorage, "Media")
        )

        for (topDir in dirsToScan) {
            if (!topDir.exists() || !topDir.isDirectory) continue
            scanDirForMediaFolders(topDir, knownPaths, folders, maxDepth = 3, currentDepth = 0)
        }
    }

    private fun scanDirForMediaFolders(
        dir: File,
        knownPaths: Set<String>,
        folders: MutableMap<Long, MutableMediaFolder>,
        maxDepth: Int,
        currentDepth: Int
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
                    syntheticBucketId, dir.name, dirPath, thumbUri, mediaCount
                )
                syntheticFolderPaths[syntheticBucketId] = dirPath
            }
        }

        // Recurse into subdirectories
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirForMediaFolders(file, knownPaths, folders, maxDepth, currentDepth + 1)
            }
        }
    }

    fun getFolderPath(bucketId: Long): String? {
        val syntheticPath = syntheticFolderPaths[bucketId]
        if (syntheticPath != null) return syntheticPath

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
                        if (!path.isNullOrBlank()) return path.substringBeforeLast('/')
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
                        if (!path.isNullOrBlank()) return path.substringBeforeLast('/')
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

    private data class MutableMediaFolder(
        val bucketId: Long,
        val name: String,
        val path: String,
        var thumbnailUri: Uri?,
        var count: Int
    )
}
