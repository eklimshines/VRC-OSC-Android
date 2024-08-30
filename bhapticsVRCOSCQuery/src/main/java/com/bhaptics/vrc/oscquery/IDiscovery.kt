package com.bhaptics.vrc.oscquery

interface IDiscovery : AutoCloseable {
    fun refreshServices()
    var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)?
    var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)?
    fun getOSCQueryServices(): Set<OSCQueryServiceProfile>
    fun getOSCServices(): Set<OSCQueryServiceProfile>
    fun advertise(profile: OSCQueryServiceProfile)
    fun unadvertise(profile: OSCQueryServiceProfile)
}