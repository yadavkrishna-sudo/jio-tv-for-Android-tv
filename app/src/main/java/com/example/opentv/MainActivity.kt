@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.example.opentv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.opentv.ui.AppViewModel
import com.example.opentv.ui.Screen
import com.example.opentv.ui.home.HomeScreen
import com.example.opentv.ui.player.PlayerScreen
import com.example.opentv.ui.settings.SettingsScreen
import com.example.opentv.ui.theme.OpenTvTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenTvTheme { Root(vm) }
        }
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (vm.screen) {
            Screen.HOME -> HomeScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
            Screen.PLAYER -> PlayerScreen(vm)
        }

        val message = vm.ui.message
        if (message != null) {
            LaunchedEffect(message) {
                delay(3500)
                vm.clearMessage()
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .background(Color(0xEE212A36), RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}
