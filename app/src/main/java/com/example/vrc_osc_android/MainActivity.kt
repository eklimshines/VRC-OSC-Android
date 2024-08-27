package com.example.vrc_osc_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vrc_osc_android.receiver.OSCQueryApp
import com.example.vrc_osc_android.sender.OSCQueryServiceScreen
import com.example.vrc_osc_android.sender.OSCQueryServiceViewModel
import com.example.vrc_osc_android.ui.theme.VRCOSCAndroidTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: OSCQueryServiceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = OSCQueryServiceViewModel(applicationContext)

        setContent {
            VRCOSCAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    //sender
                    OSCQueryServiceScreen(viewModel)

                    //receiver
                    //OSCQueryApp()
                }
            }
        }

        // Observe ViewModel's lifecycle
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // This block is automatically cancelled when the lifecycle
                // transitions to STOPPED, and re-launched when it resumes.
                // You can put any long-running operations here.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.dispose()
    }
}