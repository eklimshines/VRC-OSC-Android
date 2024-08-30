package com.bhaptics.vrc.oscquery

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class OSCQueryServiceProfile(
    var name: String,
    var address: InetAddress,
    var port: Int,
    var serviceType: ServiceType
)  {
    enum class ServiceType {
        Unknown, OSCQuery, OSC
    }
    fun getServiceTypeString(): String {
        return when (serviceType) {
            ServiceType.OSC -> Attributes.SERVICE_OSC_UDP
            ServiceType.OSCQuery -> Attributes.SERVICE_OSCJSON_TCP
            else -> "UNKNOWN"
        }
    }
    override fun equals(other: Any?): Boolean {
        if (other === null) return false
        if (this === other) return true
        if (other !is OSCQueryServiceProfile) return false

        return port == other.port &&
                name == other.name &&
                address == other.address &&
                serviceType == other.serviceType
    }
    override fun hashCode(): Int {
        var result = port
        result = 31 * result + (name.hashCode())
        result = 31 * result + (address.hashCode())
        result = 31 * result + serviceType.ordinal
        return result
    }
    @Test
    fun serviceTypeInt(){
        assertEquals(ServiceType.OSC.ordinal, 2)
    }
}