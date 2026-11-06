package com.ninjapanda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Screen { Home, PhoneOnly, SendToTv }

@Composable
fun PhoneApp(cameraGranted: Boolean, onRequestCamera: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            if (!cameraGranted) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("The camera is used to see your moves. Nothing is recorded or saved.")
                    Button(onClick = onRequestCamera, modifier = Modifier.padding(top = 16.dp)) { Text("Allow camera") }
                }
                return@Surface
            }

            var screen by rememberSaveable { mutableStateOf(Screen.Home) }
            if (screen != Screen.Home) BackHandler { screen = Screen.Home }
            when (screen) {
                Screen.Home -> HomeScreen(onSendToTv = { screen = Screen.SendToTv }, onPhoneOnly = { screen = Screen.PhoneOnly })
                Screen.PhoneOnly -> MotionDebugScreen(onBack = { screen = Screen.Home })
                Screen.SendToTv -> SendToTvScreen(onBack = { screen = Screen.Home })
            }
        }
    }
}

@Composable
private fun HomeScreen(onSendToTv: () -> Unit, onPhoneOnly: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ninja Panda", fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text("Motion test app", color = Color.Gray)
        Button(onClick = onSendToTv, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("Send to TV", fontSize = 20.sp)
        }
        Text("Phone is the camera, the TV shows the game.", color = Color.Gray, fontSize = 14.sp)
        OutlinedButton(onClick = onPhoneOnly, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Try on this phone", fontSize = 18.sp)
        }
        Text("Camera, detection and readouts all on the phone.", color = Color.Gray, fontSize = 14.sp)
    }
}
