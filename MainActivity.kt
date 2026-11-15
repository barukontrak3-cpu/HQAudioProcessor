package com.hqaudio.processor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val projectionRequest = 4102
    private val audioPermissionRequest = 4103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 24, 39)
        window.navigationBarColor = Color.rgb(16, 24, 39)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 48, 28, 28)
            setBackgroundColor(Color.rgb(16, 24, 39))
        }
        val title = TextView(this).apply {
            text = "HQ AUDIO PROCESSOR"
            textSize = 24f
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "SYSTEM CAPTURE • STEREO 2 IN / 2 OUT • 48 kHz"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10, 0, 30)
        }
        status = TextView(this).apply {
            text = "Status: siap. Hubungkan Bluetooth sebelum memulai."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 24)
        }
        val start = Button(this).apply {
            text = "MULAI SYSTEM CAPTURE"
            setOnClickListener { requestCapture() }
        }
        val stop = Button(this).apply {
            text = "STOP DSP"
            setOnClickListener {
                startService(Intent(this@MainActivity, AudioCaptureService::class.java).setAction(AudioCaptureService.ACTION_STOP))
                status.text = "Status: perintah berhenti dikirim."
            }
        }
        val note = TextView(this).apply {
            text = "Catatan: Android hanya mengizinkan capture dari aplikasi yang mengaktifkan playback capture. Audio asli dari aplikasi sumber mungkin tetap terdengar bersama output DSP."
            textSize = 13f
            setTextColor(Color.rgb(255, 205, 120))
            setPadding(0, 28, 0, 0)
        }
        root.addView(title); root.addView(subtitle); root.addView(status)
        root.addView(start); root.addView(stop); root.addView(note)
        setContentView(root)
    }

    private fun requestCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionRequest)
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), projectionRequest)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioPermissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestCapture()
        } else if (requestCode == audioPermissionRequest) {
            status.text = "Izin mikrofon/audio capture ditolak."
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "Status: meminta akses capture. Periksa notifikasi layanan."
        } else if (requestCode == projectionRequest) {
            status.text = "Status: izin capture dibatalkan."
        }
    }
}
