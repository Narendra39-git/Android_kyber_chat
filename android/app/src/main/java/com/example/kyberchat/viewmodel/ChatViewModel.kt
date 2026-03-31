package com.example.kyberchat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kyberchat.crypto.CryptoManager
import com.example.kyberchat.data.AppDatabase
import com.example.kyberchat.data.MessageEntity
import com.example.kyberchat.data.SessionManager
import com.example.kyberchat.network.ApiService
import com.example.kyberchat.network.Message
import com.example.kyberchat.network.RegisterRequest
import com.example.kyberchat.network.LogRequest
import com.example.kyberchat.network.WebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Base64

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val messageDao = database.messageDao()

    private val _clientId = MutableStateFlow(sessionManager.getClientId() ?: "")
    val clientId = _clientId.asStateFlow()

    val isLoggedIn = _clientId.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Contacts list (derived from distinct senders/recipients in DB)
    val contacts = _clientId.flatMapLatest { myId ->
        if (myId.isNotEmpty()) messageDao.getRecentContacts(myId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    // Logs for debug
    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs = _debugLogs.asStateFlow()

    private val baseUrl = "http://192.168.132.26:8000"
    private val wsUrl = "ws://192.168.132.26:8000/ws"

    private val apiService: ApiService
    private val webSocketClient: WebSocketClient

    val isConnected: StateFlow<Boolean> get() = webSocketClient.isConnected

    init {
        // Restore Crypto Keys if logged in
        val storedKyber = sessionManager.getKyberPrivKey()
        val storedDilithium = sessionManager.getDilithiumPrivKey()
        if (!storedKyber.isNullOrEmpty() && !storedDilithium.isNullOrEmpty()) {
            try {
                CryptoManager.loadKeys(storedKyber, storedDilithium)
            } catch (e: Exception) {
                e.printStackTrace()
                sessionManager.clearSession()
                _clientId.value = ""
            }
        }

        val okHttpClient = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)
        webSocketClient = WebSocketClient(okHttpClient)

        // Connect WS if logged in
        if (_clientId.value.isNotEmpty()) {
            connectWebSocket(_clientId.value)
        }

        viewModelScope.launch {
            launch {
                webSocketClient.messageFlow.collect { msg ->
                    handleIncomingMessage(msg)
                }
            }
        }
    }
    
    fun getMessages(contactId: String): Flow<List<MessageEntity>> {
         return messageDao.getMessagesForContact(_clientId.value, contactId)
    }

    fun register(newClientId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                log("Registering as $newClientId...")
                // 1. Generate Keys
                val kyberPk = CryptoManager.generateKyberKeys()
                val dilithiumPk = CryptoManager.generateDilithiumKeys()

                // 2. Register with Backend
                val response = apiService.register(RegisterRequest(newClientId, kyberPk, dilithiumPk))
                
                if (response.isSuccessful) {
                    // 3. Save Session
                    val kyberPriv = CryptoManager.getKyberPrivateKeyB64() ?: ""
                    val dilithiumPriv = CryptoManager.getDilithiumPrivateKeyB64() ?: ""
                    
                    sessionManager.saveSession(newClientId, kyberPriv, dilithiumPriv)
                    _clientId.value = newClientId
                    
                    connectWebSocket(newClientId)
                    log("Registration Successful!")
                } else {
                    log("Registration Failed: ${response.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                log("Error: ${e.message}")
            }
        }
    }

    // connectWebSocket moved to bottom to resolve duplication

    fun logout() {
        sessionManager.clearSession()
        _clientId.value = ""
        webSocketClient.close()
    }

    fun deleteChat(contactId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.deleteChat(_clientId.value, contactId)
        }
    }

    fun sendMessage(recipientId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                log("Sending to $recipientId...")
                // 1. Get Recipient Key
                val keyResponse = apiService.getKey(recipientId)
                if (!keyResponse.isSuccessful || keyResponse.body() == null) {
                    log("Error: Recipient $recipientId not found")
                    return@launch
                }
                val recipientKyberPk = keyResponse.body()!!.kyberPublicKey

                // 2. Encapsulate & Encrypt
                val (ciphertext, sharedSecret) = CryptoManager.encapsulate(recipientKyberPk)
                val messageAesKey = CryptoManager.deriveAesKey(sharedSecret)
                val (encryptedContent, nonce) = CryptoManager.encrypt(content, messageAesKey)

                // 3. Sign
                val clientIdBytes = _clientId.value.toByteArray(Charsets.UTF_8)
                val recipientIdBytes = recipientId.toByteArray(Charsets.UTF_8)
                val encryptedContentBytes = encryptedContent.toByteArray(Charsets.UTF_8)
                val nonceBytes = nonce.toByteArray(Charsets.UTF_8)
                val ciphertextBytes = ciphertext.toByteArray(Charsets.UTF_8)
                val payloadToSign = clientIdBytes + recipientIdBytes + encryptedContentBytes + nonceBytes + ciphertextBytes
                val signature = CryptoManager.sign(payloadToSign)

                // 4. Send
                val message = Message(
                    senderId = _clientId.value,
                    recipientId = recipientId,
                    content = encryptedContent,
                    signature = signature,
                    nonce = nonce,
                    kemCiphertext = ciphertext
                )
                webSocketClient.sendMessage(message)

                // 5. Save to DB
                val entity = MessageEntity(
                    senderId = _clientId.value,
                    recipientId = recipientId,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = true
                )
                messageDao.insertMessage(entity)
                
            } catch (e: Exception) {
                e.printStackTrace()
                log("Send Error: ${e.message}")
            }
        }
    }

    private fun handleIncomingMessage(msg: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Decapsulate
                val sharedSecret = CryptoManager.decapsulate(msg.kemCiphertext)
                val messageAesKey = CryptoManager.deriveAesKey(sharedSecret)

                // 2. Decrypt
                val decryptedContent = CryptoManager.decrypt(msg.content, msg.nonce, messageAesKey)
                
                // 3. Save to DB (Normal Message)
                val entity = MessageEntity(
                    senderId = msg.senderId,
                    recipientId = _clientId.value,
                    content = decryptedContent,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = false
                )
                messageDao.insertMessage(entity)
                
            } catch (e: Exception) {
                e.printStackTrace()
                log("Decrypt Error: ${e.message}")
                try {
                    apiService.sendLog(LogRequest("DECRYPT_ERROR [$_clientId -> local]: ${e.message}\nStack: ${e.stackTraceToString()}"))
                } catch (e2: Exception) {
                    // ignore log send error
                }
            }
        }
    }
    
    // WebRTC Login removed by user request
    
    private fun connectWebSocket(id: String) {
        webSocketClient.connect("$wsUrl/$id")
    }
    
    // ...
    
    private fun log(msg: String) {
        println("KyberChat: $msg")
        _debugLogs.value = _debugLogs.value + msg
    }
}
