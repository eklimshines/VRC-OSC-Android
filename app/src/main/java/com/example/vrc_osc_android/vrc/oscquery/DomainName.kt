package com.example.vrc_osc_android.vrc.oscquery

data class DomainName(val labels: List<String>) {
    override fun toString(): String = labels.joinToString(".")

    companion object {
        fun fromString(name: String): DomainName {
            return DomainName(name.split("."))
        }
    }
}
