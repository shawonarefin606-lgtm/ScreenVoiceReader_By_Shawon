package com.shawon.screenvoicereader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnStartReader: Button
    private lateinit var btnStopReader: Button
    private lateinit var tvSpeedLabel: TextView
    private lateinit var seekSpeed: SeekBar

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val prefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            val speed = prefs.getFloat("speech_rate", 1.0f)

            val serviceIntent = Intent(this, FloatingReaderService::class.java).apply {
                action = FloatingReaderService.ACTION_START
                putExtra(FloatingReaderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingReaderService.EXTRA_DATA, result.data)
                putExtra(FloatingReaderService.EXTRA_SPEECH_RATE, speed)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Floating bubble started!", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Screen capture permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnStartReader = findViewById(R.id.btnStartReader)
        btnStopReader = findViewById(R.id.btnStopReader)
        tvSpeedLabel = findViewById(R.id.tvSpeedLabel)
        seekSpeed = findViewById(R.id.seekSpeed)

        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val prefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        val savedRate = prefs.getFloat("speech_rate", 1.0f)
        val initialProgress = ((savedRate - 0.5f) * 10f).toInt().coerceIn(0, 15)
        seekSpeed.progress = initialProgress
        tvSpeedLabel.text = String.format("Reading Speed: %.1fx", savedRate)

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress * 0.1f)
                tvSpeedLabel.text = String.format("Reading Speed: %.1fx", rate)
                prefs.edit().putFloat("speech_rate", rate).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnOverlayPermission.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnStartReader.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay permission first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        btnStopReader.setOnClickListener {
            val stopIntent = Intent(this, FloatingReaderService::class.java).apply {
                action = FloatingReaderService.ACTION_STOP
            }
            startService(stopIntent)
            Toast.makeText(this, "Floating bubble stopped.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        tvStatus.text = if (hasOverlay) {
            "Overlay Permission: Granted (Ready)"
        } else {
            "Overlay Permission: Not Granted"
        }
        btnStartReader.isEnabled = hasOverlay
    }
}
