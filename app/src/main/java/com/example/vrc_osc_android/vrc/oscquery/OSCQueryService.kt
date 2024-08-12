package com.example.vrc_osc_android.vrc.oscquery

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Socket

class OSCQueryService @Deprecated("Use the Fluent Interface instead")
constructor(
    private val context: Context,
    serverName: String = DEFAULT_SERVER_NAME,
    httpPort: Int = DEFAULT_PORT_HTTP,
    oscPort: Int = DEFAULT_PORT_OSC,
    vararg middleware: suspend (okhttp3.Request) -> Boolean
) {
    companion object {
        const val DEFAULT_PORT_HTTP = 8060
        const val DEFAULT_PORT_OSC = 9000
        const val DEFAULT_SERVER_NAME = "OSCQueryService"
        const val TAG = "OSCQueryService"

        val LOCAL_OSC_UDP_SERVICE_NAME = "${Attributes.SERVICE_OSC_UDP}.local"
        val LOCAL_OSC_JSON_SERVICE_NAME = "${Attributes.SERVICE_OSCJSON_TCP}.local"

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
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private var _discovery: IDiscovery? = null
    private val discovery: IDiscovery
        get() {
            if (_discovery == null) {
                Log.w(TAG, "Creating default MeaModDiscovery")
                setDiscovery(MeaModDiscovery(context))
            }
            return _discovery!!
        }

    public val hostInfo: HostInfo by lazy {
        HostInfo(
            name = serverName,
            oscPort = oscPort,
            oscIP = InetAddress.getLoopbackAddress().hostAddress
        )
    }

    public val rootNode: OSCQueryRootNode by lazy {
        buildRootNode()
    }

    val localIp: InetAddress by lazy {
        try {
            Socket("8.8.8.8", 65530).use { socket ->
                socket.localAddress
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get local IP, using loopback address", e)
            InetAddress.getLoopbackAddress()
        }
    }

    var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null

    init {
        initialize(serverName)
        startOSCQueryService(serverName, httpPort, *middleware)
        if (oscPort != DEFAULT_PORT_OSC) {
            advertiseOSCService(serverName, oscPort)
        }
        refreshServices()
    }

    fun addMiddleware(middleware: suspend (okhttp3.Request) -> Boolean) {
        http.addMiddleware(middleware)
    }

    fun setDiscovery(discovery: IDiscovery) {
        this._discovery = discovery
        discovery.onOscQueryServiceAdded = { profile -> onOscQueryServiceAdded?.invoke(profile) }
        discovery.onOscServiceAdded = { profile -> onOscServiceAdded?.invoke(profile) }
    }

    fun getOSCQueryServices(): Set<OSCQueryServiceProfile> = discovery.getOSCQueryServices()
    fun getOSCServices(): Set<OSCQueryServiceProfile> = discovery.getOSCServices()

    fun startHttpServer() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                http = OSCQueryHttpServer(context, this@OSCQueryService)
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
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                http.stop()
                discovery.close()
            }
        }
    }

    private fun initialize(serverName: String = DEFAULT_SERVER_NAME) {
        this.serverName = serverName
        setDiscovery(MeaModDiscovery(context))
    }

    private fun startOSCQueryService(serverName: String, httpPort: Int = -1, vararg middleware: suspend (okhttp3.Request) -> Boolean) {
        this.serverName = serverName
        tcpPort = if (httpPort == -1) Extensions.getAvailableTcpPort() else httpPort
        middleware.forEach { addMiddleware(it) }
        advertiseOSCQueryService(serverName, tcpPort)
        startHttpServer()
    }
}