package com.example.catastrofesnaturales

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class GrabacionActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var tvCronometro: TextView
    private lateinit var btnDetener: Button

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var segundosGrabando = 0
    private val handler = Handler(Looper.getMainLooper())
    private val runnableTimer = object : Runnable {
        override fun run() {
            segundosGrabando++
            val minutos = segundosGrabando / 60
            val segs = segundosGrabando % 60
            tvCronometro.text = String.format(Locale.getDefault(), "REC %02d:%02d", minutos, segs)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_grabacion)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.view_finder)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewFinder = findViewById(R.id.view_finder)
        tvCronometro = findViewById(R.id.tv_cronometro)
        btnDetener = findViewById(R.id.btn_detener_grabacion)

        btnDetener.setOnClickListener {
            detenerGrabacion()
        }

        val usarFrontal = intent.getBooleanExtra("USAR_CAMARA_FRONTAL", false)
        iniciarCamaraYGrabar(usarFrontal)
    }

    private fun iniciarCamaraYGrabar(usarFrontal: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = (cameraProviderFuture as java.util.concurrent.Future<ProcessCameraProvider>).get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = if (usarFrontal) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                // Apenas inicia la cámara, ¡empezamos a grabar inmediatamente!
                comenzarGrabacionInmediata()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error al iniciar la cámara: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun comenzarGrabacionInmediata() {
        val capture = videoCapture ?: return

        val nombreArchivo = "VID_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Catastrofes")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        try {
            recording = capture.output
                .prepareRecording(this, mediaStoreOutputOptions)
                .apply {
                    if (ContextCompat.checkSelfPermission(
                            this@GrabacionActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        withAudioEnabled()
                    }
                }
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            handler.post(runnableTimer)
                        }
                        is VideoRecordEvent.Finalize -> {
                            handler.removeCallbacks(runnableTimer)
                            if (!event.hasError()) {
                                Toast.makeText(this, "Video guardado en la galería", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Grabación finalizada", Toast.LENGTH_SHORT).show()
                            }
                            finish()
                        }
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error de permisos: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun detenerGrabacion() {
        handler.removeCallbacks(runnableTimer)
        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnableTimer)
        recording?.stop()
    }
}
