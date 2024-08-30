package com.example.osc_datareceiver

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bhaptics.vrc.oscquery.OSCQueryService
import com.bhaptics.vrc.oscquery.OSCQueryServiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun ListServiceData(oscQueryService: OSCQueryService, profile: OSCQueryServiceProfile) {
    var data by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            while (true) {
                data = fetchData(profile)
                Log.d("LiveServiceData", "data: ${data}")
                delay(500) // Poll every half second
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "${profile.name} on ${profile.port}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(data)
    }
}

suspend fun fetchData(profile: OSCQueryServiceProfile): String = withContext(Dispatchers.IO) {
    //val hostName = profile.getHostName()
    Log.d("LiveServiceData", "fetchData port: ${profile.port}, ${profile.address}, ${profile.address.hostName}, ${profile.address.hostAddress}")

    try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://${profile.address.hostAddress}:${profile.port}/")
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)
            val contents = json.optJSONObject("CONTENTS")
            val result = StringBuilder()
            contents?.keys()?.forEach { key ->
                val value = contents.getJSONArray(key).get(0)
                result.append("$key: $value\n")
            }
            Log.d("LiveServiceData", "result: ${result.toString()}")
            result.toString()
        } else {
            "Failed to fetch data: ${response.code}"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}