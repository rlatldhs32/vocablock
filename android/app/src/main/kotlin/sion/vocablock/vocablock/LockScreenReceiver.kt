package sion.vocablock.vocablock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.app.KeyguardManager

class LockScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("VocabLock", "Screen turned OFF")
                // 화면이 꺼졌을 때의 처리
            }
            
            Intent.ACTION_SCREEN_ON -> {
                Log.d("VocabLock", "Screen turned ON - Launching app")
                
                // 잠금화면이 활성 상태인지 확인
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isPhoneLocked = keyguardManager.isKeyguardLocked
                Log.d("VocabLock", "Is phone locked: $isPhoneLocked")
                
                // 약간의 지연 후 앱 시작 (시스템 UI가 준비되도록)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val lockIntent = Intent(context, MainActivity::class.java).apply {
                            // 강력한 플래그 조합 설정
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            addFlags(Intent.FLAG_FROM_BACKGROUND)
                            
                            // 잠금화면 관련 플래그는 제거하고 대신 MainActivity에서 처리
                            putExtra("STARTED_FROM_LOCK", true)
                        }
                        
                        context.startActivity(lockIntent)
                        Log.d("VocabLock", "Activity started from screen on event")
                    } catch (e: Exception) {
                        Log.e("VocabLock", "Error starting activity: ${e.message}")
                        e.printStackTrace()
                    }
                }, 500) // 500ms 지연
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d("VocabLock", "Device unlocked")
                // 기기가 잠금 해제되었을 때의 처리
            }
        }
    }
} 