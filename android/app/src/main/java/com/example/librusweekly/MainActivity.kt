package com.example.librusweekly

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.librusweekly.state.AppSettings
import com.example.librusweekly.ui.AppRoot
import com.example.librusweekly.ui.theme.LibrusWeeklyTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the stored per-app language before any Compose string resources are read.
        AppSettings(applicationContext).applyOnStartup()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibrusWeeklyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}
