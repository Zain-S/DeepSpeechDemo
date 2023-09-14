package com.example.deepspeechdemo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

fun Context.checkPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Activity.initRecordAudioPermissionChecks(requestPermissionLauncher: ActivityResultLauncher<Array<String>>, onSuccess: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        recordAudioPermissionChecksSDK31(requestPermissionLauncher, onSuccess)
    } else recordAudioPermissionChecks(requestPermissionLauncher, onSuccess)
}

fun Activity.recordAudioPermissionChecks(requestPermissionLauncher: ActivityResultLauncher<Array<String>>, onSuccess: () -> Unit) {
    when {
        checkPermission(Manifest.permission.RECORD_AUDIO) -> {
            onSuccess.invoke()
        }

        else -> {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
fun Activity.recordAudioPermissionChecksSDK31(requestPermissionLauncher: ActivityResultLauncher<Array<String>>, onSuccess: () -> Unit) {
    when {
        checkPermission(Manifest.permission.RECORD_AUDIO) -> {
            onSuccess.invoke()
        }

        shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        else -> {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
}