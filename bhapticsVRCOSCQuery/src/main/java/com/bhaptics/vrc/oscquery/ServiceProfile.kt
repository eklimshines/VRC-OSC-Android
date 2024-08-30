package com.bhaptics.vrc.oscquery

import android.net.nsd.NsdServiceInfo
import java.net.InetAddress

class ServiceProfile(
    val instanceName: String,
    val serviceType: String,
    val port: Int,
    val addresses: List<InetAddress>
) {
    fun toNsdServiceInfo(): NsdServiceInfo {
        return NsdServiceInfo().apply {
            serviceName = instanceName
            serviceType = this@ServiceProfile.serviceType
            port = this@ServiceProfile.port
            // NsdServiceInfo doesn't support multiple addresses, so we use the first one
            host = addresses.firstOrNull()
        }
    }

    companion object {
        fun fromNsdServiceInfo(serviceInfo: NsdServiceInfo): ServiceProfile {
            return ServiceProfile(
                instanceName = serviceInfo.serviceName,
                serviceType = serviceInfo.serviceType,
                port = serviceInfo.port,
                addresses = listOf(serviceInfo.host)
            )
        }
    }
}