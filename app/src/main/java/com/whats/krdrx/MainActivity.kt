package com.whats.krdrx

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val colors = if (darkMode) darkColorScheme(
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
    MaterialTheme(colorScheme = colors, content = content)
}

private data class Feature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val status: String = "SEGERA"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsKrdrxApp(darkMode: Boolean, onToggleTheme: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple("Home", Icons.Default.Home, "Beranda"),
        Triple("Status", Icons.Default.Image, "Status"),
        Triple("Messages", Icons.Default.Message, "Pesan"),
        Triple("Tools", Icons.Default.Build, "Tools"),
        Triple("Settings", Icons.Default.Settings, "Setelan")
    )
    val features = listOf(
        Feature("Status Downloader", "Lihat dan simpan media Status dengan izin akses pengguna.", Icons.Default.Image),
        Feature("Deleted Messages", "Arsip notifikasi untuk melihat pesan yang sempat muncul di notifikasi.", Icons.Default.Message),
        Feature("WhatsApp Tools", "Kumpulan alat bantu WhatsApp yang aman dan praktis.", Icons.Default.Build),
        Feature("Privacy & Security", "Kontrol privasi dan pengelolaan arsip lokal.", Icons.Default.Security)
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
                        Icon(
                            if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Ganti tema"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.second, contentDescription = tab.third) },
                        label = { Text(tab.third, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(features, darkMode, onToggleTheme, Modifier.padding(padding))
            1 -> PlaceholderScreen("Status Downloader", "Modul akses media Status akan dibuat pada tahap berikutnya.", Modifier.padding(padding))
            2 -> PlaceholderScreen("Deleted Messages", "Modul arsip notifikasi akan dibuat pada tahap berikutnya.", Modifier.padding(padding))
            3 -> PlaceholderScreen("WhatsApp Tools", "Kumpulan alat bantu WhatsApp akan dikembangkan bertahap.", Modifier.padding(padding))
            else -> SettingsScreen(darkMode, onToggleTheme, Modifier.padding(padding))
        }
    }
}

@Composable
private fun HomeScreen(
    features: List<Feature>,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = DarkGreen
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Whats-Krdrx", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Teman praktis untuk mengelola media dan notifikasi WhatsApp.", color = Color.White.copy(alpha = 0.88f))
                    Spacer(Modifier.height(14.dp))
                    Text("Versi 1.0.0  •  Android 11+", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Fitur utama", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("4 fitur", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
        items(features) { feature -> FeatureCard(feature) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Tema ${if (darkMode) "Gelap" else "Terang"}", fontWeight = FontWeight.SemiBold)
                        Text("Ketuk sekali untuk mengganti tema.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                    Switch(checked = darkMode, onCheckedChange = { onToggleTheme() })
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun FeatureCard(feature: Feature) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), modifier = Modifier.size(46.dp)) {
                Icon(feature.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(feature.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(feature.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), lineHeight = 16.sp)
            }
            Text(feature.status, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
        Text("Whats-Krdrx • 1.0.0", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
