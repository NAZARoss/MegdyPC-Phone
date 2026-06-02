package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class NetworkClient(
    private val context: Context,
    val host: String,  // IP-адрес ПК, например "192.168.1.10"
    val port: Int = 5000
) {
    private val baseUrl = "http://$host:$port"
    // Connect directly to the Socket.IO WebSocket transport endpoint
    private val wsUrl = "ws://$host:$port/socket.io/?EIO=4&transport=websocket"

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var uploadCall: Call? = null
    private var downloadCall: Call? = null

    private val gson = com.google.gson.Gson()

    // Папка для сохранения файлов (Downloads/FileTransfer)
    private val downloadDirName = "FileTransfer"

    // Слушатели событий
    var listener: Listener? = null

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onChatMessage(from: String, text: String)
        fun onFileAvailable(filename: String)
        fun onFileCancelled(filename: String)
        fun onFileReceived(filename: String)
        fun onError(error: String)
    }

    fun connect() {
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Socket.IO requires sending a connect packet "40" to complete the handshake
                webSocket.send("40")
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    // Handle Socket.IO Ping (2) -> Pong (3) heartbeat to keep connection alive
                    if (text == "2") {
                        webSocket.send("3")
                        return
                    }

                    var eventType: String? = null
                    var payload: JSONObject? = null

                    if (text.startsWith("42[")) {
                        // Socket.IO format: 42["event_name", {payload_object}]
                        val arrayStr = text.substring(2)
                        val jsonArray = JSONArray(arrayStr)
                        eventType = jsonArray.optString(0)
                        payload = jsonArray.optJSONObject(1)
                    } else if (text.startsWith("{")) {
                        // Regular fallback JSON parsing
                        val json = JSONObject(text)
                        eventType = json.optString("type", "")
                        payload = json
                    }

                    if (eventType != null && payload != null) {
                        when (eventType) {
                            "chat" -> {
                                val from = payload.optString("from", "unknown")
                                val msgText = payload.optString("text", "")
                                listener?.onChatMessage(from, msgText)
                            }
                            "file_available" -> {
                                val filename = payload.optString("filename", "")
                                listener?.onFileAvailable(filename)
                            }
                            "file_cancelled" -> {
                                val filename = payload.optString("filename", "")
                                cancelFileDownload()
                                listener?.onFileCancelled(filename)
                            }
                            "file_received" -> {
                                val filename = payload.optString("filename", "")
                                listener?.onFileReceived(filename)
                            }
                        }
                    }
                } catch (e: Exception) {
                    listener?.onError("JSON parsing error: ${e.localizedMessage}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener?.onDisconnected()
                listener?.onError("WebSocket failure: ${t.localizedMessage}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener?.onDisconnected()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        uploadCall?.cancel()
        uploadCall = null
        downloadCall?.cancel()
        downloadCall = null
    }

    // Отправка текстового сообщения в чат
    fun sendChatMessage(text: String) {
        val payload = JSONObject().apply {
            put("text", text)
        }
        // Send in Socket.IO format for PC server compatibility
        val socketIoMsg = "42[\"chat\",$payload]"
        webSocket?.send(socketIoMsg)
    }

    // Отправка файла на ПК
    fun sendFile(filePath: String, onUploadStart: () -> Unit = {}, onUploadSuccess: () -> Unit = {}, onUploadFailure: (String) -> Unit = {}) {
        val file = File(filePath)
        if (!file.exists()) {
            listener?.onError("File not found: $filePath")
            onUploadFailure("File not found")
            return
        }

        onUploadStart()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name,
                file.asRequestBody("application/octet-stream".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("$baseUrl/upload")
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        uploadCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    return
                }
                listener?.onError("Upload failed: ${e.localizedMessage}")
                onUploadFailure(e.localizedMessage ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                uploadCall = null
                if (!response.isSuccessful) {
                    listener?.onError("Upload error: ${response.code}")
                    onUploadFailure("Server returned error code: ${response.code}")
                } else {
                    onUploadSuccess()
                }
            }
        })
    }

    fun cancelFileUpload() {
        uploadCall?.cancel()
        uploadCall = null
    }

    // Скачивание файла с ПК (вызывается, например, после onFileAvailable)
    fun downloadFile(filename: String, onDownloadStart: () -> Unit = {}, onDownloadSuccess: () -> Unit = {}, onDownloadFailure: (String) -> Unit = {}) {
        onDownloadStart()
        val request = Request.Builder()
            .url("$baseUrl/download/$filename")
            .get()
            .build()

        val call = client.newCall(request)
        downloadCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    return
                }
                listener?.onError("Download failed: ${e.localizedMessage}")
                onDownloadFailure(e.localizedMessage ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                downloadCall = null
                if (!response.isSuccessful) {
                    listener?.onError("Download error: ${response.code}")
                    onDownloadFailure("Server response not successful: ${response.code}")
                    return
                }

                response.body?.byteStream()?.use { inputStream ->
                    try {
                        val savedUri = saveToDownloads(filename, inputStream)
                        if (savedUri != null) {
                            listener?.onFileReceived(filename)
                            onDownloadSuccess()
                        } else {
                            listener?.onError("File save error: URI is null")
                            onDownloadFailure("Failed to write file to downloads")
                        }
                    } catch (e: Exception) {
                        listener?.onError("File save error: ${e.localizedMessage}")
                        onDownloadFailure(e.localizedMessage ?: "File save error")
                    }
                }
            }
        })
    }

    fun cancelFileDownload() {
        downloadCall?.cancel()
        downloadCall = null
    }

    /**
     * Сохраняет InputStream в папку Downloads/FileTransfer, используя MediaStore.
     * Возвращает Uri сохранённого файла или null при ошибке.
     */
    private fun saveToDownloads(filename: String, inputStream: InputStream): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            // Для Android 10+ относительный путь внутри Downloads
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$downloadDirName")
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            inputStream.copyTo(outputStream)
        } ?: throw IOException("Failed to open output stream")

        return uri
    }
}
