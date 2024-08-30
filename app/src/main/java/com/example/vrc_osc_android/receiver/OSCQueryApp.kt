package com.example.vrc_osc_android.receiver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bhaptics.vrc.oscquery.OSCQueryService
import com.bhaptics.vrc.oscquery.OSCQueryServiceProfile
import com.example.vrc_osc_android.ListServiceData

@Composable
fun OSCQueryApp() {
    var showServiceList by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<OSCQueryServiceProfile?>(null) }
    val context = LocalContext.current
    val oscQueryService = remember { OSCQueryService(context) }

    if (!showServiceList) {
        FindServiceDialog(
            oscQueryService = oscQueryService,
            onServiceSelected = { profile ->
                selectedProfile = profile
                showServiceList = true
            }
        )
    } else {
        selectedProfile?.let { profile ->
            ListServiceData(oscQueryService, profile)
        }
    }
}