package com.bhaptics.vrc.oscquery

data class DomainName(val labels: List<String>) {
    override fun toString(): String = labels.joinToString(".")

    companion object {
        fun fromString(name: String): DomainName {
            return DomainName(name.split("."))
        }
    }
}
