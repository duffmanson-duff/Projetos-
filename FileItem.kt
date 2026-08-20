package com.abridor.app

import java.io.File

data class FileItem(val file: File) {
    val name: String get() = file.name
    val isDir: Boolean get() = file.isDirectory
    val ext: String get() = if (isDir) "" else file.name.substringAfterLast('.', "").lowercase()
    val size: Long get() = if (isDir) 0L else file.length()
}
