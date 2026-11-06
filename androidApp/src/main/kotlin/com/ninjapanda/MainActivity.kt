package com.ninjapanda

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val cameraGranted = mutableStateOf(false)

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraGranted.value = result[Manifest.permission.CAMERA] ?: cameraGranted.value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Players stand away from the phone; don't let the screen sleep mid-game.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraGranted.value = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        askPermissions()

        setContent {
            PhoneApp(cameraGranted = cameraGranted.value, onRequestCamera = ::askPermissions)
        }
    }

    private fun askPermissions() {
        val wanted = buildList {
            if (!cameraGranted.value) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }
        if (wanted.isNotEmpty()) requestPermissions.launch(wanted.toTypedArray())
    }
}
