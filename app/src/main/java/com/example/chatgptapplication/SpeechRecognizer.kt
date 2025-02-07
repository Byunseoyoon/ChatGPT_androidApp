package com.example.chatgptapplication

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

class SpeechRecognizer : AppCompatActivity() {
    private lateinit var audioManager: AudioRecordManager
    private lateinit var viewModel: RecordingViewModel
    private var isRecording = false

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech)

        viewModel = ViewModelProvider(this).get(RecordingViewModel::class.java)
        audioManager = AudioRecordManager(applicationContext)

        checkPermissions()
        setupObservers()
        startDecibelMonitoring()
    }

    private fun checkPermissions() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions,
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun setupObservers() {
        viewModel.decibelLevel.observe(this) { level ->
            if (level > DECIBEL_THRESHOLD && !isRecording) {
                startRecording()
            } else if (level < DECIBEL_THRESHOLD && isRecording) {
                stopRecording()
            }
        }
    }

    private fun startDecibelMonitoring() {
        lifecycleScope.launch {
            while (true) {
                val decibelLevel = audioManager.getCurrentDecibelLevel()
                viewModel.updateDecibelLevel(decibelLevel)
                delay(100) // 100ms 간격으로 체크
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        audioManager.startRecording()
        viewModel.updateRecordingState(true)
    }

    private fun stopRecording() {
        isRecording = false
        audioManager.stopRecording()
        viewModel.updateRecordingState(false)
        processRecording()
    }

    private fun processRecording() {
        lifecycleScope.launch {
            val audioFile = audioManager.getLastRecordingFile()
            // TODO: 음성을 텍스트로 변환하는 로직 추가
            viewModel.processAudioFile(audioFile)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val DECIBEL_THRESHOLD = 60.0 // 조절 가능한 데시벨 임계값
    }
}

// AudioRecordManager.kt
class AudioRecordManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingFile: File? = null

    fun startRecording() {
        if (isRecording) return

        val fileName = "recording_${System.currentTimeMillis()}.pcm"
        recordingFile = File(context.getExternalFilesDir(null), fileName)

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            writeAudioDataToFile()
        }
    }

    private suspend fun writeAudioDataToFile() {
        val buffer = ShortArray(BUFFER_SIZE)
        recordingFile?.outputStream()?.use { outputStream ->
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, BUFFER_SIZE) ?: -1
                if (readSize > 0) {
                    outputStream.write(
                        buffer.toByteArray(),
                        0,
                        readSize * 2
                    )
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun getCurrentDecibelLevel(): Double {
        val buffer = ShortArray(BUFFER_SIZE)
        audioRecord?.read(buffer, 0, BUFFER_SIZE)

        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        val rms = sqrt(sum / BUFFER_SIZE)
        return 20 * log10(rms)
    }

    fun getLastRecordingFile(): File? = recordingFile

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE = 1024
    }
}

// RecordingViewModel.kt
class RecordingViewModel : ViewModel() {
    private val _decibelLevel = MutableLiveData<Double>()
    val decibelLevel: LiveData<Double> = _decibelLevel

    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> = _isRecording

    fun updateDecibelLevel(level: Double) {
        _decibelLevel.postValue(level)
    }

    fun updateRecordingState(isRecording: Boolean) {
        _isRecording.postValue(isRecording)
    }

    fun processAudioFile(file: File?) {
        // TODO: 음성 파일 처리 로직 구현
    }
}