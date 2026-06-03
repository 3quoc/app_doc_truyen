package com.example.sitruyenaudio.ui

import android.app.Application
import android.speech.tts.Voice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitruyenaudio.data.Chapter
import com.example.sitruyenaudio.data.HistoryItem
import com.example.sitruyenaudio.data.HistoryManager
import com.example.sitruyenaudio.data.ScraperRepository
import com.example.sitruyenaudio.service.TtsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScraperRepository()
    private val historyManager = HistoryManager(application)
    
    var currentUrl: String = ""

    var ttsService: TtsService? = null
        set(value) {
            field = value
            // Lắng nghe sự kiện chuyển chương
            if (value != null) {
                viewModelScope.launch {
                    value.onChapterFinished.collect { finished ->
                        if (finished) {
                            val nextUrl = currentChapter.value?.nextChapterUrl
                            if (!nextUrl.isNullOrEmpty()) {
                                fetchChapter(nextUrl, autoPlay = true)
                                value.resetChapterFinishState()
                            }
                        }
                    }
                }
                viewModelScope.launch {
                    value.currentIndexFlow.collect { index ->
                        _currentParagraphIndex.value = index
                    }
                }
                viewModelScope.launch {
                    value.isPlaying.collect { playing ->
                        _isPlaying.value = playing
                    }
                }
            }
        }

    private val _currentChapter = MutableStateFlow<Chapter?>(null)
    val currentChapter: StateFlow<Chapter?> = _currentChapter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentParagraphIndex = MutableStateFlow(0)
    val currentParagraphIndex: StateFlow<Int> = _currentParagraphIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    fun fetchChapter(url: String, autoPlay: Boolean = false) {
        currentUrl = url
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = repository.fetchChapter(url)
            if (result.isSuccess) {
                val chapter = result.getOrNull()
                _currentChapter.value = chapter
                _currentParagraphIndex.value = 0
                
                // Lưu lịch sử
                if (chapter != null) {
                    historyManager.addHistory(HistoryItem(
                        title = chapter.title,
                        url = url,
                        timestamp = System.currentTimeMillis()
                    ))
                }

                ttsService?.let { service ->
                    if (chapter != null) {
                        service.setChapter(chapter)
                        if (autoPlay) {
                            service.play()
                        }
                    }
                }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Lỗi tải dữ liệu"
            }
            _isLoading.value = false
        }
    }

    fun playPause() {
        if (_isPlaying.value) {
            ttsService?.pause()
        } else {
            ttsService?.play()
        }
    }
    
    fun playFromParagraph(index: Int) {
        ttsService?.playParagraph(index)
    }

    fun loadVoices() {
        ttsService?.let { service ->
            _availableVoices.value = service.getVoices()
        }
    }

    fun setVoice(voice: Voice) {
        ttsService?.setVoice(voice)
    }

    fun setSpeed(speed: Float) {
        ttsService?.setSpeechRate(speed)
    }

    fun getHistoryList(): List<HistoryItem> {
        return historyManager.getHistory()
    }
}
