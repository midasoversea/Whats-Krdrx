package com.whats.krdrx

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private val ToolsGreen = Color(0xFF075E54)
private const val MAX_PICK = 10

enum class ItemState { Waiting, Running, Done, Failed }

class OptimizeItem(
    val id: Int,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean
) {
    var state by mutableStateOf(ItemState.Waiting)
    var progress by mutableIntStateOf(0)
    var result by mutableStateOf<OptimizeResult?>(null)
    var error by mutableStateOf<String?>(null)
}

/**
 * State proses optimasi dipegang di level proses (bukan di composable) agar tidak
 * terhenti saat pengguna berpindah tab atau layar diputar.
 */
object StatusOptimizerSession {
    val items = mutableStateListOf<OptimizeItem>()
    var running by mutableStateOf(false)
        private set
    var profile by mutableStateOf(VideoProfile.HD720)

    private var nextId = 0
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun enqueue(context: Context, uris: List<Uri>) {
        val app = context.applicationContext
        uris.forEach { uri ->
            if (items.none { it.uri == uri }) {
                val type = app.contentResolver.getType(uri).orEmpty()
                items.add(
                    OptimizeItem(
                        id = nextId++,
                        uri = uri,
                        name = StatusOptimizer.displayName(app, uri),
                        isVideo = type.startsWith("video/")
                    )
                )
            }
        }
    }

    fun remove(item: OptimizeItem) {
        if (!running) items.remove(item)
    }

    fun start(context: Context) {
        if (running) return
        val app = context.applicationContext
        val pending = items.filter { it.state == ItemState.Waiting || it.state == ItemState.Failed }
        if (pending.isEmpty()) return
        running = true
        job = scope.launch {
            try {
                for (item in pending) {
                    item.state = ItemState.Running
                    item.progress = 0
                    item.error = null
                    try {
                        item.result = StatusOptimizer.optimize(app, item.uri, item.isVideo, profile) {
                            item.progress = it
                        }
                        item.state = ItemState.Done
                    } catch (e: CancellationException) {
                        item.state = ItemState.Failed
                        item.error = "Dibatalkan."
                        throw e
                    } catch (e: Exception) {
                        item.state = ItemState.Failed
                        item.error = e.message ?: "Gagal memproses media."
                    }
                }
            } finally {
                running = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun doneFiles(): List<File> =
        items.filter { it.state == ItemState.Done }.mapNotNull { it.result?.outputFile }

    fun clear(context: Context) {
        if (running) return
        items.clear()
        StatusOptimizer.clearCache(context.applicationContext)
    }
}

@Composable
fun StatusOptimizerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session = StatusOptimizerSession

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)
    ) { uris -> if (uris.isNotEmpty()) session.enqueue(context, uris) }

    val pendingCount = session.items.count { it.state == ItemState.Waiting || it.state == ItemState.Failed }
    val doneFiles = session.items
        .filter { it.state == ItemState.Done }
        .mapNotNull { it.result?.outputFile }
    val hasWhatsApp = remember { StatusOptimizer.isInstalled(context, StatusOptimizer.PACKAGE_WHATSAPP) }
    val hasBusiness = remember { StatusOptimizer.isInstalled(context, StatusOptimizer.PACKAGE_WHATSAPP_BUSINESS) }

    fun share(packageName: String) {
        val ok = StatusOptimizer.shareToWhatsApp(context, session.doneFiles(), packageName)
        if (!ok) Toast.makeText(context, "WhatsApp tidak ditemukan atau tidak dapat menerima berkas.", Toast.LENGTH_LONG).show()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = ToolsGreen) {
                Text(
                    "WhatsApp Tools",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        item {
            Text(
                "Status Media Optimizer",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Cara kerja", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Foto: sisi terpanjang maks. 1600 px, JPEG 90%, warna sRGB. " +
                            "Video: H.264, maks. 30 fps, bitrate disesuaikan agar ukuran ≤ 16 MB. " +
                            "WhatsApp tetap memproses ulang Status, tetapi berkas yang sudah mendekati targetnya cenderung lebih sedikit rusak.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Resolusi video maks.", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                VideoProfile.values().forEach { option ->
                    FilterChip(
                        selected = session.profile == option,
                        onClick = { session.profile = option },
                        enabled = !session.running,
                        label = { Text(option.label) },
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !session.running,
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pilih media")
                }
                if (session.running) {
                    Button(modifier = Modifier.weight(1f), onClick = { session.cancel() }) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Batalkan")
                    }
                } else {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = pendingCount > 0,
                        onClick = { session.start(context) }
                    ) { Text("Optimasi ($pendingCount)") }
                }
            }
        }

        if (session.items.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Belum ada media", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Tekan Pilih media (maks. $MAX_PICK sekali pilih), lalu tekan Optimasi.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(session.items, key = { it.id }) { item ->
                OptimizeItemCard(item, canRemove = !session.running, onRemove = { session.remove(item) })
            }
        }

        if (doneFiles.isNotEmpty() && !session.running) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${doneFiles.size} berkas siap dibagikan. Di WhatsApp, pilih tujuan Status jika tersedia.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = hasWhatsApp,
                            onClick = { share(StatusOptimizer.PACKAGE_WHATSAPP) }
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("WhatsApp")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = hasBusiness,
                            onClick = { share(StatusOptimizer.PACKAGE_WHATSAPP_BUSINESS) }
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("WA Business")
                        }
                    }
                    if (!hasWhatsApp && !hasBusiness) {
                        Text(
                            "WhatsApp tidak terdeteksi di perangkat ini.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (session.items.isNotEmpty() && !session.running) {
            item {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { session.clear(context) }) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Bersihkan daftar dan cache")
                }
            }
        }
    }
}

@Composable
private fun OptimizeItemCard(item: OptimizeItem, canRemove: Boolean, onRemove: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (item.isVideo) Icons.Default.Videocam else Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.name, fontWeight = FontWeight.Medium, maxLines = 1)
                when (item.state) {
                    ItemState.Waiting -> Text("Menunggu", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    ItemState.Running -> {
                        Text(
                            if (item.isVideo) "Memproses ${item.progress}%" else "Memproses...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(6.dp))
                        if (item.isVideo) {
                            LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    ItemState.Done -> {
                        val r = item.result
                        if (r != null) {
                            Text(
                                "${Formatter.formatShortFileSize(context, r.inputBytes)} → " +
                                    "${Formatter.formatShortFileSize(context, r.outputBytes)} • " +
                                    "${r.outputWidth}x${r.outputHeight}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            r.note?.let {
                                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                    ItemState.Failed -> Text(
                        item.error ?: "Gagal.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            when (item.state) {
                ItemState.Done -> Icon(Icons.Default.CheckCircle, "Selesai", tint = MaterialTheme.colorScheme.primary)
                ItemState.Failed -> Icon(Icons.Default.Error, "Gagal", tint = MaterialTheme.colorScheme.error)
                else -> {}
            }
            if (canRemove && item.state != ItemState.Running) {
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Hapus dari daftar") }
            }
        }
    }
}
