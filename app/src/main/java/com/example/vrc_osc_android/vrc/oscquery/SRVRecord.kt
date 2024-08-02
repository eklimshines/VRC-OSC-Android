package com.example.vrc_osc_android.vrc.oscquery

import android.net.nsd.NsdServiceInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class SRVRecord(
    val name: DomainName,
    val target: String,
    val port: Int,
    val priority: Int = 0,
    val weight: Int = 0,
    val ttl: Duration = 0.seconds
) {
    fun toNsdServiceInfo(): NsdServiceInfo {
        return NsdServiceInfo().apply {
            serviceName = name.toString()
            serviceType = "_${name.labels.lastOrNull() ?: ""}"
            this.port = this@SRVRecord.port
        }
    }

    companion object {
        fun fromNsdServiceInfo(serviceInfo: NsdServiceInfo): SRVRecord {
            return SRVRecord(
                name = DomainName.fromString(serviceInfo.serviceName),
                target = serviceInfo.host?.hostName ?: "",
                port = serviceInfo.port
            )
        }
    }
}