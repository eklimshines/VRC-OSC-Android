package com.example.vrc_osc_android.vrc.oscquery

object Attributes {
    enum class AccessValues {
        NoValue,
        ReadOnly,
        WriteOnly,
        ReadWrite
    }

    private val oscTypeLookup = mapOf(
        Int::class.java to "i",
        Integer::class.java to "i",
        Long::class.java to "h",
        Float::class.java to "f",
        Double::class.java to "d",
        String::class.java to "s",
        Char::class.java to "c",
        Array::class.java to "[,]",
        ByteArray::class.java to "b",
        Boolean::class.java to "T",
    )

    @Deprecated("Please use OSCTypeFor(clazz: Class<*>, oscType: (String) -> Unit) instead", ReplaceWith("OSCTypeFor(clazz) { it }"))
    fun OSCTypeFor(clazz: Class<*>): String? {
        return oscTypeLookup[clazz]
    }

    // Todo: handle array types
    fun OSCTypeFor(clazz: Class<*>, oscType: (String) -> Unit): Boolean {
        val value = oscTypeLookup[clazz]
        if (value != null) {
            oscType(value)
            return true
        }
        return false
    }

    // Required Attributes
    const val CONTENTS = "CONTENTS"
    const val HOST_INFO = "HOST_INFO"
    const val FULL_PATH = "FULL_PATH"
    const val TYPE = "TYPE"

    // Optional Attributes
    const val ACCESS = "ACCESS"
    const val CLIPMODE = "CLIPMODE"
    const val CRITICAL = "CRITICAL"
    const val DESCRIPTION = "DESCRIPTION"
    const val EXTENDED_TYPE = "EXTENDED_TYPE"
    const val HTML = "HTML"
    const val OVERLOADS = "OVERLOADS"
    const val RANGE = "RANGE"
    const val TAGS = "TAGS"
    const val UNIT = "UNIT"
    const val VALUE = "VALUE"

    // Service Types
    const val SERVICE_OSCJSON_TCP = "_oscjson._tcp"
    const val SERVICE_OSC_UDP = "_osc._udp"

    // HTTPServer
    const val EXPLORER = "?explorer"
}