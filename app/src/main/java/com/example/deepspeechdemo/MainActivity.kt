package com.example.deepspeechdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.deepspeechdemo.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import linc.com.pcmdecoder.PCMDecoder
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var model: DeepSpeechModel? = null

    private var transcriptionThread: Thread? = null
    private var isRecording: AtomicBoolean = AtomicBoolean(false)

    private val TFLITE_MODEL_FILENAME = "deepspeech-0.9.3-models.tflite"
    private val SCORER_FILENAME = "deepspeech-0.9.3-models.scorer"

    private lateinit var cacheDirectory: File
    private lateinit var audioFile: File

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var granted = true
            permissions.entries.forEach {
                if (!it.value) granted = false
            }
            if (granted) {
                startListening()
            }
        }

    private fun checkAudioPermission(): Boolean {
        // Permission is automatically granted on SDK < 23 upon installation.
        if (Build.VERSION.SDK_INT >= 23) {
            val permission = Manifest.permission.RECORD_AUDIO

            return if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 3)
                false
            } else
                true
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun transcribe() {
        // We read from the recorder in chunks of 2048 shorts. With a model that expects its input
        // at 16000Hz, this corresponds to 2048/16000 = 0.128s or 128ms.
        val audioBufferSize = 2048
        val audioData = ShortArray(audioBufferSize)

        runOnUiThread { binding.btnStartInference.text = "Stop Recording" }

        model?.let { model ->
            val streamContext = model.createStream()

            Log.d("debugging", model.sampleRate().toString())

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                model.sampleRate(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSize
            )
            recorder.startRecording()

            /*while (isRecording.get()) {
                recorder.read(audioData, 0, audioBufferSize)
                model.feedAudioContent(streamContext, audioData, audioData.size)
                val decoded = model.intermediateDecode(streamContext)
                runOnUiThread { binding.transcription.text = decoded }
            }*/

            audioFile = File(cacheDirectory, "recording_${System.currentTimeMillis()}.pcm")
            val outputStream = FileOutputStream(audioFile)

            while (isRecording.get()) {
                recorder.read(audioData, 0, audioBufferSize)
                model.feedAudioContent(streamContext, audioData, audioData.size)
                val decoded = model.intermediateDecode(streamContext)
                runOnUiThread { binding.transcription.text = decoded }

                // Write the audio data to the cache file
                outputStream.write(convertShortArrayToByteArray(audioData))
            }

            // Close the output stream
            outputStream.close()

            val decoded = model.finishStream(streamContext)

            runOnUiThread {
                binding.btnStartInference.text = "Start Recording"
                binding.transcription.text = decoded
            }

            recorder.stop()
            recorder.release()
        }
    }

    private fun createModel(): Boolean {
        val modelsPath = getExternalFilesDir(null).toString()
        val tfliteModelPath = "$modelsPath/$TFLITE_MODEL_FILENAME"
        val scorerPath = "$modelsPath/$SCORER_FILENAME"

        for (path in listOf(tfliteModelPath, scorerPath)) {
            if (!File(path).exists()) {
                binding.status.append("Model creation failed: $path does not exist.\n")
                return false
            }
        }

        model = DeepSpeechModel(tfliteModelPath)
        model?.enableExternalScorer(scorerPath)

        return true
    }

    private fun startListening() {
        if (isRecording.compareAndSet(false, true)) {
            transcriptionThread = Thread(Runnable { transcribe() }, "Transcription Thread")
            transcriptionThread?.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkAudioPermission()

        // Create application data directory on the device
        val modelsPath = getExternalFilesDir(null).toString()

        binding.status.text = "Ready. Copy model files to \"$modelsPath\" if running for the first time.\n"

        binding.btnStartInference.setOnClickListener {
            if (model == null) {
                if (createModel()) {
                    return@setOnClickListener
                }
                binding.status.append("Created model.\n")
            }

            if (isRecording.get()) {
                stopListening()
            } else {
                initRecordAudioPermissionChecks(recordAudioPermissionLauncher) {
                    startListening()
                }
            }
        }

        // create
        cacheDirectory = File(cacheDir, "audio_cache")
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs()
        }

    }

    private fun stopListening() {
        isRecording.set(false)
        playAudio(model?.sampleRate() ?: 44100)

        try {
            val outputFile = File.createTempFile("audioRecordTest", ".3gp", externalCacheDir);
            if (outputFile.exists()) {
                Log.d("debugging", "Yes")
                PCMDecoder.encodeToMp3(
                    audioFile.absolutePath,     // Input PCM file
                    1,                                            // Number of channels
                    128000,                                        // Bit rate
                    (model?.sampleRate()?.div(2)) ?: 44100,                                        // Sample rate
                    "/storage/emulated/0/Music/decoded_audio.mp3" // Output MP3 file
                )
            }
        } catch ( e: IOException) {
            Log.e("debugging", "Error: " + e.message.toString());
        }
    }

    private fun playAudio(sampleRate: Int) {
        val audioBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            audioBufferSize,
            AudioTrack.MODE_STREAM
        )

        val audioData = ByteArray(audioBufferSize)
        val inputStream = FileInputStream(audioFile)

        audioTrack.play()

        var bytesRead: Int
        while (inputStream.read(audioData).also { bytesRead = it } != -1) {
            audioTrack.write(audioData, 0, bytesRead)
        }

        audioTrack.stop()
        audioTrack.release()

        inputStream.close()
    }


    override fun onDestroy() {
        super.onDestroy()
        if (model != null) {
            model?.freeModel()
        }
    }

    private fun convertShortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            val sample = shortArray[i].toInt()
            byteArray[i * 2] = (sample shr 0).toByte()
            byteArray[i * 2 + 1] = (sample shr 8).toByte()
        }
        return byteArray
    }
}
