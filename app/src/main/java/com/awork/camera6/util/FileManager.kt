package com.awork.camera6.util

import android.content.Context
import android.os.Environment
import java.io.File

class FileManager(private val context: Context) {

    fun getPhotoSaveDirectory(customPath: String = "", hidden: Boolean = false): File {
        return getDirectory(Environment.DIRECTORY_PICTURES, customPath, hidden)
    }

    fun getVideoSaveDirectory(customPath: String = "", hidden: Boolean = false): File {
        return getDirectory(Environment.DIRECTORY_MOVIES, customPath, hidden)
    }

    fun getSaveDirectory(customPath: String = "", hidden: Boolean = false): File {
        return getPhotoSaveDirectory(customPath, hidden)
    }

    private fun getDirectory(type: String, customPath: String, hidden: Boolean): File {
        val folderName = getCleanFolderName(customPath)

        val targetDir = if (hidden) {
            val basePrivate = context.getExternalFilesDir(type) ?: context.filesDir
            val subDir = if (folderName.isNotBlank() && folderName != SCOS_DIR) {
                File(File(basePrivate, "Hidden"), folderName)
            } else {
                File(basePrivate, "Hidden")
            }
            if (!subDir.exists()) subDir.mkdirs()
            toggleHidden(subDir, true)
            subDir
        } else {
            val basePublic = try {
                Environment.getExternalStoragePublicDirectory(type)
            } catch (e: Exception) {
                context.getExternalFilesDir(type) ?: context.filesDir
            }
            val dir = File(basePublic, folderName)
            if (!dir.exists()) dir.mkdirs()
            dir
        }

        return targetDir
    }

    fun getCleanFolderName(customPath: String): String = Companion.getCleanFolderName(customPath)

    fun toggleHidden(dir: File, hide: Boolean) {
        val nomedia = File(dir, ".nomedia")
        try {
            if (hide && !nomedia.exists()) {
                nomedia.createNewFile()
            } else if (!hide && nomedia.exists()) {
                nomedia.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isHidden(dir: File): Boolean {
        return File(dir, ".nomedia").exists()
    }

    companion object {
        const val SCOS_DIR = "SCOS"

        fun getCleanFolderName(customPath: String): String {
            val sanitized = customPath.trim().replace("\\", "/").trim('/')
            val lastSegment = sanitized.substringAfterLast('/')
            return if (lastSegment.isNotBlank()) lastSegment else SCOS_DIR
        }
    }
}
