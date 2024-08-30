package com.bhaptics.vrc.oscquery

import java.net.InetAddress
import kotlin.time.Duration

data class MessageEventArgs(
    val message: Message,
    val remoteEndPoint: InetAddress
)

data class Message(
    val answers: List<DNSRecord>,
    val additionalRecords: List<DNSRecord>
)

sealed class DNSRecord {
    abstract val name: DomainName
    abstract val type: Int
    abstract val clazz: Int
    abstract val ttl: Duration
}

class ARecord(
    override val name: DomainName,
    override val type: Int,
    override val clazz: Int,
    override val ttl: Duration,
    val address: InetAddress
) : DNSRecord()