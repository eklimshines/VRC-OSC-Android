package com.example.vrc_osc_android.vrc.oscquery

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    init {
        preMiddleware.add(::hostInfoMiddleware)
        postMiddleware.addAll(listOf(::faviconMiddleware, ::explorerMiddleware, ::rootNodeMiddleware))
    }

    override fun start() {
        try {
            super.start(SOCKET_READ_TIMEOUT, false)

            Log.i(TAG, "OSCQuery HTTP server started on this.hostname: ${this.hostname}, listeningPort: ${this.listeningPort}")
            Log.i(TAG, "OSCQuery HTTP server started on http://${oscQueryService.hostIP}:${oscQueryService.tcpPort}/")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OSCQuery HTTP server: ${e.message}, http://${oscQueryService.hostIP}:${oscQueryService.tcpPort}/")
            throw e
        }
    }

    fun addMiddleware(middleware: suspend (IHTTPSession) -> Response?) {
        this.middleware.add(middleware)
    }

    override fun serve(session: IHTTPSession): Response {
        return runBlocking {
            for (mw in middleware) {
                val response = mw(session)
                if (response != null) {
                    return@runBlocking response
                }
            }
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private suspend fun processRequest(session: IHTTPSession): Response {
        for (mw in preMiddleware) {
            mw(session)?.let { return it }
        }

        for (mw in middleware) {
            mw(session)?.let { return it }
        }

        for (mw in postMiddleware) {
            mw(session)?.let { return it }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }

    private suspend fun hostInfoMiddleware(session: IHTTPSession): Response? {
        if (!session.uri.contains(Attributes.HOST_INFO)) {
            return null
        }

        return try {
            val hostInfoString = oscQueryService.hostInfo.toString()
            newFixedLengthResponse(Response.Status.OK, "application/json", hostInfoString)
        } catch (e: Exception) {
            Log.e(TAG, "Could not construct and send Host Info: ${e.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
        }
    }

    private suspend fun explorerMiddleware(session: IHTTPSession): Response? {
        if (!session.parameters.containsKey(Attributes.EXPLORER)) {
            return null
        }

        val path = File(context.filesDir, "Resources/OSCQueryExplorer.html")
        return if (path.exists()) {
            serveStaticFile(path, "text/html")
        } else {
            Log.e(TAG, "Cannot find file at ${path.absolutePath} to serve.")
            null
        }
    }

    private suspend fun faviconMiddleware(session: IHTTPSession): Response? {
        if (!session.uri.contains("favicon.ico")) {
            return null
        }

        val path = File(context.filesDir, "Resources/favicon.ico")
        return if (path.exists()) {
            serveStaticFile(path, "image/x-icon")
        } else {
            Log.e(TAG, "Cannot find file at ${path.absolutePath} to serve.")
            null
        }
    }

    private suspend fun rootNodeMiddleware(session: IHTTPSession): Response? {
        val path = session.uri
        val matchedNode = oscQueryService.rootNode.getNodeWithPath(path)

        return if (matchedNode == null) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "OSC Path not found")
        } else {
            try {
                val stringResponse = matchedNode.toString()
                newFixedLengthResponse(Response.Status.OK, "application/json", stringResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Could not serialize node ${matchedNode.name}: ${e.message}")
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
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

    override fun stop() {
        super.stop()
        _isRunning.postValue(false)
        Log.i(TAG, "OSCQuery HTTP server stopped")
    }

    companion object {
        private const val TAG = "OSCQueryHttpServer"
    }
}