package com.abridor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var pathText: TextView
    private lateinit var storageText: TextView
    private lateinit var storageProgress: ProgressBar
    private lateinit var pasteBar: LinearLayout
    private lateinit var pasteLabel: TextView
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionCountLabel: TextView
    private lateinit var adapter: FileAdapter
    private lateinit var palette: Palette

    private var currentDir: File = Environment.getExternalStorageDirectory()
    private val rootDir: File = Environment.getExternalStorageDirectory()

    private var currentItems: List<FileItem> = emptyList()

    // clipboard para mover/copiar
    private var clipboardFile: File? = null
    private var clipboardIsCut: Boolean = false

    // seleção múltipla (toque longo)
    private var selectionMode: Boolean = false
    private val selectedFiles = mutableSetOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        palette = ThemeManager.current(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        pathText = findViewById(R.id.pathText)
        storageText = findViewById(R.id.storageText)
        storageProgress = findViewById(R.id.storageProgress)
        pasteBar = findViewById(R.id.pasteBar)
        pasteLabel = findViewById(R.id.pasteLabel)
        selectionBar = findViewById(R.id.selectionBar)
        selectionCountLabel = findViewById(R.id.selectionCountLabel)

        applyTheme(toolbar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(
            emptyList(), palette,
            onClick = ::onItemTap,
            onMore = ::onItemMore,
            onLongPress = ::onItemLongPress,
            isSelected = { selectedFiles.contains(it.file) },
            isSelectionMode = { selectionMode }
        )
        recyclerView.adapter = adapter

        findViewById<View>(R.id.btnPasteHere).setOnClickListener { pasteHere() }
        findViewById<View>(R.id.btnCancelPaste).setOnClickListener {
            clipboardFile = null
            pasteBar.visibility = View.GONE
        }

        findViewById<View>(R.id.btnCancelSelection).setOnClickListener { exitSelectionMode() }
        findViewById<View>(R.id.btnSelectAll).setOnClickListener { selectAllToggle() }
        findViewById<View>(R.id.btnShareSelected).setOnClickListener { shareSelected() }
        findViewById<View>(R.id.btnDeleteSelected).setOnClickListener { confirmDeleteSelected() }

        checkPermissionsAndLoad()
    }

    private fun applyTheme(toolbar: Toolbar) {
        findViewById<View>(R.id.rootLayout).setBackgroundColor(palette.bg)
        toolbar.setBackgroundColor(palette.bg)
        toolbar.setTitleTextColor(palette.paper)
        pathText.setTextColor(palette.accent)
        storageText.setTextColor(palette.inkDim)
        storageProgress.progressTintList = ColorStateList.valueOf(palette.accent)
        storageProgress.progressBackgroundTintList = ColorStateList.valueOf(palette.line)
        pasteBar.setBackgroundColor(palette.panel)
        pasteLabel.setTextColor(palette.inkDim)
        findViewById<TextView>(R.id.btnPasteHere).setTextColor(palette.accent)
        findViewById<TextView>(R.id.btnCancelPaste).setTextColor(palette.inkDim)

        selectionBar.setBackgroundColor(palette.panel)
        selectionCountLabel.setTextColor(palette.paper)
        findViewById<TextView>(R.id.btnCancelSelection).setTextColor(palette.paper)
        findViewById<TextView>(R.id.btnSelectAll).setTextColor(palette.inkDim)
        findViewById<TextView>(R.id.btnShareSelected).setTextColor(palette.accent)
        findViewById<TextView>(R.id.btnDeleteSelected).setTextColor(0xFFE0714F.toInt())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "TEMAS")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            ThemePickerDialog.show(this) { recreate() }
        }
        return true
    }

    override fun onBackPressed() {
        if (selectionMode) {
            exitSelectionMode()
        } else if (currentDir.absolutePath != rootDir.absolutePath) {
            currentDir = currentDir.parentFile ?: rootDir
            refresh()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- permissões ----------

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                refresh()
            }
        } else {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (perms.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                ActivityCompat.requestPermissions(this, perms, 100)
            } else {
                refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            refresh()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            refresh()
        } else {
            Toast.makeText(this, "Preciso de acesso aos arquivos para funcionar.", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- listagem ----------

    private fun refresh() {
        pathText.text = currentDir.absolutePath
        val files = currentDir.listFiles()?.toList() ?: emptyList()
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        currentItems = sorted.map { FileItem(it) }
        adapter.update(currentItems)
        title = currentDir.name.ifEmpty { "Abridor" }
        updateStorageInfo()
    }

    private fun updateStorageInfo() {
        try {
            val stat = StatFs(rootDir.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free
            val pct = if (total > 0) ((used * 100) / total).toInt() else 0
            storageText.text = "${humanSize(used)} usados de ${humanSize(total)} · ${humanSize(free)} livres"
            storageProgress.progress = pct
        } catch (e: Exception) {
            storageText.text = "Armazenamento indisponível"
            storageProgress.progress = 0
        }
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val df = DecimalFormat("#.#")
        if (bytes < 1024 * 1024) return "${df.format(bytes / 1024.0)} KB"
        if (bytes < 1024 * 1024 * 1024) return "${df.format(bytes / (1024.0 * 1024.0))} MB"
        return "${df.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    private fun onItemClick(item: FileItem) {
        if (item.isDir) {
            currentDir = item.file
            refresh()
        } else if (item.ext == "zip") {
            confirmExtract(item.file)
        } else if (item.ext in MediaTypes.AUDIO_EXT) {
            val intent = Intent(this, AudioPlayerActivity::class.java)
            intent.putExtra(AudioPlayerActivity.EXTRA_PATH, item.file.absolutePath)
            startActivity(intent)
        } else if (item.ext in MediaTypes.VIDEO_EXT) {
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra(VideoPlayerActivity.EXTRA_PATH, item.file.absolutePath)
            startActivity(intent)
        } else if (item.ext in MediaTypes.IMAGE_EXT) {
            ImageEditorActivity.open(this, item.file)
        } else if (FileOpener.isTextFile(item.ext)) {
            TextEditorActivity.open(this, item.file)
        } else {
            FileOpener.openWithSystem(this, item.file)
        }
    }

    // toque comum: navega/abre normalmente, ou alterna seleção se já estiver selecionando
    private fun onItemTap(item: FileItem) {
        if (selectionMode) {
            toggleSelection(item)
        } else {
            onItemClick(item)
        }
    }

    // toque longo: entra no modo de seleção (ou alterna, se já estiver nele)
    private fun onItemLongPress(item: FileItem) {
        if (!selectionMode) {
            selectionMode = true
        }
        toggleSelection(item)
    }

    // ---------- seleção múltipla ----------

    private fun toggleSelection(item: FileItem) {
        if (selectedFiles.contains(item.file)) {
            selectedFiles.remove(item.file)
        } else {
            selectedFiles.add(item.file)
        }
        if (selectedFiles.isEmpty()) {
            selectionMode = false
        }
        adapter.notifyDataSetChanged()
        updateSelectionBar()
    }

    private fun selectAllToggle() {
        val allSelected = currentItems.isNotEmpty() && selectedFiles.size == currentItems.size
        selectedFiles.clear()
        if (!allSelected) {
            selectionMode = true
            selectedFiles.addAll(currentItems.map { it.file })
        } else {
            selectionMode = false
        }
        adapter.notifyDataSetChanged()
        updateSelectionBar()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedFiles.clear()
        adapter.notifyDataSetChanged()
        selectionBar.visibility = View.GONE
    }

    private fun updateSelectionBar() {
        if (selectionMode && selectedFiles.isNotEmpty()) {
            pasteBar.visibility = View.GONE
            selectionBar.visibility = View.VISIBLE
            selectionCountLabel.text = "${selectedFiles.size} selecionado(s)"
        } else {
            selectionBar.visibility = View.GONE
        }
    }

    private fun shareSelected() {
        val files = selectedFiles.filter { !it.isDirectory }
        if (files.isEmpty()) {
            Toast.makeText(this, "Selecione arquivos (não pastas) para compartilhar.", Toast.LENGTH_SHORT).show()
            return
        }
        FileOpener.shareMultiple(this, files)
    }

    private fun confirmDeleteSelected() {
        val files = selectedFiles.toList()
        if (files.isEmpty()) return
        val builder = themedAlert(
            "Excluir",
            "Excluir ${files.size} item(ns) selecionado(s) definitivamente?"
        )
        builder.setPositiveButton("Excluir") { _, _ ->
            var okAll = true
            files.forEach { if (!FileOperations.delete(it)) okAll = false }
            toastResult(okAll, "Excluído(s)", "Alguns itens não puderam ser excluídos")
            exitSelectionMode()
            refresh()
        }
        builder.setNegativeButton("Cancelar", null)
        showThemedDialog(builder)
    }

    // ---------- menu de ação por item (⋮) ----------

    private data class MenuAction(val id: Int, val label: String, val danger: Boolean = false)

    private fun onItemMore(item: FileItem, anchor: View) {
        val actions = mutableListOf<MenuAction>()
        if (!item.isDir) actions.add(MenuAction(R.id.action_open, "Abrir"))
        if (!item.isDir) actions.add(MenuAction(R.id.action_share, "Compartilhar"))
        actions.add(MenuAction(R.id.action_rename, "Renomear"))
        if (!item.isDir && FileOpener.isTextFile(item.ext)) actions.add(MenuAction(R.id.action_edit, "Editar como texto"))
        actions.add(MenuAction(R.id.action_copy, "Copiar"))
        actions.add(MenuAction(R.id.action_move, "Mover (recortar)"))
        actions.add(MenuAction(R.id.action_compress, "Compactar (.zip)"))
        if (item.ext == "zip") actions.add(MenuAction(R.id.action_extract, "Descompactar aqui"))
        actions.add(MenuAction(R.id.action_delete, "Excluir", danger = true))
        actions.add(MenuAction(R.id.action_details, "Detalhes"))

        showThemedPopupMenu(anchor, actions) { id ->
            when (id) {
                R.id.action_open -> onItemClick(item)
                R.id.action_share -> if (!item.isDir) FileOpener.share(this, item.file)
                    else Toast.makeText(this, "Compartilhe arquivos individuais.", Toast.LENGTH_SHORT).show()
                R.id.action_rename -> renameDialog(item.file)
                R.id.action_edit -> TextEditorActivity.open(this, item.file)
                R.id.action_copy -> { clipboardFile = item.file; clipboardIsCut = false; showPasteBar(item.file) }
                R.id.action_move -> { clipboardFile = item.file; clipboardIsCut = true; showPasteBar(item.file) }
                R.id.action_compress -> {
                    val ok = ZipUtils.compress(item.file, currentDir)
                    toastResult(ok, "Compactado", "Falha ao compactar")
                    refresh()
                }
                R.id.action_extract -> confirmExtract(item.file)
                R.id.action_delete -> confirmDelete(item.file)
                R.id.action_details -> showDetails(item)
            }
        }
    }

    /** Menu "⋮" desenhado do zero com as cores do tema atual — o PopupMenu padrão
     *  do Android ignora nossa paleta e aparecia com fundo branco. */
    private fun showThemedPopupMenu(anchor: View, actions: List<MenuAction>, onSelect: (Int) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeManager.glossyDrawable(palette.panel, dp(10).toFloat(), palette.line, dp(1))
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        val outValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        lateinit var popup: PopupWindow

        actions.forEach { action ->
            val row = TextView(this).apply {
                text = action.label
                setTextColor(if (action.danger) 0xFFE0714F.toInt() else palette.paper)
                textSize = 14f
                minWidth = dp(180)
                setPadding(dp(18), dp(13), dp(18), dp(13))
                if (outValue.resourceId != 0) setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    popup.dismiss()
                    onSelect(action.id)
                }
            }
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        popup = PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.elevation = dp(8).toFloat()
        popup.showAsDropDown(anchor, 0, dp(4))
    }

    // ---------- ações ----------

    private fun renameDialog(file: File) {
        val input = EditText(this).apply {
            setText(file.name)
            setTextColor(palette.paper)
            setHintTextColor(palette.inkDim)
            highlightColor = palette.accentDim
            background = ThemeManager.glossyDrawable(palette.card, dp(6).toFloat(), palette.line, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val builder = themedAlert("Renomear", extraView = input)
        builder.setPositiveButton("Renomear") { _, _ ->
            val ok = FileOperations.rename(file, input.text.toString().trim())
            toastResult(ok, "Renomeado", "Não foi possível renomear")
            refresh()
        }
        builder.setNegativeButton("Cancelar", null)
        showThemedDialog(builder)
    }

    private fun confirmDelete(file: File) {
        val builder = themedAlert("Excluir", "Excluir \"${file.name}\" definitivamente?")
        builder.setPositiveButton("Excluir") { _, _ ->
            val ok = FileOperations.delete(file)
            toastResult(ok, "Excluído", "Não foi possível excluir")
            refresh()
        }
        builder.setNegativeButton("Cancelar", null)
        showThemedDialog(builder)
    }

    private fun confirmExtract(zipFile: File) {
        val builder = themedAlert("Descompactar", "Descompactar \"${zipFile.name}\" nesta pasta?")
        builder.setPositiveButton("Descompactar") { _, _ ->
            val ok = ZipUtils.extract(zipFile, currentDir)
            toastResult(ok, "Descompactado", "Falha ao descompactar")
            refresh()
        }
        builder.setNegativeButton("Cancelar", null)
        showThemedDialog(builder)
    }

    private fun showDetails(item: FileItem) {
        val msg = buildString {
            append("Nome: ${item.name}\n")
            append("Local: ${item.file.absolutePath}\n")
            if (!item.isDir) append("Tamanho: ${item.size} bytes\n")
            append("Modificado: ${java.util.Date(item.file.lastModified())}")
        }
        val builder = themedAlert("Detalhes", msg)
        builder.setPositiveButton("OK", null)
        showThemedDialog(builder)
    }

    private fun showPasteBar(file: File) {
        selectionBar.visibility = View.GONE
        pasteBar.visibility = View.VISIBLE
        pasteLabel.text = (if (clipboardIsCut) "Mover: " else "Copiar: ") + file.name
    }

    private fun pasteHere() {
        val src = clipboardFile ?: return
        val ok = if (clipboardIsCut) FileOperations.move(src, currentDir) else FileOperations.copy(src, currentDir)
        toastResult(ok, if (clipboardIsCut) "Movido" else "Copiado", "Falha na operação")
        clipboardFile = null
        pasteBar.visibility = View.GONE
        refresh()
    }

    private fun toastResult(ok: Boolean, successMsg: String, failMsg: String) {
        Toast.makeText(this, if (ok) successMsg else failMsg, Toast.LENGTH_SHORT).show()
    }

    // ---------- diálogos com o tema do app (fundo/texto sempre legíveis) ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Monta um diálogo com título/mensagem já pintados nas cores do tema atual,
     *  em vez de depender do estilo padrão do sistema (que ficava com fundo
     *  branco e texto claro, ilegível). */
    private fun themedAlert(title: String, message: String? = null, extraView: View? = null): AlertDialog.Builder {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(6))
        }
        content.addView(TextView(this).apply {
            text = title
            setTextColor(palette.paper)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        if (message != null) {
            content.addView(TextView(this).apply {
                text = message
                setTextColor(palette.ink)
                textSize = 14.5f
                setPadding(0, dp(14), 0, 0)
            })
        }
        if (extraView != null) {
            content.addView(
                extraView,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(14)
                }
            )
        }
        return AlertDialog.Builder(this).setView(content)
    }

    private fun showThemedDialog(builder: AlertDialog.Builder) {
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(
            ThemeManager.glossyDrawable(palette.panel, dp(14).toFloat(), palette.line, dp(1))
        )
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(palette.accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(palette.inkDim)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(palette.inkDim)
    }
}
