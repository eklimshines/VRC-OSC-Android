package com.bhaptics.vrc.oscquery

class MulticastService {
    var useIpv6: Boolean = false
    var ignoreDuplicateMessages: Boolean = false
    var networkInterfaceDiscovered: (() -> Unit)? = null
    var answerReceived: ((Any, MessageEventArgs) -> Unit)? = null

    fun start() {
        // Implementation needed
    }

    fun stop() {
        // Implementation needed
    }

    fun sendQuery(query: String) {
        // Implementation needed
    }
}