import okhttp3.*
import okio.ByteString

class WebSocketManager(private val serverUrl: String) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("WebSocket 연결 성공!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("서버 응답: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("WebSocket 오류: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                println("WebSocket 닫힘: $reason")
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }
}
