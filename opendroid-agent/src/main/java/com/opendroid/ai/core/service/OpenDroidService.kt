package com.opendroid.ai.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.opendroid.ai.core.agent.AgentLoop
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.core.voice.TextToSpeechEngine
import com.opendroid.ai.core.voice.WakeWordDetector
import com.opendroid.ai.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceInputUiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val error: String? = null
)

@AndroidEntryPoint
class OpenDroidService : Service() {

    @Inject
    lateinit var agentLoop: AgentLoop

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var speechRecognitionEngine: SpeechRecognitionEngine
    private lateinit var textToSpeechEngine: TextToSpeechEngine

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var showFloatingButton = false
    private var voiceStartJob: Job? = null

    companion object {
        const val ACTION_TRIGGER_RECORD = "com.opendroid.ai.action.TRIGGER_RECORD"
        const val ACTION_CANCEL_RECORD = "com.opendroid.ai.action.CANCEL_RECORD"
        private const val CHANNEL_ID = "opendroid_channel"
        private const val NOTIFICATION_ID = 2024

        private val _voiceInputState = MutableStateFlow(VoiceInputUiState())
        val voiceInputState: StateFlow<VoiceInputUiState> = _voiceInputState.asStateFlow()
        
        fun start(context: Context) {
            val intent = Intent(context, OpenDroidService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestVoiceInput(context: Context) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                _voiceInputState.value = VoiceInputUiState(
                    error = "录音权限未开启，请在系统权限中允许麦克风。"
                )
                return
            }

            _voiceInputState.value = VoiceInputUiState(isListening = true)
            try {
                val intent = Intent(context, OpenDroidService::class.java).apply {
                    action = ACTION_TRIGGER_RECORD
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                _voiceInputState.value = VoiceInputUiState(
                    error = "无法启动系统语音服务：${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }

        fun cancelVoiceInput(context: Context) {
            val intent = Intent(context, OpenDroidService::class.java).apply {
                action = ACTION_CANCEL_RECORD
            }
            context.startService(intent)
            _voiceInputState.value = VoiceInputUiState()
        }

        fun stop(context: Context) {
            val intent = Intent(context, OpenDroidService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize engines
        wakeWordDetector = WakeWordDetector(this)
        speechRecognitionEngine = SpeechRecognitionEngine(this)
        textToSpeechEngine = TextToSpeechEngine(this, settingsRepository)

        // Bind Agent Loop TTS
        agentLoop.onSpeakCallback = { text ->
            textToSpeechEngine.speak(text)
        }

        // Set TTS completion listener to transition back to Idle
        textToSpeechEngine.onCompletionListener = {
            agentLoop.setAgentState(AgentState.Idle)
        }

        // Start Foreground Notification
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Monitor floating button config to start/stop wake word detection dynamically
        serviceScope.launch {
            settingsRepository.llmConfig.collectLatest { config ->
                showFloatingButton = config.showFloatingButton
                if (showFloatingButton) {
                    wakeWordDetector.stopListening()
                } else {
                    startWakeWordDetection()
                }
            }
        }
    }

    private fun startWakeWordDetection() {
        wakeWordDetector.startListening {
            // Wake word detected! Prompt user with a sound or greeting and start listing for query
            textToSpeechEngine.speak("OpenDroid online.")
            startListeningForQuery()
        }
    }

    private fun startListeningForQuery() {
        voiceStartJob?.cancel()

        // Temporarily pause wake word to avoid hearing itself
        wakeWordDetector.stopListening()
        textToSpeechEngine.stop()

        // Set agent state to Listening
        agentLoop.setAgentState(AgentState.Listening)
        _voiceInputState.value = VoiceInputUiState(isListening = true)

        // HyperOS needs a short hand-off after the wake-word recognizer releases
        // the microphone; starting the next recognizer immediately can return error 11.
        voiceStartJob = serviceScope.launch {
            delay(300)
            speechRecognitionEngine.startListening(
                onResult = { query ->
                    voiceStartJob = null
                    _voiceInputState.value = VoiceInputUiState()
                    agentLoop.processQuery(query, this@OpenDroidService)
                    // Resume wake word detection only if floating button is disabled
                    if (!showFloatingButton) {
                        wakeWordDetector.startListening {
                            textToSpeechEngine.speak("OpenDroid online.")
                            startListeningForQuery()
                        }
                    } else {
                        agentLoop.setAgentState(AgentState.Idle)
                    }
                },
                onPartialResult = { partial ->
                    _voiceInputState.value = VoiceInputUiState(
                        isListening = true,
                        partialText = partial
                    )
                },
                onError = { error ->
                    voiceStartJob = null
                    _voiceInputState.value = VoiceInputUiState(
                        error = friendlyVoiceError(error)
                    )
                    agentLoop.setAgentState(AgentState.Idle)
                    // Resume wake word detection only if floating button is disabled
                    if (!showFloatingButton) {
                        wakeWordDetector.startListening {
                            textToSpeechEngine.speak("OpenDroid online.")
                            startListeningForQuery()
                        }
                    }
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_RECORD -> startListeningForQuery()
            ACTION_CANCEL_RECORD -> {
                voiceStartJob?.cancel()
                voiceStartJob = null
                speechRecognitionEngine.cancelListening()
                _voiceInputState.value = VoiceInputUiState()
                agentLoop.setAgentState(AgentState.Idle)
                if (!showFloatingButton) startWakeWordDetection()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeWordDetector.destroy()
        speechRecognitionEngine.destroy()
        textToSpeechEngine.destroy()
        _voiceInputState.value = VoiceInputUiState()
    }

    private fun friendlyVoiceError(error: String): String = when {
        error.contains("not available", ignoreCase = true) ->
            "系统没有可用的语音识别服务，请检查系统语音助手设置。"
        error.contains("permission", ignoreCase = true) ->
            "录音权限未开启，请在系统权限中允许麦克风。"
        error.contains("network", ignoreCase = true) ->
            "语音识别网络不可用，请检查网络后重试。"
        error.contains("No speech", ignoreCase = true) ||
            error.contains("timeout", ignoreCase = true) ->
            "没有识别到语音，请靠近麦克风后重试。"
        error.contains("disconnected", ignoreCase = true) ||
            error.contains("(11)", ignoreCase = true) ->
            "系统语音识别服务已断开，请稍等一秒后重试。"
        error.contains("busy", ignoreCase = true) ||
            error.contains("too many", ignoreCase = true) ->
            "语音识别服务正忙，请稍等一秒后重试。"
        error.contains("language", ignoreCase = true) ->
            "系统中文语音识别不可用，请在系统语音服务中启用中文。"
        else -> "语音输入失败：$error"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenDroid Agent Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps OpenDroid background agent alive"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenDroid Active")
            .setContentText("Listening for wake word 'OpenDroid'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}
