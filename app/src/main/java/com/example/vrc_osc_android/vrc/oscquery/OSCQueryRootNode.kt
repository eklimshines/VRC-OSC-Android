package com.example.vrc_osc_android.vrc.oscquery

import android.util.Log
import com.google.gson.Gson

class OSCQueryRootNode : OSCQueryNode() {
    private var pathLookup: MutableMap<String, OSCQueryNode> = mutableMapOf("/" to this)

    fun getNodeWithPath(path: String): OSCQueryNode? {
        if (pathLookup.isEmpty()) {
            rebuildLookup()
        }

        return pathLookup[path]
    }

    fun addNode(node: OSCQueryNode): OSCQueryNode? {
        // Todo: parse path and figure out which sub-node to add it to
        var parent = getNodeWithPath(node.parentPath)
        if (parent == null) {
            parent = addNode(OSCQueryNode(node.parentPath))
        }
        if (parent?.contents == null) {
            parent?.contents = mutableMapOf()
        } else if (parent.contents?.containsKey(node.name) == true) {
            Log.w(TAG, "Child node ${node.name} already exists on ${fullPath}, you need to remove the existing entry first")
            return null
        }
        // Add to contents
        parent?.contents?.put(node.name, node)


        // Todo: handle case where this full path already exists, but I don't think it should ever happen
        pathLookup[node.fullPath] = node

        return node
    }

    fun removeNode(path: String): Boolean {
        val node = pathLookup[path]
        if (node != null) {
            val parent = getNodeWithPath(node.parentPath)
            if (parent?.contents != null) {
                if (parent.contents?.containsKey(node.name) == true) {
                    parent.contents?.remove(node.name)
                    pathLookup.remove(path)
                    return true
                }
            }
        }
        return false
    }

    fun rebuildLookup() {
        pathLookup = mutableMapOf("/" to this)
        addContents(this)
    }

    /**
     * Recursive Function to rebuild Lookup
     */
    private fun addContents(node: OSCQueryNode) {
        // Don't try to add null contents
        node.contents?.values?.forEach { subNode ->
            pathLookup[subNode.fullPath] = subNode
            subNode.contents?.let { addContents(subNode) }
        }
    }

    companion object {
        private const val TAG = "OSCQueryRootNode"
        fun fromString(json: String): OSCQueryRootNode {
            val gson = Gson()
            val tree = gson.fromJson(json, OSCQueryRootNode::class.java)
            tree.rebuildLookup()
            return tree
        }
    }
}