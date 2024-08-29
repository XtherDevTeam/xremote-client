package com.jerrychau.xremote
import android.util.DisplayMetrics
import io.socket.client.IO
import io.socket.client.Socket
import com.github.kittinunf.fuel.*
import com.github.kittinunf.result.Result
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@Serializable
data class ErrorResponse(
    val message: String,
)

@Serializable
data class HandshakeRequest(
    val token: String,
    val guest_width: Int,
    val guest_height: Int,
)

@Serializable
data class PeerInfo (
    val width: Int,
    val height: Int,
)

@Serializable
data class ConnectionStatus(
    val host: PeerInfo,
    val guest: PeerInfo,
    val timestamp: Long
)

@Serializable
data class HandshakeResponse(
    val connection_status: ConnectionStatus
)

@Serializable
data class InitiateResponse(
    val status: String,
    val error: String? = null,
    val token: String? = null,
)

@Serializable
data class FrameUpdateFull(
    val timestamp: Long,
    val length: Long,
    val frame: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrameUpdateFull

        if (timestamp != other.timestamp) return false
        if (length != other.length) return false
        if (!frame.contentEquals(other.frame)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + length.hashCode()
        result = 31 * result + frame.contentHashCode()
        return result
    }
}


@Serializable
data class InputEvent(
    /**
     * The type of the event.
     * Possible values: touch_down, touch_up, touch_move, text, btn_power, btn_back, btn_multitask, btn_home, backspace
     */
    val type: String,
    val touch_x: Int? = null,
    val touch_y: Int? = null,
    val touch_id: Int? = null,
    val text: String? = null,
    val btn_id: String? = null,
)


fun unpackFrameData(packedData: ByteArray): FrameUpdateFull {
    // 将字节数组转换为 ByteBuffer
    val buffer = ByteBuffer.wrap(packedData).order(ByteOrder.LITTLE_ENDIAN)

    // 读取时间戳、长度和帧数据
    val timestamp = buffer.long
    val length = buffer.long
    val frame = ByteArray(length.toInt())
    buffer.get(frame)

    // 返回 FrameData 对象
    return FrameUpdateFull(timestamp, length, frame)
}

@Serializable
data class SetCompensationRatioRequest(
    val ratio: Float,
)

/**
 * Remote class for interacting with the server.
 * @author Jerry Chau
 */
class Remote {
    var socketIO: Socket? = null
    var secondStepToken: String? = null
    var displayMetrics: DisplayMetrics? = null
    var connectionStatus: ConnectionStatus? = null
    var onFrameReceived: ((FrameUpdateFull) -> Unit)? = null
    var lastFrameTimestamp: Long = 0
    var connectTimestamp: Long = 0
    var frameCountIn2S: Long = 0
    var compensationRatio: Float = 1.0f
    var server: String = ""

    fun getConnStatus(): ConnectionStatus {
        return connectionStatus!!
    }


    fun setOnFrameReceivedListener(listener: (FrameUpdateFull) -> Unit) {
        onFrameReceived = listener
    }

    /**
     * Disconnect from the server and revert to the initial state.
     */
    fun disconnect() {
        socketIO?.disconnect()
        socketIO = null
        connectionStatus = null
    }


    /**
     * Check if the remote is connected.
     */
    fun connected(): Boolean {
        return connectionStatus != null
    }



    /**
     * Register socket.io event listeners.
     */
    fun initiateSocketIO() {
        socketIO?.on("connected") {
            connectionStatus = Json.decodeFromString<HandshakeResponse>(it[0].toString()).connection_status
            connectTimestamp = System.currentTimeMillis()
            println("Established proper connection successfully")
        }
        socketIO?.on("error") {
            val error = Json.decodeFromString<ErrorResponse>(it[0].toString())
            throw RemoteException(error.message)
        }
        socketIO?.on("frame_update_full") {
            val currentTime = System.currentTimeMillis() - connectTimestamp
            val frame = unpackFrameData(it[0] as ByteArray)
            val frameTime = frame.timestamp
            val latency = abs(currentTime - frameTime)
            onFrameReceived?.invoke(frame)
            frameCountIn2S++

            if (System.currentTimeMillis() - lastFrameTimestamp > 2000) {
                val fps = frameCountIn2S / 2f
                println("Frame rate: ${fps} FPS | Latency ${latency} ms")
                lastFrameTimestamp = System.currentTimeMillis()
                frameCountIn2S = 0
            }
            if (latency > 5000) {
                reconnect()
            }
        }
    }

    /**
     * Send a handshake message to the server.
     */
    fun sendHandshake() {
        // get current screen resolution
        val width = displayMetrics?.widthPixels
        val height = displayMetrics?.heightPixels
        val handshake = HandshakeRequest(secondStepToken!!, width!!, height!!)
        val json = Json.encodeToString(HandshakeRequest.serializer(), handshake)
        socketIO?.emit("handshake", json)
    }

    fun initiateSocketConnection(server: String, secondStepToken: String, metrics: DisplayMetrics, onFinished: (error: Exception?) -> Unit) {
        this.displayMetrics = metrics
        this.secondStepToken = secondStepToken
        this.server = server
        println("Initiating socket connection ${this.secondStepToken} ${secondStepToken}")
        socketIO = IO.socket("ws://$server/").connect()
        socketIO?.on(Socket.EVENT_CONNECT_ERROR) {
            throw ConnectionFailureException("Unable to connect to Socket.IO server")
        }
        socketIO?.on(Socket.EVENT_CONNECT) {
            try {
                if (!connected()) {
                    println("First time connection, sending handshake")
                    initiateSocketIO()
                    sendHandshake()
                    println("Handshake sent, preparing to receive frames")
                    socketIO?.on("connected") {
                        onFinished(null)
                    }
                }
            } catch (e: Exception) {
                onFinished(e)
            }
        }
    }

    /**
     * Initiate a connection with the server.
     * @param server The server address.
     * @param token The token generated by the server.
     * @param metrics The display metrics of the device.
     * @throws ConnectionFailureException If the connection fails.
     */
    fun initiateConnection(server: String, token: String, metrics: DisplayMetrics, onFinished: (secondStepToken: String?, error: Exception?) -> Unit) {
        displayMetrics = metrics
        // make http request to server to initiate connection
        "http://$server/initiate".httpGet(listOf("token" to token)).responseString { req, resp, result ->
            when (result) {
                is Result.Failure -> {
                    println("Error: ${result.getException()}")
                    onFinished(null, result.getException())
                }
                is Result.Success -> {
                    println(result.get())
                    val initResp = Json.decodeFromString<InitiateResponse>(result.get())
                    if (initResp.status == "ok") {
                        // establish socket connection with server
                        secondStepToken = initResp.token
                        onFinished(secondStepToken, null)
                    } else {
                        onFinished(null, ConnectionFailureException(initResp.error))
                    }
                }
            }
        }

    }

    fun sendCompensationRequest(expectedRatio: Float = 1.0f) {
        val req = SetCompensationRatioRequest(expectedRatio)
        val json = Json.encodeToString(SetCompensationRatioRequest.serializer(), req)
        socketIO?.emit("set_compensation_ratio", json)
    }

    fun sendBtnBack() {
        val event = InputEvent("btn_back")
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun sendBtnHome() {
        val event = InputEvent("btn_home")
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun sendBtnPower() {
        val event = InputEvent("btn_power")
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun sendBtnMultitask() {
        val event = InputEvent("btn_multitask")
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun sendTouchDown(x: Int, y: Int, id: Int) {
        val event = InputEvent("touch_down", x, y, id)
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun sendTouchUp(x: Int, y: Int, id: Int) {
        val event = InputEvent("touch_up", x, y, id)
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun sendTouchMove(x: Int, y: Int, id: Int) {
        val event = InputEvent("touch_move", x, y, id)
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun sendDestroy() {
        socketIO?.emit("destroy", "")
    }

    fun sendText(text: String) {
        val event = InputEvent("text", text = text)
        val json = Json.encodeToString(InputEvent.serializer(), event)
        socketIO?.emit("input_event", json)
    }

    fun reconnect() {
        println("Latency is too high! Reconnecting...")
        disconnect()
        initiateSocketConnection(server, secondStepToken!!, displayMetrics!!) {
            when (it) {
                null -> {
                    println("Reconnected successfully")
                }
                else -> {
                    println("Reconnection failed: ${it.message}")
                }
            }
        }
    }
}