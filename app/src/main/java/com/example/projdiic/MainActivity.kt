package com.example.projdiic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.ProjDIICTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ProjDIICTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReelsBlockerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ReelsBlockerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var reelsCount by remember { mutableStateOf(InstagramReelsAccessibilityService.currentReelsCount) }
    var isAccessibilityRunning by remember { mutableStateOf(InstagramReelsAccessibilityService.isServiceEnabled) }

    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = PermissionUtils.hasOverlayPermission(context)
            reelsCount = InstagramReelsAccessibilityService.currentReelsCount
            isAccessibilityRunning = InstagramReelsAccessibilityService.isServiceEnabled
            delay(1000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🛑 Stop getting distracted",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "After watching 8 different Reels, displays what you should actually be doing!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Accessibility Service Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityRunning) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isAccessibilityRunning) "✅ Accessibility Service Active" else "❌ Accessibility Service Required",
                    fontWeight = FontWeight.Bold,
                    color = if (isAccessibilityRunning) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!isAccessibilityRunning) {
                    Text(
                        text = "Enable Reels Blocker in Accessibility settings to track Reels.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Text("Open Accessibility Settings")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Overlay Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasOverlayPermission) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hasOverlayPermission) "✅ Overlay Permission Granted" else "⚠️ Overlay Permission Required",
                    fontWeight = FontWeight.Bold,
                    color = if (hasOverlayPermission) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!hasOverlayPermission) {
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                        // Note: For Uri in Kotlin, string to Uri is implicit or standard
                    }) {
                        Text("Grant Overlay Permission")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reels Counter Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Reels Watched: $reelsCount / 8",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}
