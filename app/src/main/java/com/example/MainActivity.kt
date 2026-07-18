package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.example.ui.TodoScreen
import com.example.ui.TodoViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SDKInitializer.setAgreePrivacy(applicationContext, true)
    SDKInitializer.initialize(applicationContext)
    SDKInitializer.setCoordType(CoordType.GCJ02)
    
    // Pre-create WebView wasm cache directory to prevent Chromium from spamming warnings
    try {
      val wasmCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
      if (!wasmCacheDir.exists()) {
        wasmCacheDir.mkdirs()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: TodoViewModel = viewModel()
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          TodoScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

