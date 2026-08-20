package com.abridor.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

class AudioPlayerActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var equalizer: EqualizerController? = null
    private lateinit var visualizerView: VisualizerView
    private lateinit var btnPlayPause: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var balancePan = 0f

    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    seekBar.progress = it.currentPosition
                    timeCurrent.text = formatTime(it.currentPosition)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)
        val palette = ThemeManager.current(this)
        setSupportActionBar(findViewById(R.id.toolbar))

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val file = File(path)

        findViewById<TextView>(R.id.trackName).text = file.name
        visualizerView = findViewById(R.id.visualizer)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        seekBar = findViewById(R.id.seekBar)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)

        applyTheme(palette)
        visualizerView.accentColor = palette.accent
        visualizerView.paperColor = palette.paper
        visualizerView.cardColor = palette.card
        visualizerView.lineColor = palette.line

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, Uri.fromFile(file))
                prepare()
                setOnCompletionListener {
                    this@AudioPlayerActivity.isPlaying = false
                    btnPlayPause.text = "▶"
                }
            }
            seekBar.max = mediaPlayer!!.duration
            timeTotal.text = formatTime(mediaPlayer!!.duration)
            requestVisualizerPermissionThenSetup()
            equalizer = try { EqualizerController(this, mediaPlayer!!.audioSessionId) } catch (e: Exception) { null }
            // aplica de volta o balanço estéreo salvo (o resto da equalização já é
            // aplicado sozinho dentro do EqualizerController)
            balancePan = equalizer?.savedBalance() ?: 0f
            applyBalance(balancePan)
            play()
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível tocar este arquivo de áudio.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        btnPlayPause.setOnClickListener { togglePlay() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun applyTheme(p: Palette) {
        findViewById<View>(R.id.rootLayout).setBackgroundColor(p.bg)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setBackgroundColor(p.bg)
        findViewById<TextView>(R.id.trackName).setTextColor(p.paper)
        seekBar.progressTintList = android.content.res.ColorStateList.valueOf(p.accent)
        seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(p.accent)
        timeCurrent.setTextColor(p.inkDim)
        timeTotal.setTextColor(p.inkDim)
        btnPlayPause.background = ThemeManager.glossyOval(p.accent)
        btnPlayPause.setTextColor(p.onAccent)
    }

    private fun requestVisualizerPermissionThenSetup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            setupVisualizer()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupVisualizer()
        }
        // se negado, a música continua tocando normalmente, só sem o efeito visual
    }

    private fun setupVisualizer() {
        try {
            visualizer = Visualizer(mediaPlayer!!.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft?.let { visualizerView.updateFft(it) }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            // Alguns aparelhos restringem o Visualizer; o áudio continua tocando normalmente sem o efeito.
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "EQ")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            val eq = equalizer
            if (eq != null) {
                EqualizerDialog.show(this, eq, ThemeManager.current(this), balancePan) { left, right ->
                    balancePan = if (left < 1f) (1f - left) else if (right < 1f) (right - 1f) else 0f
                    mediaPlayer?.setVolume(left, right)
                }
            } else {
                Toast.makeText(this, "Equalizador não disponível neste aparelho.", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private fun applyBalance(pan: Float) {
        val left = if (pan > 0) 1f - pan else 1f
        val right = if (pan < 0) 1f + pan else 1f
        mediaPlayer?.setVolume(left, right)
    }

    private fun play() {
        mediaPlayer?.start()
        isPlaying = true
        btnPlayPause.text = "❚❚"
        handler.post(progressRunnable)
    }

    private fun togglePlay() {
        val mp = mediaPlayer ?: return
        if (isPlaying) {
            mp.pause()
            btnPlayPause.text = "▶"
        } else {
            mp.start()
            btnPlayPause.text = "❚❚"
        }
        isPlaying = !isPlaying
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
        visualizer?.release()
        equalizer?.release()
        mediaPlayer?.release()
    }

    companion object {
        const val EXTRA_PATH = "extra_path"
    }
}
