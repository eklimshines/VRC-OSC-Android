package com.example.vrc_osc_android.vrc.oscquery

import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSerializer

open class OSCQueryNode {
    @SerializedName(Attributes.DESCRIPTION)
    var description: String? = null

    @SerializedName(Attributes.FULL_PATH)
    var fullPath: String = ""

    @SerializedName(Attributes.ACCESS)
    var access: Attributes.AccessValues? = null

    @SerializedName(Attributes.CONTENTS)
    var contents: MutableMap<String, OSCQueryNode>? = null

    @SerializedName(Attributes.TYPE)
    var oscType: String? = null

    @SerializedName(Attributes.VALUE)
    var value: Array<Any>? = null

    val parentPath: String
        get() {
            val length = maxOf(1, fullPath.lastIndexOf("/"))
            return fullPath.substring(0, length)
        }

    val name: String
        get() = fullPath.substring(fullPath.lastIndexOf('/') + 1)

    constructor()

    constructor(fullPath: String) {
        this.fullPath = fullPath
    }

    override fun toString(): String {
        return writeSettings.toJson(this)
    }

    companion object {
        private val writeSettings: Gson = GsonBuilder()
            .serializeNulls()
            .create()

        fun addConverter(serializer: JsonSerializer<*>) {
            (writeSettings as GsonBuilder).registerTypeAdapter(
                serializer.javaClass,
                serializer
            )
        }
    }
}