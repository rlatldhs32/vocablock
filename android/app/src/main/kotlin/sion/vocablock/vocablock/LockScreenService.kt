package sion.vocablock.vocablock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class LockScreenService : Service() {
    private var receiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmManager: AlarmManager? = null
    private var restartPendingIntent: PendingIntent? = null
    
    private val serviceWatchdog = object : Runnable {
        override fun run() {
            Log.d("VocabLock", "Service watchdog: 서비스 실행 확인 중")
            
            // 리시버가 등록되어 있는지 확인
            if (receiver == null) {
                Log.d("VocabLock", "리시버가 없음, 다시 등록")
                registerScreenReceiver()
            }
            
            // WakeLock이 유효한지 확인하고 필요시 갱신
            if (wakeLock?.isHeld != true) {
                Log.d("VocabLock", "WakeLock이 유지되지 않음, 다시 획득")
                acquireWakeLock()
            }
            
            // 서비스 재시작 알람 설정
            scheduleServiceRestart()
            
            // 다음 확인 예약
            handler.postDelayed(this, SERVICE_CHECK_INTERVAL)
        }
    }

    companion object {
        private const val SERVICE_CHECK_INTERVAL = 30000L // 30초 간격으로 확인 (더 짧게 변경)
        private const val WAKE_LOCK_TIMEOUT = 3600000L // 1시간 타임아웃 (더 길게 변경)
        private const val RESTART_ALARM_INTERVAL = 60000L // 서비스 재시작 알람 간격 (1분)
        private const val RESTART_SERVICE_REQUEST_CODE = 1001
        
        private var isServiceRunning = false

        fun isRunning(): Boolean {
            return isServiceRunning
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d("VocabLock", "LockScreenService - onCreate")
        isServiceRunning = true
        
        // 즉시 포그라운드 서비스로 시작 (Android 8.0+ 필수)
        startForegroundService()
        
        // WakeLock 획득
        acquireWakeLock()
        
        // 스크린 리시버 등록
        registerScreenReceiver()
        
        // 알람 매니저 초기화
        setupAlarmManager()
        
        // 서비스 워치독 시작
        handler.postDelayed(serviceWatchdog, SERVICE_CHECK_INTERVAL)
    }
    
    private fun setupAlarmManager() {
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(this, LockScreenService::class.java)
        intent.action = "RESTART_VOCABLOCK_SERVICE"
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        restartPendingIntent = PendingIntent.getService(
            this,
            RESTART_SERVICE_REQUEST_CODE,
            intent,
            flags
        )
        
        scheduleServiceRestart()
    }
    
    private fun scheduleServiceRestart() {
        try {
            // alarmManager와 restartPendingIntent가 모두 null이 아닌 경우에만 진행
            val am = alarmManager
            val pi = restartPendingIntent
            
            if (am != null && pi != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + RESTART_ALARM_INTERVAL,
                        pi
                    )
                } else {
                    am.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + RESTART_ALARM_INTERVAL,
                        pi
                    )
                }
                Log.d("VocabLock", "서비스 재시작 알람 설정됨")
            } else {
                Log.d("VocabLock", "알람 또는 PendingIntent가 null이어서 알람 설정 실패")
                // PendingIntent가 없으면 다시 초기화 시도
                if (pi == null) {
                    setupAlarmManager()
                }
            }
        } catch (e: Exception) {
            Log.e("VocabLock", "알람 설정 중 오류: ${e.message}")
        }
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VocabLock:LockScreenServiceWakeLock"
            )
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT)
            Log.d("VocabLock", "WakeLock 획득됨")
        } catch (e: Exception) {
            Log.e("VocabLock", "WakeLock 획득 중 오류: ${e.message}")
        }
    }
    
    private fun registerScreenReceiver() {
        try {
            // 화면 리시버 생성 및 등록
            if (receiver == null) {
                receiver = LockScreenReceiver()
                val filter = IntentFilter().apply {
                    // 우선순위를 최대로 설정하여 다른 앱보다 먼저 처리되도록 함
                    priority = 999
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_BOOT_COMPLETED)
                    addAction("android.intent.action.QUICKBOOT_POWERON")
                }
                registerReceiver(receiver, filter)
                Log.d("VocabLock", "LockScreenReceiver 등록됨")
            }
        } catch (e: Exception) {
            Log.e("VocabLock", "리시버 등록 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun startForegroundService() {
        val channelId = "vocablock_lock_service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 채널이 존재하지 않는 경우에만 생성
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
        
        // 재시작 인텐트 생성
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )
        
        // 지속적 알림 생성
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.lock_screen_notification))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
        
        // 포그라운드로 시작
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(1, notificationBuilder.build())
            }
            Log.d("VocabLock", "포그라운드 서비스로 시작됨")
        } catch (e: Exception) {
            Log.e("VocabLock", "포그라운드 서비스 시작 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VocabLock", "LockScreenService - onStartCommand: ${intent?.action}")
        
        // 서비스가 재시작된 경우
        if (intent?.action == "RESTART_VOCABLOCK_SERVICE") {
            Log.d("VocabLock", "알람으로 서비스 재시작됨")
        }
        
        // 포그라운드 서비스로 실행 중인지 확인
        startForegroundService()
        
        // WakeLock 획득 확인
        if (wakeLock == null || wakeLock?.isHeld != true) {
            acquireWakeLock()
        }
        
        // 리시버 등록 확인
        if (receiver == null) {
            registerScreenReceiver()
        }
        
        // START_STICKY보다 더 강력하게 재시작
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Log.d("VocabLock", "LockScreenService - onDestroy")
        super.onDestroy()
        isServiceRunning = false
        
        // WakeLock 해제
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("VocabLock", "WakeLock 해제됨")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("VocabLock", "WakeLock 해제 중 오류: ${e.message}")
        }
        
        // 리시버 등록 해제
        receiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("VocabLock", "리시버 등록 해제됨")
            } catch (e: Exception) {
                Log.e("VocabLock", "리시버 등록 해제 중 오류: ${e.message}")
            }
            receiver = null
        }
        
        // 워치독 중지
        handler.removeCallbacks(serviceWatchdog)
        
        // 서비스가 종료되면 즉시 재시작
        restartService()
    }
    
    private fun restartService() {
        try {
            Log.d("VocabLock", "서비스 재시작 시도")
            val restartIntent = Intent(applicationContext, LockScreenService::class.java)
            restartIntent.action = "RESTART_AFTER_KILL"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
            
            // 재시작 알람 설정
            val am = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }
            
            val pIntent = PendingIntent.getService(
                applicationContext,
                RESTART_SERVICE_REQUEST_CODE + 1,
                restartIntent,
                pendingIntentFlags
            )
            
            if (pIntent != null && am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 1000, // 1초 후 재시작
                        pIntent
                    )
                } else {
                    am.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 1000,
                        pIntent
                    )
                }
                Log.d("VocabLock", "서비스 재시작 알람 설정됨")
            } else {
                Log.e("VocabLock", "알람 또는 PendingIntent 생성 실패")
            }
        } catch (e: Exception) {
            Log.e("VocabLock", "서비스 재시작 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("VocabLock", "LockScreenService - onTaskRemoved: 서비스 재시작")
        restartService()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 