package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TransferScreen(
    viewModel: TransferViewModel,
    modifier: Modifier = Modifier
) {
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val autoDownload by viewModel.autoDownload.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val transferState by viewModel.transferState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val fileOffer by viewModel.incomingFileOffer.collectAsState()

    var showConfig by remember { mutableStateOf(false) }
    var hostInput by remember { mutableStateOf(host) }
    var portInput by remember { mutableStateOf(port.toString()) }
    var chatInputText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Document Picker to send a file to PC
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendFileFromUri(it) }
    }

    // Auto scroll chat to the bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Bar
            TransferHeader(
                connectionState = connectionState,
                onConfigToggle = { showConfig = !showConfig },
                isConfigOpen = showConfig
            )

            // Configuration Sheet/Card (persists current layout values)
            AnimatedVisibility(
                visible = showConfig,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ConfigPanel(
                    host = hostInput,
                    port = portInput,
                    autoDownload = autoDownload,
                    onHostChange = { hostInput = it },
                    onPortChange = { portInput = it },
                    onAutoDownloadChange = { viewModel.saveConfig(hostInput, portInput.toIntOrNull() ?: 5000, it) },
                    onSave = {
                        val p = portInput.toIntOrNull() ?: 5000
                        viewModel.saveConfig(hostInput, p, autoDownload)
                        viewModel.connect()
                        showConfig = false
                    },
                    onDisconnect = {
                        viewModel.disconnect()
                        showConfig = false
                    },
                    connectionState = connectionState
                )
            }

            // Connection Quick action and indicator if disconnected
            if (connectionState == ConnectionState.Disconnected && !showConfig) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clickable { showConfig = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEECEB),
                        contentColor = Color(0xFFBA1A1A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFDAD6))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, contentDescription = "WiFi disconnected", modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Настроить соединение", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Компьютер не подключен. Нажмите здесь.", fontSize = 12.sp, modifier = Modifier.alpha(0.8f))
                            }
                        }
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Chat / Events listing
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                border = BorderStroke(1.dp, Color(0xFFE1E2E9))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ИСТОРИЯ ПЕРЕДАЧ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1C1E),
                            letterSpacing = 1.5.sp
                        )
                        if (messages.isNotEmpty()) {
                            Text(
                                text = "Очистить все",
                                fontSize = 12.sp,
                                color = Color(0xFF74777F),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { viewModel.clearMessages() }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (messages.isEmpty()) {
                            EmptyHistoryPlaceholder(onConfigureClick = { showConfig = true })
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    MessageBubble(message = msg)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Actions (Chat input and fast upload triggers)
            BottomPanel(
                chatText = chatInputText,
                onChatTextChange = { chatInputText = it },
                onSendChat = {
                    if (chatInputText.isNotBlank()) {
                        viewModel.sendChatMessage(chatInputText)
                        chatInputText = ""
                    }
                },
                onSendFileClick = {
                    fileLauncher.launch("*/*")
                },
                onClearClick = {
                    viewModel.clearMessages()
                },
                isConnected = connectionState == ConnectionState.Connected
            )
        }

        // Dialog offering for downloads (if autoDownload is turned off)
        fileOffer?.let { offerFilename ->
            AlertDialog(
                onDismissRequest = { viewModel.rejectFileOffer() },
                confirmButton = {
                    Button(
                        onClick = { viewModel.downloadFile(offerFilename) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Скачать", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.rejectFileOffer() }) {
                        Text("Отклонить", color = Color.LightGray)
                    }
                },
                title = { Text("Доступен файл", fontWeight = FontWeight.Bold, color = Color.White) },
                text = { Text("ПК предлагает скачать файл:\n\"$offerFilename\"", color = Color.LightGray) },
                shape = RoundedCornerShape(20.dp),
                containerColor = Color(0xFF1E2129)
            )
        }

        // Full Screen Visual Overlay for Transfers
        AnimatedVisibility(
            visible = transferState != TransferUiState.Idle,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 1.05f)
        ) {
            TransferOverlay(
                state = transferState,
                onDismiss = { viewModel.resetTransferState() },
                onCancel = { viewModel.cancelTransfer() }
            )
        }
    }
}

@Composable
fun TransferHeader(
    connectionState: ConnectionState,
    onConfigToggle: () -> Unit,
    isConfigOpen: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutBack),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF7F9FF),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF005AC1).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = "Logo icon",
                        tint = Color(0xFF005AC1),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "AirTransfer",
                        color = Color(0xFF1A1C1E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.3).sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val indicatorColor = when (connectionState) {
                            ConnectionState.Connected -> Color(0xFF15803D)
                            ConnectionState.Connecting -> Color(0xFFF59E0B)
                            ConnectionState.Disconnected -> Color(0xFFEF4444)
                        }

                        val modifierVal = if (connectionState == ConnectionState.Connecting) {
                            Modifier.alpha(pulseAlpha)
                        } else Modifier

                        Box(
                            modifier = modifierVal
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        val statusText = when (connectionState) {
                            ConnectionState.Connected -> "Подключено к ПК"
                            ConnectionState.Connecting -> "Подключение..."
                            ConnectionState.Disconnected -> "Не подключено"
                        }

                        Text(
                            text = statusText,
                            color = Color(0xFF44474E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            IconButton(
                onClick = onConfigToggle,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isConfigOpen) Color(0xFFDCE2F9) else Color(0xFFDCE2F9).copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (isConfigOpen) Icons.Default.Close else Icons.Default.Settings,
                    contentDescription = "Toggle configuration panel",
                    tint = Color(0xFF1A1C1E),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ConfigPanel(
    host: String,
    port: String,
    autoDownload: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onAutoDownloadChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDisconnect: () -> Unit,
    connectionState: ConnectionState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E2E9))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Настройки подключения",
                color = Color(0xFF1A1C1E),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text("IP адрес", color = Color(0xFF74777F)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1A1C1E),
                        unfocusedTextColor = Color(0xFF1A1C1E),
                        focusedContainerColor = Color(0xFFF1F3F9),
                        unfocusedContainerColor = Color(0xFFF1F3F9),
                        focusedBorderColor = Color(0xFF005AC1),
                        unfocusedBorderColor = Color(0xFFE1E2E9)
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("host_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Порт", color = Color(0xFF74777F)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1A1C1E),
                        unfocusedTextColor = Color(0xFF1A1C1E),
                        focusedContainerColor = Color(0xFFF1F3F9),
                        unfocusedContainerColor = Color(0xFFF1F3F9),
                        focusedBorderColor = Color(0xFF005AC1),
                        unfocusedBorderColor = Color(0xFFE1E2E9)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("port_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Автоскачивание", color = Color(0xFF1A1C1E), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Скачивать прибывающие файлы автоматически", color = Color(0xFF74777F), fontSize = 11.sp)
                }
                Switch(
                    checked = autoDownload,
                    onCheckedChange = onAutoDownloadChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF005AC1),
                        checkedTrackColor = Color(0xFF005AC1).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color(0xFF74777F),
                        uncheckedTrackColor = Color(0xFFE1E2E9)
                    ),
                    modifier = Modifier.testTag("auto_download_switch")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (connectionState == ConnectionState.Connected) {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("disconnect_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFBA1A1A)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, contentDescription = "Exit Connection", modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отключить", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("connect_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF005AC1)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = connectionState != ConnectionState.Connecting
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (connectionState == ConnectionState.Connecting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Wifi, contentDescription = "WiFi connection action", modifier = Modifier.size(18.dp), tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (connectionState == ConnectionState.Connecting) "Подключение..." else "Подключить",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: TransferMessage) {
    val isSystem = message.sender == MessageSender.System
    val isFromMe = message.sender == MessageSender.Phone

    val bubbleBg = when (message.sender) {
        MessageSender.Phone -> Color(0xFF005AC1)
        MessageSender.PC -> Color(0xFFF1F3F9)
        MessageSender.System -> Color(0xFFF1F3F9).copy(alpha = 0.7f)
    }

    val textColor = when (message.sender) {
        MessageSender.Phone -> Color.White
        MessageSender.PC -> Color(0xFF1A1C1E)
        MessageSender.System -> Color(0xFF44474E)
    }

    val alignment = if (isSystem) {
        Alignment.CenterHorizontally
    } else if (isFromMe) {
        Alignment.End
    } else {
        Alignment.Start
    }

    val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = format.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        if (!isSystem) {
            Text(
                text = if (isFromMe) "Телефон" else "Компьютер (ПК)",
                color = Color(0xFF74777F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 2.dp, start = 6.dp, end = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isSystem || !isFromMe) 4.dp else 16.dp,
                        bottomEnd = if (isSystem || isFromMe) 4.dp else 16.dp
                    )
                )
                .background(bubbleBg)
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 14.sp,
                    fontStyle = if (isSystem) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = if (isSystem) FontFamily.Monospace else FontFamily.Default,
                    overflow = TextOverflow.Clip
                )

                if (!isSystem) {
                    Text(
                        text = timeString,
                        color = (if (isFromMe) Color.White.copy(alpha = 0.75f) else Color(0xFF74777F)),
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder(onConfigureClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Computer,
            contentDescription = "Desktop placeholder symbol",
            tint = Color(0xFF005AC1).copy(alpha = 0.2f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Нет сообщений или файлов",
            color = Color(0xFF1A1C1E),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Для отправки файлов или текстового чата подключитесь к IP компьютера в одной сети WiFi.",
            color = Color(0xFF74777F),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        TextButton(
            onClick = onConfigureClick,
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF005AC1))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = "Direct Settings Link", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Открыть настройки подключения", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun BottomPanel(
    chatText: String,
    onChatTextChange: (String) -> Unit,
    onSendChat: () -> Unit,
    onSendFileClick: () -> Unit,
    onClearClick: () -> Unit,
    isConnected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Document triggers row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSendFileClick,
                    enabled = isConnected,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp)
                        .testTag("send_file_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF005AC1),
                        disabledContainerColor = Color(0xFFF1F3F9)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = "Upload trigger icon",
                            modifier = Modifier.size(18.dp),
                            tint = if (isConnected) Color.White else Color(0xFF74777F)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Отправить файл",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isConnected) Color.White else Color(0xFF74777F)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onClearClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("clear_logs_button"),
                    border = BorderStroke(1.dp, Color(0xFFE1E2E9)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF44474E),
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs", modifier = Modifier.size(18.dp), tint = Color(0xFF44474E))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Chat input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = chatText,
                    onValueChange = onChatTextChange,
                    placeholder = { Text("Сообщение в чат...", color = Color(0xFF74777F), fontSize = 14.sp) },
                    singleLine = true,
                    enabled = isConnected,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1A1C1E),
                        unfocusedTextColor = Color(0xFF1A1C1E),
                        focusedContainerColor = Color(0xFFF1F3F9),
                        unfocusedContainerColor = Color(0xFFF1F3F9),
                        disabledContainerColor = Color(0xFFF1F3F9).copy(alpha = 0.5f),
                        focusedBorderColor = Color(0xFF005AC1),
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                val showSend = isConnected && chatText.isNotBlank()
                val sendClickableModifier = if (showSend) {
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF005AC1))
                        .clickable(onClick = onSendChat)
                } else {
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F3F9))
                }

                Box(
                    modifier = sendClickableModifier.testTag("send_chat_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send message tool button",
                        tint = if (showSend) Color.White else Color(0xFF74777F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Custom animated view for real-time transfers (Pulsing circles and glowing vectors)
@Composable
fun TransferOverlay(
    state: TransferUiState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    val isSuccess = state is TransferUiState.Success
    val isError = state is TransferUiState.Error

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")
    val pulseProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    // Sparkle or bubble floats during active transfers
    val dotAnimationSpec = infiniteRepeatable<Float>(
        animation = tween(3000, easing = EaseInOutBack),
        repeatMode = RepeatMode.Reverse
    )
    
    val dotOffsetFactor by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = dotAnimationSpec,
        label = "dot_movement"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20D0E12))
            .clickable(enabled = isSuccess || isError) { onDismiss() }, // Tapping succeeds dismissal
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Drawing dynamic active data streams
                if (!isSuccess && !isError) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerOffset = Offset(size.width / 2, size.height / 2)
                        val maxRadius = size.width / 2

                        // Drawing ring 1
                        val ring1Radius = maxRadius * pulseProgress1
                        val ring1Alpha = 1f - pulseProgress1
                        drawCircle(
                            color = Color(0xFF005AC1).copy(alpha = ring1Alpha * 0.4f),
                            radius = ring1Radius,
                            center = centerOffset,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Drawing ring 2
                        val ring2Radius = maxRadius * pulseProgress2
                        val ring2Alpha = 1f - pulseProgress2
                        drawCircle(
                            color = Color(0xFF005AC1).copy(alpha = ring2Alpha * 0.4f),
                            radius = ring2Radius,
                            center = centerOffset,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Floating particles wrapping around center
                        val particleCount = 4
                        for (i in 0 until particleCount) {
                            val angle = (i * (360 / particleCount) + (dotOffsetFactor * 40f)) * (Math.PI / 180.0)
                            val distance = maxRadius * (0.4f + 0.3f * cos(dotOffsetFactor + i))
                            val particleX = centerOffset.x + (distance * cos(angle)).toFloat()
                            val particleY = centerOffset.y + (distance * sin(angle)).toFloat()
                            drawCircle(
                                color = Color(0xFF005AC1),
                                radius = 4.dp.toPx(),
                                center = Offset(particleX, particleY)
                            )
                        }
                    }
                }

                // Center Icon badge or indicator
                val badgeColor = when (state) {
                    is TransferUiState.Sending -> Color(0xFF005AC1)
                    is TransferUiState.Receiving -> Color(0xFF005AC1)
                    is TransferUiState.Success -> Color(0xFF15803D)
                    is TransferUiState.Error -> Color(0xFFBA1A1A)
                    else -> Color.DarkGray
                }

                val sizeScale by animateFloatAsState(
                    targetValue = if (isSuccess) 1.2f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
                )

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(badgeColor.copy(alpha = 0.15f), shape = CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(badgeColor.copy(alpha = 0.3f), Color.Transparent)
                            ), shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp * sizeScale)
                            .background(badgeColor, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (state) {
                            is TransferUiState.Sending -> Icons.Default.CloudUpload
                            is TransferUiState.Receiving -> Icons.Default.CloudDownload
                            is TransferUiState.Success -> Icons.Default.CheckCircle
                            is TransferUiState.Error -> Icons.Default.Error
                            else -> Icons.Default.Info
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = "Status badge visual represent",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // State title
            val stateTitle = when (state) {
                is TransferUiState.Sending -> "Отправка файла..."
                is TransferUiState.Receiving -> "Получение файла..."
                is TransferUiState.Success -> if (state.isUpload) "Файл успешно отправлен!" else "Файл успешно получен!"
                is TransferUiState.Error -> "Ошибка передачи"
                else -> ""
            }

            Text(
                text = stateTitle,
                color = when (state) {
                    is TransferUiState.Success -> Color(0xFF34D399)
                    is TransferUiState.Error -> Color(0xFFF87171)
                    else -> Color.White
                },
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Direction / Filename details
            val detailsText = when (state) {
                is TransferUiState.Sending -> state.filename
                is TransferUiState.Receiving -> state.filename
                is TransferUiState.Success -> state.filename
                is TransferUiState.Error -> state.message
                else -> ""
            }

            Text(
                text = detailsText,
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Direction badges to show flow
            if (!isSuccess && !isError) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (state is TransferUiState.Sending) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                        contentDescription = "Source",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Arrow direction pointer",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(14.dp)
                    )
                    Icon(
                        imageVector = if (state is TransferUiState.Sending) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                        contentDescription = "Destination",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isSuccess || isError) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSuccess) Color(0xFF15803D) else Color(0xFFBA1A1A)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .widthIn(min = 160.dp)
                        .height(50.dp)
                ) {
                    Text("Готово", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .widthIn(min = 160.dp)
                        .height(50.dp)
                ) {
                    Text("Отмена", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}
