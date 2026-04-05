package com.example.smogtiming

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.smogtiming.ui.theme.SMOGTimingTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled in VideoCapture composable
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request camera permission
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        
        setContent {
            SMOGTimingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VideoCaptureScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCaptureScreen(modifier: Modifier = Modifier) {
    val cycleTimer = remember { SmogCycleTimer() }
    var showResultDialog by remember { mutableStateOf(false) }
    var elapsedTimeMs by remember { mutableStateOf(0L) }

    VideoCapture(
        onFrameAnalyzed = { result ->
            when (val event = cycleTimer.onFrameAnalyzed(result)) {
                is SmogCycleTimer.Event.Completed -> {
                    elapsedTimeMs = event.elapsedMs
                    showResultDialog = true
                }
                SmogCycleTimer.Event.None -> Unit
            }
        },
        modifier = modifier
    )
    
    // Show result dialog
    if (showResultDialog) {
        TimeResultDialog(
            elapsedTimeMs = elapsedTimeMs,
            onDismiss = { showResultDialog = false }
        )
    }
}

@Composable
fun TimeResultDialog(
    elapsedTimeMs: Long,
    onDismiss: () -> Unit
) {
    val seconds = elapsedTimeMs / 1000.0
    val formattedTime = String.format("%.3f", seconds)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer Result") },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Elapsed time: $formattedTime seconds")
                Text("($elapsedTimeMs milliseconds)")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SMOGTimingTheme {
        Greeting("Android")
    }
}