package com.abridor.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {

    private val TEXT_EXT = setOf(
        "txt", "md", "log", "json", "xml", "yml", "yaml", "ini", "conf", "csv",
        "php", "js", "ts", "jsx", "tsx", "py", "java", "kt", "c", "cpp", "h",
        "html", "css", "sh", "rb", "go", "rs", "sql"
    )

    fun isTextFile(ext: String) = ext.lowercase() in TEXT_EXT

    fun mimeTypeFor(ext: String): String {
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        if (fromMap != null) return fromMap
        return when (ext.lowercase()) {
            "php" -> "text/x-php"
            "kt" -> "text/x-kotlin"
            else -> "text/plain"
        }
    }

    /** Abre o arquivo delegando para os apps instalados no celular — é assim que se
     *  consegue abrir QUALQUER formato (o Android já sabe quais apps tocam cada coisa). */
    fun openWithSystem(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "com.abridor.app.fileprovider", file)
            val mime = mimeTypeFor(file.extension)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Abrir com"))
        } catch (e: Exception) {
            Toast.makeText(context, "Nenhum app instalado consegue abrir este arquivo.", Toast.LENGTH_LONG).show()
        }
    }

    fun share(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "com.abridor.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file.extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar"))
    }

    /** Compartilha vários arquivos de uma vez (usado na seleção múltipla). */
    fun shareMultiple(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            share(context, files[0])
            return
        }
        val uris = ArrayList<Uri>()
        files.forEach { uris.add(FileProvider.getUriForFile(context, "com.abridor.app.fileprovider", it)) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar"))
    }
}
