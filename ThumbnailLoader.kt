package com.abridor.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

enum class ThumbKind { IMAGE, VIDEO, PDF, AUDIO }

/** Gera miniaturas (fotos, quadro do vídeo, 1ª página de PDF, capa do áudio)
 *  em segundo plano, com cache em memória, sem travar a rolagem da lista. */
object ThumbnailLoader {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxMemoryKb / 8) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Pede a miniatura de [file]. [target] é usado só como "crachá" pra saber se a
     *  linha ainda é a mesma quando o resultado chegar (RecyclerView recicla views). */
    fun load(file: File, kind: ThumbKind, sizePx: Int, target: ImageView, onLoaded: (Bitmap?) -> Unit) {
        val key = "${file.absolutePath}:${file.lastModified()}:$sizePx:$kind"
        val cached = cache.get(key)
        if (cached != null) {
            onLoaded(cached)
            return
        }
        target.tag = key
        executor.execute {
            val bmp = try {
                when (kind) {
                    ThumbKind.IMAGE -> decodeImage(file, sizePx)
                    ThumbKind.VIDEO -> decodeVideo(file, sizePx)
                    ThumbKind.PDF -> decodePdf(file, sizePx)
                    ThumbKind.AUDIO -> decodeAudioArt(file, sizePx)
                }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) cache.put(key, bmp)
            mainHandler.post {
                if (target.tag == key) onLoaded(bmp)
            }
        }
    }

    private fun decodeImage(file: File, sizePx: Int): Bitmap? {
        val path = file.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > sizePx * 2 || bounds.outHeight / sample > sizePx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        bmp = rotateForExif(path, bmp)
        return ThumbnailUtils.extractThumbnail(bmp, sizePx, sizePx)
    }

    private fun rotateForExif(path: String, bmp: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) bmp else {
                val m = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        } catch (e: Exception) {
            bmp
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeVideo(file: File, sizePx: Int): Bitmap? {
        val frame = try {
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
        } catch (e: Exception) {
            null
        } ?: return null
        val cropped = ThumbnailUtils.extractThumbnail(frame, sizePx, sizePx) ?: return null
        return drawPlayBadge(cropped)
    }

    /** Desenha um "▶" translúcido em cima do quadro do vídeo, pra diferenciar de foto. */
    private fun drawPlayBadge(bmp: Bitmap): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val cx = out.width / 2f
        val cy = out.height / 2f
        val r = out.width.coerceAtMost(out.height) * 0.26f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawCircle(cx, cy, r, circlePaint)
        val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val path = Path().apply {
            moveTo(cx - r * 0.42f, cy - r * 0.55f)
            lineTo(cx - r * 0.42f, cy + r * 0.55f)
            lineTo(cx + r * 0.6f, cy)
            close()
        }
        canvas.drawPath(path, trianglePaint)
        return out
    }

    private fun decodePdf(file: File, sizePx: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null
            val page = renderer.openPage(0)
            val scale = sizePx.toFloat() / page.width.coerceAtLeast(1)
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            ThumbnailUtils.extractThumbnail(bmp, sizePx, sizePx)
        } catch (e: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
        }
    }

    private fun decodeAudioArt(file: File, sizePx: Int): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            retriever.release()
            if (art == null) null else {
                val bmp = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return null
                ThumbnailUtils.extractThumbnail(bmp, sizePx, sizePx)
            }
        } catch (e: Exception) {
            null
        }
    }
}
