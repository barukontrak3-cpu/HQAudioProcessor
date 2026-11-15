package com.hqaudio.processor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AudioCaptureService : Service() {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var dsp: NativeDsp? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopPipeline()
            ACTION_START -> {
                if (running.get()) stopPipeline()
                startForeground(NOTIFICATION_ID, buildNotification("Menyiapkan audio capture"))
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.parcelableIntent(EXTRA_RESULT_DATA)
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startPipeline(resultCode, data)
            }
        }
        return START_NOT_STICKY
    }

    private fun startPipeline(resultCode: Int, data: Intent) {
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val config = android.media.AudioPlaybackCaptureConfiguration.Builder(projection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            if (minIn <= 0) throw IllegalStateException("AudioRecord buffer tidak didukung: $minIn")
            val inBytes = maxOf(minIn * 4, BLOCK_FRAMES * 2 * 4)
            recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(inBytes)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            val outFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            if (minOut <= 0) throw IllegalStateException("AudioTrack buffer tidak didukung: $minOut")
            player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(outFormat)
                .setBufferSizeInBytes(maxOf(minOut * 4, BLOCK_FRAMES * 2 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            dsp = NativeDsp(SAMPLE_RATE).also {
                // Default compressor is gentle; EQ bands start flat.
                it.setCompressor(-12f, 2f, 10f, 120f)
            }
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopPipeline() }
            }, null)

            recorder?.startRecording()
            player?.play()
            running.set(true)
            updateNotification("DSP aktif • 48 kHz stereo")
            worker = thread(name = "HQ-DSP-Audio", isDaemon = true) { audioLoop() }
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal memulai pipeline", t)
            stopPipeline()
            updateNotification("Gagal: ${t.message ?: "audio tidak tersedia"}")
        }
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buffer = FloatArray(BLOCK_FRAMES * 2)
        while (running.get()) {
            val read = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: break
            if (read > 0) {
                dsp?.process(buffer, read)
                var written = 0
                while (written < read && running.get()) {
                    val n = player?.write(buffer, written, read - written, AudioTrack.WRITE_BLOCKING) ?: -1
                    if (n <= 0) break
                    written += n
                }
            } else if (read < 0) {
                Log.e(TAG, "AudioRecord read error: $read")
                break
            }
        }
        stopPipeline()
    }

    @Synchronized private fun stopPipeline() {
        running.set(false)
        // Clear shared references before stopping MediaProjection; its onStop callback may re-enter here.
        val oldRecorder = recorder
        val oldPlayer = player
        val oldDsp = dsp
        val oldProjection = projection
        recorder = null; player = null; dsp = null; projection = null
        try { oldRecorder?.stop() } catch (_: Exception) {}
        try { oldPlayer?.pause(); oldPlayer?.flush(); oldPlayer?.stop() } catch (_: Exception) {}
        try { oldRecorder?.release() } catch (_: Exception) {}
        try { oldPlayer?.release() } catch (_: Exception) {}
        try { oldDsp?.close() } catch (_: Exception) {}
        try { oldProjection?.stop() } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopPipeline(); super.onDestroy() }

    private fun buildNotification(message: String): Notification {
        val channelId = "hq_dsp_capture"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(NotificationChannel(channelId, "HQ DSP Capture", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("HQ Audio Processor").setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableIntent(key: String): Intent? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Intent::class.java) else getParcelableExtra(key)

    companion object {
        const val ACTION_START = "com.hqaudio.processor.START"
        const val ACTION_STOP = "com.hqaudio.processor.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val TAG = "HQAudioCapture"
        private const val NOTIFICATION_ID = 77
        private const val SAMPLE_RATE = 48_000
        private const val BLOCK_FRAMES = 256
    }
}
