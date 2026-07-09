package com.example.ui.screens

import android.os.BatteryManager
import android.os.StatFs
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun SystemMonitorScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    
    // State for metrics
    var cpuUsage by remember { mutableStateOf(0f) }
    var ramTotal by remember { mutableStateOf(0L) }
    var ramAvailable by remember { mutableStateOf(0L) }
    var batteryLevel by remember { mutableStateOf(0) }
    var batteryStatus by remember { mutableStateOf("") }
    var storageTotal by remember { mutableStateOf(0L) }
    var storageFree by remember { mutableStateOf(0L) }
    var temperature by remember { mutableStateOf(0f) }
    
    // History for graphs
    var cpuHistory by remember { mutableStateOf(listOf<Float>()) }
    var ramHistory by remember { mutableStateOf(listOf<Float>()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            // CPU Usage from /proc/stat
            try {
                val statText = File("/proc/stat").readText()
                val cpuLine = statText.lines().first { it.startsWith("cpu ") }
                val values = cpuLine.split("\\s+".toRegex()).filter { it.isNotEmpty() }.drop(1).map { it.toLong() }
                val idle = values[3]
                val total = values.sum()
                cpuUsage = ((total - idle).toFloat() / total) * 100
                cpuHistory = (cpuHistory + cpuUsage).takeLast(30)
            } catch (_: Exception) {}
            
            // RAM Usage from /proc/meminfo
            try {
                val meminfoText = File("/proc/meminfo").readText()
                val totalMatch = Regex("MemTotal:\\s+(\\d+)").find(meminfoText)
                val availableMatch = Regex("MemAvailable:\\s+(\\d+)").find(meminfoText)
                ramTotal = totalMatch?.groupValues?.get(1)?.toLongOrNull()?.times(1024) ?: 0L
                ramAvailable = availableMatch?.groupValues?.get(1)?.toLongOrNull()?.times(1024) ?: 0L
                val ramUsed = ramTotal - ramAvailable
                ramHistory = (ramHistory + (ramUsed.toFloat() / ramTotal * 100)).takeLast(30)
            } catch (_: Exception) {}
            
            // Battery
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            batteryStatus = when (status) {
                2 -> "Charging"
                3 -> "Discharging"
                4 -> "Not charging"
                5 -> "Full"
                else -> "Unknown"
            }
            
            // Storage
            try {
                val stat = StatFs(context.filesDir.path)
                storageTotal = stat.totalBytes
                storageFree = stat.availableBytes
            } catch (_: Exception) {}
            
            // Temperature (if available)
            try {
                val tempFile = File("/sys/class/thermal/thermal_zone0/temp")
                if (tempFile.exists()) {
                    temperature = tempFile.readText().trim().toFloatOrNull()?.div(1000) ?: 0f
                }
            } catch (_: Exception) {}
            
            delay(2000)
        }
    }
    
    val ramUsed = ramTotal - ramAvailable
    val ramPercent = if (ramTotal > 0) (ramUsed.toFloat() / ramTotal * 100) else 0f
    val storageUsed = storageTotal - storageFree
    val storagePercent = if (storageTotal > 0) (storageUsed.toFloat() / storageTotal * 100) else 0f
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) DaoBackground else Color.White)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("System Monitor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Menu", tint = if (isDark) DaoTextPrimary else Color.Black)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // CPU Card
        MetricCard(
            title = "CPU Usage",
            value = "%.1f%%".format(cpuUsage),
            icon = Icons.Default.Memory,
            history = cpuHistory,
            color = ZenBlue,
            isDark = isDark
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // RAM Card
        MetricCard(
            title = "RAM Usage",
            value = "${formatSize(ramUsed)} / ${formatSize(ramTotal)} (%.0f%%)".format(ramPercent),
            icon = Icons.Default.Dns,
            history = ramHistory,
            color = ZenGold,
            isDark = isDark
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Battery Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isDark) DaoCard else Color.LightGray)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryFull, null, tint = StatusSuccess, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Battery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("$batteryLevel%", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
                Text(batteryStatus, color = DaoTextSecondary, fontSize = 14.sp)
                LinearProgressIndicator(
                    progress = batteryLevel / 100f,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = DaoBorder,
                    color = if (batteryLevel < 20) StatusError else StatusSuccess
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Storage Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isDark) DaoCard else Color.LightGray)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, null, tint = ZenSienna, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("${formatSize(storageUsed)} / ${formatSize(storageTotal)} (%.0f%%)".format(storagePercent), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
                LinearProgressIndicator(
                    progress = storagePercent / 100f,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = DaoBorder,
                    color = StatusInfo
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Temperature Card (if available)
        if (temperature > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isDark) DaoCard else Color.LightGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Thermostat, null, tint = ZenRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Temperature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("%.1f°C".format(temperature), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    history: List<Float>,
    color: Color,
    isDark: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isDark) DaoCard else Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (isDark) DaoTextPrimary else Color.Black)
            if (history.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    val width = size.width
                    val height = size.height
                    val path = Path()
                    history.forEachIndexed { index, v ->
                        val x = (index.toFloat() / (history.size - 1)) * width
                        val y = height - (v / 100f) * height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
