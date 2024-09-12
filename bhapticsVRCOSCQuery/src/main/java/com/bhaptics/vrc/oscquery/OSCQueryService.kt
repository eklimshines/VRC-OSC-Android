package com.bhaptics.vrc.oscquery

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Socket
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

class OSCQueryService(
    private val context: Context,
    serverName: String = DEFAULT_SERVER_NAME,
    httpPort: Int = DEFAULT_PORT_HTTP,
    oscPort: Int = DEFAULT_PORT_OSC,
    vararg middleware: suspend (NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response?
) {
    companion object {
        const val DEFAULT_PORT_HTTP = 8060
        const val DEFAULT_PORT_OSC = 9000
        const val DEFAULT_SERVER_NAME = "OSCQueryServiceTest1"
        const val TAG = "OSCQueryService"

        val LOCAL_OSC_UDP_SERVICE_NAME = "${Attributes.SERVICE_OSC_UDP}"
        val LOCAL_OSC_JSON_SERVICE_NAME = "${Attributes.SERVICE_OSCJSON_TCP}"

        val MATCHED_NAMES = setOf(LOCAL_OSC_UDP_SERVICE_NAME, LOCAL_OSC_JSON_SERVICE_NAME)
    }

    var tcpPort: Int = httpPort
    var oscPort: Int = oscPort
        set(value) {
            field = value
            hostInfo.oscPort = value
        }
    var serverName: String = serverName
        set(value) {
            field = value
            hostInfo.name = value
        }
    var hostIP: InetAddress = InetAddress.getLoopbackAddress()
    var oscIP: InetAddress = InetAddress.getLoopbackAddress()

    private lateinit var http: OSCQueryHttpServer
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var _discovery: IDiscovery? = null
    private val discovery: IDiscovery by lazy {
        MeaModDiscovery(context)
    }

    val hostInfo: HostInfo by lazy {
        HostInfo(
            name = serverName,
            oscPort = oscPort,
            oscIP = InetAddress.getLoopbackAddress().hostAddress
        )
    }

    val rootNode: OSCQueryRootNode by lazy {
        buildRootNode()
    }

    val localIp: InetAddress by lazy {
        try {
            Socket("8.8.8.8", 65530).use { socket ->
                socket.localAddress
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get local IP, using loopback address: ${e.message}")
            InetAddress.getLoopbackAddress()
        }
    }

    var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    var onOscServiceRemoved: ((String) -> Unit)? = null
    var onOscQueryServiceRemoved: ((String) -> Unit)? = null

    init {
        serviceScope.launch {
            initialize(serverName)
            startOSCQueryService(serverName, httpPort, *middleware)
            if (oscPort != DEFAULT_PORT_OSC) {
                advertiseOSCService(serverName, oscPort)
            }
            refreshServices()
        }
    }

    fun addMiddleware(middleware: suspend (NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response?) {
        http.addMiddleware(middleware)
    }

    fun setDiscovery(discovery: IDiscovery) {
        this._discovery = discovery
        discovery.onOscQueryServiceAdded = { profile -> onOscQueryServiceAdded?.invoke(profile) }
        discovery.onOscServiceAdded = { profile -> onOscServiceAdded?.invoke(profile) }
        discovery.onOscServiceRemoved = { profile -> onOscServiceRemoved?.invoke(profile) }
        discovery.onOscQueryServiceRemoved = { profile -> onOscQueryServiceRemoved?.invoke(profile) }
    }

    fun getOSCQueryServices(): Set<OSCQueryServiceProfile> = discovery.getOSCQueryServices()
    fun getOSCServices(): Set<OSCQueryServiceProfile> = discovery.getOSCServices()

    fun getIPAddress(useIPv4: Boolean = true): String {
        try {
            val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(0, delim).uppercase(Locale.getDefault())
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            // 예외 처리
        }
        return ""
    }

    fun startHttpServer() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Log.e(TAG, "startHttpServer: ${hostIP.hostName}, ${hostIP.hostAddress}, ${hostIP}")
                    http = OSCQueryHttpServer(context, this@OSCQueryService)
                    http.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start HTTP server: ${e.message}")
                }
            }
        }
    }

    fun advertiseOSCQueryService(serviceName: String, port: Int = -1) {
        serviceScope.launch {
            val actualPort = withContext(Dispatchers.IO) {
                if (port < 0) Extensions.getAvailableTcpPort() else port
            }
            discovery.advertise(OSCQueryServiceProfile(serviceName, hostIP, actualPort, OSCQueryServiceProfile.ServiceType.OSCQuery))
        }
    }

    fun advertiseOSCService(serviceName: String, port: Int = -1) {
        serviceScope.launch {
            val actualPort = withContext(Dispatchers.IO) {
                if (port < 0) Extensions.getAvailableUdpPort() else port
            }
            discovery.advertise(OSCQueryServiceProfile(serviceName, oscIP, actualPort, OSCQueryServiceProfile.ServiceType.OSC))
        }
    }

    fun refreshServices() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                discovery.refreshServices()
            }
        }
    }

    fun setValue(address: String, value: String) {
        setValue(address, arrayOf(value))
    }

    fun setValue(address: String, value: Array<Any>) {
        serviceScope.launch {
            var target = rootNode.getNodeWithPath(address)
            if (target == null) {
                target = OSCQueryNode(address)
                rootNode.addNode(target)
            }
            target.value = value
        }
    }

    fun addEndpoint(path: String, oscTypeString: String, accessValues: Attributes.AccessValues,
                    initialValue: Array<Any>? = null, description: String = ""): Boolean {
        if (!path.startsWith("/")) {
            Log.e(TAG, "An OSC path must start with a '/', your path $path does not.")
            return false
        }

        if (rootNode.getNodeWithPath(path) != null) {
            Log.w(TAG, "Path already exists, skipping: $path")
            return false
        }

        rootNode.addNode(OSCQueryNode(path).apply {
            access = accessValues
            this.description = description
            oscType = oscTypeString
            value = initialValue
        })

        return true
    }

    inline fun <reified T> addEndpoint(path: String, accessValues: Attributes.AccessValues,
                                       initialValue: Array<Any>? = null, description: String = ""): Boolean {
        val oscType = Attributes.OSCTypeFor(T::class.java) ?: run {
            Log.e(TAG, "Could not add $path to OSCQueryService because type ${T::class.java} is not supported.")
            return false
        }
        return addEndpoint(path, oscType, accessValues, initialValue, description)
    }

    fun removeEndpoint(path: String): Boolean {
        if (rootNode.getNodeWithPath(path) == null) {
            Log.w(TAG, "No endpoint found for $path")
            return false
        }

        rootNode.removeNode(path)
        return true
    }

    private fun buildRootNode(): OSCQueryRootNode {
        return OSCQueryRootNode().apply {
            access = Attributes.AccessValues.NoValue
            description = "root node"
            fullPath = "/"
        }
    }

    fun dispose() {
        try {
            if (::http.isInitialized) {
                http.stop()
            }
            discovery.close()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: ", e)
        }
        serviceScope.cancel()
    }

    private suspend fun initialize(serverName: String = DEFAULT_SERVER_NAME) = withContext(Dispatchers.Default) {
        this@OSCQueryService.serverName = serverName
        setDiscovery(discovery)
    }

    fun startOSCQueryService(serverName: String, httpPort: Int = -1, vararg middleware: suspend (NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response?) {
        this.serverName = serverName

        tcpPort = if (httpPort == -1) Extensions.getAvailableTcpPort() else httpPort

        middleware.forEach { addMiddleware(it) }

        advertiseOSCQueryService(serverName, tcpPort)
        startHttpServer()
    }
}