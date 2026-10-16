package com.example.projdiic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projdiic.ui.theme.ProjDIICTheme

class PopupWarningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)

        val popupType = intent.getStringExtra("popup_type") ?: "times_up"
        val title = if (popupType == "active") "🛡️ Instagram Guard Active" else "⏳ Time's Up!"
        val message = if (popupType == "active") {
            "Instagram Time Guard is active. You have 15 minutes for this session!"
        } else {
            "You have been viewing Instagram for 15 minutes! Time to take a break and step away from the screen."
        }
        val buttonText = if (popupType == "active") "Got it" else "Closing"

        setContent {
            ProjDIICTheme {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (popupType == "active") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(buttonText)
                        }
                    }
                }
            }
        }
    }
}
