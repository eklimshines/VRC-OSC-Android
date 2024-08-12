package com.example.vrc_osc_android.vrc.oscquery

import android.content.Context
import android.util.Log
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File

class OSCQueryHttpServer(
    private val context: Context,
    private val oscQueryService: OSCQueryService
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var isProcessingHttp = false

    private val preMiddleware = mutableListOf<suspend (Request) -> Boolean>()
    private val middleware = mutableListOf<suspend (Request) -> Boolean>()
    private val postMiddleware = mutableListOf<suspend (Request) -> Boolean>()

    init {
        preMiddleware.add(::hostInfoMiddleware)
        postMiddleware.addAll(listOf(::faviconMiddleware, ::explorerMiddleware, ::rootNodeMiddleware))
    }

    fun start() {
        isProcessingHttp = true
        val request = Request.Builder()
            .url("ws://${oscQueryService.hostIP}:${oscQueryService.tcpPort}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                CoroutineScope(Dispatchers.Default).launch {
                    processRequest(text)
                }
            }
        })
    }

    fun addMiddleware(middleware: suspend (Request) -> Boolean) {
        this.middleware.add(middleware)
    }

    private suspend fun processRequest(requestText: String) {
        if (!isProcessingHttp) return

        val request = Request.Builder().url(requestText).build()

        for (mw in preMiddleware) {
            if (!mw(request)) return
        }

        for (mw in middleware) {
            if (!mw(request)) return
        }

        for (mw in postMiddleware) {
            if (!mw(request)) return
        }
    }

    private suspend fun hostInfoMiddleware(request: Request): Boolean {
        if (!request.url.toString().contains(Attributes.HOST_INFO)) {
            return true
        }

        try {
            val hostInfoString = oscQueryService.hostInfo.toString()
            sendResponse(hostInfoString, "application/json")
        } catch (e: Exception) {
            Log.e(TAG, "Could not construct and send Host Info: ${e.message}")
        }
        return false
    }

    private suspend fun explorerMiddleware(request: Request): Boolean {
        if (!request.url.query?.contains(Attributes.EXPLORER)!!) {
            return true
        }

        val path = File(context.filesDir, "Resources/OSCQueryExplorer.html")
        if (!path.exists()) {
            Log.e(TAG, "Cannot find file at ${path.absolutePath} to serve.")
            return true
        }

        serveStaticFile(path, "text/html")
        return false
    }

    private suspend fun faviconMiddleware(request: Request): Boolean {
        if (!request.url.toString().contains("favicon.ico")) {
            return true
        }

        val path = File(context.filesDir, "Resources/favicon.ico")
        if (!path.exists()) {
            Log.e(TAG, "Cannot find file at ${path.absolutePath} to serve.")
            return true
        }

        serveStaticFile(path, "image/x-icon")
        return false
    }

    private suspend fun rootNodeMiddleware(request: Request): Boolean {
        val path = request.url.encodedPath
        val matchedNode = oscQueryService.rootNode.getNodeWithPath(path)

        if (matchedNode == null) {
            sendResponse("OSC Path not found", "text/plain", 404)
            return false
        }

        try {
            val stringResponse = matchedNode.toString()
            sendResponse(stringResponse, "application/json")
        } catch (e: Exception) {
            Log.e(TAG, "Could not serialize node ${matchedNode.name}: ${e.message}")
        }

        return false
    }

    private fun sendResponse(content: String, contentType: String, statusCode: Int = 200) {
        webSocket?.send("{\"statusCode\": $statusCode, \"contentType\": \"$contentType\", \"content\": \"$content\"}")
    }

    private fun serveStaticFile(file: File, contentType: String) {
        try {
            val content = file.readText()
            sendResponse(content, contentType)
        } catch (e: IOException) {
            Log.e(TAG, "Error serving static file: ${e.message}")
        }
    }

    fun stop() {
        isProcessingHttp = false
        webSocket?.close(1000, "Server shutting down")
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val TAG = "OSCQueryHttpServer"
    }
}