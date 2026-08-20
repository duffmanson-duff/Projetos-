package com.abridor.app

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

class FileAdapter(
    private var items: List<FileItem>,
    private val palette: Palette,
    private val onClick: (FileItem) -> Unit,
    private val onMore: (FileItem, View) -> Unit,
    private val onLongPress: (FileItem) -> Unit,
    private val isSelected: (FileItem) -> Boolean,
    private val isSelectionMode: () -> Boolean
) : RecyclerView.Adapter<FileAdapter.VH>() {

    // ícones 3D gerados uma única vez e reaproveitados em todas as linhas (mais leve)
    private var folderBitmap: Bitmap? = null
    private var zipBitmap: Bitmap? = null
    private var thumbSizePx: Int = 0

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val badge: TextView = view.findViewById(R.id.badge)
        val folderIcon: ImageView = view.findViewById(R.id.folderIcon)
        val thumb: ImageView = view.findViewById(R.id.thumbImage)
        val name: TextView = view.findViewById(R.id.fileName)
        val meta: TextView = view.findViewById(R.id.fileMeta)
        val more: TextView = view.findViewById(R.id.btnMore)
        val check: CheckBox = view.findViewById(R.id.checkSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        v.findViewById<TextView>(R.id.badge).background = ThemeManager.glossyDrawable(palette.card, dp(parent, 4).toFloat(), palette.accentDim, dp(parent, 1))
        v.findViewById<TextView>(R.id.badge).setTextColor(palette.accent)
        v.findViewById<TextView>(R.id.fileName).setTextColor(palette.paper)
        v.findViewById<TextView>(R.id.fileMeta).setTextColor(palette.inkDim)
        v.findViewById<TextView>(R.id.btnMore).setTextColor(palette.inkDim)
        v.findViewById<CheckBox>(R.id.checkSelect).buttonTintList = ColorStateList.valueOf(palette.accent)
        v.findViewById<ImageView>(R.id.thumbImage).setBackgroundColor(palette.card)

        if (folderBitmap == null) {
            val iconW = dp(parent, 46)
            val iconH = dp(parent, 38)
            folderBitmap = IconDrawer.folderBitmap(iconW, iconH, palette.accent)
            zipBitmap = IconDrawer.zipBitmap(iconW, iconH, palette.accentDim)
            thumbSizePx = dp(parent, 46)
        }
        return VH(v)
    }

    private fun dp(view: ViewGroup, value: Int) = (value * view.resources.displayMetrics.density).toInt()

    private fun thumbKindFor(item: FileItem): ThumbKind? = when {
        item.ext in MediaTypes.IMAGE_EXT -> ThumbKind.IMAGE
        item.ext in MediaTypes.VIDEO_EXT -> ThumbKind.VIDEO
        item.ext == "pdf" -> ThumbKind.PDF
        item.ext in MediaTypes.AUDIO_EXT -> ThumbKind.AUDIO
        else -> null
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name

        // limpa o "crachá" de miniatura pendente antes de decidir o que mostrar nesta linha
        // (evita que um carregamento antigo, ainda em andamento, pinte a linha errada quando reciclada)
        holder.thumb.tag = null
        holder.thumb.setImageDrawable(null)

        val kind = thumbKindFor(item)

        when {
            item.isDir -> {
                holder.badge.visibility = View.GONE
                holder.thumb.visibility = View.GONE
                holder.folderIcon.visibility = View.VISIBLE
                holder.folderIcon.setImageBitmap(folderBitmap)
                val count = item.file.listFiles()?.size ?: 0
                holder.meta.text = "$count item(ns)"
            }
            item.ext == "zip" -> {
                holder.badge.visibility = View.GONE
                holder.thumb.visibility = View.GONE
                holder.folderIcon.visibility = View.VISIBLE
                holder.folderIcon.setImageBitmap(zipBitmap)
                holder.meta.text = humanSize(item.size)
            }
            kind != null -> {
                holder.folderIcon.visibility = View.GONE
                holder.thumb.visibility = View.GONE
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = if (item.ext.isNotEmpty()) item.ext.take(4).uppercase() else "???"
                holder.meta.text = humanSize(item.size)
                ThumbnailLoader.load(item.file, kind, thumbSizePx, holder.thumb) { bmp ->
                    if (bmp != null) {
                        holder.badge.visibility = View.GONE
                        holder.thumb.visibility = View.VISIBLE
                        holder.thumb.setImageBitmap(bmp)
                    }
                }
            }
            else -> {
                holder.folderIcon.visibility = View.GONE
                holder.thumb.visibility = View.GONE
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = if (item.ext.isNotEmpty()) item.ext.take(4).uppercase() else "???"
                holder.meta.text = humanSize(item.size)
            }
        }

        val selMode = isSelectionMode()
        val selected = selMode && isSelected(item)

        holder.check.visibility = if (selMode) View.VISIBLE else View.GONE
        holder.check.isChecked = selected
        holder.more.visibility = if (selMode) View.GONE else View.VISIBLE
        holder.itemView.setBackgroundColor(if (selected) palette.card else Color.TRANSPARENT)

        holder.itemView.setOnClickListener { onClick(item) }
        holder.more.setOnClickListener { onMore(item, holder.more) }
        holder.itemView.setOnLongClickListener { onLongPress(item); true }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<FileItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val df = DecimalFormat("#.#")
        if (bytes < 1024 * 1024) return "${df.format(bytes / 1024.0)} KB"
        return "${df.format(bytes / (1024.0 * 1024.0))} MB"
    }
}
