package com.pitchforge.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager as SysAudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.util.Log
import com.pitchforge.app.domain.NoteName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SoundPool-based playback for sampled timbres plus AudioTrack-synthesized sine/square
 * (Study A's square-wave timbre).
 *
 * Latency (§3): sampled notes are preloaded into SoundPool and synth buffers are pre-
 * synthesized in memory, so playback is a single non-decoding call. SoundPool has no
 * play-start callback, so the onset timestamp is captured immediately after play()/write()
 * returns — as close to true onset as the platform exposes (documented in the README).
 *
 * A per-sample gain table normalizes loudness across acoustic samples and lets synthetic
 * vs. acoustic levels be balanced independently (§2.3).
 */
@Singleton
class AudioManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) : NotePlayer {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    private val loadedSamples = mutableMapOf<String, Int>()
    private val synthBuffers = mutableMapOf<String, ShortArray>()
    private val gainTable = mutableMapOf<String, Float>()
    private val loadedTimbres = mutableSetOf<String>()
    /** soundId -> continuation resumed once SoundPool finishes decoding that sample. */
    private val pendingLoads = ConcurrentHashMap<Int, (Boolean) -> Unit>()
    /** Samples that finished loading before a waiter was registered (race with SoundPool). */
    private val alreadyLoadedIds = ConcurrentHashMap.newKeySet<Int>()
    private val loadLock = Any()
    @Volatile private var lastSineTrack: AudioTrack? = null
    @Volatile private var lastStreamId: Int = 0
    @Volatile private var washoutTrack: AudioTrack? = null

    @Volatile var masterVolume: Float = 0.8f

    val samplesDir: File get() = File(appContext.filesDir, "samples")

    init {
        // SoundPool.load() returns before the sample is actually playable. Without waiting
        // on this callback, chaos/multi-timbre modes often play silence and fall back to
        // sine — which is why other instruments can "not work" even when selected.
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val ok = status == 0
            val waiter = synchronized(loadLock) {
                pendingLoads.remove(sampleId) ?: run {
                    if (ok) alreadyLoadedIds.add(sampleId)
                    null
                }
            }
            waiter?.invoke(ok)
        }
    }

    companion object {
        private const val TAG = "AudioManager"
        private const val MAX_STREAMS = 8
        private const val SAMPLE_RATE = 44100
        private const val SINE = "sine"
        private const val SQUARE = "square"
        /** Global scaling so synthetic tones aren't louder than acoustic samples. */
        private const val SYNTH_GAIN = 0.6f
        /** Washout level relative to master — audible mask, not ear-splitting. */
        private const val NOISE_GAIN = 0.28f
        private const val CLUSTER_GAIN = 0.22f
        /** Target RMS (vs full-scale) for acoustic sample loudness normalization. */
        private const val TARGET_SAMPLE_RMS = 0.12f
        private const val LOAD_TIMEOUT_MS = 15_000L
        private val SYNTH_TIMBRES = setOf(SINE, SQUARE)

        private fun key(timbre: String, octave: Int, note: NoteName) = "$timbre/$octave/${note.fileToken()}"

        /** Maps a note to its on-disk filename token: sharps use 's' (C# -> Cs). */
        private fun NoteName.fileToken(): String = label.replace("#", "s")

        /** A4 = 440 Hz; midi = (octave + 1) * 12 + semitone. */
        internal fun frequencyOf(note: NoteName, octave: Int): Double {
            val midi = (octave + 1) * 12 + note.semitone
            return 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        }
    }

    /** Set a per-sample gain (0..1+) used to normalize loudness. */
    fun setGain(timbre: String, octave: Int, note: NoteName, gain: Float) {
        gainTable[key(timbre, octave, note)] = gain
    }

    override fun availableTimbres(): List<String> {
        val fromDisk = samplesDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        return (listOf(SINE, SQUARE) + fromDisk).distinct()
    }

    override suspend fun ensureLoaded(timbre: String, octaves: List<Int>) = withContext(Dispatchers.IO) {
        if (timbre in loadedTimbres) return@withContext
        if (timbre in SYNTH_TIMBRES) {
            octaves.forEach { octave ->
                NoteName.entries.forEach { note ->
                    val freq = frequencyOf(note, octave)
                    synthBuffers[key(timbre, octave, note)] = when (timbre) {
                        SQUARE -> synthesizeSquare(freq)
                        else -> synthesizeSine(freq)
                    }
                }
            }
            loadedTimbres.add(timbre)
            return@withContext
        }
        copyBundledAssets(timbre)
        val timbreDir = File(samplesDir, timbre)
        val pendingIds = mutableListOf<Int>()
        octaves.forEach { octave ->
            NoteName.entries.forEach { note ->
                val sampleKey = key(timbre, octave, note)
                if (sampleKey in loadedSamples) return@forEach
                val file = File(timbreDir, "${octave}_${note.fileToken()}.wav")
                if (file.exists()) {
                    val id = soundPool.load(file.absolutePath, 1)
                    if (id != 0) {
                        loadedSamples[sampleKey] = id
                        pendingIds.add(id)
                        calibrateGainFromWav(file, timbre, octave, note)
                    }
                }
            }
        }
        // Wait until SoundPool has finished decoding every newly requested sample.
        pendingIds.forEach { id -> awaitSampleLoaded(id) }
        if (loadedSamples.keys.any { it.startsWith("$timbre/") }) {
            loadedTimbres.add(timbre)
            Log.i(TAG, "Loaded timbre '$timbre' (${pendingIds.size} samples)")
        } else {
            Log.w(TAG, "No samples found for timbre '$timbre'; will fall back to sine")
        }
    }

    private suspend fun awaitSampleLoaded(sampleId: Int) {
        val already = synchronized(loadLock) {
            if (alreadyLoadedIds.remove(sampleId)) true
            else {
                // Will be completed by the OnLoadCompleteListener.
                false
            }
        }
        if (already) return

        try {
            kotlinx.coroutines.withTimeout(LOAD_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val waiter: (Boolean) -> Unit = {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    val raced = synchronized(loadLock) {
                        if (alreadyLoadedIds.remove(sampleId)) {
                            true
                        } else {
                            pendingLoads[sampleId] = waiter
                            false
                        }
                    }
                    if (raced) {
                        if (cont.isActive) cont.resume(Unit)
                    } else {
                        cont.invokeOnCancellation { pendingLoads.remove(sampleId) }
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            pendingLoads.remove(sampleId)
            Log.w(TAG, "Timed out waiting for SoundPool sample $sampleId")
        }
    }

    private fun copyBundledAssets(timbre: String) {
        val assetFiles = runCatching { appContext.assets.list("samples/$timbre") }.getOrNull() ?: return
        if (assetFiles.isEmpty()) return
        val dir = File(samplesDir, timbre).apply { mkdirs() }
        assetFiles.forEach { name ->
            val dest = File(dir, name)
            val assetPath = "samples/$timbre/$name"
            val needsCopy = when {
                !dest.exists() -> true
                else -> {
                    val assetLen = runCatching {
                        appContext.assets.openFd(assetPath).use { it.length }
                    }.getOrNull()
                    when {
                        assetLen != null -> dest.length() != assetLen
                        // Compressed assets: rewrite legacy oversized piano samples.
                        timbre == "piano" && dest.length() > 500_000L -> true
                        else -> false
                    }
                }
            }
            if (!needsCopy) return@forEach
            appContext.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            }
        }
    }

    override fun play(timbre: String, octave: Int, note: NoteName): Long {
        stopWashout()
        val gain = (gainTable[key(timbre, octave, note)] ?: 1.0f) * masterVolume
        val sampleId = loadedSamples[key(timbre, octave, note)]
        if (sampleId != null) {
            lastStreamId = soundPool.play(sampleId, gain, gain, 1, 0, 1.0f)
            return System.currentTimeMillis()
        }
        // Synth timbre, or fallback when a sampled timbre has no file for this note.
        val synthKey = key(timbre, octave, note)
        val buffer = synthBuffers[synthKey]
            ?: synthBuffers[key(SINE, octave, note)]
            ?: synthesizeSine(frequencyOf(note, octave)).also {
                synthBuffers[key(SINE, octave, note)] = it
            }
        playPcm(buffer, gain * SYNTH_GAIN)
        return System.currentTimeMillis()
    }

    override suspend fun playNoiseWashout(octave: Int, durationMs: Int) {
        stopActiveNotes()
        val color = NoiseColor.forOctave(octave)
        val buffer = synthesizeColoredNoise(color, durationMs)
        val gain = masterVolume * NOISE_GAIN * colorGain(color)
        withContext(Dispatchers.Default) {
            playWashoutPcm(buffer, gain)
        }
        delay(durationMs.toLong())
        stopWashout()
    }

    override suspend fun playClusterWashout(octave: Int, durationMs: Int) {
        stopActiveNotes()
        val buffer = synthesizeCluster(octave, durationMs)
        val gain = masterVolume * CLUSTER_GAIN
        withContext(Dispatchers.Default) {
            playWashoutPcm(buffer, gain)
        }
        delay(durationMs.toLong())
        stopWashout()
    }

    /**
     * Measure WAV PCM RMS and set [gainTable] so quieter samples play louder (and vice versa).
     * Keeps “louder = higher” from becoming an accidental relative-pitch cue across timbres.
     */
    private fun calibrateGainFromWav(file: File, timbre: String, octave: Int, note: NoteName) {
        val rms = wavRms16le(file) ?: return
        if (rms < 1e-4f) return
        val gain = (TARGET_SAMPLE_RMS / rms).coerceIn(0.35f, 2.8f)
        setGain(timbre, octave, note, gain)
    }

    /** Reads a standard 16-bit little-endian PCM WAV and returns RMS in 0..1. */
    private fun wavRms16le(file: File): Float? = runCatching {
        FileInputStream(file).use { input ->
            val header = ByteArray(12)
            if (input.read(header) < 12) return@runCatching null
            if (String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WAVE") return@runCatching null

            var dataSize = -1
            val chunkHdr = ByteArray(8)
            while (input.read(chunkHdr) == 8) {
                val id = String(chunkHdr, 0, 4)
                val size = ByteBuffer.wrap(chunkHdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (id == "data") {
                    dataSize = size
                    break
                }
                // Skip chunk body (pad to even).
                val skip = size + (size and 1)
                var left = skip.toLong()
                while (left > 0) {
                    val n = input.skip(left)
                    if (n <= 0) break
                    left -= n
                }
            }
            if (dataSize <= 0) return@runCatching null

            // Sample up to ~1s of audio for a stable RMS without reading huge files fully.
            val maxBytes = minOf(dataSize, SAMPLE_RATE * 2) // mono 16-bit ~1s
            val bytes = ByteArray(maxBytes)
            val read = input.read(bytes)
            if (read < 4) return@runCatching null
            val samples = read / 2
            var sumSq = 0.0
            val bb = ByteBuffer.wrap(bytes, 0, samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(samples) {
                val s = bb.short.toInt() / 32768.0
                sumSq += s * s
            }
            sqrt(sumSq / samples).toFloat()
        }
    }.getOrNull()

    /** Pink/brown read quieter at the same peak — bump them so masking stays comparable. */
    private fun colorGain(color: NoiseColor): Float = when (color) {
        NoiseColor.BROWN -> 1.55f
        NoiseColor.PINK -> 1.25f
        NoiseColor.WHITE -> 1.0f
        NoiseColor.BLUE -> 0.95f
    }

    private fun stopActiveNotes() {
        val stream = lastStreamId
        if (stream != 0) {
            runCatching { soundPool.stop(stream) }
            lastStreamId = 0
        }
        runCatching { lastSineTrack?.stop() }
        runCatching { lastSineTrack?.release() }
        lastSineTrack = null
    }

    private fun stopWashout() {
        runCatching { washoutTrack?.stop() }
        runCatching { washoutTrack?.release() }
        washoutTrack = null
    }

    /**
     * Dense dissonant cluster near [octave] — scrambles pitch memory without a single
     * tonal center the learner can latch onto as a reference.
     */
    private fun synthesizeCluster(octave: Int, durationMs: Int): ShortArray {
        val total = SAMPLE_RATE * durationMs / 1000
        val fadeSamples = SAMPLE_RATE / 50
        // Four close, non-harmonic partials around the octave's mid chroma region.
        val base = frequencyOf(NoteName.F, octave.coerceIn(3, 5))
        val freqs = doubleArrayOf(
            base * 0.94,
            base * 1.06,
            base * 1.19,
            base * 1.41
        )
        val raw = FloatArray(total)
        for (i in 0 until total) {
            var sum = 0.0
            for (f in freqs) {
                sum += sin(2.0 * PI * f * i / SAMPLE_RATE)
            }
            raw[i] = (sum / freqs.size).toFloat()
        }
        var peak = 1e-4f
        for (v in raw) {
            val a = kotlin.math.abs(v)
            if (a > peak) peak = a
        }
        val norm = 0.9f / peak
        return ShortArray(total) { i ->
            val env = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > total - fadeSamples -> (total - i).toFloat() / fadeSamples
                else -> 1f
            }
            val sample = (raw[i] * norm * env).coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizeColoredNoise(color: NoiseColor, durationMs: Int): ShortArray {
        val total = SAMPLE_RATE * durationMs / 1000
        val fadeSamples = SAMPLE_RATE / 50 // 20ms fades to avoid clicks
        val rng = ThreadLocalRandom.current()
        val raw = FloatArray(total)

        when (color) {
            NoiseColor.WHITE -> {
                for (i in 0 until total) raw[i] = rng.nextFloat() * 2f - 1f
            }
            NoiseColor.PINK -> {
                // Paul Kellet-style approximate pink filter on white input.
                var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
                for (i in 0 until total) {
                    val white = rng.nextFloat() * 2f - 1f
                    b0 = 0.99886f * b0 + white * 0.0555179f
                    b1 = 0.99332f * b1 + white * 0.0750759f
                    b2 = 0.96900f * b2 + white * 0.1538520f
                    b3 = 0.86650f * b3 + white * 0.3104856f
                    b4 = 0.55000f * b4 + white * 0.5329522f
                    b5 = -0.7616f * b5 - white * 0.0168980f
                    val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
                    b6 = white * 0.115926f
                    raw[i] = pink * 0.11f
                }
            }
            NoiseColor.BROWN -> {
                // Integrated white noise with leak to avoid DC wander.
                var brown = 0f
                for (i in 0 until total) {
                    val white = rng.nextFloat() * 2f - 1f
                    brown = (brown + white * 0.02f) * 0.998f
                    raw[i] = brown * 3.5f
                }
            }
            NoiseColor.BLUE -> {
                // Differentiated white ≈ high-frequency emphasis.
                var prev = 0f
                for (i in 0 until total) {
                    val white = rng.nextFloat() * 2f - 1f
                    raw[i] = (white - prev) * 0.85f
                    prev = white
                }
            }
        }

        // Soft peak normalize so gain table stays predictable across colors.
        var peak = 1e-4f
        for (v in raw) {
            val a = kotlin.math.abs(v)
            if (a > peak) peak = a
        }
        val norm = 0.92f / peak

        return ShortArray(total) { i ->
            val env = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > total - fadeSamples -> (total - i).toFloat() / fadeSamples
                else -> 1f
            }
            val sample = (raw[i] * norm * env).coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun playWashoutPcm(buffer: ShortArray, gain: Float) {
        stopWashout()
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            buffer.size * 2,
            AudioTrack.MODE_STATIC,
            SysAudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(buffer, 0, buffer.size)
        track.setVolume(gain.coerceIn(0f, 1f))
        washoutTrack = track
        track.play()
    }

    private fun synthesizeSine(freqHz: Double, durationMs: Int = 700): ShortArray {
        val total = SAMPLE_RATE * durationMs / 1000
        val fadeSamples = SAMPLE_RATE / 100 // 10ms fade in/out to avoid clicks
        return ShortArray(total) { i ->
            val env = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > total - fadeSamples -> (total - i).toFloat() / fadeSamples
                else -> 1f
            }
            (sin(2.0 * PI * freqHz * i / SAMPLE_RATE) * env * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
    }

    /** 50% duty square wave — Study A's synthetic "square" timbre. */
    private fun synthesizeSquare(freqHz: Double, durationMs: Int = 700): ShortArray {
        val total = SAMPLE_RATE * durationMs / 1000
        val fadeSamples = SAMPLE_RATE / 100
        val period = SAMPLE_RATE / freqHz
        return ShortArray(total) { i ->
            val env = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > total - fadeSamples -> (total - i).toFloat() / fadeSamples
                else -> 1f
            }
            val phase = (i % period) / period
            val sample = if (phase < 0.5) 1.0 else -1.0
            (sample * env * Short.MAX_VALUE * 0.55).toInt().toShort()
        }
    }

    private fun playPcm(buffer: ShortArray, gain: Float) {
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            buffer.size * 2,
            AudioTrack.MODE_STATIC,
            SysAudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(buffer, 0, buffer.size)
        track.setVolume(gain.coerceIn(0f, 1f))
        // Keep at most one synthesized track alive to avoid leaking on rapid replays.
        runCatching { lastSineTrack?.stop() }
        runCatching { lastSineTrack?.release() }
        lastSineTrack = track
        track.play()
    }

    fun release() {
        stopActiveNotes()
        stopWashout()
        soundPool.release()
        loadedSamples.clear()
        synthBuffers.clear()
        loadedTimbres.clear()
    }
}
