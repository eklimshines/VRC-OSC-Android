package com.example.vrc_osc_android.vrc.oscquery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MeaModDiscovery(private val context: Context) : IDiscovery {
    companion object {
        private const val TAG = "MeaModDiscovery"
        private const val SERVICE_TYPE_OSC = "_osc._udp."
        private const val SERVICE_TYPE_OSCQUERY = "_oscjson._tcp."
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private val oscQueryServices = mutableSetOf<OSCQueryServiceProfile>()
    private val oscServices = mutableSetOf<OSCQueryServiceProfile>()

    override var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    override var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service discovered: ${service.serviceName}")
            when (service.serviceType) {
                SERVICE_TYPE_OSC -> resolveService(service, OSCQueryServiceProfile.ServiceType.OSC)
                SERVICE_TYPE_OSCQUERY -> resolveService(service, OSCQueryServiceProfile.ServiceType.OSCQuery)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "Service lost: ${service.serviceName}")
            removeService(service)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code: $errorCode")
        }
    }

    init {
        startDiscovery()
    }

    private fun startDiscovery() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                nsdManager.discoverServices(SERVICE_TYPE_OSC, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                nsdManager.discoverServices(SERVICE_TYPE_OSCQUERY, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            }
        }
    }

    private fun resolveService(service: NsdServiceInfo, type: OSCQueryServiceProfile.ServiceType) {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed: ${serviceInfo.serviceName} Error code: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Resolve Succeeded. ${serviceInfo.serviceName}")
                        val profile = OSCQueryServiceProfile(
                            serviceInfo.serviceName,
                            serviceInfo.host,
                            serviceInfo.port,
                            type
                        )
                        addService(profile)
                    }
                })
            }
        }
    }

    private fun addService(profile: OSCQueryServiceProfile) {
        serviceScope.launch {
            when (profile.serviceType) {
                OSCQueryServiceProfile.ServiceType.OSC -> {
                    oscServices.add(profile)
                    onOscServiceAdded?.invoke(profile)
                }
                OSCQueryServiceProfile.ServiceType.OSCQuery -> {
                    oscQueryServices.add(profile)
                    onOscQueryServiceAdded?.invoke(profile)
                }

                else -> {}
            }
        }
    }

    private fun removeService(service: NsdServiceInfo) {
        serviceScope.launch {
            oscServices.removeIf { it.name == service.serviceName }
            oscQueryServices.removeIf { it.name == service.serviceName }
        }
    }

    override fun refreshServices() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                // Re-initiate discovery
                nsdManager.stopServiceDiscovery(discoveryListener)
                startDiscovery()
            }
        }
    }

    override fun getOSCQueryServices(): Set<OSCQueryServiceProfile> = oscQueryServices

    override fun getOSCServices(): Set<OSCQueryServiceProfile> = oscServices

    override fun advertise(profile: OSCQueryServiceProfile) {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = profile.name
                    serviceType = when (profile.serviceType) {
                        OSCQueryServiceProfile.ServiceType.OSC -> SERVICE_TYPE_OSC
                        OSCQueryServiceProfile.ServiceType.OSCQuery -> SERVICE_TYPE_OSCQUERY
                        else -> {""}
                    }
                    port = profile.port
                    host = profile.address
                }

                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Service registered: ${NsdServiceInfo.serviceName}")
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

    override fun unadvertise(profile: OSCQueryServiceProfile) {
        // This method is left unimplemented as NsdManager doesn't provide a direct way to unregister a specific service
        Log.w(TAG, "Unadvertise is not implemented in MeaModDiscovery")
    }

    override fun close() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                nsdManager.stopServiceDiscovery(discoveryListener)
            }
        }
    }
}
