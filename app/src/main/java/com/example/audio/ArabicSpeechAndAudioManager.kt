package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ArabicSpeechAndAudioManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isSpeakingTts = MutableStateFlow(false)
    val isSpeakingTts: StateFlow<Boolean> = _isSpeakingTts.asStateFlow()

    private val _waveBars = MutableStateFlow(List(20) { 0.08f })
    val waveBars: StateFlow<List<Float>> = _waveBars.asStateFlow()

    private val _volumeScore = MutableStateFlow(75)
    val volumeScore: StateFlow<Int> = _volumeScore.asStateFlow()

    private var currentRecognitionCallback: ((heardText: String, confidence: Float, errorMsg: String?) -> Unit)? = null
    private var currentTtsDoneCallback: (() -> Unit)? = null

    private var audioRecordJob: Job? = null
    private var lastRecordedWavPath: String? = null

    private val pcmBufferStream = ByteArrayOutputStream()
    private var pcmBufferFlushed = false
    private var currentRecordingPrefix: String = "rec"
    private var speechStartTimeMs: Long = 0L
    private var lastSpeechDurationSec: Float = 0f
    private val liveRmsHistory = mutableListOf<Float>()

    var hapticEnabled: Boolean = true
    var ttsSpeechRate: Float = 1.0f

    val recordingsDir: File by lazy {
        File(context.filesDir, "recordings").apply { mkdirs() }
    }

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val arSa = Locale("ar", "SA")
                val arGen = Locale("ar")
                val res = tts?.setLanguage(arSa)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(arGen)
                }
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeakingTts.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeakingTts.value = false
                        mainHandler.post {
                            currentTtsDoneCallback?.invoke()
                            currentTtsDoneCallback = null
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeakingTts.value = false
                        mainHandler.post {
                            currentTtsDoneCallback?.invoke()
                            currentTtsDoneCallback = null
                        }
                    }
                })
            }
        }
    }

    fun playBeep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
            mainHandler.postDelayed({ tg.release() }, 250)
        } catch (_: Exception) {
        }
    }

    fun playPraiseTone() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 260)
            mainHandler.postDelayed({ tg.release() }, 350)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun vibrateHaptic(type: String = "start") {
        if (!hapticEnabled) return
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    "success" -> VibrationEffect.createWaveform(longArrayOf(0, 45, 60, 90), -1)
                    "warning" -> VibrationEffect.createWaveform(longArrayOf(0, 75, 50, 75), -1)
                    else -> VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    "success" -> vibrator.vibrate(longArrayOf(0, 45, 60, 90), -1)
                    "warning" -> vibrator.vibrate(longArrayOf(0, 75, 50, 75), -1)
                    else -> vibrator.vibrate(35L)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Kelimeye, cümleye veya diyaloğa atanmış gerçek ses dosyası (.mp3 / .wav)
     * filesDir/custom_media/ veya assets/categories/ altında mevcutsa MediaPlayer ile çalar.
     * Dosya bulunamazsa false döndürür ve speakArabic otomatik olarak Android TTS motoruna geçer.
     */
    fun playCustomOrAssetAudioIfAvailable(
        audioPath: String?,
        itemId: String? = null,
        paketId: String? = null,
        voiceType: String = "male",
        slow: Boolean = false,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        val cleanPath = audioPath?.trim()?.replace("\\", "/")?.trimStart('/') ?: ""
        val cleanId = itemId?.trim()?.removePrefix("w_") ?: ""
        val vSuffix = if (voiceType == "female") "female" else "male"
        val vShort = if (voiceType == "female") "f" else "m"
        val exts = listOf("mp3", "wav", "m4a", "ogg")
        val effectiveRate = (if (slow) min(ttsSpeechRate, 0.75f) else ttsSpeechRate).coerceIn(0.5f, 2.0f)

        // 1. Yerel Dosya Sistemi (filesDir / custom_media) adayları
        val localCandidates = mutableListOf<File>()
        if (cleanPath.isNotEmpty()) {
            val direct = File(cleanPath)
            if (direct.isAbsolute) localCandidates.add(direct)
            localCandidates.add(File(context.filesDir, cleanPath))
            localCandidates.add(File(context.filesDir, "custom_media/$cleanPath"))
            localCandidates.add(File(context.filesDir, "custom_media/audio/${File(cleanPath).name}"))
            if (!paketId.isNullOrBlank()) {
                localCandidates.add(File(context.filesDir, "custom_media/categories/$paketId/${File(cleanPath).name}"))
            }
        }
        if (cleanId.isNotEmpty()) {
            for (ext in exts) {
                if (!paketId.isNullOrBlank()) {
                    localCandidates.add(File(context.filesDir, "custom_media/categories/$paketId/${cleanId}_${vSuffix}.$ext"))
                    localCandidates.add(File(context.filesDir, "custom_media/categories/$paketId/${cleanId}_${vShort}.$ext"))
                    localCandidates.add(File(context.filesDir, "custom_media/categories/$paketId/$cleanId.$ext"))
                }
                localCandidates.add(File(context.filesDir, "custom_media/audio/${cleanId}_${vSuffix}.$ext"))
                localCandidates.add(File(context.filesDir, "custom_media/audio/$cleanId.$ext"))
            }
        }

        for (file in localCandidates.distinct()) {
            if (file.exists() && file.isFile && file.length() > 64) {
                try {
                    _isSpeakingTts.value = true
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(FileInputStream(file).fd)
                        setOnCompletionListener { mp ->
                            _isSpeakingTts.value = false
                            try { mp.release() } catch (_: Exception) {}
                            mainHandler.post { onComplete?.invoke() }
                        }
                        setOnErrorListener { mp, _, _ ->
                            _isSpeakingTts.value = false
                            try { mp.release() } catch (_: Exception) {}
                            mainHandler.post { onComplete?.invoke() }
                            true
                        }
                        prepare()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && kotlin.math.abs(effectiveRate - 1.0f) > 0.03f) {
                            try {
                                playbackParams = playbackParams.setSpeed(effectiveRate)
                            } catch (_: Exception) {
                            }
                        }
                        start()
                    }
                    return true
                } catch (_: Exception) {
                    _isSpeakingTts.value = false
                }
            }
        }

        // 2. APK İçi Assets (assets/ ve assets/categories/...) adayları
        val assetCandidates = mutableListOf<String>()
        if (cleanPath.isNotEmpty()) {
            assetCandidates.add(cleanPath)
            if (!cleanPath.startsWith("categories/")) {
                assetCandidates.add("categories/$cleanPath")
            }
            if (!paketId.isNullOrBlank()) {
                assetCandidates.add("categories/$paketId/${File(cleanPath).name}")
            }
        }
        if (cleanId.isNotEmpty()) {
            for (ext in exts) {
                if (!paketId.isNullOrBlank()) {
                    assetCandidates.add("categories/$paketId/${cleanId}_${vSuffix}.$ext")
                    assetCandidates.add("categories/$paketId/${cleanId}_${vShort}.$ext")
                    assetCandidates.add("categories/$paketId/$cleanId.$ext")
                }
                assetCandidates.add("categories/$cleanId.$ext")
            }
        }

        for (assetPath in assetCandidates.distinct()) {
            try {
                val afd = context.assets.openFd(assetPath)
                _isSpeakingTts.value = true
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    setOnCompletionListener { mp ->
                        _isSpeakingTts.value = false
                        try { mp.release() } catch (_: Exception) {}
                        mainHandler.post { onComplete?.invoke() }
                    }
                    setOnErrorListener { mp, _, _ ->
                        _isSpeakingTts.value = false
                        try { mp.release() } catch (_: Exception) {}
                        mainHandler.post { onComplete?.invoke() }
                        true
                    }
                    prepare()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && kotlin.math.abs(effectiveRate - 1.0f) > 0.03f) {
                        try {
                            playbackParams = playbackParams.setSpeed(effectiveRate)
                        } catch (_: Exception) {
                        }
                    }
                    start()
                }
                return true
            } catch (_: Exception) {
                _isSpeakingTts.value = false
            }
        }

        return false
    }

    fun speakArabic(
        text: String,
        voiceType: String = "male", // "female" or "male"
        slow: Boolean = false,
        repeatCount: Int = 1,
        audioPath: String? = null,
        itemId: String? = null,
        paketId: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stopAllAudio()
        if (!audioPath.isNullOrBlank() || !itemId.isNullOrBlank()) {
            val playedRealFile = playCustomOrAssetAudioIfAvailable(
                audioPath = audioPath,
                itemId = itemId,
                paketId = paketId,
                voiceType = voiceType,
                slow = slow,
                onComplete = onComplete
            )
            if (playedRealFile) return
        }
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }
        val pitch = if (voiceType == "female") 1.16f else 0.94f
        val rate = if (slow) min(ttsSpeechRate, 0.75f) else ttsSpeechRate
        try {
            val arSa = Locale("ar", "SA")
            val res = tts?.setLanguage(arSa)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("ar"))
            }
        } catch (_: Exception) {
        }
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)

        currentTtsDoneCallback = onComplete
        _isSpeakingTts.value = true

        val cleanText = text.split("/").firstOrNull()?.trim() ?: text
        val fullSpeech = List(max(1, min(3, repeatCount))) { cleanText }.joinToString(" ، ")

        if (ttsReady && tts != null) {
            val utteranceId = "utt_${System.currentTimeMillis()}"
            tts?.speak(fullSpeech, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            // Fallback simulated duration if TTS engine is still initializing on emulator
            scope.launch {
                val estMs = max(1100L, cleanText.length * 90L)
                delay(estMs)
                _isSpeakingTts.value = false
                onComplete?.invoke()
            }
        }
    }

    fun speakTurkish(
        text: String,
        slow: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        stopAllAudio()
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            onComplete?.invoke()
            return
        }
        currentTtsDoneCallback = onComplete
        _isSpeakingTts.value = true

        if (ttsReady && tts != null) {
            try {
                val trLocale = Locale("tr", "TR")
                val res = tts?.setLanguage(trLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(if (slow) 0.85f else ttsSpeechRate)
                val utteranceId = "utt_tr_${System.currentTimeMillis()}"
                tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } catch (_: Exception) {
                _isSpeakingTts.value = false
                onComplete?.invoke()
            }
        } else {
            scope.launch {
                val estMs = max(1000L, cleanText.length * 75L)
                delay(estMs)
                _isSpeakingTts.value = false
                onComplete?.invoke()
            }
        }
    }

    fun startListening(
        itemIdPrefix: String = "rec",
        onResult: (heardText: String, confidence: Float, errorMsg: String?) -> Unit
    ) = startSpeechRecognition(itemIdPrefix, onResult)

    fun startContinuousEvaluation(
        itemIdPrefix: String = "rec",
        onResult: (heardText: String, confidence: Float, errorMsg: String?) -> Unit
    ) = startSpeechRecognition(itemIdPrefix, onResult)

    @Synchronized
    private fun flushAccumulatedPcmToWav(itemIdPrefix: String) {
        if (!pcmBufferFlushed && pcmBufferStream.size() > 0) {
            pcmBufferFlushed = true
            val allBytes = pcmBufferStream.toByteArray()
            pcmBufferStream.reset()
            saveRawPcmToWav(allBytes, itemIdPrefix)
        }
    }

    fun getLastSpeechDurationSec(): Float = lastSpeechDurationSec

    fun getLastSpeechEnvelope48(): List<Float> {
        if (liveRmsHistory.isEmpty()) return List(48) { 0.12f }
        if (liveRmsHistory.size == 48) return liveRmsHistory.toList()
        return List(48) { idx ->
            val srcIdx = ((idx.toFloat() / 48f) * liveRmsHistory.size).toInt().coerceIn(0, liveRmsHistory.size - 1)
            liveRmsHistory[srcIdx]
        }
    }

    fun startSpeechRecognition(
        itemIdPrefix: String = "rec",
        onResult: (heardText: String, confidence: Float, errorMsg: String?) -> Unit
    ) {
        // Hoparlör yankısını mikrofonun kendi sesi sanmaması için önce tüm sesleri derhal sustur
        stopSpeaking()
        mainHandler.removeCallbacksAndMessages(null)
        playBeep()
        vibrateHaptic("start")
        currentRecognitionCallback = onResult
        currentRecordingPrefix = itemIdPrefix
        pcmBufferStream.reset()
        pcmBufferFlushed = false
        liveRmsHistory.clear()
        speechStartTimeMs = System.currentTimeMillis()
        _isRecording.value = true
        _waveBars.value = List(20) { 0.1f }

        // Hoparlör yankısının sönümlenmesi için 150ms bekle ve mikrofonu öyle aç
        mainHandler.postDelayed({
            if (!_isRecording.value) return@postDelayed
            try {
                if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                val recognizer = speechRecognizer
                if (recognizer == null) {
                    _isRecording.value = false
                    onResult("", 0f, "Cihazda ses tanıma servisi bulunamadı.")
                    return@postDelayed
                }

                speechStartTimeMs = System.currentTimeMillis()
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        speechStartTimeMs = System.currentTimeMillis()
                    }
                    override fun onBeginningOfSpeech() {
                        speechStartTimeMs = System.currentTimeMillis()
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0.06f, 1.0f)
                        liveRmsHistory.add(norm)
                        val updated = _waveBars.value.drop(1) + norm
                        _waveBars.value = updated
                        _volumeScore.value = (norm * 100).roundToInt().coerceIn(45, 100)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        if (buffer != null && buffer.isNotEmpty()) {
                            pcmBufferStream.write(buffer, 0, buffer.size)
                        }
                    }

                    override fun onEndOfSpeech() {
                        _isRecording.value = false
                        val elapsed = ((System.currentTimeMillis() - speechStartTimeMs) / 100f).roundToInt() / 10f
                        lastSpeechDurationSec = elapsed.coerceAtLeast(0.6f)
                        flushAccumulatedPcmToWav(itemIdPrefix)
                    }

                    override fun onError(error: Int) {
                        _isRecording.value = false
                        flushAccumulatedPcmToWav(itemIdPrefix)
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Sesiniz net algılanamadı, lütfen mikrofona yakın ve net konuşun."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Konuşma sesi duyulamadı."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni verilmedi."
                            else -> "Ses tanıma tamamlanamadı (Kod: $error). Tekrar deneyin."
                        }
                        currentRecognitionCallback?.invoke("", 0f, msg)
                        currentRecognitionCallback = null
                    }

                    override fun onResults(results: Bundle?) {
                        _isRecording.value = false
                        val elapsed = ((System.currentTimeMillis() - speechStartTimeMs) / 100f).roundToInt() / 10f
                        if (lastSpeechDurationSec <= 0f) {
                            lastSpeechDurationSec = elapsed.coerceAtLeast(0.8f)
                        }
                        flushAccumulatedPcmToWav(itemIdPrefix)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        val bestText = matches?.firstOrNull()?.trim() ?: ""
                        val bestConf = scores?.firstOrNull()?.takeIf { it > 0f } ?: 0.86f
                        currentRecognitionCallback?.invoke(bestText, bestConf, null)
                        currentRecognitionCallback = null
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer.startListening(intent)
            } catch (ex: Exception) {
                _isRecording.value = false
                onResult("", 0f, "Ses tanıma başlatılamadı: ${ex.localizedMessage}")
            }
        }, 150L)
    }

    fun stopListening() {
        stopSpeechRecognition()
    }

    fun stopSpeechRecognition() {
        _isRecording.value = false
        flushAccumulatedPcmToWav(currentRecordingPrefix)
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        audioRecordJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun recordWavForShadowingOrAdmin(
        itemIdPrefix: String,
        maxDurationSec: Float = 4.5f,
        onProgressBars: (List<Float>) -> Unit,
        onFinished: (wavFile: File?, envelope48: List<Float>, durationSec: Float) -> Unit
    ) {
        stopSpeaking()
        playBeep()
        _isRecording.value = true

        audioRecordJob = scope.launch(Dispatchers.IO) {
            delay(150L)
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var recorder: AudioRecord? = null
            val pcmShorts = mutableListOf<Short>()
            val startMs = System.currentTimeMillis()
            val maxMs = (maxDurationSec * 1000).toLong().coerceIn(2200L, 12000L)

            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buf = ShortArray(1024)
                    var speechStarted = false
                    var silenceChunks = 0

                    while (isActive && _isRecording.value && (System.currentTimeMillis() - startMs) < maxMs) {
                        val read = recorder.read(buf, 0, buf.size)
                        if (read > 0) {
                            var sumSq = 0.0
                            for (i in 0 until read) {
                                val s = buf[i]
                                pcmShorts.add(s)
                                val norm = s / 32768.0
                                sumSq += norm * norm
                            }
                            val rms = sqrt(sumSq / read).toFloat()
                            val barVal = (rms * 6.5f).coerceIn(0.06f, 1.0f)
                            val updatedWave = (_waveBars.value.drop(1) + barVal)
                            _waveBars.value = updatedWave
                            launch(Dispatchers.Main) { onProgressBars(updatedWave) }

                            if (rms > 0.02f) {
                                speechStarted = true
                                silenceChunks = 0
                            } else if (speechStarted) {
                                silenceChunks++
                                if (silenceChunks > 28) break
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {
                }
                _isRecording.value = false
            }

            if (pcmShorts.size < 800) {
                // Generate synthetic envelope if emulator mic has no hardware input
                val dur = 2.2f
                val env = List(48) { idx ->
                    val phase = Math.sin(idx * 0.35).toFloat()
                    (0.25f + abs(phase) * 0.65f).coerceIn(0.08f, 0.95f)
                }
                launch(Dispatchers.Main) { onFinished(null, env, dur) }
                return@launch
            }

            val wavFile = File(recordingsDir, "${itemIdPrefix}_${System.currentTimeMillis()}.wav")
            writeShortsToWavFile(pcmShorts.toShortArray(), sampleRate, wavFile)
            lastRecordedWavPath = wavFile.absolutePath

            val durationSec = ((pcmShorts.size.toFloat() / sampleRate) * 10f).roundToInt() / 10f
            val env48 = computeEnvelopeFromShorts(pcmShorts, 48)
            launch(Dispatchers.Main) {
                onFinished(wavFile, env48, durationSec)
            }
        }
    }

    private fun saveRawPcmToWav(pcmBytes: ByteArray, itemIdPrefix: String) {
        try {
            val wavFile = File(recordingsDir, "${itemIdPrefix}_${System.currentTimeMillis()}.wav")
            val shorts = ShortArray(pcmBytes.size / 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            writeShortsToWavFile(shorts, 16000, wavFile)
            lastRecordedWavPath = wavFile.absolutePath
        } catch (_: Exception) {
        }
    }

    private fun computeEnvelopeFromShorts(samples: List<Short>, numBars: Int = 48): List<Float> {
        if (samples.size < numBars) return List(numBars) { 0.15f }
        val chunkSize = samples.size / numBars
        val rmsVals = (0 until numBars).map { idx ->
            val start = idx * chunkSize
            val end = min(samples.size, start + chunkSize)
            var sumSq = 0.0
            for (i in start until end) {
                val v = samples[i] / 32768.0
                sumSq += v * v
            }
            sqrt(sumSq / max(1, end - start)).toFloat()
        }
        val maxVal = (rmsVals.maxOrNull() ?: 1f).coerceAtLeast(1e-5f)
        return rmsVals.map { (it / maxVal).coerceIn(0.08f, 1.0f) }
    }

    private fun writeShortsToWavFile(shorts: ShortArray, sampleRate: Int, outFile: File) {
        val byteData = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        val totalAudioLen = byteData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (sampleRate * channels * 2).toLong()

        FileOutputStream(outFile).use { fos ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2
            header[33] = 0
            header[34] = 16
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
            fos.write(header, 0, 44)
            fos.write(byteData)
        }
    }

    fun playSavedWavFile(path: String? = lastRecordedWavPath): Boolean {
        val target = path ?: lastRecordedWavPath ?: return false
        val file = File(target)
        if (!file.exists()) return false
        stopAllAudio()
        return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(FileInputStream(file).fd)
                prepare()
                start()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun findLatestStudentWavForId(itemId: String): File? {
        val files = recordingsDir.listFiles { f ->
            f.extension.equals("wav", ignoreCase = true) &&
                (f.name.startsWith("${itemId}_") || f.name.startsWith("shadow_${itemId}_") || f.name.startsWith("rp_${itemId}_"))
        } ?: return null
        return files.maxByOrNull { it.lastModified() }
    }

    fun getRecordingsStats(): Pair<Int, Double> {
        val files = recordingsDir.listFiles { f -> f.extension.equals("wav", ignoreCase = true) } ?: emptyArray()
        val totalBytes = files.sumOf { it.length() }
        val mb = ((totalBytes.toDouble() / (1024.0 * 1024.0)) * 100.0).roundToInt() / 100.0
        return files.size to mb
    }

    fun cleanupRecordings(keepLatest: Int = 0): Int {
        val files = recordingsDir.listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() } ?: return 0
        val toDelete = if (keepLatest > 0) files.drop(keepLatest) else files
        var deleted = 0
        for (f in toDelete) {
            if (f.delete()) deleted++
        }
        return deleted
    }

    fun stopSpeaking() {
        stopAllAudio()
    }

    fun stopAllAudio() {
        _isSpeakingTts.value = false
        currentTtsDoneCallback = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun shutdown() {
        stopSpeaking()
        stopListening()
        try {
            speechRecognizer?.destroy()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
