package sion.vocablock.vocablock

import android.content.pm.ApplicationInfo
import android.app.Application
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import android.util.Log

// Application class that pre-warms the FlutterEngine on app start
class VocabLockApp : Application() {
    companion object {
        const val ENGINE_ID = "pre_warmed_engine"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("VocabLock", "VocabLockApp: Pre-warming FlutterEngine")
        
        // Always pre-warm the FlutterEngine regardless of build type
        try {
            val flutterEngine = FlutterEngine(this)
            flutterEngine.dartExecutor.executeDartEntrypoint(
                DartExecutor.DartEntrypoint.createDefault()
            )
            FlutterEngineCache.getInstance().put(ENGINE_ID, flutterEngine)
            Log.d("VocabLock", "FlutterEngine successfully pre-warmed and cached")
        } catch (e: Exception) {
            Log.e("VocabLock", "Error pre-warming FlutterEngine: ${e.message}")
            e.printStackTrace()
        }
    }
} 