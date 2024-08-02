package com.example.vrc_osc_android.vrc.oscquery

import java.net.InetAddress

data class OSCQueryServiceProfile(
    val name: String,
    val address: InetAddress,
    val port: Int,
    val serviceType: ServiceType
) {
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
    companion object {
        // If you need any static members or methods, put them here
    }

    // equals(), hashCode(), and toString() are automatically generated by data class
    /*
    public bool Equals(OSCQueryServiceProfile other)
    {
        if (ReferenceEquals(null, other)) return false;
        if (ReferenceEquals(this, other)) return true;
        return port == other.port && name == other.name && Equals(address, other.address) && serviceType == other.serviceType;
    }

    public override bool Equals(object obj)
    {
        if (ReferenceEquals(null, obj)) return false;
        if (ReferenceEquals(this, obj)) return true;
        if (obj.GetType() != this.GetType()) return false;
        return Equals((OSCQueryServiceProfile)obj);
    }

    public override int GetHashCode()
    {
        unchecked
        {
            var hashCode = port;
            hashCode = (hashCode * 397) ^ (name != null ? name.GetHashCode() : 0);
            hashCode = (hashCode * 397) ^ (address != null ? address.GetHashCode() : 0);
            hashCode = (hashCode * 397) ^ (int)serviceType;
            return hashCode;
        }
    }
     */
}