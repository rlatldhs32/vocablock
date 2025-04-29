package sion.vocablock.vocablock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class LockScreenService : Service() {
    private var receiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceWatchdog = object : Runnable {
        override fun run() {
            Log.d("VocabLock", "Service watchdog: checking if service is running")
            // 서비스가 여전히 실행 중임을 로그로 확인
            handler.postDelayed(this, SERVICE_CHECK_INTERVAL)
        }
    }

    companion object {
        private const val SERVICE_CHECK_INTERVAL = 60000L // 1분 간격으로 확인
        private var isServiceRunning = false

        fun isRunning(): Boolean {
            return isServiceRunning
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d("VocabLock", "LockScreenService - onCreate")
        isServiceRunning = true
        
        // 가장 먼저 Foreground 서비스로 시작 (Android 8.0 이상에서는 서비스 시작 후 5초 이내에 startForeground 호출 필요)
        startForegroundService()
        
        // Receiver 등록은 그 후에 진행
        try {
            receiver = LockScreenReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_BOOT_COMPLETED)
            }
            registerReceiver(receiver, filter)
            Log.d("VocabLock", "LockScreenService created and receiver registered")
            
            // 서비스 감시 메커니즘 시작
            handler.postDelayed(serviceWatchdog, SERVICE_CHECK_INTERVAL)
        } catch (e: Exception) {
            Log.e("VocabLock", "Error registering receiver: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun startForegroundService() {
        val channelId = "vocablock_lock_service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Only create the channel if it doesn't already exist
            val existingChannel = manager.getNotificationChannel(channelId)
            if (existingChannel == null) {
                val channelName = "VocabLock Service"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(channelId, channelName, importance).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
        // Build the persistent notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.lock_screen_notification))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        // Start foreground with the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(1, notificationBuilder.build())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VocabLock", "LockScreenService - onStartCommand")
        startForegroundService()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("VocabLock", "LockScreenService - onDestroy")
        super.onDestroy()
        isServiceRunning = false
        
        // Unregister receiver
        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e("VocabLock", "Error unregistering receiver: ${e.message}")
            }
            receiver = null
        }
        
        // 서비스 감시 메커니즘 중지
        handler.removeCallbacks(serviceWatchdog)
        
        // 서비스 재시작 시도
        val restartIntent = Intent(applicationContext, LockScreenService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("VocabLock", "LockScreenService - onTaskRemoved: restarting service")
        val restartIntent = Intent(applicationContext, LockScreenService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 