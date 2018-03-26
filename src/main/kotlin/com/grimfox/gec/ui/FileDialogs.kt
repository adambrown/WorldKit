package com.grimfox.gec.ui

import com.grimfox.logging.LOG
import org.lwjgl.util.nfd.NFDPathSet
import org.lwjgl.system.MemoryUtil.memFree
import org.lwjgl.util.nfd.NativeFileDialog.*
import org.lwjgl.system.MemoryUtil.*

object FileDialogs {

    private fun wrapError(r: Int): Int {
        if (r == NFD_ERROR) {
            LOG.error("NFD error: " + NFD_GetError())
        }
        return r
    }

    fun selectFile(filter: String, defaultPath: String): String? {
        val out = memAllocPointer(1)
        val r = wrapError(NFD_OpenDialog(filter, defaultPath, out))
        val ptr = out.get(0)
        if (r != NFD_OKAY) {
            return null
        }
        val str = memUTF8(ptr)
        memFree(out)
        return str
    }

    fun selectFiles(filter: String, defaultPath: String): Array<String> {
        val pathSet = NFDPathSet.calloc()
        val r = wrapError(NFD_OpenDialogMultiple(filter, defaultPath, pathSet))
        if (r != NFD_OKAY) {
            return arrayOf()
        }
        val out = Array(NFD_PathSet_GetCount(pathSet).toInt()) { i ->
            NFD_PathSet_GetPath(pathSet, i.toLong())!!
        }
        NFD_PathSet_Free(pathSet)
        return out
    }

    fun selectFolder(defaultPath: String): String? {
        val out = memAllocPointer(1)
        val r = wrapError(NFD_PickFolder(defaultPath, out))
        val ptr = out.get(0)
        if (r != NFD_OKAY) {
            return null
        }
        val str = memUTF8(ptr)
        memFree(out)
        return str
    }

    fun saveFile(filter: String?, defaultPath: String): String? {
        val out = memAllocPointer(1)
        val r = wrapError(NFD_SaveDialog(filter, defaultPath, out))
        val ptr = out.get(0)
        if (r != NFD_OKAY) {
            return null
        }
        val str = memUTF8(ptr)
        memFree(out)
        return str
    }
}