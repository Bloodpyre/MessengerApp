package com.example.messengerapp

import android.os.Bundle
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.messengerapp.data.database.ReadMessageEntity
import com.example.messengerapp.data.database.AppDatabase
import com.example.messengerapp.data.database.ChatEntity
import com.example.messengerapp.data.models.UserLogin
import com.example.messengerapp.data.crypto.CryptoManager
import com.example.messengerapp.data.models.MessageSend
import com.example.messengerapp.data.models.UserRegister
import com.example.messengerapp.data.models.UserResponse
import com.example.messengerapp.data.network.RetrofitClient
import com.example.messengerapp.ui.theme.MessengerAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MessengerAppTheme {
                MessengerApp()
            }
        }
    }
}

@Composable
fun MessengerApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("messenger_prefs", Context.MODE_PRIVATE)
    val savedUsername = prefs.getString("current_user", null)

    // Если пользователь уже входил, идем сразу в main, иначе на экран авторизации
    val startDestination = if (!savedUsername.isNullOrEmpty()) "main/$savedUsername" else "auth"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // Экран авторизации
            composable("auth") {
                AuthScreen(
                    onAuthSuccess = { username ->
                        navController.navigate("main/$username") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }

            // Главный экран
            composable(
                route = "main/{username}",
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { backStackEntry ->
                val username = backStackEntry.arguments?.getString("username") ?: ""
                MainScreen(
                    username = username,
                    navController = navController,
                    onLogout = {
                        println("🔓 Выход из аккаунта: $username")
                        // Очищаем сохраненного пользователя
                        prefs.edit().remove("current_user").apply()
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            // Экран чата
            composable(
                route = "chat/{currentUsername}/{chatPartner}",
                arguments = listOf(
                    navArgument("currentUsername") { type = NavType.StringType },
                    navArgument("chatPartner") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val currentUsername = backStackEntry.arguments?.getString("currentUsername") ?: ""
                val chatPartner = backStackEntry.arguments?.getString("chatPartner") ?: ""
                ChatScreen(
                    currentUsername = currentUsername,
                    chatPartner = chatPartner,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLoginMode by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = RetrofitClient.instance
    val prefs = context.getSharedPreferences("messenger_prefs", Context.MODE_PRIVATE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Messenger",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Имя пользователя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (username.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            if (isLoginMode) {
                                // ВХОД
                                val response = withContext(Dispatchers.IO) {
                                    api.login(UserLogin(username, password))
                                }
                                prefs.edit().putString("current_user", username).apply()
                                Toast.makeText(context, "Вход выполнен!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess(username)
                            } else {
                                // РЕГИСТРАЦИЯ
                                val response = withContext(Dispatchers.IO) {
                                    api.register(UserRegister(username, password))
                                }
                                prefs.edit().putString("current_user", username).apply()
                                Toast.makeText(context, "Регистрация успешна!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess(username)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            isLoading = false
                        }
                    }
                } else {
                    Toast.makeText(context, "Заполните все поля", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isLoginMode) "Войти" else "Зарегистрироваться")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { isLoginMode = !isLoginMode }
        ) {
            Text(
                if (isLoginMode)
                    "Нет аккаунта? Зарегистрироваться"
                else
                    "Уже есть аккаунт? Войти"
            )
        }
    }
}

@Composable
fun MainScreen(
    username: String,
    navController: NavController,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Чаты", "Контакты", "Настройки")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> ImageVector.vectorResource(R.drawable.ic_chat)
                                    1 -> ImageVector.vectorResource(R.drawable.ic_contacts)
                                    else -> ImageVector.vectorResource(R.drawable.ic_settings)
                                },
                                contentDescription = title
                            )
                        },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> ChatsScreen(
                    username = username,
                    onChatClick = { partnerName ->
                        navController.navigate("chat/$username/$partnerName")
                    }
                )
                1 -> ContactsScreen(
                    username = username,
                    navController = navController
                )
                2 -> SettingsScreen(username, onLogout)
            }
        }
    }
}

@Composable
fun ChatsScreen(
    username: String,
    onChatClick: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val chatsFlow = remember { db.chatDao().getAllFlow() }
    val chats by chatsFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Чаты",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        if (chats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет чатов. Начните диалог из списка контактов",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn {
                items(chats) { chat ->
                    ChatItem(
                        partnerName = chat.partnerName,
                        lastMessage = chat.lastMessage,
                        unreadCount = chat.unreadCount,
                        onClick = {
                            coroutineScope.launch {
                                db.chatDao().upsert(chat.copy(unreadCount = 0))
                            }
                            onChatClick(chat.partnerName)
                        }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(
    username: String,
    navController: NavController
) {
    var users by remember { mutableStateOf<List<UserResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = RetrofitClient.instance

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val userList = withContext(Dispatchers.IO) {
                    api.getUsers()
                }
                users = userList.filter { it.username != username }
                isLoading = false
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Контакты",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (users.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет других пользователей")
            }
        } else {
            LazyColumn {
                items(users) { user ->
                    ContactItem(
                        username = user.username,
                        onClick = {
                            navController.navigate("chat/$username/${user.username}")
                        }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun ContactItem(username: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = username.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = username,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_chat),
            contentDescription = "Чат",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SettingsScreen(username: String, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Пользователь:", style = MaterialTheme.typography.bodyMedium)
                Text(
                    username,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                println("🔘 Кнопка выхода нажата")
                onLogout()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Выйти из аккаунта")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUsername: String,
    chatPartner: String,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var processedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }  // ← храним ID обработанных сообщений
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val cryptoManager = remember { CryptoManager(context) }
    val api = RetrofitClient.instance
    val db = remember { AppDatabase.getInstance(context) }
    val listState = rememberLazyListState()

    // Загрузка сообщений с сервера
    fun loadMessages() {
        coroutineScope.launch {
            try {
                val serverMessages = withContext(Dispatchers.IO) {
                    api.getMessages(currentUsername)
                }

                println("📥 Получено с сервера: ${serverMessages.size} сообщений")

                val loadedMessages = mutableListOf<ChatMessage>()
                var lastMessageForChat: String? = null
                var lastMessageTime: Long = 0
                var newUnreadCount = 0

                for (msg in serverMessages) {
                    val isInThisChat = (msg.sender == chatPartner && msg.recipient == currentUsername) ||
                            (msg.sender == currentUsername && msg.recipient == chatPartner)

                    if (isInThisChat) {
                        val isSent = msg.sender == currentUsername
                        val timestamp = msg.timestamp.toLongOrNull() ?: System.currentTimeMillis()

                        val decryptedText = try {
                            cryptoManager.decrypt(msg.encrypted_text)
                        } catch (e: Exception) {
                            println("❌ Ошибка расшифровки: ${e.message}")
                            if (msg.encrypted_text.length > 30) {
                                "[Зашифрованное сообщение]"
                            } else {
                                msg.encrypted_text
                            }
                        }

                        loadedMessages.add(ChatMessage(
                            messageId = msg.message_id,
                            text = decryptedText,
                            isSent = isSent,
                            timestamp = timestamp
                        ))

                        if (timestamp > lastMessageTime) {
                            lastMessageTime = timestamp
                            lastMessageForChat = decryptedText
                        }

                        // Проверяем, прочитано ли сообщение
                        val isRead = withContext(Dispatchers.IO) {
                            db.readMessageDao().isMessageRead(msg.message_id)
                        }

                        if (!isSent && !isRead) {
                            newUnreadCount++
                            println("   📬 Новое непрочитанное: $decryptedText (ID: ${msg.message_id})")
                        }
                    }
                }

                messages = loadedMessages.sortedBy { it.timestamp }

                // Сохраняем чат в БД
                if (lastMessageForChat != null) {
                    db.chatDao().upsert(
                        ChatEntity(
                            partnerName = chatPartner,
                            lastMessage = lastMessageForChat,
                            lastMessageTime = lastMessageTime,
                            unreadCount = newUnreadCount
                        )
                    )
                    println("💾 Чат сохранен: $chatPartner, последнее: $lastMessageForChat, непрочитанных: $newUnreadCount")
                }

                println("📱 Итого сообщений в чате: ${messages.size}")

            } catch (e: Exception) {
                println("❌ Ошибка загрузки: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Отправка сообщения
    fun sendMessage() {
        if (inputText.isBlank()) return

        val textToSend = inputText
        val timestamp = System.currentTimeMillis()
        val tempId = "temp_$timestamp"

        val encryptedText = try {
            cryptoManager.encrypt(textToSend)
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка шифрования: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        // Добавляем сообщение с временным ID
        messages = messages + ChatMessage(
            messageId = tempId,
            text = textToSend,
            isSent = true,
            timestamp = timestamp
        )
        processedMessageIds = processedMessageIds + tempId
        inputText = ""

        coroutineScope.launch {
            isLoading = true
            try {
                val response = withContext(Dispatchers.IO) {
                    api.sendMessage(MessageSend(chatPartner, encryptedText, currentUsername))
                }

                // Обновляем список, чтобы получить реальный ID
                loadMessages()
                println("✅ Сообщение отправлено: $textToSend")

            } catch (e: Exception) {
                messages = messages.filter { it.messageId != tempId }
                processedMessageIds = processedMessageIds - tempId
                Toast.makeText(context, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
                println("❌ Ошибка отправки: ${e.message}")
            }
            isLoading = false
        }
    }

    // Автообновление
    LaunchedEffect(Unit) {
        loadMessages()
        // Отмечаем все сообщения от chatPartner как прочитанные
        coroutineScope.launch {
            for (msg in messages) {
                if (!msg.isSent) {
                    db.readMessageDao().insert(ReadMessageEntity(msg.messageId, chatPartner))
                }
            }
            // Обновляем счетчик в БД чатов
            val existingChat = db.chatDao().getChat(chatPartner)
            if (existingChat != null && existingChat.unreadCount > 0) {
                db.chatDao().upsert(existingChat.copy(unreadCount = 0))
            }
        }
        while (true) {
            delay(3000)
            loadMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatPartner) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { loadMessages() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages.reversed()) { message ->
                    MessageBubble(
                        text = message.text,
                        isSent = message.isSent
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Введите сообщение...") },
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { sendMessage() },
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_send),
                        contentDescription = "Отправить"
                    )
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
}

@Composable
fun MessageBubble(text: String, isSent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSent) 16.dp else 4.dp,
                bottomEnd = if (isSent) 4.dp else 16.dp
            ),
            color = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChatItem(
    partnerName: String,
    lastMessage: String,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = partnerName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Информация о чате
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = partnerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (unreadCount > 0)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Счетчик непрочитанных
        if (unreadCount > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else "$unreadCount",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

data class ChatMessage(
    val messageId: String,
    val text: String,
    val isSent: Boolean,
    val timestamp: Long
)