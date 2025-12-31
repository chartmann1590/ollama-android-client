package com.charles.ollama.client.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VibrationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    
    private var lastVibrationTime = 0L
    private val vibrationMutex = Mutex()
    private val minVibrationInterval = 50L // Minimum 50ms between vibrations
    
    suspend fun vibrate(durationMillis: Long = 10) {
        if (vibrator == null || !vibrator!!.hasVibrator()) {
            return
        }
        
        vibrationMutex.withLock {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVibrationTime < minVibrationInterval) {
                return
            }
            lastVibrationTime = currentTime
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator!!.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator!!.vibrate(durationMillis)
                }
            } catch (e: Exception) {
                // Ignore vibration errors
            }
        }
    }
}

