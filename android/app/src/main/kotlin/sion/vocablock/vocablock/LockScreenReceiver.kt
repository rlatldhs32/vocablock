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
        Log.d("VocabLock", "LockScreenReceiver: received action: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("VocabLock", "Screen turned OFF")
                wasScreenOff = true
            }
            
            Intent.ACTION_SCREEN_ON -> {
                Log.d("VocabLock", "Screen turned ON")
                // 항상 화면이 켜지면 앱을 시작 - wasScreenOff 조건 없이
                Handler(Looper.getMainLooper()).postDelayed({
                    launchLockScreen(context)
                }, 300) // 300ms 지연으로 시스템이 안정화될 시간 제공
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d("VocabLock", "Device unlocked")
                // 사용자가 잠금을 해제함
            }
            
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d("VocabLock", "Boot completed")
                // 부팅 완료 시 서비스 시작
                startLockScreenService(context)
            }
        }
    }
    
    private fun launchLockScreen(context: Context) {
        try {
            // 잠금 화면 위에 표시할 인텐트 생성
            val lockIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra("STARTED_FROM_LOCK", true)
            }
            
            // PendingIntent와 직접 시작 방식 모두 사용해 신뢰성 향상
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context, 
                    0, 
                    lockIntent,
                    pendingIntentFlags
                )
                
                try {
                    pendingIntent.send()
                    Log.d("VocabLock", "LockScreen Activity launched via PendingIntent")
                } catch (e: Exception) {
                    Log.e("VocabLock", "Error launching via PendingIntent: ${e.message}")
                    // 직접 실행으로 폴백
                    context.startActivity(lockIntent)
                }
            } else {
                // 이전 Android 버전용 직접 시작
                context.startActivity(lockIntent)
                Log.d("VocabLock", "LockScreen Activity started directly")
            }
        } catch (e: Exception) {
            Log.e("VocabLock", "Error launching LockScreen Activity: ${e.message}")
            e.printStackTrace()
            
            // 첫 번째 방법이 실패하면 다른 시도
            try {
                val alternativeIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("STARTED_FROM_LOCK", true)
                }
                context.startActivity(alternativeIntent)
                Log.d("VocabLock", "LockScreen Activity started with alternative method")
            } catch (e2: Exception) {
                Log.e("VocabLock", "Alternative launch also failed: ${e2.message}")
            }
        }
    }
    
    private fun startLockScreenService(context: Context) {
        try {
            val serviceIntent = Intent(context, LockScreenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d("VocabLock", "Started foreground service from receiver")
            } else {
                context.startService(serviceIntent)
                Log.d("VocabLock", "Started service from receiver")
            }
        } catch (e: Exception) {
            Log.e("VocabLock", "Error starting service from receiver: ${e.message}")
            e.printStackTrace()
        }
    }
} 