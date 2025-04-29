package sion.vocablock.vocablock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.app.Notification

class LockScreenReceiver : BroadcastReceiver() {
    companion object {
        private const val FULLSCREEN_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vocablock_lock_service"
        private var wasScreenOff = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("VocabLock", "Screen turned OFF")
                wasScreenOff = true
            }
            
            Intent.ACTION_SCREEN_ON -> {
                Log.d("VocabLock", "Screen turned ON")
                if (wasScreenOff) {
                    wasScreenOff = false
                    try {
                        val lockIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                     Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                     Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("STARTED_FROM_LOCK", true)
                        }
                        context.startActivity(lockIntent)
                        Log.d("VocabLock", "LockScreen Activity started")
                    } catch (e: Exception) {
                        Log.e("VocabLock", "Error launching LockScreen Activity: ${e.message}")
                    }
                }
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d("VocabLock", "Device unlocked")
                // 기기가 잠금 해제되었을 때의 처리
            }
        }
    }
} 