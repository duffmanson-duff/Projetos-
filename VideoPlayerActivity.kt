package com.abridor.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_video_player)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val file = File(path)

        val videoView = findViewById<VideoView>(R.id.videoView)
        val loadingLabel = findViewById<TextView>(R.id.loadingLabel)
        loadingLabel.setTextColor(ThemeManager.current(this).accent)

        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)

        videoView.setOnPreparedListener {
            loadingLabel.visibility = View.GONE
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Não foi possível tocar este vídeo — o codec pode não ser suportado pelo aparelho.", Toast.LENGTH_LONG).show()
            finish()
            true
        }

        try {
            videoView.setVideoURI(Uri.fromFile(file))
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir este vídeo.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
    }
}
