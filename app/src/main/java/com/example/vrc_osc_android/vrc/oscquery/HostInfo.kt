package com.example.vrc_osc_android.vrc.oscquery

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class HostInfo(
    @SerializedName(Keys.NAME)
    var name: String = "",

    @SerializedName(Keys.EXTENSIONS)
    var extensions: MutableMap<String, Boolean> = mutableMapOf(
        Attributes.ACCESS to true,
        Attributes.CLIPMODE to false,
        Attributes.RANGE to true,
        Attributes.TYPE to true,
        Attributes.VALUE to true
    ),

    @SerializedName(Keys.OSC_IP)
    var oscIP: String? = null,

    @SerializedName(Keys.OSC_PORT)
    var oscPort: Int = OSCQueryService.DEFAULT_PORT_OSC,

    @SerializedName(Keys.OSC_TRANSPORT)
    var oscTransport: String = Keys.OSC_TRANSPORT_UDP
) {
    override fun toString(): String {
        return Gson().toJson(this)
    }

    object Keys {
        const val NAME = "NAME"
        const val EXTENSIONS = "EXTENSIONS"
        const val OSC_IP = "OSC_IP"
        const val OSC_PORT = "OSC_PORT"
        const val OSC_TRANSPORT = "OSC_TRANSPORT"
        const val OSC_TRANSPORT_UDP = "UDP"
    }
}
