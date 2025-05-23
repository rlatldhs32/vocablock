package sion.vocablock.vocablock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.app.KeyguardManager

class MainActivity: FlutterActivity() {
    private val DEVICE_ADMIN_REQUEST = 1
    private val CHANNEL = "sion.vocablock.vocablock/lockscreen"
    private var isLockScreenMode = false
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Set up method channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isLockScreenMode" -> {
                    result.success(isLockScreenMode)
                }
                "unlockDevice" -> {
                    unlockDevice()
                    result.success(true)
                }
                "minimizeApp" -> {
                    minimizeApp()
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if app is being started from lock screen
        isLockScreenMode = intent.getBooleanExtra("STARTED_FROM_LOCK", false)
        
        Log.d("VocabLock", "MainActivity onCreate - isLockScreenMode: $isLockScreenMode")
        
        if (isLockScreenMode || checkIfPhoneLocked()) {
            Log.d("VocabLock", "Setting lock screen flags")
            setupLockScreenMode()
        }
        
        // Start the lock screen service
        startLockScreenService()
        
        // Request device admin privileges if not already granted
        requestDeviceAdmin()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("VocabLock", "MainActivity - onResume")
        
        // Check if service is running and restart if needed
        ensureLockScreenServiceRunning()
        
        // Setup lock screen mode if needed
        if (isLockScreenMode || checkIfPhoneLocked()) {
            setupLockScreenMode()
        }
    }
    
    private fun unlockDevice() {
        // Dismiss keyguard (this might work on some devices)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
        } else {
            // For older API versions
            try {
                @Suppress("DEPRECATION")
                window.clearFlags(0x00080000) // WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD value
            } catch (e: Exception) {
                Log.e("VocabLock", "Error clearing flags: ${e.message}")
            }
        }
        
        // Try to use keyguard manager to dismiss keyguard
        // This requires DISABLE_KEYGUARD permission
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        Log.d("VocabLock", "Unlocking device")
    }
    
    private fun startLockScreenService() {
        // Start service based on API level
        try {
            val serviceIntent = Intent(this, LockScreenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
                Log.d("VocabLock", "Started foreground service on Android O+")
            } else {
                startService(serviceIntent)
                Log.d("VocabLock", "Started service on Android N-")
            }
        } catch (e: Exception) {
            Log.e("VocabLock", "Error starting service: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun requestDeviceAdmin() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)
        
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This app needs lock screen admin permission for vocabulary learning.")
            }
            startActivityForResult(intent, DEVICE_ADMIN_REQUEST)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == DEVICE_ADMIN_REQUEST) {
            if (resultCode == RESULT_OK) {
                Log.d("VocabLock", "Device admin enabled")
            } else {
                Log.d("VocabLock", "Device admin rejected")
            }
        }
    }
    
    // Check if phone is currently locked
    private fun checkIfPhoneLocked(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked
        Log.d("VocabLock", "Phone lock state: $isLocked")
        return isLocked
    }
    
    // Setup display on top of lock screen
    private fun setupLockScreenMode() {
        runOnUiThread {
            // Show on lock screen based on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                
                // Try to dismiss keyguard (API 26+)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                // For older API versions use integer constants
                try {
                    @Suppress("DEPRECATION")
                    val DISMISS_KEYGUARD = 0x00080000 // WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    val SHOW_WHEN_LOCKED = 0x00000080 // WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    val TURN_SCREEN_ON = 0x00200000 // WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    
                    window.addFlags(DISMISS_KEYGUARD or SHOW_WHEN_LOCKED or TURN_SCREEN_ON or 
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } catch (e: Exception) {
                    Log.e("VocabLock", "Error setting window flags: ${e.message}")
                }
            }
            
            // Increase screen brightness
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1.0f // Maximum brightness
            window.attributes = layoutParams
        }
    }
    
    private fun ensureLockScreenServiceRunning() {
        if (!LockScreenService.isRunning()) {
            Log.d("VocabLock", "Service not running, starting it")
            startLockScreenService()
        } else {
            Log.d("VocabLock", "Service already running")
        }
    }

    // Minimize app by moving task to back
    private fun minimizeApp() {
        Log.d("VocabLock", "Minimizing app")
        moveTaskToBack(true)
    }
}
