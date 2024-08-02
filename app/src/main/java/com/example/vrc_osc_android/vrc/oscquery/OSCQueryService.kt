package com.example.vrc_osc_android.vrc.oscquery

import java.net.InetAddress
import java.util.logging.Logger

class OSCQueryService : AutoCloseable {
    var tcpPort: Int = DefaultPortHttp
    var oscPort: Int
        get() = hostInfo.oscPort
        set(value) {
            hostInfo.oscPort = value
        }
    var serverName: String
        get() = hostInfo.name ?: ""
        set(value) {
            hostInfo.name = value
        }
    var hostIP: InetAddress = InetAddress.getLoopbackAddress()
    var oscIP: InetAddress = InetAddress.getLoopbackAddress()

    private var _localIp: InetAddress? = null
    val localIp: InetAddress
        get() {
            if (_localIp == null) {
                // TODO: Implement the equivalent of C#'s Socket connection to get local IP
            }
            return _localIp ?: InetAddress.getLoopbackAddress()
        }

    companion object {
        const val DefaultPortHttp = 8060
        const val DefaultPortOsc = 9000
        const val DefaultServerName = "OSCQueryService"

        val logger: Logger = object : Logger {
            override fun warning(message: String) {
                println("WARNING: $message")
            }
            override fun error(message: String) {
                println("ERROR: $message")
            }
        }

        val localOscUdpServiceName = "${Attributes.SERVICE_OSC_UDP}.local"
        val localOscJsonServiceName = "${Attributes.SERVICE_OSCJSON_TCP}.local"

        val matchedNames = setOf(localOscUdpServiceName, localOscJsonServiceName)
    }

    private var discovery: IDiscovery? = null
    private val Discovery: IDiscovery
        get() {
            if (discovery == null) {
                logger.warning("Creating default MeaModDiscovery")
                discovery = MeaModDiscovery(logger)
            }
            return discovery!!
        }

    // Events
    var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null

    private lateinit var http: OSCQueryHttpServer

    private var _hostInfo: HostInfo? = null
    val hostInfo: HostInfo
        get() {
            if (_hostInfo == null) {
                _hostInfo = HostInfo(
                    name = DefaultServerName,
                    oscPort = DefaultPortOsc,
                    oscIP = InetAddress.getLoopbackAddress().hostAddress
                )
            }
            return _hostInfo!!
        }

    private var _rootNode: OSCQueryRootNode? = null
    val rootNode: OSCQueryRootNode
        get() {
            if (_rootNode == null) {
                buildRootNode()
            }
            return _rootNode!!
        }

    fun addMiddleware(middleware: suspend (HttpURLConnection) -> Unit) {
        // TODO: Implement addMiddleware
    }

    fun setDiscovery(discovery: IDiscovery) {
        this.discovery = discovery
        discovery.onOscQueryServiceAdded = { profile -> onOscQueryServiceAdded?.invoke(profile) }
        discovery.onOscServiceAdded = { profile -> onOscServiceAdded?.invoke(profile) }
    }

    fun startHttpServer() {
        http = OSCQueryHttpServer(this, logger)
    }

    fun advertiseOSCQueryService(serviceName: String, port: Int = -1) {
        val actualPort = if (port < 0) getAvailableTcpPort() else port
        Discovery.advertise(OSCQueryServiceProfile(serviceName, hostIP, actualPort, OSCQueryServiceProfile.ServiceType.OSCQuery))
    }

    fun advertiseOSCService(serviceName: String, port: Int = -1) {
        val actualPort = if (port < 0) getAvailableUdpPort() else port
        Discovery.advertise(OSCQueryServiceProfile(serviceName, oscIP, actualPort, OSCQueryServiceProfile.ServiceType.OSC))
    }

    fun refreshServices() {
        Discovery.refreshServices()
    }

    fun setValue(address: String, value: String) {
        var target = rootNode.getNodeWithPath(address)
        if (target == null) {
            target = rootNode.addNode(OSCQueryNode(address))
        }
        target.value = arrayOf(value)
    }

    fun setValue(address: String, value: Array<Any>) {
        var target = rootNode.getNodeWithPath(address)
        if (target == null) {
            target = rootNode.addNode(OSCQueryNode(address))
        }
        target.value = value
    }

    fun addEndpoint(path: String, oscTypeString: String, accessValues: Attributes.AccessValues, initialValue: Array<Any>? = null, description: String = ""): Boolean {
        if (!path.startsWith("/")) {
            logger.error("An OSC path must start with a '/', your path $path does not.")
            return false
        }

        if (rootNode.getNodeWithPath(path) != null) {
            logger.warning("Path already exists, skipping: $path")
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

    inline fun <reified T> addEndpoint(path: String, accessValues: Attributes.AccessValues, initialValue: Array<Any>? = null, description: String = ""): Boolean {
        var oscType = ""
        val typeExists = Attributes.OSCTypeFor(T::class.java) { oscType = it }

        return if (typeExists) addEndpoint(path, oscType, accessValues, initialValue, description)
        else {
            logger.error("Could not add $path to OSCQueryService because type ${T::class.java} is not supported.")
            false
        }
    }

    fun removeEndpoint(path: String): Boolean {
        if (rootNode.getNodeWithPath(path) == null) {
            logger.warning("No endpoint found for $path")
            return false
        }

        rootNode.removeNode(path)
        return true
    }

    private fun buildRootNode() {
        _rootNode = OSCQueryRootNode().apply {
            access = Attributes.AccessValues.NoValue
            description = "root node"
            fullPath = "/"
        }
    }

    override fun close() {
        // TODO: Implement dispose logic
    }

    // TODO: Implement other methods and properties

    private fun getAvailableTcpPort(): Int {
        // TODO: Implement logic to get available TCP port
        return 0
    }

    private fun getAvailableUdpPort(): Int {
        // TODO: Implement logic to get available UDP port
        return 0
    }
}