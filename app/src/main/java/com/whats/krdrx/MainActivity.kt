package com.whats.krdrx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.media.MediaMetadataRetriever
import android.util.Size
import android.widget.VideoView
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WhatsGreen = Color(0xFF25D366)
private val DarkGreen = Color(0xFF075E54)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("whats_krdrx_prefs", Context.MODE_PRIVATE)
        setContent {
            var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
            WhatsKrdrxTheme(darkMode) {
                WhatsKrdrxApp(darkMode) {
                    darkMode = !darkMode
                    prefs.edit().putBoolean("dark_mode", darkMode).apply()
                }
            }
        }
    }
}

@Composable
private fun WhatsKrdrxTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    val scheme = if (darkMode) darkColorScheme(
        primary = WhatsGreen,
        secondary = Color(0xFF128C7E),
        background = Color(0xFF0B141A),
        surface = Color(0xFF111B21),
        onPrimary = Color(0xFF062D20),
        onBackground = Color(0xFFE9EDEF),
        onSurface = Color(0xFFE9EDEF)
    ) else lightColorScheme(
        primary = DarkGreen,
        secondary = Color(0xFF128C7E),
        background = Color(0xFFF7F8FA),
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color(0xFF111B21),
        onSurface = Color(0xFF111B21)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsKrdrxApp(darkMode: Boolean, onToggleTheme: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple(Icons.Default.Dashboard, "Dasbor", "Dasbor"),
        Triple(Icons.Default.Image, "Status", "Status"),
        Triple(Icons.Default.Notifications, "Pesan", "Pesan"),
        Triple(Icons.Default.Build, "Tools", "Tools"),
        Triple(Icons.Default.Settings, "Setelan", "Settings")
    )
    BackHandler(enabled = tab != 0) { tab = 0 }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Whats-Krdrx", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("WhatsApp Companion", fontSize = 11.sp, color = Color.White.copy(alpha = 0.82f))
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Ganti tema", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGreen,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.first, item.second) },
                        label = { Text(item.second, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> DashboardScreen(onOpenTab = { tab = it }, modifier = Modifier.padding(padding))
            1 -> StatusDownloaderScreen(Modifier.padding(padding))
            2 -> NotificationArchiveScreen(Modifier.padding(padding))
            3 -> SimpleScreen("WhatsApp Tools", "Modul alat bantu tambahan akan dikembangkan setelah dua fitur utama stabil.", Modifier.padding(padding))
            else -> SettingsScreen(darkMode, onToggleTheme, Modifier.padding(padding))
        }
    }
}

private data class DashboardState(
    val loaded: Boolean = false,
    val accessEnabled: Boolean = false,
    val treeSelected: Boolean = false,
    val folderError: Boolean = false,
    val statusTotal: Int = 0,
    val statusUnsaved: Int = 0,
    val archiveToday: Int = 0,
    val archiveTotal: Int = 0
)

private fun loadDashboard(context: Context): DashboardState {
    val access = isNotificationAccessEnabled(context)
    val tree = StatusStorage.savedTree(context)
    var total = 0
    var unsaved = 0
    var folderError = false
    if (tree != null) {
        runCatching { StatusStorage.list(context, tree) }
            .onSuccess { list ->
                total = list.size
                unsaved = list.count { !StatusStorage.isSaved(context, it) }
            }
            .onFailure { folderError = true }
    }
    val rows = runCatching { NotificationArchiveDb(context).use { it.all() } }
        .getOrDefault(emptyList())
    val startOfDay = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    return DashboardState(
        loaded = true,
        accessEnabled = access,
        treeSelected = tree != null,
        folderError = folderError,
        statusTotal = total,
        statusUnsaved = unsaved,
        archiveToday = rows.count { it.receivedAt >= startOfDay },
        archiveTotal = rows.size
    )
}

@Composable
private fun DashboardScreen(onOpenTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var state by remember { mutableStateOf(DashboardState()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshKey++
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(refreshKey) {
        state = withContext(Dispatchers.IO) { loadDashboard(context) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Dasbor", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!state.loaded) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        item {
            DashboardCard(
                icon = Icons.Default.Notifications,
                title = "Akses notifikasi",
                detail = if (state.accessEnabled) "Aktif. Pesan baru dicatat ke arsip."
                else "Mati. Arsip tidak mencatat pesan baru.",
                ok = state.accessEnabled,
                actionLabel = if (state.accessEnabled) "Periksa" else "Aktifkan",
                onAction = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )
        }
        item {
            DashboardCard(
                icon = Icons.Default.Image,
                title = "Folder status",
                detail = when {
                    !state.treeSelected -> "Folder .Statuses belum dipilih."
                    state.folderError -> "Folder tidak dapat dibaca. Pilih ulang."
                    state.statusTotal == 0 -> "Tidak ada status ditemukan."
                    else -> "${state.statusTotal} status, ${state.statusUnsaved} belum disimpan."
                },
                ok = state.treeSelected && !state.folderError,
                actionLabel = if (state.treeSelected && !state.folderError) "Buka" else "Pilih",
                onAction = { onOpenTab(1) }
            )
        }
        item {
            DashboardCard(
                icon = Icons.Default.Archive,
                title = "Arsip notifikasi",
                detail = "${state.archiveToday} hari ini  •  ${state.archiveTotal} tercatat",
                ok = true,
                actionLabel = "Buka",
                onAction = { onOpenTab(2) }
            )
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun DashboardCard(
    icon: ImageVector,
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null, modifier = Modifier.size(28.dp),
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    detail, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun StatusDownloaderScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tree by remember { mutableStateOf(StatusStorage.savedTree(context)) }
    val files = remember { mutableStateListOf<StatusFile>() }
    val selected = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var previewUri by rememberSaveable { mutableStateOf<String?>(null) }
    var previewName by rememberSaveable { mutableStateOf("") }
    var previewMime by rememberSaveable { mutableStateOf("") }

    fun loadFiles() {
        val selectedTree = tree ?: return
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) { runCatching { StatusStorage.list(context, selectedTree) } }
            files.clear()
            result.getOrNull()?.let { files.addAll(it) }
            selected.retainAll(files.map { it.uri.toString() }.toSet())
            if (result.isFailure) {
                tree = null
                Toast.makeText(context, "Folder tidak dapat dibaca. Pilih ulang folder .Statuses.", Toast.LENGTH_LONG).show()
            }
            loading = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { StatusStorage.rememberTree(context, uri) }
                .onSuccess { tree = uri; selected.clear(); loadFiles() }
                .onFailure { Toast.makeText(context, "Izin folder gagal disimpan.", Toast.LENGTH_LONG).show() }
        }
    }

    LaunchedEffect(tree) { if (tree != null) loadFiles() }

    fun shareSelected() {
        val chosen = files.filter { it.uri.toString() in selected }
        if (chosen.isEmpty()) return
        val uris = ArrayList(chosen.map { it.uri })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (chosen.all { it.isVideo }) "video/*" else if (chosen.all { !it.isVideo }) "image/*" else "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, chosen.first().name, chosen.first().uri).apply {
                chosen.drop(1).forEach { addItem(android.content.ClipData.Item(it.uri)) }
            }
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan status")) }
            .onFailure { Toast.makeText(context, "Tidak ada aplikasi yang dapat menerima media ini.", Toast.LENGTH_LONG).show() }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkGreen).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Status Downloader", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = { loadFiles() }, enabled = tree != null && !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tree == null) {
            Button(onClick = { picker.launch(null) }) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(5.dp))
                Text("Pilih folder .Statuses")
            }
        }
        Spacer(Modifier.height(10.dp))
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (tree == null) {
            InfoCard("Folder belum dipilih", "Buka penyimpanan internal > Android > media > com.whatsapp > WhatsApp > Media > .Statuses, lalu pilih folder .Statuses.")
        } else if (files.isEmpty()) {
            InfoCard("Tidak ada media ditemukan", "Lihat Status di WhatsApp terlebih dahulu, lalu tekan Muat ulang. Pastikan folder yang dipilih adalah .Statuses.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected.isEmpty()) "${files.size} file ditemukan" else "${selected.size} dipilih", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    if (selected.size == files.size) selected.clear()
                    else { selected.clear(); selected.addAll(files.map { it.uri.toString() }) }
                }) { Text(if (selected.size == files.size) "Batal pilih" else "Pilih semua") }
            }
            if (selected.isNotEmpty()) {
                val selectedFiles = files.filter { it.uri.toString() in selected }
                val unsavedSelected = selectedFiles.filterNot { StatusStorage.isSaved(context, it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (unsavedSelected.isNotEmpty()) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !saving,
                            onClick = {
                                scope.launch {
                                    saving = true
                                    var count = 0
                                    var skipped = 0
                                    unsavedSelected.forEach { file ->
                                        val result = withContext(Dispatchers.IO) { runCatching { StatusStorage.save(context, file) } }
                                        if (result.isSuccess) count++ else skipped++
                                    }
                                    saving = false
                                    selected.clear()
                                    loadFiles()
                                    Toast.makeText(context, "Berhasil: $count • Gagal: $skipped", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) { Text(if (saving) "Menyimpan..." else "Simpan (${unsavedSelected.size})") }
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { shareSelected() }
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Bagikan")
                    }
                }
            } else {
                Text("Ketuk media untuk melihat. Tekan lama untuk memilih beberapa file.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.uri.toString() }) { file ->
                    val key = file.uri.toString()
                    val isSelected = key in selected
                    val isSaved = StatusStorage.isSaved(context, file)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        if (isSelected) selected.remove(key) else selected.add(key)
                                    } else {
                                        previewUri = key; previewName = file.name; previewMime = file.mimeType
                                    }
                                },
                                onLongClick = {
                                    if (isSelected) selected.remove(key) else selected.add(key)
                                }
                            ).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusThumbnail(file, Modifier.size(92.dp), onClick = {
                                if (selected.isNotEmpty()) {
                                    if (isSelected) selected.remove(key) else selected.add(key)
                                } else {
                                    previewUri = key; previewName = file.name; previewMime = file.mimeType
                                }
                            })
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(file.name, fontWeight = FontWeight.Medium, maxLines = 2)
                                Text(if (file.isVideo) "Video" else "Foto", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                if (isSaved) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Sudah tersimpan", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (selected.isNotEmpty()) {
                                Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                    if (checked) selected.add(key) else selected.remove(key)
                                })
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { StatusStorage.save(context, file) } }
                                        Toast.makeText(
                                            context,
                                            if (result.isSuccess) "Disimpan ke Pictures/Whats-Krdrx" else result.exceptionOrNull()?.message ?: "Gagal menyimpan file.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        loadFiles()
                                    }
                                }, enabled = !saving && !isSaved) {
                                    Icon(if (isSaved) Icons.Default.CheckCircle else Icons.Default.Download, if (isSaved) "Sudah disimpan" else "Simpan ke galeri")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (previewUri != null) {
        StatusPreviewDialog(
            file = StatusFile(Uri.parse(previewUri), previewName, previewMime),
            onDismiss = { previewUri = null; previewName = ""; previewMime = "" }
        )
    }
}

@Composable
private fun StatusThumbnail(
    file: StatusFile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (file.isVideo) {
                    MediaMetadataRetriever().let { retriever ->
                        try {
                            retriever.setDataSource(context, file.uri)
                            retriever.getScaledFrameAtTime(
                                1_000_000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                320,
                                320
                            )
                        } finally {
                            retriever.release()
                        }
                    } ?: context.contentResolver.loadThumbnail(file.uri, Size(320, 320), null)
                } else {
                    context.contentResolver.loadThumbnail(file.uri, Size(320, 320), null)
                }
            }.getOrNull()
        }
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = if (file.isVideo) "Putar pratinjau video" else "Lihat foto",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    if (file.isVideo) Icons.Default.Videocam else Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
            if (file.isVideo) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Putar video",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusPreviewDialog(file: StatusFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var zoom by remember(file.uri) { mutableFloatStateOf(1f) }
    var pan by remember(file.uri) { mutableStateOf(Offset.Zero) }
    var videoView by remember(file.uri) { mutableStateOf<VideoView?>(null) }
    var videoDuration by remember(file.uri) { mutableIntStateOf(0) }
    var videoPosition by remember(file.uri) { mutableIntStateOf(0) }
    var videoPlaying by remember(file.uri) { mutableStateOf(false) }
    var seeking by remember(file.uri) { mutableStateOf(false) }

    LaunchedEffect(videoView, seeking) {
        while (videoView != null) {
            if (!seeking) {
                videoView?.let { view ->
                    videoPosition = view.currentPosition.coerceAtLeast(0)
                    videoPlaying = view.isPlaying
                }
            }
            kotlinx.coroutines.delay(300)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (file.isVideo) "Pratinjau video" else "Pratinjau foto", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup") }
                }
                if (file.isVideo) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                videoView = this
                                setVideoURI(file.uri)
                                setOnPreparedListener { player ->
                                    videoDuration = player.duration.coerceAtLeast(0)
                                    player.isLooping = false
                                    start()
                                    videoPlaying = true
                                }
                                setOnCompletionListener { videoPlaying = false }
                            }
                        },
                        onRelease = {
                            it.stopPlayback()
                            if (videoView === it) videoView = null
                        }
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(enabled = videoView != null, onClick = {
                            val target = ((videoView?.currentPosition ?: videoPosition) - 10_000).coerceAtLeast(0)
                            videoView?.seekTo(target)
                            videoPosition = target
                        }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Mundur 10 detik")
                        }
                        IconButton(enabled = videoView != null, onClick = {
                            videoView?.let { view ->
                                if (view.isPlaying) { view.pause(); videoPlaying = false }
                                else { view.start(); videoPlaying = true }
                            }
                        }) {
                            Icon(if (videoPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, contentDescription = if (videoPlaying) "Jeda video" else "Putar video", modifier = Modifier.size(42.dp))
                        }
                        IconButton(enabled = videoView != null, onClick = {
                            val target = ((videoView?.currentPosition ?: videoPosition) + 10_000).coerceAtMost(videoDuration)
                            videoView?.seekTo(target)
                            videoPosition = target
                        }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Maju 10 detik")
                        }
                    }
                    Slider(
                        value = videoPosition.toFloat().coerceIn(0f, videoDuration.coerceAtLeast(1).toFloat()),
                        onValueChange = {
                            seeking = true
                            videoPosition = it.toInt()
                        },
                        onValueChangeFinished = {
                            videoView?.seekTo(videoPosition.coerceIn(0, videoDuration))
                            seeking = false
                        },
                        valueRange = 0f..videoDuration.coerceAtLeast(1).toFloat(),
                        enabled = videoDuration > 0 && videoView != null
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatVideoTime(videoPosition), style = MaterialTheme.typography.labelSmall)
                        Text(formatVideoTime(videoDuration), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    val displayMetrics = context.resources.displayMetrics
                    val targetWidth = displayMetrics.widthPixels.coerceAtLeast(1)
                    val targetHeight = displayMetrics.heightPixels.coerceAtLeast(1)
                    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                loadStatusPhotoBitmap(context, file.uri, targetWidth, targetHeight)
                            }.getOrNull()
                        }
                    }
                    if (bitmap != null) {
                        var imageBoxSize by remember(file.uri) { mutableStateOf(IntSize.Zero) }
                        val imageAspect = bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
                        val boxWidth = imageBoxSize.width.toFloat()
                        val boxHeight = imageBoxSize.height.toFloat()
                        val fittedWidth = if (boxWidth > 0f && boxHeight > 0f) minOf(boxWidth, boxHeight * imageAspect) else 0f
                        val fittedHeight = if (boxWidth > 0f && boxHeight > 0f) minOf(boxHeight, boxWidth / imageAspect) else 0f
                        val maxPanX = fittedWidth * (zoom - 1f) / 2f
                        val maxPanY = fittedHeight * (zoom - 1f) / 2f
                        Box(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 260.dp, max = 560.dp)
                                .onSizeChanged { imageBoxSize = it },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Foto status, cubit untuk memperbesar",
                                modifier = Modifier.fillMaxSize()
                                    .pointerInput(file.uri, bitmap, imageBoxSize) {
                                        detectTransformGestures { _, panChange, zoomChange, _ ->
                                            val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                            zoom = nextZoom
                                            val nextMaxX = fittedWidth * (nextZoom - 1f) / 2f
                                            val nextMaxY = fittedHeight * (nextZoom - 1f) / 2f
                                            pan = if (nextZoom <= 1f) Offset.Zero else Offset(
                                                x = (pan.x + panChange.x).coerceIn(-nextMaxX, nextMaxX),
                                                y = (pan.y + panChange.y).coerceIn(-nextMaxY, nextMaxY)
                                            )
                                        }
                                    }
                                    .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) {
                                Icon(Icons.Default.ZoomOutMap, contentDescription = null)
                                Text("Atur ulang zoom")
                            }
                        }
                    } else Text("Foto tidak dapat dibuka.")
                }
                Spacer(Modifier.height(8.dp))
                Text(file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun loadStatusPhotoBitmap(context: Context, uri: Uri, width: Int, height: Int): Bitmap? {
    val resolver = context.contentResolver
    val thumbnail = runCatching { resolver.loadThumbnail(uri, Size(width, height), null) }.getOrNull()
    if (thumbnail != null) return thumbnail

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > width * 2 || bounds.outHeight / sample > height * 2) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun formatVideoTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun NotificationArchiveScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
    val rows = remember { mutableStateListOf<ArchivedNotification>() }
    var confirmClear by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            try {
                access = isNotificationAccessEnabled(context)
                val latest = withContext(Dispatchers.IO) {
                    runCatching { NotificationArchiveDb(context).use { it.all() } }
                }
                latest.onSuccess {
                    rows.clear()
                    rows.addAll(it)
                }
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkGreen).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Arsip Notifikasi", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = { refresh() }, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!access) {
            InfoCard(
                "Akses notifikasi belum aktif",
                "Tekan Pengaturan akses, lalu izinkan Whats-Krdrx pada daftar Akses notifikasi Android."
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text("Pengaturan akses")
            }
            Spacer(Modifier.height(12.dp))
        } else {
            InfoCard("Akses notifikasi aktif", "")
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Catatan tersimpan: ${rows.size}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(enabled = rows.isNotEmpty(), onClick = { confirmClear = true }) { Text("Hapus semua") }
                    }
                }
                if (rows.isEmpty()) {
                    item {
                        InfoCard(
                            "Belum ada catatan",
                            "Pesan baru yang muncul sebagai notifikasi WhatsApp akan tercatat di sini jika preview notifikasi menampilkan isi pesan."
                        )
                    }
                } else {
                    items(rows, key = { it.id }) { row ->
                        Card {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.sender, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(row.receivedAt)), fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(row.message, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Sumber: notifikasi Android", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus seluruh arsip?") },
            text = { Text("Semua catatan notifikasi lokal akan dihapus dari perangkat.") },
            confirmButton = {
                TextButton(onClick = {
                    NotificationArchiveDb(context).use { it.clearAll() }
                    rows.clear()
                    confirmClear = false
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun InfoCard(title: String, description: String) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun SimpleScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = DarkGreen) {
            Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SettingsScreen(darkMode: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = DarkGreen) {
            Text("Pengaturan", modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(18.dp))
        Card {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Tema aplikasi", fontWeight = FontWeight.SemiBold)
                    Text(if (darkMode) "Mode gelap aktif" else "Mode terang aktif", fontSize = 12.sp)
                }
                Switch(checked = darkMode, onCheckedChange = { onToggleTheme() })
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Whats-Krdrx • 1.4.2", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
