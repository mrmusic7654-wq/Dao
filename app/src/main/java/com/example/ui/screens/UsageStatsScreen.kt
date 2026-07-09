package com.example.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.*

data class AppUsage(
    val packageName: String,
    val appName: String,
    val timeUsedMs: Long,
    val lastUsed: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var appList by remember { mutableStateOf(listOf<AppUsage>()) }
    var totalScreenTime by remember { mutableLongStateOf(0L) }
    var hasPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasPermission = checkUsageStatsPermission(context)
        if (hasPermission) {
            loadUsageStats(context) { apps, total ->
                appList = apps
                totalScreenTime = total
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0A0A10) else Color(0xFFF5F5F5))) {
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White) {
            Row(modifier = Modifier.padding(12.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) }
                Text("📊 App Usage", color = ZenGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (!hasPermission) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Grant Permission") }
                }
            }
        }

        if (!hasPermission) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Permission needed", color = Color.Gray)
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Grant Usage Access") }
                }
            }
        } else {
            // Total screen time card
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Screen Time Today", color = YinTextSecondary, fontSize = 12.sp)
                    Text(formatDuration(totalScreenTime), color = ZenGold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { (totalScreenTime.toFloat() / (4 * 60 * 60 * 1000)).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (totalScreenTime > 3 * 60 * 60 * 1000) ZenRed else ZenGold,
                        trackColor = Color(0xFF333340)
                    )
                }
            }

            // App list
            LazyColumn(padding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(appList) { app ->
                    val percentage = if (totalScreenTime > 0) (app.timeUsedMs.toFloat() / totalScreenTime * 100) else 0f
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(ZenGold.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Text(app.appName.take(1), color = ZenGold, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = YinText, fontWeight = FontWeight.Medium)
                                Text("${"%.1f".format(percentage)}% • ${formatDuration(app.timeUsedMs)}", color = YinTextSecondary, fontSize = 11.sp)
                            }
                            LinearProgressIndicator(
                                progress = { percentage / 100f },
                                modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = ZenGold, trackColor = Color(0xFF333340)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName)
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

fun loadUsageStats(context: Context, onLoaded: (List<AppUsage>, Long) -> Unit) {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance()
    val endTime = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val startTime = calendar.timeInMillis

    val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
    val pm = context.packageManager

    val apps = stats.filter { it.totalTimeInForeground > 0 }
        .sortedByDescending { it.totalTimeInForeground }
        .map {
            val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() } catch (e: Exception) { it.packageName }
            AppUsage(it.packageName, appName, it.totalTimeInForeground, it.lastTimeUsed)
        }

    val total = apps.sumOf { it.timeUsedMs }
    onLoaded(apps, total)
}

fun formatDuration(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
