package com.example.vrc_osc_android.vrc.oscquery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MeaModDiscovery(private val context: Context) : IDiscovery {
    companion object {
        private const val TAG = "MeaModDiscovery"
        private val SERVICE_TYPE_OSC = "${Attributes.SERVICE_OSC_UDP}"
        private val SERVICE_TYPE_OSCQUERY = "${Attributes.SERVICE_OSCJSON_TCP}"
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private val oscQueryServices = mutableSetOf<OSCQueryServiceProfile>()
    private val oscServices = mutableSetOf<OSCQueryServiceProfile>()

    override var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    override var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    var onOscServiceRemoved: ((String) -> Unit)? = null
    var onOscQueryServiceRemoved: ((String) -> Unit)? = null

    private val profiles = mutableMapOf<OSCQueryServiceProfile, NsdServiceInfo>()

    private val discoveryMutex = Mutex()
    private var isOscDiscoveryActive = false
    private var isOscQueryDiscoveryActive = false

    private val oscDiscoveryListener = createDiscoveryListener(SERVICE_TYPE_OSC)
    private val oscQueryDiscoveryListener = createDiscoveryListener(SERVICE_TYPE_OSCQUERY)

    private fun createDiscoveryListener(serviceType: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started for $regType")
            serviceScope.launch {
                discoveryMutex.withLock {
                    when (serviceType) {
                        SERVICE_TYPE_OSC -> isOscDiscoveryActive = true
                        SERVICE_TYPE_OSCQUERY -> isOscQueryDiscoveryActive = true
                    }
                }
            }
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service discovered: ${service.serviceName}")
            resolveService(service)
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "Service lost: ${service.serviceName}")
            removeMatchedService(service)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
            serviceScope.launch {
                discoveryMutex.withLock {
                    when (serviceType) {
                        SERVICE_TYPE_OSC -> isOscDiscoveryActive = false
                        SERVICE_TYPE_OSCQUERY -> isOscQueryDiscoveryActive = false
                    }
                }
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed to start for $serviceType: Error code: $errorCode")
            serviceScope.launch {
                discoveryMutex.withLock {
                    when (serviceType) {
                        SERVICE_TYPE_OSC -> isOscDiscoveryActive = false
                        SERVICE_TYPE_OSCQUERY -> isOscQueryDiscoveryActive = false
                    }
                }
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed to stop for $serviceType: Error code: $errorCode")
        }
    }

    init {
        //startDiscovery()
    }

    private suspend fun startDiscovery() {
        discoveryMutex.withLock {
            stopDiscoveryInternal()
            startDiscoveryInternal()
        }
    }

    private suspend fun startDiscoveryInternal() {
        if (!isOscDiscoveryActive) {
            try {
                nsdManager.discoverServices(SERVICE_TYPE_OSC, NsdManager.PROTOCOL_DNS_SD, oscDiscoveryListener)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to start OSC discovery: ${e.message}")
            }
        }
        if (!isOscQueryDiscoveryActive) {
            try {
                nsdManager.discoverServices(SERVICE_TYPE_OSCQUERY, NsdManager.PROTOCOL_DNS_SD, oscQueryDiscoveryListener)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to start OSCQuery discovery: ${e.message}")
            }
        }
    }

    private suspend fun stopDiscoveryInternal() {
        if (isOscDiscoveryActive) {
            try {
                nsdManager.stopServiceDiscovery(oscDiscoveryListener)
                isOscDiscoveryActive = false
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to stop OSC discovery: ${e.message}")
            }
        }
        if (isOscQueryDiscoveryActive) {
            try {
                nsdManager.stopServiceDiscovery(oscQueryDiscoveryListener)
                isOscQueryDiscoveryActive = false
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to stop OSCQuery discovery: ${e.message}")
            }
        }
    }

    private fun resolveService(service: NsdServiceInfo) {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                var retryCount = 0
                while (retryCount < 3) {
                    try {
                        val resolvedInfo = resolveServiceSuspend(service)
                        addMatchedService(resolvedInfo)
                        return@withContext
                    } catch (e: Exception) {
                        Log.e(TAG, "Resolve failed: ${service.serviceName}, attempt ${retryCount + 1}, Error: ${e.message}")
                        retryCount++
                        delay(1000) // Wait for 1 second before retrying
                    }
                }
                Log.e(TAG, "Failed to resolve service after 3 attempts: ${service.serviceName}")
            }
        }
    }

    private suspend fun resolveServiceSuspend(service: NsdServiceInfo): NsdServiceInfo = suspendCancellableCoroutine { continuation ->
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                continuation.resumeWithException(Exception("Resolve failed with error code: $errorCode"))
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                continuation.resume(serviceInfo)
            }
        })
    }

    private fun addMatchedService(serviceInfo: NsdServiceInfo) {
        val instanceName = serviceInfo.serviceName
        val port = serviceInfo.port
        val address = serviceInfo.host

        when (serviceInfo.serviceType) {
            SERVICE_TYPE_OSC -> {
                if (oscServices.none { it.name == instanceName }) {
                    val profile = OSCQueryServiceProfile(instanceName, address, port, OSCQueryServiceProfile.ServiceType.OSC)
                    oscServices.add(profile)
                    onOscServiceAdded?.invoke(profile)
                }
            }
            SERVICE_TYPE_OSCQUERY -> {
                if (oscQueryServices.none { it.name == instanceName }) {
                    val profile = OSCQueryServiceProfile(instanceName, address, port, OSCQueryServiceProfile.ServiceType.OSCQuery)
                    oscQueryServices.add(profile)
                    onOscQueryServiceAdded?.invoke(profile)
                }
            }
        }
    }

    private fun removeMatchedService(serviceInfo: NsdServiceInfo) {
        val instanceName = serviceInfo.serviceName

        when (serviceInfo.serviceType) {
            SERVICE_TYPE_OSC -> {
                oscServices.removeAll { it.name == instanceName }
                onOscServiceRemoved?.invoke(instanceName)
            }
            SERVICE_TYPE_OSCQUERY -> {
                oscQueryServices.removeAll { it.name == instanceName }
                onOscQueryServiceRemoved?.invoke(instanceName)
            }
        }
    }

    override fun refreshServices() {
        serviceScope.launch {
            startDiscovery()
        }
    }

    override fun advertise(profile: OSCQueryServiceProfile) {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = profile.name
                    serviceType = when (profile.serviceType) {
                        OSCQueryServiceProfile.ServiceType.OSC -> SERVICE_TYPE_OSC
                        OSCQueryServiceProfile.ServiceType.OSCQuery -> SERVICE_TYPE_OSCQUERY
                        else -> throw IllegalArgumentException("Unknown service type")
                    }
                    port = profile.port
                    host = profile.address
                }

                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Service registered: ${NsdServiceInfo.serviceName}")
                        profiles[profile] = serviceInfo
                    }

                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Registration failed: ${serviceInfo.serviceName} Error code: $errorCode")
                    }

                    override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                        Log.d(TAG, "Service unregistered: ${arg0.serviceName}")
                    }

                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Unregistration failed: ${serviceInfo.serviceName} Error code: $errorCode")
                    }
                })
            }
        }
    }

    override fun getOSCQueryServices(): Set<OSCQueryServiceProfile> = oscQueryServices

    override fun getOSCServices(): Set<OSCQueryServiceProfile> = oscServices

    override fun unadvertise(profile: OSCQueryServiceProfile) {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                profiles[profile]?.let { serviceInfo ->
                    nsdManager.unregisterService(object : NsdManager.RegistrationListener {
                        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
                            profiles.remove(profile)
                        }

                        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Unregistration failed: ${serviceInfo.serviceName} Error code: $errorCode")
                        }

                        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    })
                }
            }
        }
    }


    override fun close() {
        serviceScope.launch {
            discoveryMutex.withLock {
                stopDiscoveryInternal()
            }
        }
        profiles.values.forEach { serviceInfo ->
            nsdManager.unregisterService(object : NsdManager.RegistrationListener {
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Unregistration failed: ${serviceInfo.serviceName} Error code: $errorCode")
                }

                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            })
        }
    }
}