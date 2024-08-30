package com.bhaptics.vrc.oscquery

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

object Extensions {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun <T> Iterable<T>.skipLast(n: Int): List<T> {
        require(n >= 0) { "Requested element count $n is less than zero." }
        if (n == 0) return this.toList()
        val list = ArrayList<T>()
        var count = 0
        for (item in this) {
            if (count < n) {
                list.add(item)
                count++
            } else {
                list.add(item)
                list.removeAt(0)
            }
        }
        return list
    }

    fun getAvailableTcpPort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }

    fun getAvailableUdpPort(): Int {
        Socket().use { socket ->
            socket.bind(null)
            return socket.localPort
        }
    }

    suspend fun getOSCTree(ip: InetAddress, port: Int): OSCQueryRootNode? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://${ip.hostAddress}:$port/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val oscTreeString = response.body?.string() ?: return@withContext null
            return@withContext gson.fromJson(oscTreeString, OSCQueryRootNode::class.java)
        }
    }

    suspend fun getHostInfo(address: InetAddress, port: Int): HostInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://${address.hostAddress}:$port?${Attributes.HOST_INFO}")
            .build()

        client.newCall(request).execute().use { response ->
            val hostInfoString = response.body?.string() ?: return@withContext null
            return@withContext gson.fromJson(hostInfoString, HostInfo::class.java)
        }
    }

    suspend fun serveStaticFile(path: String, mimeType: String, outputStream: java.io.OutputStream) = withContext(Dispatchers.IO) {
        try {
            File(path).inputStream().use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("Extensions", "Error serving static file", e)
        }
    }
}