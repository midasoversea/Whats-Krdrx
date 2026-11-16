package com.whats.krdrx

import android.content.Context
import android.graphics.Bitmap
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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

private data class Feature(val title: String, val description: String, val icon: ImageVector, val status: String = "AKTIF")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsKrdrxApp(darkMode: Boolean, onToggleTheme: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple(Icons.Default.Home, "Beranda", "Home"),
        Triple(Icons.Default.Image, "Status", "Status"),
        Triple(Icons.Default.Notifications, "Pesan", "Pesan"),
        Triple(Icons.Default.Build, "Tools", "Tools"),
        Triple(Icons.Default.Settings, "Setelan", "Settings")
    )
    val features = listOf(
        Feature("Status Downloader", "Pilih folder .Statuses, lalu simpan foto dan video ke galeri.", Icons.Default.Image),
        Feature("Arsip Notifikasi", "Catat teks pesan yang ditampilkan oleh notifikasi WhatsApp.", Icons.Default.Notifications),
        Feature("WhatsApp Tools", "Kumpulan alat bantu WhatsApp.", Icons.Default.Build, "SEGERA"),
        Feature("Privacy & Security", "Kontrol arsip dan data lokal.", Icons.Default.Security)
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Whats-Krdrx", fontWeight = FontWeight.Bold)
                        Text("WhatsApp Companion", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Ganti tema")
                    }
                }
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
            0 -> HomeScreen(features, darkMode, onToggleTheme, Modifier.padding(padding))
            1 -> StatusDownloaderScreen(Modifier.padding(padding))
            2 -> NotificationArchiveScreen(Modifier.padding(padding))
            3 -> SimpleScreen("WhatsApp Tools", "Modul alat bantu tambahan akan dikembangkan setelah dua fitur utama stabil.", Modifier.padding(padding))
            else -> SettingsScreen(darkMode, onToggleTheme, Modifier.padding(padding))
        }
    }
}

@Composable
private fun HomeScreen(features: List<Feature>, darkMode: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = DarkGreen) {
                Column(Modifier.padding(20.dp)) {
                    Text("Whats-Krdrx", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Pendamping praktis untuk media dan notifikasi WhatsApp.", color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(14.dp))
                    Text("Versi 1.3.1  •  Android 11+", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fitur utama", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("2 fitur aktif", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
        items(features) { feature ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), modifier = Modifier.size(44.dp)) {
                        Icon(feature.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(feature.title, fontWeight = FontWeight.SemiBold)
                        Text(feature.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                    Text(feature.status, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(if (darkMode) "Tema Gelap" else "Tema Terang", fontWeight = FontWeight.SemiBold)
                        Text("Ketuk sekali untuk mengganti tema.", fontSize = 12.sp)
                    }
                    Switch(checked = darkMode, onCheckedChange = { onToggleTheme() })
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        Text("Status Downloader", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Akses hanya ke folder yang kamu pilih melalui pemilih folder Android.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(null) }) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(5.dp))
                Text(if (tree == null) "Pilih folder .Statuses" else "Ganti folder")
            }
            OutlinedButton(onClick = { loadFiles() }, enabled = tree != null && !loading) {
                Icon(Icons.Default.Refresh, null)
                Text("Muat ulang")
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        onClick = {
                            scope.launch {
                                saving = true
                                var count = 0
                                var skipped = 0
                                val chosen = files.filter { it.uri.toString() in selected }
                                chosen.forEach { file ->
                                    val result = withContext(Dispatchers.IO) { runCatching { StatusStorage.save(context, file) } }
                                    if (result.isSuccess) count++ else skipped++
                                }
                                saving = false
                                selected.clear()
                                loadFiles()
                                Toast.makeText(context, "Berhasil: $count • Gagal: $skipped", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text(if (saving) "Menyimpan..." else "Simpan (${selected.size})") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { shareSelected() }) {
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
                                        files.replaceAll { it.copy() }
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
                                context.contentResolver.loadThumbnail(file.uri, Size(targetWidth, targetHeight), null)
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

    fun refresh() {
        access = isNotificationAccessEnabled(context)
        runCatching { NotificationArchiveDb(context).use { it.all() } }.onSuccess {
            rows.clear()
            rows.addAll(it)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Arsip Notifikasi", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Menyimpan teks yang ditampilkan WhatsApp di notifikasi setelah akses diaktifkan.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(12.dp))
        InfoCard(
            if (access) "Akses notifikasi aktif" else "Akses notifikasi belum aktif",
            if (access) "Notifikasi WhatsApp yang berisi teks dapat dicatat." else "Tekan Pengaturan akses, lalu izinkan Whats-Krdrx pada daftar Akses notifikasi Android."
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Pengaturan akses") }
            OutlinedButton(onClick = { refresh() }) { Text("Muat ulang") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Catatan tersimpan: " + rows.size, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(enabled = rows.isNotEmpty(), onClick = { confirmClear = true }) { Text("Hapus semua") }
        }
        if (rows.isEmpty()) {
            InfoCard("Belum ada catatan", "Pesan baru yang muncul sebagai notifikasi WhatsApp akan tercatat di sini jika preview notifikasi menampilkan isi pesan.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SimpleScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun SettingsScreen(darkMode: Boolean, onToggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Pengaturan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
        Text("Whats-Krdrx • 1.3.1", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
