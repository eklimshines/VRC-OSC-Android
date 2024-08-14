package com.example.vrc_osc_android.receiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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

@Composable
fun FindServiceDialog(
    oscQueryService: OSCQueryService,
    onServiceSelected: (OSCQueryServiceProfile) -> Unit
) {
    var services by remember { mutableStateOf<Set<OSCQueryServiceProfile>>(emptySet()) }
    var isSearching by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            oscQueryService.refreshServices()
            delay(2000) // Simulate network delay
            services = oscQueryService.getOSCQueryServices()
            isSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSearching) "Searching for OSCQuery Services..."
            else "OSCQuery Services Found: ${services.size}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!isSearching) {
            LazyColumn {
                items(services.toList()) { service ->
                    Button(
                        onClick = { onServiceSelected(service) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${service.name} (${service.address}:${service.port})")
                    }
                }
            }
        }
    }
}
