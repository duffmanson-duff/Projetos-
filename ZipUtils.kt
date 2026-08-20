package com.abridor.app

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    /** Descompacta um .zip para dentro de destDir (cria uma subpasta com o nome do zip). */
    fun extract(zipFile: File, destDir: File): Boolean {
        return try {
            val outRoot = File(destDir, zipFile.nameWithoutExtension).apply { mkdirs() }
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    val outFile = File(outRoot, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Compacta um arquivo ou pasta (recursivo) num .zip dentro de destDir. */
    fun compress(source: File, destDir: File): Boolean {
        return try {
            val zipFile = File(destDir, "${source.nameWithoutExtension.ifEmpty { source.name }}.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                addToZip(source, source.name, zos)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            }
            for (child in children) {
                addToZip(child, "$entryName/${child.name}", zos)
            }
        } else {
            FileInputStream(file).use { fis ->
                zos.putNextEntry(ZipEntry(entryName))
                val buffer = ByteArray(8192)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
            }
        }
    }
}
