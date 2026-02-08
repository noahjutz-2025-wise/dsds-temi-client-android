package com.noahjutz.kinetiquery

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.noahjutz.kinetiquery.ui.Main
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.util.network.UnresolvedAddressException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = appTheme) {
                Surface {
                    MainScreen()
                }
            }
        }
    }
}

val appTheme = darkColorScheme(
    primary = Color(26, 65, 120),
    onPrimary = Color.White,
    secondary = Color(254, 113, 0),
    onSecondary = Color.Black
)

sealed class State {
    data class Error(val e: Exception) : State()
    object MissingPermissions : State()
    object Running : State()
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    var state: State by remember { mutableStateOf(State.Running) }
    val audioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    if (audioPermission.status != PermissionStatus.Granted) {
        state = State.MissingPermissions
    } else if (state == State.MissingPermissions) {
        state = State.Running
    }

    when (state) {
        is State.Error -> Err((state as State.Error).e) { state = State.Running }
        State.MissingPermissions -> MissingPermission(
            audioPermission,
            onError = { state = State.Error(it) },
            onResolved = { state = State.Running }
        )

        State.Running -> Main(
            onError = { state = State.Error(it) }
        )
    }
}

@Composable
fun Err(e: Exception, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("An Error occurred.", style = MaterialTheme.typography.headlineLarge)
        val scrollState = rememberScrollState()
        Text(
            e.toString(),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.horizontalScroll(scrollState)
        )
        when (e) {
            is UnresolvedAddressException -> {
                Text("Please make sure you are connected to the internet.")
            }

            is HttpRequestTimeoutException -> {
                Text("Connecting after a long period of inactivity may take a few minutes.")
            }
        }
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MissingPermission(
    audioPermission: PermissionState,
    onResolved: () -> Unit,
    onError: (Exception) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Sapp needs to access your microphone to hear you. ")

        Row {
            Button(onClick = {
                audioPermission.launchPermissionRequest()
                onResolved()
            }) {
                Text("Request permission")
            }

            val context = LocalContext.current
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    onError(e)
                }
            }, colors = ButtonDefaults.textButtonColors().copy(contentColor = Color.White)) {
                Text("Go to settings")
            }
        }
    }
}