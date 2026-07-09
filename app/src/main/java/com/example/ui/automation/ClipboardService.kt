package com.example.ui.automation

import android.app.Service
import android.content.*
import android.os.IBinder

class ClipboardService : Service() {
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private var lastClip = ""
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: return@addPrimaryClipChangedListener
            if (clip != lastClip && clip.length > 5) {
                lastClip = clip
                // Broadcast to app
                val intent = Intent("com.example.CLIPBOARD_CHANGED")
                intent.putExtra("text", clip)
                sendBroadcast(intent)
            }
        }
        return START_STICKY
    }
}
