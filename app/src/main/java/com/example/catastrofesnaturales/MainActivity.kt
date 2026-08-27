package com.example.catastrofesnaturales

import android.content.Context
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String = ""
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btn_flash = findViewById<Button>(R.id.btn_flash)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try{
            cameraId = cameraManager.cameraIdList[0]
        }catch (e: Exception){
            e.printStackTrace()
        }

        btn_flash.setOnClickListener{
            try{
               isFlashOn = !isFlashOn
                cameraManager.setTorchMode(cameraId, isFlashOn)

                if(isFlashOn){
                    btn_flash.setBackgroundColor(Color.YELLOW)
                }else{
                    btn_flash.setBackgroundColor(Color.GRAY)
                }
            }catch(e: Exception){
                e.printStackTrace()
            }
        }

        val tvTemp = findViewById<TextView>(R.id.tv_temperatura)
        val tvIcono = findViewById<TextView>(R.id.tv_icono_clima)
        val tvDetalles = findViewById<TextView>(R.id.tv_detalles)

        val urlClima = "https://api.open-meteo.com/v1/forecast?latitude=-35.6566&longitude=-63.7568&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"

        Thread {
            try {
                val respuestaJson = URL(urlClima).readText()
                val jsonObject = JSONObject(respuestaJson)
                val current = jsonObject.getJSONObject("current")

                val temperatura = current.getDouble("temperature_2m")
                val humedad = current.getInt("relative_humidity_2m")
                val viento = current.getDouble("wind_speed_10m")
                val codigoClima = current.getInt("weather_code")

                val emojiClima = when (codigoClima) {
                    0, 1 -> "☀️"
                    2, 3 -> "⛅"
                    45, 48 -> "🌫️"
                    51, 61, 80 -> "🌧️"
                    71, 85 -> "❄️"
                    95 -> "⛈️"
                    else -> "🌡️"
                }

                runOnUiThread {
                    tvTemp.text = "$temperatura°C"
                    tvIcono.text = emojiClima
                    tvDetalles.text = "Viento: $viento km/h | Humedad: $humedad%"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    tvTemp.text = "Error"
                    tvDetalles.text = "No se pudo cargar el clima"
                }

            }
        }.start()

        val btnDesplegar = findViewById<Button>(R.id.btn_desplegar_emergencias)
        val contenedorEmergencias = findViewById<LinearLayout>(R.id.contenedor_emergencias)
        val db = Firebase.firestore


        btnDesplegar.setOnClickListener {
            if (contenedorEmergencias.visibility == View.GONE) {
                contenedorEmergencias.visibility = View.VISIBLE
                btnDesplegar.text = "OCULTAR NÚMEROS" // Cambiamos el texto
            } else {
                contenedorEmergencias.visibility = View.GONE
                btnDesplegar.text = "🚨 NÚMEROS DE EMERGENCIA" // Volvemos al original
            }
        }

        db.collection("emergencias").addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("Error al escuchar Firebase: $error")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                contenedorEmergencias.removeAllViews()

                for (documento in snapshot.documents) {
                    val nombre = documento.getString("nombre") ?: "Sin nombre"
                    val telefono = documento.getString("telefono") ?: ""

                    val botonLlamar = Button(this).apply {
                        text = "Llamar a $nombre"
                        setOnClickListener {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$telefono")
                            }
                            startActivity(intent)
                        }
                    }
                    contenedorEmergencias.addView(botonLlamar)
                }
            }
        }

    }

    override fun onStop() {
        super.onStop()
        if (isFlashOn) {
            try {
                cameraManager.setTorchMode(cameraId, false)
                isFlashOn = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}