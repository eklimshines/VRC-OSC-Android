package com.example.vrc_osc_android

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
import com.example.vrc_osc_android.vrc.oscquery.OSCQueryService
import com.example.vrc_osc_android.vrc.oscquery.OSCQueryServiceProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                // This is a placeholder. You should implement actual data fetching from the OSCQueryService
                data = "Fetched data for ${profile.name} (${profile.address}:${profile.port})"
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

suspend fun fetchData(port: Int): String {
    return try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://localhost:$port/")
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
            result.toString()
        } else {
            "Failed to fetch data"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}