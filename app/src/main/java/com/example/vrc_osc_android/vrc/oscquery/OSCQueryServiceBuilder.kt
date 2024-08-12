package com.example.vrc_osc_android.vrc.oscquery

import android.content.Context
import java.net.InetAddress

class OSCQueryServiceBuilder(private val context: Context) {
    private val service = OSCQueryService(context)
    private var customStartup = false

    fun build(): OSCQueryService {
        if (!customStartup) {
            withDefaults()
        }
        return service
    }

    fun withDefaults(): OSCQueryServiceBuilder {
        customStartup = true
        startHttpServer()
        withDiscovery(MeaModDiscovery(context))
        advertiseOSCQuery()
        advertiseOSC()
        return this
    }

    fun withTcpPort(port: Int): OSCQueryServiceBuilder {
        customStartup = true
        service.tcpPort = port
        return this
    }

    fun withUdpPort(port: Int): OSCQueryServiceBuilder {
        customStartup = true
        service.oscPort = port
        return this
    }

    fun withHostIP(address: InetAddress): OSCQueryServiceBuilder {
        customStartup = true
        service.hostIP = address
        if (service.oscIP == InetAddress.getLoopbackAddress()) {
            service.oscIP = address
        }
        return this
    }

    fun withOscIP(address: InetAddress): OSCQueryServiceBuilder {
        customStartup = true
        service.oscIP = address
        return this
    }

    fun startHttpServer(): OSCQueryServiceBuilder {
        customStartup = true
        service.startHttpServer()
        return this
    }

    fun withServiceName(name: String): OSCQueryServiceBuilder {
        customStartup = true
        service.serverName = name
        return this
    }

    fun withMiddleware(middleware: suspend (okhttp3.Request) -> Boolean): OSCQueryServiceBuilder {
        customStartup = true
        service.addMiddleware(middleware)
        return this
    }

    fun withDiscovery(discovery: IDiscovery): OSCQueryServiceBuilder {
        customStartup = true
        service.setDiscovery(discovery)
        return this
    }

    fun addListenerForServiceType(listener: (OSCQueryServiceProfile) -> Unit,
                                  type: OSCQueryServiceProfile.ServiceType): OSCQueryServiceBuilder {
        customStartup = true
        when (type) {
            OSCQueryServiceProfile.ServiceType.OSC -> service.onOscServiceAdded = listener
            OSCQueryServiceProfile.ServiceType.OSCQuery -> service.onOscQueryServiceAdded = listener
            else -> {}
        }
        return this
    }

    fun advertiseOSC(): OSCQueryServiceBuilder {
        customStartup = true
        service.advertiseOSCService(service.serverName, service.oscPort)
        return this
    }

    fun advertiseOSCQuery(): OSCQueryServiceBuilder {
        customStartup = true
        service.advertiseOSCQueryService(service.serverName, service.tcpPort)
        return this
    }
}

//data class Request(val data: String)