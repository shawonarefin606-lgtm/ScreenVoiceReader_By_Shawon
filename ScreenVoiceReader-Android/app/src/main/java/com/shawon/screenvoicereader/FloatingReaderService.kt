package com.shawon.screenvoicereader

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.math.abs

class FloatingReaderService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.shawon.screenvoicereader.ACTION_START"
        const val ACTION_STOP = "com.shawon.screenvoicereader.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_SPEECH_RATE = "speech_rate"
        private const val CHANNEL_ID = "floating_reader_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var btnReadScreen: ImageButton
    private lateinit var btnStopSpeech: ImageButton

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDensity = 320

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRate = 1.0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post { stopSelf() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        initScreenMetrics()
        setupFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START) {
            speechRate = intent.getFloatExtra(EXTRA_SPEECH_RATE, 1.0f)
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_DATA)
            }

            if (resultCode == Activity.RESULT_OK && data != null) {
                setupMediaProjection(resultCode, data)
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun initScreenMetrics() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)?.apply {
            registerCallback(projectionCallback, mainHandler)
        }

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenVoiceCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )
    }

    private fun setupFloatingBubble() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)
        btnReadScreen = floatingView!!.findViewById(R.id.btnReadScreen)
        btnStopSpeech = floatingView!!.findViewById(R.id.btnStopSpeech)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        windowManager.addView(floatingView, params)

        btnReadScreen.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoved = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoved) {
                            captureAndReadScreen()
                        }
                        return true
                    }
                }
                return false
            }
        })

        btnStopSpeech.setOnClickListener {
            tts?.stop()
            btnStopSpeech.visibility = View.GONE
        }
    }

    private fun captureAndReadScreen() {
        tts?.stop()
        btnStopSpeech.visibility = View.GONE
        floatingView?.visibility = View.INVISIBLE

        mainHandler.postDelayed({
            val image = imageReader?.acquireLatestImage()
            floatingView?.visibility = View.VISIBLE

            if (image == null) {
                Toast.makeText(this, "Could not capture screen. Try again.", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }

            val bitmap = imageToBitmap(image)
            image.close()

            if (bitmap == null) {
                Toast.makeText(this, "Failed to process screen image.", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isEmpty()) {
                        Toast.makeText(this, "No readable English text found.", Toast.LENGTH_SHORT).show()
                    } else {
                        speakText(text)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Text recognition failed.", Toast.LENGTH_SHORT).show()
                }
        }, 120)
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
    }

    private fun speakText(text: String) {
        if (!isTtsReady) {
            Toast.makeText(this, "Text-to-Speech is still initializing...", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        speechRate = prefs.getFloat("speech_rate", 1.0f)
        tts?.setSpeechRate(speechRate)

        btnStopSpeech.visibility = View.VISIBLE
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "screen_read_utterance")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { btnStopSpeech.visibility = View.GONE }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { btnStopSpeech.visibility = View.GONE }
                }
            })
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingReaderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenVoice Reader Active")
            .setContentText("Tap the floating bubble to read English text on screen.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        textRecognizer.close()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        floatingView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
