package sion.vocablock.vocablock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d("VocabLock", "Boot completed, starting service")
            
            // Start lock screen service
            try {
                val serviceIntent = Intent(context, LockScreenService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                    Log.d("VocabLock", "Started foreground service from boot receiver")
                } else {
                    context.startService(serviceIntent)
                    Log.d("VocabLock", "Started service from boot receiver")
                }
            } catch (e: Exception) {
                Log.e("VocabLock", "Error starting service from boot: ${e.message}")
                e.printStackTrace()
            }
        }
    }
} 