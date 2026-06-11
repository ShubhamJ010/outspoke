package dev.brgr.outspoke.ime.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.brgr.outspoke.inference.InferenceService
import dev.brgr.outspoke.settings.preferences.AppPreferences
import dev.brgr.outspoke.ui.overlay.OverlayContent
import dev.brgr.outspoke.ui.overlay.OverlayViewModel
import dev.brgr.outspoke.ui.theme.OutspokeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SpeechOverlayService : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val TAG = "SpeechOverlayService"

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var focusedNode: AccessibilityNodeInfo? = null
    private var overlayShowTime = 0L
    
    private var inferenceBinder: InferenceService.InferenceBinder? = null
    private var isBound = false

    private val viewModel by lazy {
        OverlayViewModel(application)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            inferenceBinder = binder as? InferenceService.InferenceBinder
            viewModel.setInferenceBinder(inferenceBinder)
            Log.d(TAG, "InferenceService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            inferenceBinder = null
            viewModel.setInferenceBinder(null)
            Log.d(TAG, "InferenceService disconnected")
        }
    }

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        bindInferenceService()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        Log.d(TAG, "Accessibility Service Connected")
    }

    private fun bindInferenceService() {
        val intent = Intent(this, InferenceService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        isBound = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        lifecycleScope.launch {
            if (!AppPreferences(applicationContext).overlayEnabled.first()) {
                hideOverlay()
                return@launch
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    val node = event.source
                    if (node != null && (node.isEditable || node.className?.contains("EditText") == true)) {
                        focusedNode = node
                        showOverlay()
                    } else if (overlayView != null && viewModel.isRecording.value.not()) {
                        hideOverlay()
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (overlayView != null && viewModel.isRecording.value.not()
                        && System.currentTimeMillis() - overlayShowTime > 1000
                    ) {
                        hideOverlay()
                    }
                }
            }
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        overlayShowTime = System.currentTimeMillis()

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SpeechOverlayService)
            setViewTreeViewModelStoreOwner(this@SpeechOverlayService)
            setViewTreeSavedStateRegistryOwner(this@SpeechOverlayService)
            
            setContent {
                OutspokeTheme {
                    OverlayContent(
                        viewModel = viewModel,
                        onInjectText = { text ->
                            if (text.isNotBlank()) {
                                val success = AccessibilityTextInjector().injectText(this@SpeechOverlayService, focusedNode, text)
                                if (!success) {
                                    Toast.makeText(this@SpeechOverlayService, "Failed to inject. Copied to clipboard.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onClose = {
                            hideOverlay()
                        }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Adjust as needed
        }

        windowManager.addView(overlayView, params)
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
        }
        hideOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
