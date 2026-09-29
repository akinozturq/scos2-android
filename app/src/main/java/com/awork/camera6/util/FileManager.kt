package com.awork.camera6.util

import android.content.Context
import android.os.Environment
import java.io.File

class FileManager(private val context: Context) {

    fun getSaveDirectory(customPath: String = "", hidden: Boolean = false): File {
        if (hidden) {
            return getHiddenDirectory()
        }
        val basePath = customPath.ifBlank {
            try {
                val picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                File(picturesDir, SCOS_DIR).absolutePath
            } catch (e: Exception) {
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath
                    ?: context.filesDir.absolutePath
            }
        }
        val dir = File(basePath)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getHiddenDirectory(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val dir = File(baseDir, "Hidden")
        if (!dir.exists()) dir.mkdirs()
        toggleHidden(dir, true)
        return dir
    }

    fun toggleHidden(dir: File, hide: Boolean) {
        val nomedia = File(dir, ".nomedia")
        if (hide && !nomedia.exists()) {
            nomedia.createNewFile()
        } else if (!hide && nomedia.exists()) {
            nomedia.delete()
        }
    }

    fun isHidden(dir: File): Boolean {
        return File(dir, ".nomedia").exists()
    }

    companion object {
        const val SCOS_DIR = "SCOS"
    }
}
