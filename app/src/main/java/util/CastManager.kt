package com.example.customgalleryviewer.util

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

private const val TAG = "CastManager"

data class CastDevice(
    val name: String,
    val location: String, // Device description URL
    val controlUrl: String, // AVTransport control URL
    val type: DeviceType
)

enum class DeviceType { DLNA, CHROMECAST }

@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaServer: LocalMediaServer
) {
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castingDevice = MutableStateFlow<CastDevice?>(null)
    val castingDevice: StateFlow<CastDevice?> = _castingDevice.asStateFlow()

    private var currentDevice: CastDevice? = null

    /** Discover DLNA renderers on the local network via SSDP */
    suspend fun discoverDevices(): List<CastDevice> = withContext(Dispatchers.IO) {
        val found = mutableListOf<CastDevice>()

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val multicastLock = wifiManager.createMulticastLock("dlna_discovery")
            multicastLock.setReferenceCounted(true)
            multicastLock.acquire()

            try {
                found.addAll(ssdpDiscover())
            } finally {
                multicastLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery error", e)
        }

        _devices.value = found
        Log.i(TAG, "Found ${found.size} devices: ${found.map { it.name }}")
        found
    }

    private fun ssdpDiscover(): List<CastDevice> {
        val devices = mutableListOf<CastDevice>()
        val ssdpAddress = InetAddress.getByName("239.255.255.250")
        val ssdpPort = 1900

        // Search for MediaRenderer devices
        val searchTarget = "urn:schemas-upnp-org:device:MediaRenderer:1"
        val searchMessage = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 3\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }

        val socket = MulticastSocket(null)
        socket.reuseAddress = true
        socket.soTimeout = 4000

        try {
            val sendData = searchMessage.toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, ssdpAddress, ssdpPort)
            socket.send(sendPacket)

            val locationSet = mutableSetOf<String>()
            val receiveBuffer = ByteArray(4096)

            while (true) {
                try {
                    val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)

                    // Extract LOCATION header
                    val location = Regex("LOCATION:\\s*(.*)", RegexOption.IGNORE_CASE)
                        .find(response)?.groupValues?.get(1)?.trim()

                    if (location != null && location !in locationSet) {
                        locationSet.add(location)
                        try {
                            val device = fetchDeviceDescription(location)
                            if (device != null) devices.add(device)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch device at $location", e)
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
            }
        } finally {
            socket.close()
        }

        return devices
    }

    private fun fetchDeviceDescription(locationUrl: String): CastDevice? {
        val connection = URL(locationUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000

        return try {
            val xml = connection.inputStream.bufferedReader().readText()
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())

            val friendlyName = doc.getElementsByTagName("friendlyName").item(0)?.textContent ?: "Unknown Device"

            // Find AVTransport service control URL
            var controlUrl: String? = null
            val serviceList = doc.getElementsByTagName("service")
            for (i in 0 until serviceList.length) {
                val service = serviceList.item(i)
                val children = service.childNodes
                var serviceType = ""
                var serviceControlUrl = ""
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    when (child.nodeName) {
                        "serviceType" -> serviceType = child.textContent ?: ""
                        "controlURL" -> serviceControlUrl = child.textContent ?: ""
                    }
                }
                if (serviceType.contains("AVTransport")) {
                    controlUrl = serviceControlUrl
                    break
                }
            }

            if (controlUrl == null) {
                Log.w(TAG, "No AVTransport service found for $friendlyName")
                return null
            }

            // Resolve relative control URL
            val baseUrl = locationUrl.substringBeforeLast('/')
            val fullControlUrl = if (controlUrl.startsWith("http")) controlUrl
            else if (controlUrl.startsWith("/")) {
                val parsed = URL(locationUrl)
                "${parsed.protocol}://${parsed.host}:${parsed.port}$controlUrl"
            } else "$baseUrl/$controlUrl"

            CastDevice(
                name = friendlyName,
                location = locationUrl,
                controlUrl = fullControlUrl,
                type = DeviceType.DLNA
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse error for $locationUrl", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Cast a media file to a DLNA device */
    suspend fun castTo(device: CastDevice, mediaUri: Uri) = withContext(Dispatchers.IO) {
        try {
            // Start local server if not running
            mediaServer.start()

            // Register the file and get HTTP URL
            val httpUrl = mediaServer.registerUri(mediaUri) ?: run {
                Log.e(TAG, "Could not register URI: $mediaUri")
                return@withContext
            }

            val mimeType = guessMimeType(mediaUri.toString())

            // Send SetAVTransportURI SOAP action
            val soapAction = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\""
            val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <CurrentURI>$httpUrl</CurrentURI>
      <CurrentURIMetaData>&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;&lt;item&gt;&lt;dc:title&gt;${mediaUri.lastPathSegment ?: "Media"}&lt;/dc:title&gt;&lt;res protocolInfo="http-get:*:$mimeType:*"&gt;$httpUrl&lt;/res&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>
    </u:SetAVTransportURI>
  </s:Body>
</s:Envelope>"""

            sendSoapRequest(device.controlUrl, soapAction, soapBody)

            // Send Play action
            val playAction = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\""
            val playBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <Speed>1</Speed>
    </u:Play>
  </s:Body>
</s:Envelope>"""

            sendSoapRequest(device.controlUrl, playAction, playBody)

            currentDevice = device
            _isCasting.value = true
            _castingDevice.value = device
            Log.i(TAG, "Casting to ${device.name}: $httpUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Cast error", e)
        }
    }

    /** Stop casting */
    suspend fun stopCasting() = withContext(Dispatchers.IO) {
        val device = currentDevice ?: return@withContext
        try {
            val stopAction = "\"urn:schemas-upnp-org:service:AVTransport:1#Stop\""
            val stopBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
    </u:Stop>
  </s:Body>
</s:Envelope>"""
            sendSoapRequest(device.controlUrl, stopAction, stopBody)
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
        }
        currentDevice = null
        _isCasting.value = false
        _castingDevice.value = null
    }

    private fun sendSoapRequest(url: String, soapAction: String, body: String) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPAction", soapAction)
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            connection.outputStream.write(body.toByteArray())
            val responseCode = connection.responseCode
            Log.d(TAG, "SOAP response: $responseCode for $soapAction")
            if (responseCode >= 400) {
                val error = try { connection.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                Log.w(TAG, "SOAP error: $error")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun guessMimeType(uriStr: String): String {
        val ext = uriStr.substringAfterLast('.', "").lowercase().substringBefore('?')
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "video/mp4"
        }
    }
}
