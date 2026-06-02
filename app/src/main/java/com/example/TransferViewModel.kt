package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class MessageSender {
    Phone, PC, System
}

data class TransferMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    object Connected : ConnectionState
}

sealed interface TransferUiState {
    object Idle : TransferUiState
    data class Sending(val filename: String) : TransferUiState
    data class Receiving(val filename: String) : TransferUiState
    data class Success(val filename: String, val isUpload: Boolean) : TransferUiState
    data class Error(val message: String) : TransferUiState
}

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("transfer_prefs", Context.MODE_PRIVATE)

    private val _host = MutableStateFlow(sharedPrefs.getString("host", "192.168.1.10") ?: "192.168.1.10")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _port = MutableStateFlow(sharedPrefs.getInt("port", 5000))
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _autoDownload = MutableStateFlow(sharedPrefs.getBoolean("auto_download", true))
    val autoDownload: StateFlow<Boolean> = _autoDownload.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _transferState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val transferState: StateFlow<TransferUiState> = _transferState.asStateFlow()

    private val _messages = MutableStateFlow<List<TransferMessage>>(emptyList())
    val messages: StateFlow<List<TransferMessage>> = _messages.asStateFlow()

    // Holds file offered by PC when auto-download is disabled
    private val _incomingFileOffer = MutableStateFlow<String?>(null)
    val incomingFileOffer: StateFlow<String?> = _incomingFileOffer.asStateFlow()

    private var client: NetworkClient? = null

    init {
        addSystemMessage("Клиент готов к работе. Задайте IP-адрес вашего ПК и нажмите 'Подключиться'")
    }

    fun saveConfig(newHost: String, newPort: Int, autoDl: Boolean) {
        _host.value = newHost
        _port.value = newPort
        _autoDownload.value = autoDl
        sharedPrefs.edit().apply {
            putString("host", newHost)
            putInt("port", newPort)
            putBoolean("auto_download", autoDl)
            apply()
        }
    }

    fun connect() {
        val currentHost = _host.value
        val currentPort = _port.value
        
        disconnect()
        
        _connectionState.value = ConnectionState.Connecting
        addSystemMessage("Подключение к $currentHost:$currentPort...")

        val newClient = NetworkClient(getApplication(), currentHost, currentPort)
        newClient.listener = object : NetworkClient.Listener {
            override fun onConnected() {
                viewModelScope.launch {
                    _connectionState.value = ConnectionState.Connected
                    addSystemMessage("Успешно подключено к ПК!")
                }
            }

            override fun onDisconnected() {
                viewModelScope.launch {
                    _connectionState.value = ConnectionState.Disconnected
                    addSystemMessage("Соединение разорвано")
                }
            }

            override fun onChatMessage(from: String, text: String) {
                viewModelScope.launch {
                    val sender = if (from.equals("PC", ignoreCase = true)) MessageSender.PC else MessageSender.Phone
                    _messages.value = _messages.value + TransferMessage(sender = sender, text = text)
                }
            }

            override fun onFileAvailable(filename: String) {
                viewModelScope.launch {
                    addSystemMessage("Доступен файл для скачивания: $filename")
                    if (_autoDownload.value) {
                        downloadFile(filename)
                    } else {
                        _incomingFileOffer.value = filename
                    }
                }
            }

            override fun onFileCancelled(filename: String) {
                viewModelScope.launch {
                    val currentState = _transferState.value
                    if (currentState is TransferUiState.Receiving && currentState.filename == filename) {
                        _transferState.value = TransferUiState.Idle
                    }
                    _messages.value = _messages.value + TransferMessage(
                        sender = MessageSender.PC,
                        text = "❌ Передача файла отменена: $filename"
                    )
                }
            }

            override fun onFileReceived(filename: String) {
                viewModelScope.launch {
                    val currentState = _transferState.value
                    val isReceivingState = currentState is TransferUiState.Receiving
                    if (isReceivingState) {
                        addSystemMessage("Файл скачан и сохранен: $filename")
                        _messages.value = _messages.value + TransferMessage(
                            sender = MessageSender.PC,
                            text = "📁 Получен файл: $filename"
                        )
                    } else {
                        addSystemMessage("ПК успешно сохранил файл: $filename")
                    }
                }
            }

            override fun onError(error: String) {
                viewModelScope.launch {
                    addSystemMessage("Ошибка: $error")
                }
            }
        }

        client = newClient
        newClient.connect()
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val currentClient = client
        if (currentClient == null || _connectionState.value != ConnectionState.Connected) {
            addSystemMessage("Ошибка: нет подключения")
            return
        }

        viewModelScope.launch {
            currentClient.sendChatMessage(text)
        }
    }

    fun downloadFile(filename: String) {
        val currentClient = client
        if (currentClient == null) {
            _transferState.value = TransferUiState.Error("Нет активного подключения")
            return
        }

        _incomingFileOffer.value = null

        currentClient.downloadFile(
            filename = filename,
            onDownloadStart = {
                viewModelScope.launch {
                    _transferState.value = TransferUiState.Receiving(filename)
                }
            },
            onDownloadSuccess = {
                viewModelScope.launch {
                    _transferState.value = TransferUiState.Success(filename, isUpload = false)
                    delay(3000)
                    if (_transferState.value is TransferUiState.Success) {
                        _transferState.value = TransferUiState.Idle
                    }
                }
            },
            onDownloadFailure = { error ->
                viewModelScope.launch {
                    _transferState.value = TransferUiState.Error("Ошибка скачивания: $error")
                    delay(4000)
                    if (_transferState.value is TransferUiState.Error) {
                        _transferState.value = TransferUiState.Idle
                    }
                }
            }
        )
    }

    fun cancelTransfer() {
        val currentState = _transferState.value
        if (currentState is TransferUiState.Sending) {
            client?.cancelFileUpload()
            val filename = currentState.filename
            _transferState.value = TransferUiState.Idle
            _messages.value = _messages.value + TransferMessage(
                sender = MessageSender.Phone,
                text = "❌ Отмена отправки файла: $filename"
            )
        } else if (currentState is TransferUiState.Receiving) {
            client?.cancelFileDownload()
            val filename = currentState.filename
            _transferState.value = TransferUiState.Idle
            _messages.value = _messages.value + TransferMessage(
                sender = MessageSender.Phone,
                text = "❌ Отмена получения файла: $filename"
            )
        }
    }

    fun resetTransferState() {
        _transferState.value = TransferUiState.Idle
    }

    fun rejectFileOffer() {
        _incomingFileOffer.value = null
    }

    fun sendFileFromUri(uri: Uri) {
        val context = getApplication<Application>()
        val file = copyUriToCache(context, uri)
        if (file == null) {
            _transferState.value = TransferUiState.Error("Ошибка чтения файла")
            return
        }

        val currentClient = client
        if (currentClient == null) {
            _transferState.value = TransferUiState.Error("Нет активного подключения")
            return
        }

        currentClient.sendFile(
            filePath = file.absolutePath,
            onUploadStart = {
                viewModelScope.launch {
                    _transferState.value = TransferUiState.Sending(file.name)
                }
            },
            onUploadSuccess = {
                viewModelScope.launch {
                    _transferState.value = TransferUiState.Success(file.name, isUpload = true)
                    addSystemMessage("Файл отправлен: ${file.name}")
                    _messages.value = _messages.value + TransferMessage(
                        sender = MessageSender.Phone,
                        text = "📁 Отправлен файл: ${file.name}"
                    )
                    delay(3000)
                    if (_transferState.value is TransferUiState.Success) {
                        _transferState.value = TransferUiState.Idle
                    }
                }
            },
            onUploadFailure = { error ->
                viewModelScope.launch {
                    _transferState.value = TransferUiState.Error("Ошибка отправки: $error")
                    delay(4000)
                    if (_transferState.value is TransferUiState.Error) {
                        _transferState.value = TransferUiState.Idle
                    }
                }
            }
        )
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun addSystemMessage(text: String) {
        _messages.value = _messages.value + TransferMessage(sender = MessageSender.System, text = text)
    }

    private fun copyUriToCache(context: Context, uri: Uri): File? {
        val contentResolver = context.contentResolver
        var fileName = "transfer_file"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }

        return try {
            val file = File(context.cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
