package com.example.messengerapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.messengerapp.data.crypto.CryptoManager
import com.example.messengerapp.data.models.UserResponse
import com.example.messengerapp.data.models.UserRegister
import com.example.messengerapp.data.network.RetrofitClient
import com.example.messengerapp.ui.theme.MessengerAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import com.example.messengerapp.data.models.MessageSend

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUsername: String,
    chatPartner: String,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val cryptoManager = remember { CryptoManager(context) }
    val api = RetrofitClient.instance

    // Загрузка сообщений
    fun loadMessages() {
        coroutineScope.launch {
            try {
                val serverMessages = withContext(Dispatchers.IO) {
                    api.getMessages(currentUsername)
                }

                val decryptedMessages = mutableListOf<ChatMessage>()
                for (msg in serverMessages) {
                    try {
                        // Сообщения от system — это сообщения от собеседника
                        val isSent = msg.sender == currentUsername
                        val decryptedText = if (isSent) {
                            // Это наши сообщения — они не зашифрованы в списке?
                            // В нашей архитектуре сообщения хранятся зашифрованными всегда
                            cryptoManager.decryptWithMyKey(msg.encrypted_text)
                        } else {
                            cryptoManager.decryptWithMyKey(msg.encrypted_text)
                        }
                        decryptedMessages.add(ChatMessage(decryptedText, isSent, System.currentTimeMillis()))
                    } catch (e: Exception) {
                        // Ошибка расшифровки — пропускаем
                    }
                }
                messages = decryptedMessages
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Отправка сообщения
    fun sendMessage() {
        if (inputText.isBlank()) return

        coroutineScope.launch {
            isLoading = true
            try {
                // Получаем публичный ключ получателя
                val publicKeyResponse = withContext(Dispatchers.IO) {
                    api.getPublicKey(chatPartner)
                }

                // Шифруем сообщение
                val encrypted = withContext(Dispatchers.Default) {
                    cryptoManager.encryptForRecipient(inputText, publicKeyResponse.public_key)
                }

                // Отправляем на сервер
                val response = withContext(Dispatchers.IO) {
                    api.sendMessage(com.example.messengerapp.data.models.MessageSend(chatPartner, encrypted))
                }

                // Добавляем сообщение в список
                messages = messages + ChatMessage(inputText, true, System.currentTimeMillis())
                inputText = ""

            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    // Загружаем сообщения при открытии экрана
    LaunchedEffect(Unit) {
        loadMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatPartner) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            // Список сообщений
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false,
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        text = message.text,
                        isSent = message.isSent
                    )
                }
            }

            // Поле ввода
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
}

data class ChatMessage(
    val text: String,
    val isSent: Boolean,
    val timestamp: Long
)

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
fun MessengerApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = "register"  // ← этот маршрут должен существовать
        ) {
            // Регистрация
            composable("register") {
                RegisterScreen(
                    onRegisterSuccess = { username ->
                        navController.navigate("main/$username") {
                            popUpTo("register") { inclusive = true }
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
                        navController.popBackStack("register", inclusive = false)
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
fun RegisterScreen(
    onRegisterSuccess: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val cryptoManager = remember { CryptoManager(context) }
    val api = RetrofitClient.instance

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
            label = { Text("Введите имя пользователя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (username.isNotBlank()) {
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            // Генерация ключей
                            val publicKey = withContext(Dispatchers.Default) {
                                cryptoManager.generateKeyPair()
                            }

                            // Регистрация на сервере
                            val response = withContext(Dispatchers.IO) {
                                api.register(UserRegister(username, publicKey))
                            }

                            Toast.makeText(context, "Регистрация успешна!", Toast.LENGTH_SHORT).show()
                            onRegisterSuccess(username)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            isLoading = false
                        }
                    }
                }
            },
            enabled = username.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Зарегистрироваться")
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
                0 -> ChatsScreen(username)
                1 -> ContactsScreen(
                    username = username,
                    onContactClick = { contactName ->
                        navController.navigate("chat/$username/$contactName")
                    }
                )
                2 -> SettingsScreen(username, onLogout)
            }
        }
    }
}

@Composable
fun ChatsScreen(username: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Чаты", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Пользователь: $username", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ContactsScreen(
    username: String,
    onContactClick: (String) -> Unit
) {
    var users by remember { mutableStateOf<List<UserResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = RetrofitClient.instance

    // Загрузка пользователей при запуске экрана
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val userList = withContext(Dispatchers.IO) {
                    api.getUsers()
                }
                // Фильтруем текущего пользователя
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
                        onClick = { onContactClick(user.username) }
                    )
                    HorizontalDivider()
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
        // Аватар (инициал)
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
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Пользователь: $username", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onLogout) {
            Text("Выйти")
        }
    }
}