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
import com.example.vrc_osc_android.receiver.OSCQueryApp
import com.example.vrc_osc_android.sender.OSCQueryServiceScreen
import com.example.vrc_osc_android.sender.OSCQueryServiceViewModel
import com.example.vrc_osc_android.ui.theme.VRCOSCAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VRCOSCAndroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    //sender
                    val viewModel = remember { OSCQueryServiceViewModel(baseContext) }
                    OSCQueryServiceScreen(viewModel)

                    //receiver
                    //OSCQueryApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //viewModel
    }
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
    VRCOSCAndroidTheme {
        Greeting("Android")
    }
}