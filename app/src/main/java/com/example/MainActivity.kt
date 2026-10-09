package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.alert.AndroidAlertVibrationManager
import com.example.ui.OtcVisionHomeScreen
import com.example.ui.OtcVisionViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContext = applicationContext
    setContent {
      MyApplicationTheme {
        val otcViewModel: OtcVisionViewModel = viewModel(
          factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
              return OtcVisionViewModel(
                alertVibrationManager = AndroidAlertVibrationManager(appContext)
              ) as T
            }
          }
        )
        OtcVisionHomeScreen(viewModel = otcViewModel)
      }
    }
  }
}
