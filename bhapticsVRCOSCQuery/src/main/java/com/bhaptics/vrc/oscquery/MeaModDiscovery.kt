package com.bhaptics.vrc.oscquery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MeaModDiscovery(private val context: Context) : IDiscovery {
    companion object {
        private const val TAG = "MeaModDiscovery"
        private const val MAX_RESOLVE_RETRIES = 3
        private const val RESOLVE_RETRY_DELAY = 1000L // 1 second

        val LOCAL_OSC_UDP_SERVICE_NAME = "${Attributes.SERVICE_OSC_UDP}.local"
        val LOCAL_OSC_JSON_SERVICE_NAME = "${Attributes.SERVICE_OSCJSON_TCP}.local"
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val oscQueryServices = mutableSetOf<OSCQueryServiceProfile>()
    private val oscServices = mutableSetOf<OSCQueryServiceProfile>()

    override var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    override var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    override var onOscServiceRemoved: ((String) -> Unit)? = null
    override var onOscQueryServiceRemoved: ((String) -> Unit)? = null

    private val advertisedServices = mutableMapOf<OSCQueryServiceProfile, NsdServiceInfo>()

    private val discoveryMutex = Mutex()
    private var isOscDiscoveryActive = false
    private var isOscQueryDiscoveryActive = false

    private val oscDiscoveryListener = createDiscoveryListener(Attributes.SERVICE_OSC_UDP)
    private val oscQueryDiscoveryListener = createDiscoveryListener(Attributes.SERVICE_OSCJSON_TCP)

    init {
        startDiscovery()
    }

    private fun createDiscoveryListener(serviceType: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started for $regType")
            coroutineScope.launch {
                discoveryMutex.withLock {
                    when (serviceType) {
                        Attributes.SERVICE_OSC_UDP -> isOscDiscoveryActive = true
                        Attributes.SERVICE_OSCJSON_TCP -> isOscQueryDiscoveryActive = true
                    }
                }
            }
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            // TODO (@eklimshines) Service discovered: VRChat-Client-CBADC3
            Log.d(TAG, "Service discovered: ${service.serviceName}")
            resolveService(service)
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            // TODO (@eklimshines) Service lost: VRChat-Client-CBADC3
            Log.d(TAG, "Service lost: ${service.serviceName}")
            removeMatchedService(service)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
            coroutineScope.launch {
                discoveryMutex.withLock {
                    when (serviceType) {
                        Attributes.SERVICE_OSC_UDP -> isOscDiscoveryActive = false
                        Attributes.SERVICE_OSCJSON_TCP -> isOscQueryDiscoveryActive = false
                    }
                }
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed to start for $serviceType: Error code: $errorCode")
            coroutineScope.launch {
                discoveryMutex.withLock {
                    when (serviceType) {
                        Attributes.SERVICE_OSC_UDP -> isOscDiscoveryActive = false
                        Attributes.SERVICE_OSCJSON_TCP -> isOscQueryDiscoveryActive = false
                    }
                }
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed to stop for $serviceType: Error code: $errorCode")
        }
    }

    private fun startDiscovery() {
        coroutineScope.launch {
            discoveryMutex.withLock {
                stopDiscoveryInternal()
                startDiscoveryInternal()
            }
        }
    }

    private fun startDiscoveryInternal() {
        if (!isOscDiscoveryActive) {
            try {
                nsdManager.discoverServices(Attributes.SERVICE_OSC_UDP, NsdManager.PROTOCOL_DNS_SD, oscDiscoveryListener)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to start OSC discovery: ${e.message}")
            }
        }
        if (!isOscQueryDiscoveryActive) {
            try {
                nsdManager.discoverServices(Attributes.SERVICE_OSCJSON_TCP, NsdManager.PROTOCOL_DNS_SD, oscQueryDiscoveryListener)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to start OSCQuery discovery: ${e.message}")
            }
        }
    }

    private fun stopDiscoveryInternal() {
        Log.d(TAG, "stopDiscoveryInternal")
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
        coroutineScope.launch {
            var retryCount = 0
            while (retryCount < MAX_RESOLVE_RETRIES) {
                try {
                    val resolvedInfo = resolveServiceSuspend(service)
                    addMatchedService(resolvedInfo)
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "Resolve failed: ${service.serviceName}, attempt ${retryCount + 1}, Error: ${e.message}")
                    retryCount++
                    if (retryCount < MAX_RESOLVE_RETRIES) {
                        delay(RESOLVE_RETRY_DELAY)
                    }
                }
            }
            Log.e(TAG, "Failed to resolve service after $MAX_RESOLVE_RETRIES attempts: ${service.serviceName}")
        }
    }

    private suspend fun resolveServiceSuspend(service: NsdServiceInfo): NsdServiceInfo = suspendCancellableCoroutine { continuation ->
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                continuation.resumeWithException(IOException("Resolve failed with error code: $errorCode"))
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
        val serviceName = serviceInfo.serviceType.split(".").drop(1).joinToString(".")

        Log.d(TAG, "addMatchedService: $instanceName, $address, $port, $serviceName")
        Log.d(TAG, "addMatchedService oscServices: ${oscServices.size}, oscQueryServices: ${oscQueryServices.size}")

        when (serviceName) {
            Attributes.SERVICE_OSC_UDP   -> {
                if (oscServices.none { it.name == instanceName }) {
                    Log.d(TAG, "addMatchedService SERVICE_OSC_UDP: $instanceName, $address, $port")
                    val profile = OSCQueryServiceProfile(instanceName, address, port, OSCQueryServiceProfile.ServiceType.OSC)
                    oscServices.add(profile)
                    onOscServiceAdded?.invoke(profile)
                }
            }
            Attributes.SERVICE_OSCJSON_TCP  -> {
                if (oscQueryServices.none { it.name == instanceName }) {
                    Log.d(TAG, "addMatchedService SERVICE_OSCJSON_TCP: $instanceName, $address, $port")
                    val profile = OSCQueryServiceProfile(instanceName, address, port, OSCQueryServiceProfile.ServiceType.OSCQuery)
                    oscQueryServices.add(profile)
                    onOscQueryServiceAdded?.invoke(profile)
                }
            }
            LOCAL_OSC_UDP_SERVICE_NAME   -> {
                if (oscServices.none { it.name == instanceName }) {
                    Log.d(TAG, "addMatchedService LOCAL_OSC_UDP_SERVICE_NAME: $instanceName, $address, $port")
                    val profile = OSCQueryServiceProfile(instanceName, address, port, OSCQueryServiceProfile.ServiceType.OSC)
                    oscServices.add(profile)
                    onOscServiceAdded?.invoke(profile)
                }
            }
            LOCAL_OSC_JSON_SERVICE_NAME  -> {
                if (oscQueryServices.none { it.name == instanceName }) {
                    Log.d(TAG, "addMatchedService LOCAL_OSC_JSON_SERVICE_NAME: $instanceName, $address, $port")
                    val profile = OSCQueryServiceProfile(instanceName, address, port, OSCQueryServiceProfile.ServiceType.OSCQuery)
                    oscQueryServices.add(profile)
                    onOscQueryServiceAdded?.invoke(profile)
                }
            }
        }
    }

    private fun removeMatchedService(serviceInfo: NsdServiceInfo) {
        val instanceName = serviceInfo.serviceName
        val serviceName = serviceInfo.serviceType.split(".").drop(1).joinToString(".")

        when (serviceName) {
            LOCAL_OSC_UDP_SERVICE_NAME -> {
                oscServices.removeAll { it.name == instanceName }
                onOscServiceRemoved?.invoke(instanceName)
            }
            LOCAL_OSC_JSON_SERVICE_NAME -> {
                oscQueryServices.removeAll { it.name == instanceName }
                onOscQueryServiceRemoved?.invoke(instanceName)
            }
            Attributes.SERVICE_OSC_UDP -> {
                oscServices.removeAll { it.name == instanceName }
                onOscServiceRemoved?.invoke(instanceName)
            }
            Attributes.SERVICE_OSCJSON_TCP -> {
                oscQueryServices.removeAll { it.name == instanceName }
                onOscQueryServiceRemoved?.invoke(instanceName)
            }
        }
    }

    override fun refreshServices() {
        startDiscovery()
    }

    override fun advertise(profile: OSCQueryServiceProfile) {
        coroutineScope.launch {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = profile.name
                serviceType = when (profile.serviceType) {
                    OSCQueryServiceProfile.ServiceType.OSC -> Attributes.SERVICE_OSC_UDP
                    OSCQueryServiceProfile.ServiceType.OSCQuery -> Attributes.SERVICE_OSCJSON_TCP
                    else -> throw IllegalArgumentException("Unknown service type")
                }
                port = profile.port
                host = profile.address
            }

            try {
                registerServiceSuspend(serviceInfo)
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
                advertisedServices[profile] = serviceInfo
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register service: ${serviceInfo.serviceName}, Error: ${e.message}")
            }
        }
    }

    private suspend fun registerServiceSuspend(serviceInfo: NsdServiceInfo) = suspendCancellableCoroutine { continuation ->
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredService: NsdServiceInfo) {
                continuation.resume(Unit)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                continuation.resumeWithException(IOException("Registration failed with error code: $errorCode"))
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        })
    }

    override fun getOSCQueryServices(): Set<OSCQueryServiceProfile> = oscQueryServices

    override fun getOSCServices(): Set<OSCQueryServiceProfile> = oscServices

    override fun unadvertise(profile: OSCQueryServiceProfile) {
        coroutineScope.launch {
            advertisedServices[profile]?.let { serviceInfo ->
                try {
                    unregisterServiceSuspend(serviceInfo)
                    Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
                    advertisedServices.remove(profile)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister service: ${serviceInfo.serviceName}, Error: ${e.message}")
                }
            }
        }
    }

    private suspend fun unregisterServiceSuspend(serviceInfo: NsdServiceInfo) = suspendCancellableCoroutine { continuation ->
        nsdManager.unregisterService(object : NsdManager.RegistrationListener {
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                continuation.resume(Unit)
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                continuation.resumeWithException(IOException("Unregistration failed with error code: $errorCode"))
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        })
    }

    override fun close() {
        Log.d(TAG, "Service close")
        coroutineScope.launch {
            discoveryMutex.withLock {
                stopDiscoveryInternal()
            }
            unregisterAllServices()
        }
        coroutineScope.cancel()
    }

    private suspend fun unregisterAllServices() {
        Log.d(TAG, "unregisterAllServices")
        advertisedServices.values.forEach { serviceInfo ->
            try {
                unregisterServiceSuspend(serviceInfo)
                Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister service: ${serviceInfo.serviceName}, Error: ${e.message}")
            }
        }
        advertisedServices.clear()
    }

    fun onAppExit() {
        runBlocking {
            close()
        }
    }
}