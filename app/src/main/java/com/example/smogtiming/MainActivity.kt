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
import androidx.compose.runtime.mutableLongStateOf
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
    var previousState by remember { mutableStateOf<Boolean?>(null) }
    var timerStartTime by remember { mutableLongStateOf(0L) }
    var showResultDialog by remember { mutableStateOf(false) }
    var elapsedTimeMs by remember { mutableLongStateOf(0L) }
    var falseTransitionCount by remember { mutableStateOf(0) }
    
    VideoCapture(
        onFrameAnalyzed = { result ->
            // Detect state change to false
            val previous = previousState
            
            // Only count transitions from true to false
            if (previous == true && result == false) {
                falseTransitionCount++
                
                // First transition to false: start timer
                if (falseTransitionCount == 1 && timerStartTime == 0L) {
                    timerStartTime = System.currentTimeMillis()
                }
                // Second transition to false: stop timer
                else if (falseTransitionCount == 2 && timerStartTime > 0L) {
                    val endTime = System.currentTimeMillis()
                    elapsedTimeMs = endTime - timerStartTime
                    timerStartTime = 0L // Reset for next cycle
                    falseTransitionCount = 0
                    showResultDialog = true
                }
            }
            // Reset counter when state goes back to true (allows new cycle)
            else if (previous == false && result == true && falseTransitionCount == 1) {
                // Timer is running, keep it running
            }
            
            previousState = result
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