package com.example.sitruyenaudio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sitruyenaudio.MainActivity
import com.example.sitruyenaudio.data.Chapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "TtsReaderChannel"

        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
    }

    private val binder = LocalBinder()
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private var currentChapter: Chapter? = null
    private var currentIndex = 0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentIndexFlow = MutableStateFlow(0)
    val currentIndexFlow: StateFlow<Int> = _currentIndexFlow.asStateFlow()
    
    private val _onChapterFinished = MutableStateFlow(false)
    val onChapterFinished: StateFlow<Boolean> = _onChapterFinished.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.toIntOrNull()?.let {
                    _currentIndexFlow.value = it
                    currentIndex = it
                }
            }

            override fun onDone(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: return
                if (currentChapter != null && index == currentChapter!!.paragraphs.size - 1) {
                    // Đọc xong chương
                    _isPlaying.value = false
                    _onChapterFinished.value = true
                } else if (index < (currentChapter?.paragraphs?.size ?: 0) - 1) {
                    // Cập nhật index hiện tại (để chuẩn bị cho trường hợp resume)
                    currentIndex = index + 1
                    // Hàng đợi đã có sẵn đoạn `index + 1` và sẽ tự động bắt đầu đọc.
                    // Ta chỉ cần nạp trước (queue) đoạn tiếp theo của nó là `index + 2`.
                    if (_isPlaying.value) {
                        val nextToQueue = currentIndex + 1
                        if (nextToQueue < currentChapter!!.paragraphs.size) {
                            speakParagraph(nextToQueue, TextToSpeech.QUEUE_ADD)
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS Error for utterance $utteranceId")
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopSelf()
        }
        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var result = tts?.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Vietnamese not supported, falling back to English")
                result = tts?.setLanguage(Locale.US)
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language completely not supported")
            } else {
                Log.i(TAG, "TTS Initialized successfully!")
                isTtsInitialized = true
            }
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
        }
    }
    
    fun getVoices(): List<Voice> {
        return try {
            val allVoices = tts?.voices?.toList() ?: emptyList()
            val viVoices = allVoices.filter { it.locale.language == "vi" }
            if (viVoices.isNotEmpty()) viVoices else allVoices
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun setVoice(voice: Voice) {
        tts?.voice = voice
    }
    
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun setChapter(chapter: Chapter) {
        currentChapter = chapter
        currentIndex = 0
        _currentIndexFlow.value = 0
        _onChapterFinished.value = false
    }
    
    fun resetChapterFinishState() {
        _onChapterFinished.value = false
    }

    private fun playStartingFrom(index: Int) {
        if (!isTtsInitialized || currentChapter == null) return
        // Xóa hàng đợi và đọc đoạn hiện tại
        speakParagraph(index, TextToSpeech.QUEUE_FLUSH)
        // Nạp trước đoạn tiếp theo vào hàng đợi
        if (index + 1 < currentChapter!!.paragraphs.size) {
            speakParagraph(index + 1, TextToSpeech.QUEUE_ADD)
        }
    }

    fun play() {
        if (!isTtsInitialized || currentChapter == null) return
        _isPlaying.value = true
        _onChapterFinished.value = false
        updateNotification()
        playStartingFrom(currentIndex)
    }

    fun pause() {
        _isPlaying.value = false
        tts?.stop()
        updateNotification()
    }

    private fun resume() {
        if (!isTtsInitialized || currentChapter == null) return
        _isPlaying.value = true
        updateNotification()
        playStartingFrom(currentIndex)
    }

    fun playParagraph(index: Int) {
        if (!isTtsInitialized || currentChapter == null) return
        if (index >= 0 && index < currentChapter!!.paragraphs.size) {
            currentIndex = index
            _isPlaying.value = true
            updateNotification()
            playStartingFrom(currentIndex)
        }
    }

    private fun speakParagraph(index: Int, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        val text = currentChapter?.paragraphs?.getOrNull(index)
        if (text == null) {
            Log.e(TAG, "No text found for index $index")
            return
        }
        
        Log.i(TAG, "Speaking paragraph $index, length: ${text.length}, queueMode: $queueMode")
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, index.toString())
        val result = tts?.speak(text, queueMode, params, index.toString())
        Log.i(TAG, "tts.speak result: $result (SUCCESS is ${TextToSpeech.SUCCESS}, ERROR is ${TextToSpeech.ERROR})")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Text-to-Speech Reader",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val playPauseAction = if (_isPlaying.value) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause",
                getPendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Play",
                getPendingIntent(ACTION_PLAY)
            ).build()
        }
        
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop",
            getPendingIntent(ACTION_STOP)
        ).build()

        val title = currentChapter?.title ?: "Reader App"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (_isPlaying.value) "Đang đọc..." else "Đã tạm dừng")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1))
            .setOngoing(true)
            .build()
    }
    
    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TtsService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
