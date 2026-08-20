package com.abridor.app

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build

/**
 * Encapsula os efeitos de áudio nativos do Android: Equalizador, Reforço de
 * graves (BassBoost), efeito surround (Virtualizer), realce de volume
 * (LoudnessEnhancer) e limitador anti-distorção. Todos ligados à sessão de
 * áudio do MediaPlayer que está tocando.
 *
 * O equalizador em si usa duas estratégias dependendo do aparelho:
 * - Android 9+ : EQ customizado de 8 bandas via DynamicsProcessing (mais fino
 *   que o equalizador nativo, que a maioria dos aparelhos limita a 5 bandas)
 * - Android mais antigo: cai para o Equalizer nativo do aparelho (a
 *   quantidade de bandas nesse caso é decidida pelo fabricante do celular)
 *
 * A equalização (bandas, graves, surround, volume, limitador e balanço) pode
 * ser salva com [saveCurrentSettings] e volta a ser aplicada automaticamente
 * na próxima música tocada (carregada sozinha aqui no início).
 */
class EqualizerController(context: Context, private val audioSessionId: Int) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // últimos valores aplicados (usados pra "SALVAR" saber o que persistir)
    private var currentBass: Short = 0
    private var currentVirt: Short = 0
    private var currentLoudDb: Float = 0f
    private var currentLimiterEnabled: Boolean = false

    val bassBoost: BassBoost? = try {
        BassBoost(0, audioSessionId).apply { enabled = true }
    } catch (e: Exception) { null }

    val virtualizer: Virtualizer? = try {
        Virtualizer(0, audioSessionId).apply { enabled = true }
    } catch (e: Exception) { null }

    val loudnessEnhancer: LoudnessEnhancer? = try {
        LoudnessEnhancer(audioSessionId).apply { enabled = true }
    } catch (e: Exception) { null }

    // ---- equalizador de 8 bandas (custom) ----
    private val customFreqs = intArrayOf(60, 150, 400, 1000, 2400, 6000, 12000, 16000)
    private val customLevels = ShortArray(customFreqs.size) // em centésimos de dB (millibel)
    private var dp: DynamicsProcessing? = null

    // ---- fallback: equalizador nativo do aparelho ----
    private var nativeEq: Equalizer? = null

    val limiterSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val usingCustomEq: Boolean get() = dp != null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                setupCustomEq()
            } catch (e: Exception) {
                dp = null
            }
        }
        if (dp == null) {
            // aparelho antigo ou não suportou o EQ customizado: usa o nativo
            nativeEq = Equalizer(0, audioSessionId).apply { enabled = true }
        }
        loadSavedSettings()
    }

    /** Aplica de volta a equalização salva (se o usuário já tiver salvado uma antes). */
    private fun loadSavedSettings() {
        val savedBands = prefs.getString(KEY_BANDS, null)
        if (savedBands != null) {
            val levels = savedBands.split(",").mapNotNull { it.toIntOrNull() }
            for (i in levels.indices) {
                if (i < bandCount) setBandLevel(i, levels[i].toShort())
            }
        }
        if (prefs.contains(KEY_BASS)) setBassBoost(prefs.getInt(KEY_BASS, 0).toShort())
        if (prefs.contains(KEY_VIRT)) setVirtualizer(prefs.getInt(KEY_VIRT, 0).toShort())
        if (prefs.contains(KEY_LOUD)) setLoudnessGainDb(prefs.getFloat(KEY_LOUD, 0f))
        if (prefs.contains(KEY_LIMITER)) setLimiterEnabled(prefs.getBoolean(KEY_LIMITER, false))
    }

    /** Salva a equalização atual (bandas, graves, surround, volume, limitador e
     *  balanço) pra ela voltar a ser usada automaticamente nas próximas músicas. */
    fun saveCurrentSettings(balancePan: Float) {
        val levels = (0 until bandCount).joinToString(",") { getBandLevel(it).toInt().toString() }
        prefs.edit()
            .putString(KEY_BANDS, levels)
            .putInt(KEY_BASS, currentBass.toInt())
            .putInt(KEY_VIRT, currentVirt.toInt())
            .putFloat(KEY_LOUD, currentLoudDb)
            .putBoolean(KEY_LIMITER, currentLimiterEnabled)
            .putFloat(KEY_BALANCE, balancePan)
            .apply()
    }

    fun hasSavedSettings(): Boolean = prefs.contains(KEY_BANDS)

    // usados pela tela do equalizador pra mostrar os sliders já na posição certa
    // (o valor pode já ter sido carregado de uma equalização salva, no init)
    fun currentBassStrength(): Short = currentBass
    fun currentVirtStrength(): Short = currentVirt
    fun currentLoudnessDb(): Float = currentLoudDb
    fun isLimiterEnabled(): Boolean = currentLimiterEnabled

    fun savedBalance(): Float = prefs.getFloat(KEY_BALANCE, 0f)

    companion object {
        private const val PREFS = "abridor_eq_prefs"
        private const val KEY_BANDS = "bands"
        private const val KEY_BASS = "bass"
        private const val KEY_VIRT = "virt"
        private const val KEY_LOUD = "loud"
        private const val KEY_LIMITER = "limiter"
        private const val KEY_BALANCE = "balance"
    }

    private fun setupCustomEq() {
        val bandCount = customFreqs.size
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // channelCount (estéreo, o caso comum)
            false, 0, // pré-EQ: não usa
            false, 0, // compressor multibanda: não usa
            true, bandCount, // pós-EQ: 8 bandas
            true // limiter embutido
        ).build()
        val processing = DynamicsProcessing(0, audioSessionId, config)

        val eq = DynamicsProcessing.Eq(true, true, bandCount)
        for (i in 0 until bandCount) {
            eq.setBand(i, DynamicsProcessing.EqBand(true, customFreqs[i].toFloat(), 0f))
        }
        processing.setPostEqAllChannelsTo(eq)
        processing.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, false, 0, 3f, 50f, 20f, -1f, 0f))
        processing.enabled = true
        dp = processing
    }

    // ---- API unificada usada pela tela do equalizador ----

    val bandCount: Int get() = if (dp != null) customFreqs.size else (nativeEq?.numberOfBands?.toInt() ?: 0)
    val minLevel: Short get() = if (dp != null) (-1500).toShort() else (nativeEq?.bandLevelRange?.get(0) ?: (-1500).toShort())
    val maxLevel: Short get() = if (dp != null) 1500.toShort() else (nativeEq?.bandLevelRange?.get(1) ?: 1500.toShort())

    fun bandFreqHz(band: Int): Int =
        if (dp != null) customFreqs[band] else (nativeEq?.getCenterFreq(band.toShort())?.div(1000) ?: 0)

    fun getBandLevel(band: Int): Short =
        if (dp != null) customLevels[band] else (nativeEq?.getBandLevel(band.toShort()) ?: 0.toShort())

    fun setBandLevel(band: Int, level: Short) {
        if (dp != null) {
            customLevels[band] = level
            val gainDb = level / 100f
            try {
                dp?.setPostEqBandAllChannelsTo(band, DynamicsProcessing.EqBand(true, customFreqs[band].toFloat(), gainDb))
            } catch (e: Exception) { }
        } else {
            nativeEq?.setBandLevel(band.toShort(), level)
        }
    }

    fun presetNames(): List<String> =
        if (dp != null) CustomPresets.NAMES
        else (nativeEq?.let { eqz -> (0 until eqz.numberOfPresets).map { eqz.getPresetName(it.toShort()) } } ?: emptyList())

    fun usePreset(index: Int) {
        if (dp != null) {
            val gains = CustomPresets.GAINS_DB[index]
            for (b in gains.indices) {
                setBandLevel(b, (gains[b] * 100).toInt().toShort())
            }
        } else {
            nativeEq?.usePreset(index.toShort())
        }
    }

    fun setBassBoost(strength: Short) {
        currentBass = strength
        bassBoost?.setStrength(strength)
    }

    fun setVirtualizer(strength: Short) {
        currentVirt = strength
        virtualizer?.setStrength(strength)
    }

    /** Ganho extra de volume em dB (0 a 20 aproximadamente é seguro sem distorcer muito). */
    fun setLoudnessGainDb(db: Float) {
        currentLoudDb = db
        loudnessEnhancer?.setTargetGain((db * 100).toInt())
    }

    /** Liga/desliga o limitador que evita que o som estoure (clipping) quando os
     *  outros efeitos (graves, ganho) estão altos. Só existe a partir do Android 9. */
    fun setLimiterEnabled(enabled: Boolean): Boolean {
        currentLimiterEnabled = enabled
        val processing = dp ?: return false
        return try {
            processing.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, enabled, 0, 3f, 50f, 20f, -1f, 0f))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun reset() {
        for (b in 0 until bandCount) {
            setBandLevel(b, 0.toShort())
        }
        bassBoost?.setStrength(0.toShort())
        virtualizer?.setStrength(0.toShort())
        loudnessEnhancer?.setTargetGain(0)
        setLimiterEnabled(false)
    }

    fun release() {
        nativeEq?.release()
        bassBoost?.release()
        virtualizer?.release()
        loudnessEnhancer?.release()
        dp?.release()
    }
}

/** Curvas de preset prontas para o EQ customizado de 8 bandas (em dB),
 *  nas frequências: 60, 150, 400, 1000, 2400, 6000, 12000, 16000 Hz. */
private object CustomPresets {
    val NAMES = listOf("Normal", "Rock", "Pop", "Jazz", "Clássica", "Dance", "Grave+", "Agudo+", "Voz")
    val GAINS_DB = listOf(
        floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        floatArrayOf(4f, 3f, -2f, -4f, -2f, 2f, 4f, 5f),
        floatArrayOf(-1f, 2f, 4f, 3f, 0f, -1f, -1f, -1f),
        floatArrayOf(3f, 2f, 0f, 1f, 2f, 3f, 3f, 2f),
        floatArrayOf(3f, 2f, 0f, 0f, 0f, -2f, -2f, -3f),
        floatArrayOf(5f, 3f, 0f, -2f, -2f, 0f, 3f, 4f),
        floatArrayOf(7f, 6f, 3f, 0f, 0f, 0f, 0f, 0f),
        floatArrayOf(0f, 0f, 0f, 0f, 2f, 4f, 6f, 7f),
        floatArrayOf(-3f, -2f, 1f, 4f, 4f, 2f, -1f, -2f)
    )
}


