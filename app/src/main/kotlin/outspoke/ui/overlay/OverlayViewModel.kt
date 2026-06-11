package dev.brgr.outspoke.ui.overlay

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.brgr.outspoke.audio.AudioCaptureManager
import dev.brgr.outspoke.ime.TranscriptAligner
import dev.brgr.outspoke.inference.EngineState
import dev.brgr.outspoke.inference.InferenceService
import dev.brgr.outspoke.inference.TranscriptResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val TAG = "OverlayViewModel"

sealed class OverlayState {
    object Dot : OverlayState()
    object Pill : OverlayState()
}

sealed class OverlayFeedback {
    object Empty : OverlayFeedback()
    object Error : OverlayFeedback()
    object Success : OverlayFeedback()
}

sealed class HapticEvent {
    object Start : HapticEvent()
    object Success : HapticEvent()
    object Error : HapticEvent()
    object Empty : HapticEvent()
}

class OverlayViewModel(application: Application) : AndroidViewModel(application) {

    private val audioCaptureManager = AudioCaptureManager(application)
    
    private var inferenceBinder: InferenceService.InferenceBinder? = null
    
    private val _overlayState = MutableStateFlow<OverlayState>(OverlayState.Dot)
    val overlayState: StateFlow<OverlayState> = _overlayState

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val engineState: StateFlow<EngineState> = _engineState

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _feedback = MutableStateFlow<OverlayFeedback?>(null)
    val feedback: StateFlow<OverlayFeedback?> = _feedback

    private var captureJob: Job? = null
    private var pendingCommit: ((String) -> Unit)? = null

    /** 
     * Words that have been "committed" (stable or trimmed from the window).
     * Used as an alignment anchor for incoming partials.
     */
    private var committedWords = mutableListOf<String>()

    private val _hapticEvents = Channel<HapticEvent>(Channel.BUFFERED)
    val hapticEvents = _hapticEvents.receiveAsFlow()

    val amplitude: StateFlow<Float> = audioCaptureManager.amplitude

    fun setInferenceBinder(binder: InferenceService.InferenceBinder?) {
        inferenceBinder = binder
        viewModelScope.launch {
            binder?.getEngineState()?.collect {
                _engineState.value = it
            }
        }
    }

    fun onDotClicked() {
        _overlayState.value = OverlayState.Pill
    }

    fun onUninitialise() {
        _overlayState.value = OverlayState.Dot
    }

    fun onRecordStart() {
        val repository = inferenceBinder?.getRepository() ?: return
        Log.d(TAG, "onRecordStart")
        _isRecording.value = true
        viewModelScope.launch { _hapticEvents.send(HapticEvent.Start) }
        _transcript.value = ""
        committedWords.clear()

        captureJob = viewModelScope.launch {
            try {
                val audioFlow = audioCaptureManager.startCapture(true)
                repository.transcribe(audioFlow, true, true).collect { result ->
                    when (result) {
                        is TranscriptResult.Partial -> {
                            updateTranscript(result.text)
                        }
                        is TranscriptResult.Final -> {
                            updateTranscript(result.text)
                            if (result.text.isNotBlank()) {
                                _feedback.value = OverlayFeedback.Success
                            }
                            Log.d(TAG, "Final transcript received: \"${_transcript.value}\"")
                        }
                        is TranscriptResult.WindowTrimmed -> {
                            if (result.stableWords.isNotEmpty()) {
                                Log.d(TAG, "Window trimmed, updating committedWords (size ${result.stableWords.size})")
                                committedWords = result.stableWords.toMutableList()
                            }
                        }
                        is TranscriptResult.Failure -> {
                            Log.e(TAG, "Inference failure", result.cause)
                            _feedback.value = OverlayFeedback.Error
                        }
                    }
                }
            } finally {
                Log.d(TAG, "Inference flow completed")
                if (_feedback.value == null && _transcript.value.isBlank()) {
                    _feedback.value = OverlayFeedback.Empty
                }
                val event = when (_feedback.value) {
                    is OverlayFeedback.Error -> HapticEvent.Error
                    is OverlayFeedback.Empty -> HapticEvent.Empty
                    else -> HapticEvent.Success
                }
                viewModelScope.launch { _hapticEvents.send(event) }
                pendingCommit?.invoke(_transcript.value)
                pendingCommit = null
                _isRecording.value = false
            }
        }
    }

    private fun updateTranscript(newText: String) {
        val currentWords = with(TranscriptAligner) { newText.splitToWords() }
        val newWords = TranscriptAligner.findNewContent(committedWords, currentWords)
        _transcript.value = (committedWords + newWords).joinToString(" ")
    }

    fun onRecordStop(onCommit: (String) -> Unit) {
        Log.d(TAG, "onRecordStop requested")
        pendingCommit = onCommit
        audioCaptureManager.stopCapture()
    }

    fun consumeFeedback() {
        _feedback.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioCaptureManager.stopCapture()
        captureJob?.cancel()
    }
}
