package com.abridor.app

import java.io.File

object FileOperations {

    fun rename(file: File, newName: String): Boolean {
        val target = File(file.parentFile, newName)
        if (target.exists()) return false
        return file.renameTo(target)
    }

    fun delete(file: File): Boolean {
        return if (file.isDirectory) {
            file.listFiles()?.forEach { delete(it) }
            file.delete()
        } else {
            file.delete()
        }
    }

    /** Copia arquivo/pasta (recursivo) para dentro de destDir. */
    fun copy(source: File, destDir: File): Boolean {
        return try {
            val target = File(destDir, source.name)
            if (source.isDirectory) {
                target.mkdirs()
                source.listFiles()?.forEach { child -> copy(child, target) }
            } else {
                source.copyTo(target, overwrite = true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Move = copia e depois apaga a origem. */
    fun move(source: File, destDir: File): Boolean {
        val ok = copy(source, destDir)
        if (ok) delete(source)
        return ok
    }

    fun createFolder(parent: File, name: String): Boolean {
        val dir = File(parent, name)
        if (dir.exists()) return false
        return dir.mkdirs()
    }
}
