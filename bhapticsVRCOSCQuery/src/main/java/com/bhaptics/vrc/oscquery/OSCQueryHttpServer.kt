package com.bhaptics.vrc.oscquery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

class OSCQueryHttpServer(
    private val context: Context,
    private val oscQueryService: OSCQueryService
) : NanoHTTPD(oscQueryService.hostIP.hostAddress, oscQueryService.tcpPort) {

    private val _isRunning = MutableLiveData<Boolean>()
    val isRunning: LiveData<Boolean> = _isRunning

    private val preMiddleware = mutableListOf<suspend (IHTTPSession) -> Response?>()
    private val middleware = mutableListOf<suspend (IHTTPSession) -> Response?>()
    private val postMiddleware = mutableListOf<suspend (IHTTPSession) -> Response?>()

    val gson = GsonBuilder()
        .setLenient()
        .disableHtmlEscaping()
        .serializeNulls()
        .setPrettyPrinting()
        .excludeFieldsWithoutExposeAnnotation() // @Expose 어노테이션이 있는 필드만 직렬화
        .create()

    init {
        preMiddleware.add(::hostInfoMiddleware)
        postMiddleware.addAll(listOf(::faviconMiddleware, ::explorerMiddleware, ::rootNodeMiddleware))
    }

    override fun start() {
        try {
            super.start(SOCKET_READ_TIMEOUT, false)
            _isRunning.postValue(true)

            Log.i(TAG, "OSCQuery HTTP server started on this.hostname: ${this.hostname}, listeningPort: ${this.listeningPort}, oscQueryService.hostIP.hostAddress: ${oscQueryService.hostIP.hostAddress}")
            Log.i(TAG, "OSCQuery HTTP server started on http://${oscQueryService.hostIP}:${oscQueryService.tcpPort}/")

            Log.i(TAG, "isNetworkAvailable: ${isNetworkAvailable(context)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OSCQuery HTTP server: ${e.message}, http://${oscQueryService.hostIP}:${oscQueryService.tcpPort}/")
            throw e
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    fun addMiddleware(middleware: suspend (IHTTPSession) -> Response?) {
        this.middleware.add(middleware)
    }

    override fun serve(session: IHTTPSession): Response {
        Log.d(TAG, "Received request: ${session.method} ${session.uri}")
        return runBlocking {
            try {
                processRequest(session) ?: addCorsHeaders(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"))
            } catch (e: Exception) {
                Log.e(TAG, "Error processing request: ${e.message}", e)
                addCorsHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error"))
            }
        }
    }

    private suspend fun processRequest(session: IHTTPSession): Response {
        for (mw in preMiddleware + middleware + postMiddleware) {
            try {
                mw(session)?.let { return it }
            } catch (e: Exception) {
                Log.e(TAG, "Error in middleware: ${e.message}", e)
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }

    private suspend fun hostInfoMiddleware(session: IHTTPSession): Response? {
        Log.e(TAG, "hostInfoMiddleware")
        if (!session.uri.contains(Attributes.HOST_INFO)) {
            return null
        }

        return try {
            val hostInfoString = oscQueryService.hostInfo.toString()
            //newFixedLengthResponse(Response.Status.OK, "application/json", hostInfoString)
            addCorsHeaders(newFixedLengthResponse(Response.Status.OK, "application/json", hostInfoString))
        } catch (e: Exception) {
            Log.e(TAG, "Could not construct and send Host Info: ${e.message}")
            //newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
            addCorsHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error"))
        }
    }

    private suspend fun explorerMiddleware(session: IHTTPSession): Response? {
        Log.e(TAG, "explorerMiddleware")

        if (!session.parameters.containsKey(Attributes.EXPLORER)) {
            return null
        }

        return try {
            val input = context.assets.open("OSCQueryExplorer.html")
            val content = input.bufferedReader().use { it.readText() }
            Log.e(TAG, "explorerMiddleware.html: ${content}")
            addCorsHeaders(newFixedLengthResponse(Response.Status.OK, "text/html", content))
        } catch (e: IOException) {
            Log.e(TAG, "Error serving OSCQueryExplorer.html: ${e.message}")
            addCorsHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error"))
        }
    }

    private suspend fun faviconMiddleware(session: IHTTPSession): Response? {
        Log.e(TAG, "faviconMiddleware")
        if (!session.uri.contains("favicon.ico")) {
            return null
        }

        return try {
            val inputStream = context.resources.openRawResource(R.raw.favicon)
            val bytes = inputStream.readBytes()
            addCorsHeaders(newFixedLengthResponse(Response.Status.OK, "image/x-icon", bytes.inputStream(), bytes.size.toLong()))
        } catch (e: IOException) {
            Log.e(TAG, "Error serving favicon: ${e.message}")
            addCorsHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error"))
        }
    }

    private suspend fun rootNodeMiddleware(session: IHTTPSession): Response? {
        Log.d(TAG, "rootNodeMiddleware: Entering")
        val path = session.uri
        val matchedNode = oscQueryService.rootNode.getNodeWithPath(path)

        return if (matchedNode == null) {
            Log.d(TAG, "rootNodeMiddleware: Node not found for path: $path")
            addCorsHeaders(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "OSC Path not found"))
        } else {
            try {
                Log.d(TAG, "rootNodeMiddleware: Serializing node: ${matchedNode.fullPath}")
                val jsonString = serializeWithDepthLimit(matchedNode, maxDepth = 5)
                Log.d(TAG, "rootNodeMiddleware: Serialization successful")
                addCorsHeaders(newFixedLengthResponse(Response.Status.OK, "application/json", jsonString))
            } catch (e: Exception) {
                Log.e(TAG, "rootNodeMiddleware: Could not serialize node ${matchedNode.fullPath}: ${e.message}", e)
                addCorsHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error"))
            }
        }
    }

    private fun serveStaticFile(file: File, mimeType: String): Response {
        return try {
            val content = file.readText()
            newFixedLengthResponse(Response.Status.OK, mimeType, content)
        } catch (e: IOException) {
            Log.e(TAG, "Error serving static file: ${e.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
        }
    }

    private fun addCorsHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "X-Requested-With,content-type")
        return response
    }

    fun serializeWithDepthLimit(node: OSCQueryNode, maxDepth: Int): String {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

        fun JsonObject.addNodeProperties(node: OSCQueryNode, currentDepth: Int, isRoot: Boolean = false) {
            addProperty(Attributes.DESCRIPTION, node.description)
            addProperty(Attributes.FULL_PATH, node.fullPath)
            addProperty(Attributes.ACCESS, node.access?.toInt())

            if (!isRoot) {
                addProperty(Attributes.TYPE, node.oscType)
                add(Attributes.VALUE, gson.toJsonTree(node.value))
            }

            if (currentDepth < maxDepth && node.contents != null) {
                add(Attributes.CONTENTS, JsonObject().apply {
                    node.contents?.forEach { (key, childNode) ->
                        add(key, JsonObject().apply { addNodeProperties(childNode, currentDepth + 1) })
                    }
                })
            }
        }

        return JsonObject().apply { addNodeProperties(node, 0, isRoot = true) }.toString()
    }

    override fun stop() {
        super.stop()
        _isRunning.postValue(false)
        Log.i(TAG, "OSCQuery HTTP server stopped")
    }

    companion object {
        private const val TAG = "OSCQueryHttpServer"
    }
}