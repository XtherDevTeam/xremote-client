/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.jerrychau.xremote.presentation

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.jerrychau.xremote.Remote
import com.jerrychau.xremote.presentation.theme.XRemoteTheme

@Composable
fun remoteAlert(alertMessage: String, alertStyle: String, onDismiss: () -> Unit) {
    val alertTitle = when (alertStyle) {
        "error" -> "Error"
        "success" -> "Success"
        "warning" -> "Warning"
        else -> "Info"
    }
    val icon = when (alertStyle) {
        "error" -> Icons.Filled.Warning
        "success" -> Icons.Filled.Done
        "warning" -> Icons.Filled.Warning
        else -> Icons.Filled.Info
    }
    return Alert(title = { Text(text = alertTitle) }, message = { Text(text = alertMessage) }, icon = { Icon(icon, alertTitle) }) {
        item {
            Button(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    var remote = Remote()

    class SharePreferenceWrapper {
        var sharedPreferences: SharedPreferences? = null;

        constructor(pf: SharedPreferences) {
            sharedPreferences = pf
        }

        fun putString(key: String, value: String) : Boolean {
            val b= sharedPreferences?.edit()?.putString(key, value)?.commit()
            return if (b is Boolean) {
                b
            } else {
                false
            }
        }

        fun getOrCreate(key: String, default: String) : String {
            val f= sharedPreferences?.getString(key, default)
            if (f is String) {
                return f
            } else {
                putString(key, default)
                return default
            }
        }

        fun getServerAddr() : String {
            return getOrCreate("serverAddr", "")
        }

        fun getConnectToken() : String {
            return getOrCreate("connectToken", "")
        }

        fun saveState(addr: String, token: String) {
            putString("serverAddr", addr)
            putString("connectToken", token)
        }
    }





    @Composable
    fun WearApp(metrics: DisplayMetrics, config: SharePreferenceWrapper) {
        var focusManager = LocalFocusManager.current
        var serverAddr by remember { mutableStateOf(config.getServerAddr()) }
        var connectToken by remember { mutableStateOf(config.getConnectToken()) }
        var showAlertDialog by remember { mutableStateOf(false) }
        var alertMessage by remember { mutableStateOf("") }
        var alertStyle by remember { mutableStateOf("error") }

        XRemoteTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .clickable(interactionSource = remember { MutableInteractionSource () }, indication = null, enabled = true) {
                        focusManager.clearFocus()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (showAlertDialog) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 1f))) {
                        remoteAlert(alertMessage, alertStyle) {
                            showAlertDialog = false
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colors.primary,
                            fontSize = 20.sp,
                            text = "X Remote for Watch"
                        )
                        // finish onValueChange
                        OutlinedTextField(value = serverAddr, maxLines = 1, singleLine = true, onValueChange = {
                            serverAddr = it
                        }, label = { Text("Server Address", fontSize = 12.sp) }, modifier = Modifier
                            .fillMaxWidth()
                            .scale(0.9f)
                        )
                        // password input
                        OutlinedTextField(value = connectToken, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, maxLines = 1, onValueChange = {
                            connectToken = it
                        }, label = { Text("Connect Token", fontSize = 12.sp) }, modifier = Modifier
                            .fillMaxWidth()
                            .scale(0.9f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    println("Attempt to connect to $serverAddr with token $connectToken")
                                    config.saveState(serverAddr, connectToken)
                                    remote.initiateConnection(serverAddr, connectToken, metrics) {
                                        secondStepToken, error ->

                                        if (error is Exception) {
                                            showAlertDialog = true
                                            alertMessage = error.message!!
                                            alertStyle = "error"
                                        } else {
                                            // switch to ViewerActivity
                                            val intent = Intent(this@MainActivity, ViewerActivity::class.java)
                                            intent.putExtra("secondStepToken", secondStepToken)
                                            intent.putExtra("serverAddr", serverAddr)
                                            startActivity(intent)
                                            println("Switching to new Activity")
                                        }
                                    }
                                }, modifier = Modifier
                                    .size(100.dp, 30.dp)
                                    .scale(0.8f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                    Text("Connect", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Preview(device = "id:wearos_rect", showSystemUi = true, name = "Xiaomi Mi Watch",
        uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_WATCH
    )
    @Composable
    fun DefaultPreview() {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(this.resources.displayMetrics, SharePreferenceWrapper(this.getSharedPreferences("credentials", MODE_PRIVATE)))
        }
    }
}

