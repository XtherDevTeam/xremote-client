package com.jerrychau.xremote.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.jerrychau.xremote.BaseXRemoteException
import com.jerrychau.xremote.FrameUpdateFull
import com.jerrychau.xremote.R
import com.jerrychau.xremote.Remote


class ViewerActivity : ComponentActivity() {
    var remote = Remote()

    class BitmapViewModel: ViewModel() {
        private var _bmp = mutableStateOf(Bitmap.createBitmap(621, 621, Bitmap.Config.ARGB_8888))

        @Composable
        fun get(): Bitmap {
            return _bmp.value
        }

        fun set(bmp: Bitmap?) {
            _bmp.value = bmp!!
        }
    }
    var bitmap: BitmapViewModel = BitmapViewModel()

    fun dpFromPx(context: Context, px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    fun pxFromDp(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }


    @Composable
    fun FAB(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val displayMetrics = context.resources.displayMetrics
        val deviceWidth = displayMetrics.widthPixels / displayMetrics.density
        val deviceHeight = displayMetrics.heightPixels / displayMetrics.density
        val keyboardFocusRequester = FocusRequester()
        var keyboardInFocus by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current
        var keyboardInput by remember { mutableStateOf("") }
        var currentResolutionLevel by remember { mutableStateOf(1) }


        Scaffold(modifier = Modifier.fillMaxSize(), vignette = {
            OutlinedTextField(value = keyboardInput,
                onValueChange = {
                    keyboardInput = it
                },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(keyboardFocusRequester)
                    .alpha(0f)
                    .onFocusChanged {
                        if (!it.isFocused) {
                            remote.sendText(keyboardInput)
                            keyboardInput = ""
                            keyboardInFocus = false
                        }
                    }
            )
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(
                    modifier = Modifier.fillMaxHeight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        remote.sendBtnBack()
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                    Button(onClick = {
                        remote.sendBtnHome()
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(painter = painterResource(R.drawable.circle_24px), contentDescription = "Home")
                    }
                    Button(onClick = {
                        remote.sendBtnMultitask()
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(painter = painterResource(R.drawable.square_24px), contentDescription = "Menu")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight(0.9f)
                        .width(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        if (keyboardInFocus) {
                            focusManager.clearFocus()
                        } else {
                            keyboardFocusRequester.requestFocus()
                        }
                        keyboardInFocus = !keyboardInFocus
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(painterResource(R.drawable.keyboard_24px), contentDescription = "Keyboard")
                    }
                    Button(onClick = {
                        remote.sendBtnPower()
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(painter = painterResource(R.drawable.power_settings_new_24px), contentDescription = "Power")
                    }
                    Button(onClick = {
                        if (currentResolutionLevel == 1) {
                            remote.sendCompensationRequest(0f)
                        } else {
                            remote.sendCompensationRequest(1f)
                        }
                        currentResolutionLevel = (currentResolutionLevel + 1) % 2
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(painter = painterResource((currentResolutionLevel).let {
                            if (it == 0) {
                                R.drawable.sd_24px
                            } else {
                                R.drawable.hd_24px
                            }
                        }), contentDescription = "Resoluton")
                    }
                    Button(onClick = {
                        // back to main activity
                        remote.sendDestroy()
                        remote.disconnect()
                        finish()
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit")
                    }
                }
            }
        }, content = content)
    }

    @Preview(device = "id:wearos_rect", showSystemUi = false)
    @Composable
    fun Viewer() {
        val context = LocalContext.current
        // get device width and height (dp)
        val displayMetrics = context.resources.displayMetrics
        var bmpOffsetX = 0f
        var bmpOffsetY = 0f
        val state = rememberSwipeToDismissBoxState()

        FAB {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = bitmap.get().asImageBitmap(),
                    contentDescription = "Canvas Image",
                    modifier = Modifier
                        .height(dpFromPx(context, bitmap.get().height.toFloat()).dp)
                        .width(dpFromPx(context, bitmap.get().width.toFloat()).dp)
                        .onGloballyPositioned {
                            bmpOffsetX = it.positionInRoot().x
                            bmpOffsetY = it.positionInRoot().y
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()

                                    for (pointer in event.changes) {
                                        if (pointer.type != PointerType.Touch) {
                                            // ignore non-touch events
                                            continue
                                        }
                                        val curPosX = pointer.position.x
                                        val curPosY = pointer.position.y
                                        if (pointer.pressed && !pointer.previousPressed) {
                                            remote.sendTouchDown(
                                                curPosX.toInt(),
                                                curPosY.toInt(),
                                                pointer.id.value.toInt()
                                            )
                                        } else if (!pointer.pressed && pointer.previousPressed) {
                                            remote.sendTouchUp(
                                                curPosX.toInt(),
                                                curPosY.toInt(),
                                                pointer.id.value.toInt()
                                            )
                                        } else if (pointer.pressed && pointer.previousPressed) {
                                            remote.sendTouchMove(
                                                curPosX.toInt(),
                                                curPosY.toInt(),
                                                pointer.id.value.toInt()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                )
            }
        }
    }

    fun initializeCanvas(): Bitmap {
        val bitmap = createBitmap(remote.getConnStatus().host.width, remote.getConnStatus().host.height, Bitmap.Config.ARGB_8888)

        return bitmap
    }

    fun drawFrame( frameUpdateFull: FrameUpdateFull): Bitmap? {
        return BitmapFactory.decodeByteArray(frameUpdateFull.frame, 0, frameUpdateFull.frame.size)
    }

    @Composable
    fun Connecting() {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator( modifier = Modifier
                .width(50.dp)
                .height(50.dp))
            Text("Connecting to Socket.IO server")
        }
    }

    @Composable
    fun ErrorAlert(message: String) {
        remoteAlert(alertMessage = message, alertStyle = "error") {
            try {
                remote.disconnect()
            } finally {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)

        val metrics = resources.displayMetrics
        val serverAddr = intent.extras!!.getString("serverAddr")
        val secondStepToken = intent.extras!!.getString("secondStepToken")
        remote.initiateSocketConnection(serverAddr!!, secondStepToken!!, metrics) { e ->
            if (e is BaseXRemoteException) {
                setContent {
                    ErrorAlert(message = e.message!!)
                }
            } else if (e is Exception) {
                throw e
            } else {
                remote.setOnFrameReceivedListener {
                    bitmap.set(drawFrame(it))
                }
                bitmap.set(initializeCanvas())

                setContent {
                    Viewer()
                }
            }
        }

        setContent {
            Connecting()
        }
    }
}
