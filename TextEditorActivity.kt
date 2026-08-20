package com.abridor.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File

class TextEditorActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var targetFile: File

    companion object {
        private const val EXTRA_PATH = "extra_path"

        fun open(context: Context, file: File) {
            val intent = Intent(context, TextEditorActivity::class.java)
            intent.putExtra(EXTRA_PATH, file.absolutePath)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        targetFile = File(path)
        val p = ThemeManager.current(this)

        editText = EditText(this).apply {
            setText(targetFile.readText())
            setTextColor(p.paper)
            setBackgroundColor(p.bg)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            gravity = android.view.Gravity.TOP
            setPadding(24, 24, 24, 24)
        }
        setContentView(editText)

        supportActionBar?.title = targetFile.name
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menu?.add("Salvar")?.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        save()
        return true
    }

    private fun save() {
        try {
            targetFile.writeText(editText.text.toString())
            Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível salvar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        save()
    }
}
