package com.example.vrc_osc_android.vrc.oscquery

import java.net.InetAddress
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class ServiceDiscovery(private val context: Context) {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveryListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val registrationListeners = mutableMapOf<String, NsdManager.RegistrationListener>()

    fun advertise(profile: OSCQueryServiceProfile) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = profile.name
            serviceType = profile.getServiceTypeString()
            port = profile.port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                println("Service registration failed: $errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                println("Service unregistration failed: $errorCode")
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                println("Service registered: ${serviceInfo.serviceName}")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                println("Service unregistered: ${serviceInfo.serviceName}")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        registrationListeners[profile.name] = listener
    }

    fun unadvertise(profile: OSCQueryServiceProfile) {
        registrationListeners[profile.name]?.let { listener ->
            nsdManager.unregisterService(listener)
            registrationListeners.remove(profile.name)
        }
    }

    fun discoverServices(serviceType: String, onServiceFound: (OSCQueryServiceProfile) -> Unit) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                println("Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                println("Service discovered: ${service.serviceName}")
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        println("Resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        println("Resolve Succeeded. ${serviceInfo.serviceName}")
                        val profile = OSCQueryServiceProfile(
                            name = serviceInfo.serviceName,
                            address = serviceInfo.host,
                            port = serviceInfo.port,
                            serviceType = when (serviceInfo.serviceType) {
                                Attributes.SERVICE_OSC_UDP -> OSCQueryServiceProfile.ServiceType.OSC
                                Attributes.SERVICE_OSCJSON_TCP -> OSCQueryServiceProfile.ServiceType.OSCQuery
                                else -> OSCQueryServiceProfile.ServiceType.Unknown
                            }
                        )
                        onServiceFound(profile)
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                println("Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                println("Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                println("Discovery failed: Error code: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                println("Discovery failed: Error code: $errorCode")
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        discoveryListeners[serviceType] = listener
    }

    fun stopDiscovery(serviceType: String) {
        discoveryListeners[serviceType]?.let { listener ->
            nsdManager.stopServiceDiscovery(listener)
            discoveryListeners.remove(serviceType)
        }
    }
}

interface Logger {
    fun info(message: String)
}