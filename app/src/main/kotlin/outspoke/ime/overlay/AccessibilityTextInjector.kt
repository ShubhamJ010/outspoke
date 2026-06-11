package dev.brgr.outspoke.ime.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

private const val TAG = "AccessibilityTextInjector"

/**
 * Handles text injection into the currently focused accessibility node.
 * Since we don't have a direct InputConnection in an AccessibilityService,
 * we use accessibility actions to manipulate the text.
 */
class AccessibilityTextInjector {

    /**
     * Injects text into the given node. 
     * Tries ACTION_SET_TEXT first, then falls back to clipboard + ACTION_PASTE.
     * Returns true if injection succeeded via any method.
     */
    fun injectText(context: Context, node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null || text.isBlank()) return false

        // 1. Try ACTION_SET_TEXT (Replaces everything)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            Log.d(TAG, "Injected text via ACTION_SET_TEXT")
            return true
        }

        // 2. Fallback: Copy to clipboard and try ACTION_PASTE
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Outspoke Dictation", text)
            clipboard.setPrimaryClip(clip)
            
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                Log.d(TAG, "Injected text via ACTION_PASTE")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject via clipboard fallback", e)
        }

        Log.w(TAG, "All injection methods failed")
        return false
    }
}
