package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalMediaServer"

@Singleton
class LocalMediaServer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val fileMap = ConcurrentHashMap<String, String>() // token -> filePath
    private var port = 0
    @Volatile private var running = false

    fun start(): Int {
        if (running) return port
        val socket = ServerSocket(0) // OS picks a free port
        serverSocket = socket
        port = socket.localPort
        running = true
        Log.i(TAG, "Starting media server on port $port")

        executor.submit {
            while (running) {
                try {
                    val client = socket.accept()
                    executor.submit { handleClient(client) }
                } catch (e: Exception) {
                    if (running) Log.e(TAG, "Accept error", e)
                }
            }
        }
        return port
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        fileMap.clear()
    }

    /** Register a file and get a URL for it */
    fun registerFile(filePath: String): String {
        val token = filePath.hashCode().toUInt().toString(16)
        fileMap[token] = filePath
        val ip = getDeviceIp()
        return "http://$ip:$port/media/$token"
    }

    /** Register a URI (file:// or content://) and return HTTP URL */
    fun registerUri(uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { registerFile(it) }
            "content" -> {
                // Resolve content URI to file path
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val path = cursor.getString(0)
                            if (!path.isNullOrBlank()) return registerFile(path)
                        }
                    }
                } catch (_: Exception) {}
                null
            }
            else -> null
        }
    }

    fun getDeviceIp(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10000
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            Log.d(TAG, "Request: $requestLine")

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (true) {
                line = input.readLine()
                if (line.isNullOrBlank()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim().lowercase()] = line.substring(colonIdx + 1).trim()
                }
            }

            // Parse GET /media/<token>
            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                sendError(socket, 405, "Method Not Allowed")
                return
            }

            val path = URLDecoder.decode(parts[1], "UTF-8")
            val tokenMatch = Regex("/media/([a-f0-9]+)").find(path)
            if (tokenMatch == null) {
                sendError(socket, 404, "Not Found")
                return
            }

            val token = tokenMatch.groupValues[1]
            val filePath = fileMap[token]
            if (filePath == null) {
                sendError(socket, 404, "File Not Found")
                return
            }

            val file = File(filePath)
            if (!file.exists()) {
                sendError(socket, 404, "File Not Found")
                return
            }

            val mimeType = guessMimeType(filePath)
            val fileLength = file.length()

            // Handle Range requests for seeking
            val rangeHeader = headers["range"]
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val rangeParts = rangeHeader.removePrefix("bytes=").split("-")
                val start = rangeParts[0].toLongOrNull() ?: 0L
                val end = if (rangeParts.size > 1 && rangeParts[1].isNotEmpty()) {
                    rangeParts[1].toLongOrNull() ?: (fileLength - 1)
                } else {
                    fileLength - 1
                }
                val contentLength = end - start + 1

                val response = buildString {
                    append("HTTP/1.1 206 Partial Content\r\n")
                    append("Content-Type: $mimeType\r\n")
                    append("Content-Length: $contentLength\r\n")
                    append("Content-Range: bytes $start-$end/$fileLength\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().write(response.toByteArray())
                sendFileRange(socket, file, start, contentLength)
            } else {
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: $mimeType\r\n")
                    append("Content-Length: $fileLength\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().write(response.toByteArray())
                sendFileRange(socket, file, 0, fileLength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendFileRange(socket: Socket, file: File, start: Long, length: Long) {
        FileInputStream(file).use { fis ->
            fis.skip(start)
            val buffer = ByteArray(8192)
            var remaining = length
            val out = socket.getOutputStream()
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = fis.read(buffer, 0, toRead)
                if (read == -1) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            out.flush()
        }
    }

    private fun sendError(socket: Socket, code: Int, message: String) {
        val response = "HTTP/1.1 $code $message\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(response.toByteArray())
        socket.close()
    }

    private fun guessMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "ts" -> "video/mp2t"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heif"
            else -> "application/octet-stream"
        }
    }
}
