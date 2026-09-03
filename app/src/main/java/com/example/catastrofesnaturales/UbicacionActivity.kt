package com.example.catastrofesnaturales

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.util.Locale

class UbicacionActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mMap: GoogleMap? = null
    private var ultimaLatitud: Double? = null
    private var ultimaLongitud: Double? = null
    private var ultimaDireccion: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            obtenerUbicacionReal()
            habilitarMiUbicacionEnMapa()
        } else {
            Toast.makeText(this, "Se requieren permisos de ubicación para mostrar el mapa.", Toast.LENGTH_LONG).show()
            val tvDireccion = findViewById<TextView>(R.id.tv_direccion_grande)
            tvDireccion.text = "Error: Faltan permisos de ubicación."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_ubicacion)

        val vistaPrincipal = findViewById<View>(R.id.main)
        if (vistaPrincipal != null) {
            ViewCompat.setOnApplyWindowInsetsListener(vistaPrincipal) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // Cargar el Fragment del mapa de Google
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapa) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        val btnVolver = findViewById<Button>(R.id.btn_volver)
        btnVolver.setOnClickListener {
            finish()
        }

        verificarPermisosYUbicacion()
    }

    private fun verificarPermisosYUbicacion() {
        val tvErrorGps = findViewById<TextView>(R.id.tv_error_gps)
        val tvDireccion = findViewById<TextView>(R.id.tv_direccion_grande)

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsActivado = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsActivado) {
            tvErrorGps.visibility = View.VISIBLE
            tvDireccion.visibility = View.GONE
            return
        }

        tvErrorGps.visibility = View.GONE
        tvDireccion.visibility = View.VISIBLE

        if (tienePermisoUbicacion()) {
            obtenerUbicacionReal()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun obtenerUbicacionReal() {
        if (!tienePermisoUbicacion()) return

        val tvDireccion = findViewById<TextView>(R.id.tv_direccion_grande)
        val etReferencia = findViewById<EditText>(R.id.et_referencia)
        val btnGuardar = findViewById<Button>(R.id.btn_guardar_ubicacion)
        val db = Firebase.firestore

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationTokenSource = CancellationTokenSource()

        try {
            // Usamos getCurrentLocation para obtener la ubicación actual precisa en tiempo real
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    procesarUbicacion(location.latitude, location.longitude, tvDireccion, etReferencia, btnGuardar, db)
                } else {
                    // Respaldo en caso de que getCurrentLocation retorne null
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            procesarUbicacion(lastLoc.latitude, lastLoc.longitude, tvDireccion, etReferencia, btnGuardar, db)
                        } else {
                            tvDireccion.text = "Ubicación no disponible en este momento."
                        }
                    }
                }
            }.addOnFailureListener {
                tvDireccion.text = "Error al obtener la ubicación actual."
            }
        } catch (e: SecurityException) {
            tvDireccion.text = "Error: Permiso de ubicación no concedido."
        }
    }

    private fun procesarUbicacion(
        latitud: Double,
        longitud: Double,
        tvDireccion: TextView,
        etReferencia: EditText,
        btnGuardar: Button,
        db: FirebaseFirestore
    ) {
        var direccionTexto = "Lat: $latitud, Lng: $longitud"

        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val direcciones = geocoder.getFromLocation(latitud, longitud, 1)
            if (!direcciones.isNullOrEmpty()) {
                direccionTexto = direcciones[0].getAddressLine(0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tvDireccion.text = direccionTexto

        ultimaLatitud = latitud
        ultimaLongitud = longitud
        ultimaDireccion = direccionTexto
        actualizarMapa()

        btnGuardar.setOnClickListener {
            val referencia = etReferencia.text.toString()
            if (referencia.isEmpty()) {
                Toast.makeText(this, "Por favor, escribe una referencia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val datos = hashMapOf(
                "direccion" to direccionTexto,
                "latitud" to latitud,
                "longitud" to longitud,
                "referencia" to referencia,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("ubicaciones").add(datos).addOnSuccessListener {
                Toast.makeText(this, "¡Ubicación registrada en Firebase!", Toast.LENGTH_LONG).show()
                etReferencia.text.clear()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        habilitarMiUbicacionEnMapa()
        actualizarMapa()
    }

    private fun habilitarMiUbicacionEnMapa() {
        if (tienePermisoUbicacion()) {
            try {
                mMap?.isMyLocationEnabled = true
                mMap?.uiSettings?.isMyLocationButtonEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun actualizarMapa() {
        val lat = ultimaLatitud
        val lng = ultimaLongitud
        val map = mMap

        if (lat != null && lng != null && map != null) {
            val posicion = LatLng(lat, lng)
            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(posicion)
                    .title("Tu Ubicación Actual")
                    .snippet(ultimaDireccion ?: "")
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(posicion, 16f))
        }
    }
}
