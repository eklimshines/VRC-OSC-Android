package com.example.vrc_osc_android.vrc.oscquery

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.time.Duration

class MeaModDiscovery(private val logger: Logger? = null) : IDiscovery {
    private lateinit var discovery: ServiceDiscovery
    private lateinit var mdns: MulticastService

    private val oscQueryServices = ConcurrentHashMap.newKeySet<OSCQueryServiceProfile>()
    private val oscServices = ConcurrentHashMap.newKeySet<OSCQueryServiceProfile>()

    override var onOscServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    override var onOscQueryServiceAdded: ((OSCQueryServiceProfile) -> Unit)? = null
    var onOscServiceRemoved: ((String) -> Unit)? = null
    var onOscQueryServiceRemoved: ((String) -> Unit)? = null

    private val profiles = ConcurrentHashMap<OSCQueryServiceProfile, ServiceProfile>()

    init {
        mdns = MulticastService().apply {
            useIpv6 = false
            ignoreDuplicateMessages = true
        }

        discovery = ServiceDiscovery(mdns)

        mdns.networkInterfaceDiscovered = { refreshServices() }
        mdns.answerReceived = { sender, eventArgs -> onRemoteServiceInfo(sender, eventArgs) }
        mdns.start()
    }

    override fun getOSCQueryServices(): Set<OSCQueryServiceProfile> = oscQueryServices

    override fun getOSCServices(): Set<OSCQueryServiceProfile> = oscServices

    override fun close() {
        profiles.keys.forEach { unadvertise(it) }
        discovery.close()
        mdns.stop()
    }

    override fun refreshServices() {
        mdns.sendQuery(OSCQueryService.localOscUdpServiceName)
        mdns.sendQuery(OSCQueryService.localOscJsonServiceName)
    }

    override fun advertise(profile: OSCQueryServiceProfile) {
        val meaProfile = ServiceProfile(profile.name, profile.getServiceTypeString(), profile.port, listOf(profile.address))
        discovery.advertise(meaProfile)
        profiles[profile] = meaProfile

        logger?.info("Advertising Service ${profile.name} of type ${profile.serviceType} on ${profile.port}")
    }

    override fun unadvertise(profile: OSCQueryServiceProfile) {
        profiles.remove(profile)?.let { meaProfile ->
            discovery.unadvertise(meaProfile)
            logger?.info("Unadvertising Service ${profile.name} of type ${profile.serviceType} on ${profile.port}")
        }
    }

    private fun onRemoteServiceInfo(sender: Any, eventArgs: MessageEventArgs) {
        val response = eventArgs.message

        try {
            val hasMatch = response.answers.any { record ->
                OSCQueryService.matchedNames.contains(record?.canonicalName)
            }
            if (!hasMatch) return

            if (response.answers.any { OSCQueryService.matchedNames.contains(it.canonicalName) }) {
                try {
                    response.additionalRecords.filterIsInstance<SRVRecord>().forEach { record ->
                        if (record.ttl == Duration.ZERO) {
                            removeMatchedService(record)
                        } else {
                            addMatchedService(response, record)
                        }
                    }
                } catch (e: Exception) {
                    logger?.info("No SRV Records found in answer from ${eventArgs.remoteEndPoint}")
                }
            }
        } catch (e: Exception) {
            logger?.info("Could not parse answer from ${eventArgs.remoteEndPoint}: ${e.message}")
        }
    }

    private fun addMatchedService(response: Message, srvRecord: SRVRecord) {
        val port = srvRecord.port
        val domainName = srvRecord.name.labels
        val instanceName = domainName[0]

        val serviceName = domainName.drop(1).joinToString(".")
        val ips = response.additionalRecords.filterIsInstance<ARecord>().map { it.address }

        val profile = ServiceProfile(instanceName, serviceName, srvRecord.port, ips)

        when {
            serviceName == OSCQueryService.localOscUdpServiceName && profile !in profiles.values -> {
                if (oscServices.none { it.name == profile.instanceName }) {
                    val p = OSCQueryServiceProfile(instanceName, ips.first(), port, OSCQueryServiceProfile.ServiceType.OSC)
                    oscServices.add(p)
                    onOscServiceAdded?.invoke(p)
                }
            }
            serviceName == OSCQueryService.localOscJsonServiceName && profile !in profiles.values -> {
                if (oscQueryServices.none { it.name == profile.instanceName }) {
                    val p = OSCQueryServiceProfile(instanceName, ips.first(), port, OSCQueryServiceProfile.ServiceType.OSCQuery)
                    oscQueryServices.add(p)
                    onOscQueryServiceAdded?.invoke(p)
                }
            }
        }
    }

    private fun removeMatchedService(srvRecord: SRVRecord) {
        val domainName = srvRecord.name.labels
        val instanceName = domainName[0]
        val serviceName = domainName.drop(1).joinToString(".")

        when (serviceName) {
            OSCQueryService.localOscUdpServiceName -> {
                oscServices.removeAll { it.name == instanceName }
                onOscServiceRemoved?.invoke(instanceName)
            }
            OSCQueryService.localOscJsonServiceName -> {
                oscQueryServices.removeAll { it.name == instanceName }
                onOscQueryServiceRemoved?.invoke(instanceName)
            }
        }
    }
}
