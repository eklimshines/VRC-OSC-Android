package com.example.vrc_osc_android.vrc.oscquery

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.Response
import kotlinx.coroutines.*
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class OSCQueryHttpServer(
    private val context: Context,
    private val oscQueryService: OSCQueryService,
    private val logger: Logger
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    private val preMiddleware = listOf(::hostInfoMiddleware)
    private val middleware = mutableListOf<suspend (Request, Response, () -> Unit) -> Unit>()
    private val postMiddleware = listOf(::faviconMiddleware, ::explorerMiddleware, ::rootNodeMiddleware)

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(oscQueryService.tcpPort)
                logger.info("Server started on port ${oscQueryService.tcpPort}")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClient(socket) }
                }
            } catch (e: IOException) {
                Log.e("", "Error starting server: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request(socket.getInputStream())
                val response = Response(socket.getOutputStream())

                runMiddleware(preMiddleware, request, response)
                runMiddleware(middleware, request, response)
                runMiddleware(postMiddleware, request, response)

                response.send()
            } catch (e: IOException) {
                Log.e("", "Error handling client: ${e.message}")
            } finally {
                socket.close()
            }
        }
    }

    private suspend fun runMiddleware(
        middlewareList: List<suspend (Request, Response, () -> Unit) -> Unit>,
        request: Request,
        response: Response
    ) {
        for (middleware in middlewareList) {
            var next = false
            middleware(request, response) { next = true }
            if (!next) break
        }
    }

    fun addMiddleware(middleware: suspend (Request, Response, () -> Unit) -> Unit) {
        this.middleware.add(middleware)
    }

    private suspend fun hostInfoMiddleware(request: Request, response: Response, next: () -> Unit) {
        if (!request.url.contains(Attributes.HOST_INFO)) {
            next()
            return
        }

        try {
            val hostInfoString = oscQueryService.hostInfo.toString()
            response.headers["pragma"] = "no-cache"
            response.contentType = "application/json"
            response.write(hostInfoString)
        } catch (e: Exception) {
            Log.e("", "Could not construct and send Host Info: ${e.message}")
        }
    }

    private suspend fun explorerMiddleware(request: Request, response: Response, next: () -> Unit) {
        if (!request.url.contains(Attributes.EXPLORER)) {
            next()
            return
        }

        try {
            val explorerHtml = context.assets.open("OSCQueryExplorer.html").bufferedReader().use { it.readText() }
            response.contentType = "text/html"
            response.write(explorerHtml)
        } catch (e: IOException) {
            Log.e("", "Cannot find OSCQueryExplorer.html in assets: ${e.message}")
            next()
        }
    }

    private suspend fun faviconMiddleware(request: Request, response: Response, next: () -> Unit) {
        if (!request.url.contains("favicon.ico")) {
            next()
            return
        }

        try {
            val favicon = context.assets.open("favicon.ico").use { it.readBytes() }
            response.contentType = "image/x-icon"
            response.write(favicon)
        } catch (e: IOException) {
            Log.e("", "Cannot find favicon.ico in assets: ${e.message}")
            next()
        }
    }

    private suspend fun rootNodeMiddleware(request: Request, response: Response, next: () -> Unit) {
        val path = request.url.substringAfter("/", "")
        val matchedNode = oscQueryService.rootNode.getNodeWithPath(path)

        if (matchedNode == null) {
            response.status = 404
            response.write("OSC Path not found")
            return
        }

        try {
            val stringResponse = matchedNode.toString()
            response.headers["pragma"] = "no-cache"
            response.contentType = "application/json"
            response.write(stringResponse)
        } catch (e: Exception) {
            Log.e("", "Could not serialize node: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        scope.cancel()
        logger.info("Server stopped")
    }
}