package com.example.sitruyenaudio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Headset
import com.example.sitruyenaudio.service.TtsService
import com.example.sitruyenaudio.theme.SiTruyenAudioTheme
import com.example.sitruyenaudio.ui.HistoryScreen
import com.example.sitruyenaudio.ui.ReaderViewModel
import com.example.sitruyenaudio.ui.WebViewScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ReaderViewModel by viewModels()
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TtsService.LocalBinder
            viewModel.ttsService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            viewModel.ttsService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Intent(this, TtsService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        enableEdgeToEdge()
        setContent {
            SiTruyenAudioTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
        
        // Handle incoming intent if opened via Share
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null && sharedText.startsWith("http")) {
                viewModel.fetchChapter(sharedText, autoPlay = true)
            }
        } else if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null) {
                viewModel.fetchChapter(data.toString(), autoPlay = true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun MainAppScreen(viewModel: ReaderViewModel) {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Public, contentDescription = "Trình duyệt") },
                    label = { Text("Lướt Web") },
                    selected = currentRoute == "web",
                    onClick = {
                        navController.navigate("web") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Headset, contentDescription = "Đang đọc") },
                    label = { Text("Đang đọc") },
                    selected = currentRoute == "reader",
                    onClick = {
                        navController.navigate("reader") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.List, contentDescription = "Lịch sử") },
                    label = { Text("Lịch sử") },
                    selected = currentRoute == "history",
                    onClick = {
                        navController.navigate("history") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "web", Modifier.padding(innerPadding)) {
            composable("web") {
                WebViewScreen(
                    initialUrl = "https://metruyenchuvn.com",
                    onReadClicked = { url ->
                        viewModel.fetchChapter(url, autoPlay = true)
                        navController.navigate("reader") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("reader") {
                ReaderScreen(viewModel = viewModel)
            }
            composable("history") {
                // Làm mới danh sách khi mở tab lịch sử
                var history by remember { mutableStateOf(viewModel.getHistoryList()) }
                LaunchedEffect(Unit) {
                    history = viewModel.getHistoryList()
                }
                HistoryScreen(
                    historyList = history,
                    onHistoryItemClicked = { url ->
                        viewModel.fetchChapter(url, autoPlay = true)
                        navController.navigate("reader") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(modifier: Modifier = Modifier, viewModel: ReaderViewModel) {
    val currentChapter by viewModel.currentChapter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentIndex by viewModel.currentParagraphIndex.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var showSettings by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }

    val currentUrl by viewModel.currentUrl.collectAsState()
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }

    // Cuộn tới dòng đang đọc
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentChapter != null) {
            coroutineScope.launch {
                listState.animateScrollToItem(currentIndex)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // App Bar
        TopAppBar(
            title = { Text("app_read_truyen", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Cài đặt")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        // URL Input
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("URL Truyện") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { 
                viewModel.fetchChapter(urlInput)
            }) {
                Text("Đọc")
            }
        }

        // Loading
        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentChapter != null) {
            // Content
            Text(
                text = currentChapter!!.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(currentChapter!!.paragraphs) { index, paragraph ->
                    val isHighlighted = index == currentIndex
                    Text(
                        text = paragraph,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.playFromParagraph(index) }
                            .padding(8.dp)
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f))
        }

        // Bottom Controls
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val prevUrl = currentChapter?.prevChapterUrl
                        if (!prevUrl.isNullOrEmpty()) {
                            viewModel.fetchChapter(prevUrl, autoPlay = true)
                        }
                    },
                    enabled = currentChapter?.prevChapterUrl != null
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Chương trước", modifier = Modifier.size(36.dp))
                }

                FloatingActionButton(
                    onClick = { viewModel.playPause() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause"
                    )
                }

                IconButton(
                    onClick = {
                        val nextUrl = currentChapter?.nextChapterUrl
                        if (!nextUrl.isNullOrEmpty()) {
                            viewModel.fetchChapter(nextUrl, autoPlay = true)
                        }
                    },
                    enabled = currentChapter?.nextChapterUrl != null
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Chương tiếp", modifier = Modifier.size(36.dp))
                }
            }
        }
    }

    // Settings Dialog
    if (showSettings) {
        LaunchedEffect(Unit) {
            viewModel.loadVoices()
        }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Cài đặt giọng đọc") },
            text = {
                Column {
                    Text("Tốc độ đọc: ${String.format("%.1f", currentSpeed)}x")
                    Slider(
                        value = currentSpeed,
                        onValueChange = { 
                            currentSpeed = it
                            viewModel.setSpeed(it)
                        },
                        valueRange = 0.5f..2.5f,
                        steps = 19
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Chọn giọng:")
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(availableVoices.size) { index ->
                            val voice = availableVoices[index]
                            Text(
                                text = voice.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.setVoice(voice)
                                        showSettings = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}
