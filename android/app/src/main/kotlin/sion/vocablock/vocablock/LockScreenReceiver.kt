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
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("VocabLock", "Screen turned OFF")
                // 화면이 꺼졌을 때의 처리
            }
            
            Intent.ACTION_SCREEN_ON -> {
                Log.d("VocabLock", "Screen turned ON - preparing full-screen notification")
                // 잠금화면이 활성 상태인지 확인
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isKeyguardLocked) {
                    try {
                        // Intent to launch lock screen activity
                        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("STARTED_FROM_LOCK", true)
                        }
                        // PendingIntent for full-screen notification
                        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                        val fullScreenPendingIntent = PendingIntent.getActivity(
                            context,
                            0,
                            fullScreenIntent,
                            pendingIntentFlags
                        )
                        // Build full-screen notification
                        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_lock_lock)
                            .setContentTitle(context.getString(R.string.app_name))
                            .setContentText(context.getString(R.string.lock_screen_notification))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                            .setFullScreenIntent(fullScreenPendingIntent, true)
                            .setOngoing(true)
                            .setAutoCancel(false)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        // Show notification
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(FULLSCREEN_NOTIFICATION_ID, notificationBuilder.build())
                        Log.d("VocabLock", "Full-screen notification sent")
                    } catch (e: Exception) {
                        Log.e("VocabLock", "Error showing full-screen notification: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    Log.d("VocabLock", "Phone not locked, skipping full-screen notification")
                }
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d("VocabLock", "Device unlocked")
                // 기기가 잠금 해제되었을 때의 처리
            }
        }
    }
} 