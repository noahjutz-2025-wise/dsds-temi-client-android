package com.noahjutz.kinetiquery.ui

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noahjutz.kinetiquery.BuildConfig
import com.robotemi.sdk.Robot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class Role { User, Bot }

data class Transcript(
    val text: String,
    val role: Role,
    val id: Int
)

private const val API = BuildConfig.PIPECAT_SERVER_URL

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = MessageSerializer::class)
sealed class Message {
    @Serializable
    sealed class Rtvi : Message() {
        @Serializable
        @JsonIgnoreUnknownKeys
        object BotLlmStarted : Rtvi()

        @Serializable
        @JsonIgnoreUnknownKeys
        data class BotLlmText(
            val data: BotLlmTextData
        ) : Rtvi() {
            @Serializable
            data class BotLlmTextData(
                val text: String
            )
        }

        @Serializable
        @JsonIgnoreUnknownKeys
        data class BotOutput(
            val data: BotOutputData
        ) : Rtvi() {
            @Serializable
            data class BotOutputData(
                val text: String,
                val spoken: Boolean,
                val aggregatedBy: String
            )
        }

        @Serializable
        @JsonIgnoreUnknownKeys
        data class BotTtsText(
            val data: BotLlmTextData
        ) : Rtvi() {
            @Serializable
            data class BotLlmTextData(
                val text: String
            )
        }

        @Serializable
        @JsonIgnoreUnknownKeys
        object BotLlmStopped : Rtvi()

        @Serializable
        @JsonIgnoreUnknownKeys
        object UserStartedSpeaking : Rtvi()

        @Serializable
        @JsonIgnoreUnknownKeys
        data class UserTranscription(
            val data: UserTranscriptionData
        ) : Rtvi() {
            @Serializable
            @JsonIgnoreUnknownKeys
            data class UserTranscriptionData(
                val text: String,
                val final: Boolean
            )
        }

        @Serializable
        @JsonIgnoreUnknownKeys
        object UserStoppedSpeaking : Rtvi()
    }

    @Serializable
    @JsonIgnoreUnknownKeys
    data class RobotAction(
        val action: String
    ) : Message()
}

object MessageSerializer : JsonContentPolymorphicSerializer<Message>(Message::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Message> {
        return when (val label = element.jsonObject["label"]?.jsonPrimitive?.content) {
            "rtvi-ai" -> when (val type = element.jsonObject["type"]?.jsonPrimitive?.content) {
                "bot-tts-text" -> Message.Rtvi.BotTtsText.serializer()
                "bot-llm-text" -> Message.Rtvi.BotLlmText.serializer()
                "bot-llm-stopped" -> Message.Rtvi.BotLlmStopped.serializer()
                "user-transcription" -> Message.Rtvi.UserTranscription.serializer()
                "user-stopped-speaking" -> Message.Rtvi.UserStoppedSpeaking.serializer()
                "bot-llm-started" -> Message.Rtvi.BotLlmStarted.serializer()
                "user-started-speaking" -> Message.Rtvi.UserStartedSpeaking.serializer()
                "bot-output" -> Message.Rtvi.BotOutput.serializer()
                else -> throw IllegalArgumentException("Unknown type: $type")
            }

            "robot-action" -> Message.RobotAction.serializer()
            else -> throw IllegalArgumentException("Unknown label: $label")
        }
    }
}

@Serializable
data class LivekitResponse(
    val url: String,
    val room: String,
    val token: String
)

class MainVM(private val application: Application) : AndroidViewModel(application) {
    @SuppressLint("MissingPermission")
    suspend fun connect() {
        disconnect()
        Log.d("MainVM", "Connecting")
        try {
            val response = client.get("$API/api/livekit")
            val body = response.body<LivekitResponse>()
            Log.d("MainVM", "body: $body")
            _url.value = body.url
            _token.value = body.token
        } catch (e: Exception) {
            _error.value = e
        }

        // mockChat()
    }

    fun disconnect() {
        Log.d("MainVM", "Disconnecting")
        _url.value = null
        _token.value = null
        _error.value = null
        _currentTranscript.value = emptyBotTranscript
        _transcriptHistory.value = emptyList()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(
                        username = "",
                        password = BuildConfig.PIPECAT_BASIC_PASSWORD
                    )
                }
            }
        }
    }

    private val _isMuted = MutableStateFlow(false)
    val isMuted: Flow<Boolean>
        get() = _isMuted

    private val _url = MutableStateFlow<String?>(null)
    val url: Flow<String?> get() = _url

    private val _token = MutableStateFlow<String?>(null)
    val token: Flow<String?> get() = _token

    private val _error = MutableStateFlow<Exception?>(null)
    val error: Flow<Exception?> get() = _error

    private val _currentTranscript = MutableStateFlow(emptyBotTranscript)
    val currentTranscript: Flow<Transcript> get() = _currentTranscript

    private val _transcriptHistory = MutableStateFlow<List<Transcript>>(emptyList())
    val transcriptHistory: Flow<List<Transcript>> get() = _transcriptHistory

    private var transcriptId = 0

    init {
        viewModelScope.launch {
            isMuted.collect { isMuted ->
                if (isMuted) {
                    disconnect()
                } else {
                    disconnect()
                    connect()
                }
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun unmute() {
        _isMuted.value = false
    }

    fun mute() {
        _isMuted.value = true
    }

    fun onMessage(msg: String) {
        Log.d("MainVM", "onMessage: $msg")
        try {
            when (val message: Message = json.decodeFromString(msg)) {
                is Message.RobotAction -> {
                    when (message.action) {
                        "follow_me" -> Robot.getInstance().beWithMe()
                        "stop_moving" -> Robot.getInstance().stopMovement()
                    }
                }

                is Message.Rtvi -> {
                    when (message) {
                        is Message.Rtvi.BotLlmText -> {
                        }

                        is Message.Rtvi.UserTranscription -> {
                            viewModelScope.launch {
                                delay(200)
                                _currentTranscript.update { transcript ->
                                    transcript.copy(
                                        text = message.data.text
                                    )
                                }
                            }
                        }

                        Message.Rtvi.BotLlmStarted -> {
                            copyToHistory(_currentTranscript.value)
                            _currentTranscript.value = emptyBotTranscript.copy(id = transcriptId++)
                        }

                        Message.Rtvi.BotLlmStopped -> {
                        }

                        Message.Rtvi.UserStartedSpeaking -> {
                            copyToHistory(_currentTranscript.value)
                            _currentTranscript.value = emptyUserTranscript.copy(id = transcriptId++)
                        }

                        Message.Rtvi.UserStoppedSpeaking -> {
                        }

                        is Message.Rtvi.BotOutput -> {
                        }

                        is Message.Rtvi.BotTtsText -> {
                            _currentTranscript.update { transcript ->
                                transcript.copy(
                                    text = (transcript.text + " " + message.data.text).trim()
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyToHistory(transcript: Transcript) {
        if (transcript.text.isBlank()) return
        _transcriptHistory.update { history ->
            (history + transcript).takeLast(20)
        }
    }

    private suspend fun mockChat() {
        for (i in 10..Int.MAX_VALUE) {
            val role = if (i % 2 == 0) Role.User else Role.Bot
            val text = if (role == Role.User) "Hello World! $i" else "Hi $i ".repeat(40)
            for (i in 0..text.lastIndex) {
                _currentTranscript.value = _currentTranscript.value.copy(
                    text = text.take(i),
                )
                delay(1)
            }
            copyToHistory(_currentTranscript.value)
            _currentTranscript.value = Transcript(
                text = "",
                role = role,
                id = i
            )
            delay(500)
        }
    }

    companion object {
        val emptyBotTranscript = Transcript("", Role.Bot, 0)
        val emptyUserTranscript = Transcript("", Role.User, 0)
    }
}
