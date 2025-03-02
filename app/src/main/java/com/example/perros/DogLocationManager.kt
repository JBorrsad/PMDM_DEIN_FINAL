package com.example.perros

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

/**
 * Gestor de ubicaciones y zonas seguras para perros.
 *
 * Esta clase se encarga de:
 * - Mostrar y actualizar la ubicación de los perros en el mapa
 * - Gestionar las zonas seguras (crear, editar, visualizar)
 * - Comprobar si un perro está dentro o fuera de su zona segura
 * - Enviar notificaciones cuando un perro sale de su zona segura
 * - Gestionar las actualizaciones de ubicación del usuario
 *
 * @property context Contexto de la aplicación
 * @property mMap Mapa de Google donde mostrar las ubicaciones
 * @property database Referencia a la base de datos de Firebase
 * @property fusedLocationClient Cliente para obtener la ubicación del dispositivo
 * @property clusterManager Gestor de marcadores para mostrar los perros en el mapa
 * @property location Ubicación actual del dispositivo
 *
 * @author Aplicación PawTracker
 * @since 1.0
 */
class DogLocationManager(
    private val context: Context,
    private val mMap: GoogleMap,
    private val database: DatabaseReference,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val clusterManager: DogsClusterManager
) {
    // Control de zona segura
    var modoEdicionZonaSegura = false
        private set

    private var zonaSeguraCircle: Circle? = null
    private var zonaCentroLat: Double? = null
    private var zonaCentroLng: Double? = null
    private var zonaRadio: Double = 0.0

    // Control de ubicación del perro
    private var marcadorPerro: Marker? = null
    private var perroActualId: String? = null
    private var dogLocationListener: ValueEventListener? = null
    private var ownerLocationListener: ValueEventListener? = null

    // Variables para la última posición conocida
    private var ultimaPosLat: Double? = null
    private var ultimaPosLng: Double? = null
    private var ultimoTimestamp: Long = 0
    private var perroFueraDeZona: Boolean = false
    private var seguirPerro = true

    // Configuración de ubicación
    private val locationRequest = LocationRequest.Builder(5000)
        .setMinUpdateIntervalMillis(2000)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                updateOwnerLocationInFirebase(location)
            }
        }
    }

    // Verificación periódica de zona segura
    private val handler = Handler(Looper.getMainLooper())
    private val comprobacionZonaSeguraRunnable = object : Runnable {
        override fun run() {
            comprobarYNotificarZonaInsegura()
            handler.postDelayed(this, 5000)
        }
    }

    /**
     * Inicia el servicio de ubicación y las comprobaciones periódicas.
     */
    fun iniciarServicioUbicacion() {
        startLocationUpdates()
        handler.post(comprobacionZonaSeguraRunnable)
    }

    /**
     * Activa el modo de edición de la zona segura.
     *
     * @param perroId ID del perro cuya zona segura se va a editar
     */
    fun activarModoEdicionZona(perroId: String) {
        this.perroActualId = perroId
        modoEdicionZonaSegura = true

        // Acercamos la cámara a la zona actual (si existe)
        database.child("users").child(perroId).child("zonaSegura")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("latitud").getValue(Double::class.java)
                    val lng = snapshot.child("longitud").getValue(Double::class.java)
                    if (lat != null && lng != null) {
                        val pos = LatLng(lat, lng)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Desactiva el modo de edición de la zona segura.
     */
    fun desactivarModoEdicionZona() {
        modoEdicionZonaSegura = false

        // Volver a cargar la zona segura actual (cancelar cambios)
        if (perroActualId != null) {
            mostrarZonaSegura(perroActualId!!)
        }
    }

    /**
     * Define una nueva zona segura en la ubicación especificada.
     * Solo funciona cuando el modo de edición está activo.
     *
     * @param latLng Ubicación central de la zona segura
     */
    fun definirZonaSegura(latLng: LatLng) {
        if (!modoEdicionZonaSegura) return

        val radio = 100.0
        zonaSeguraCircle?.remove()
        zonaCentroLat = latLng.latitude
        zonaCentroLng = latLng.longitude
        zonaRadio = radio
        zonaSeguraCircle = mMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radio)
                .strokeColor(Color.GREEN)
                .fillColor(Color.argb(50, 0, 255, 0))
        )
    }

    /**
     * Guarda la zona segura definida en Firebase.
     *
     * @param perroId ID del perro al que pertenece la zona segura
     */
    fun guardarZonaSegura(perroId: String) {
        if (zonaSeguraCircle == null) {
            Toast.makeText(context, R.string.definir_zona_segura, Toast.LENGTH_SHORT).show()
            return
        }

        val zonaSegura = mapOf(
            "latitud" to zonaSeguraCircle!!.center.latitude,
            "longitud" to zonaSeguraCircle!!.center.longitude,
            "radio" to zonaSeguraCircle!!.radius
        )

        database.child("users").child(perroId).child("zonaSegura")
            .setValue(zonaSegura)
            .addOnSuccessListener {
                Toast.makeText(context, R.string.zona_guardada, Toast.LENGTH_SHORT).show()

                // Actualizar datos precargados
                database.child("users").child(perroId).child("zonaSegura")
                    .get().addOnSuccessListener { snapshot ->
                        DatosPrecargados.guardarZonaSeguraPerro(perroId, snapshot)
                    }

                // Actualizar visualización
                actualizarColorZonaSegura()
            }
            .addOnFailureListener { e ->
                Log.e("DogLocationManager", "Error al guardar zona segura: ${e.message}")
                Toast.makeText(context, R.string.error_guardar_zona, Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Muestra la zona segura de un perro en el mapa.
     *
     * @param perroId ID del perro cuya zona segura se mostrará
     */
    fun mostrarZonaSegura(perroId: String) {
        perroActualId = perroId

        // Limpiar zona anterior si existe
        zonaSeguraCircle?.remove()
        zonaSeguraCircle = null

        // Primero intentar obtener de datos precargados
        val zonaSeguraSnapshot = DatosPrecargados.obtenerZonaSeguraPerro(perroId)

        if (zonaSeguraSnapshot != null && zonaSeguraSnapshot.exists()) {
            val latitud = zonaSeguraSnapshot.child("latitud").getValue(Double::class.java)
            val longitud = zonaSeguraSnapshot.child("longitud").getValue(Double::class.java)
            val radio = zonaSeguraSnapshot.child("radio").getValue(Double::class.java)

            if (latitud != null && longitud != null && radio != null) {
                zonaCentroLat = latitud
                zonaCentroLng = longitud
                zonaRadio = radio

                val centro = LatLng(latitud, longitud)

                // Color inicial verde
                zonaSeguraCircle = mMap.addCircle(
                    CircleOptions()
                        .center(centro)
                        .radius(radio)
                        .strokeColor(Color.GREEN)
                        .fillColor(Color.argb(50, 0, 255, 0))
                )

                // Actualizar color según la posición del perro
                actualizarColorZonaSegura()
            }
        } else {
            // Intentar obtener directamente de Firebase
            database.child("users").child(perroId).child("zonaSegura")
                .get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val latitud = snapshot.child("latitud").getValue(Double::class.java)
                        val longitud = snapshot.child("longitud").getValue(Double::class.java)
                        val radio = snapshot.child("radio").getValue(Double::class.java)

                        if (latitud != null && longitud != null && radio != null) {
                            zonaCentroLat = latitud
                            zonaCentroLng = longitud
                            zonaRadio = radio

                            val centro = LatLng(latitud, longitud)

                            zonaSeguraCircle = mMap.addCircle(
                                CircleOptions()
                                    .center(centro)
                                    .radius(radio)
                                    .strokeColor(Color.GREEN)
                                    .fillColor(Color.argb(50, 0, 255, 0))
                            )

                            // Guardar para siguiente acceso
                            DatosPrecargados.guardarZonaSeguraPerro(perroId, snapshot)

                            actualizarColorZonaSegura()
                        }
                    }
                }
        }
    }

    /**
     * Actualiza el color de la zona segura según la posición del perro.
     * Verde si está dentro, rojo si está fuera.
     */
    private fun actualizarColorZonaSegura() {
        if (perroActualId.isNullOrEmpty() ||
            zonaCentroLat == null || zonaCentroLng == null || zonaRadio <= 0.0 ||
            ultimaPosLat == null || ultimaPosLng == null
        ) {
            return
        }

        val centroPosicion = LatLng(zonaCentroLat!!, zonaCentroLng!!)
        val perroPosicion = LatLng(ultimaPosLat!!, ultimaPosLng!!)

        // Calcular la distancia entre la ubicación del perro y el centro de la zona segura
        val distancia = calcularDistanciaEnMetros(centroPosicion, perroPosicion)
        val estaDentro = distancia <= zonaRadio

        val colorCirculo = if (estaDentro) {
            Color.argb(50, 0, 255, 0)  // Verde translúcido (dentro)
        } else {
            Color.argb(50, 255, 0, 0)  // Rojo translúcido (fuera)
        }

        val colorBorde = if (estaDentro) {
            Color.GREEN
        } else {
            Color.RED
        }

        // Actualizar el color del círculo de la zona segura
        zonaSeguraCircle?.fillColor = colorCirculo
        zonaSeguraCircle?.strokeColor = colorBorde

        // Actualizar el estado de perro fuera/dentro de zona
        perroFueraDeZona = !estaDentro
    }

    /**
     * Comprueba si el perro está fuera de la zona segura y envía una notificación si es necesario.
     */
    private fun comprobarYNotificarZonaInsegura() {
        if (zonaSeguraCircle == null ||
            zonaCentroLat == null || zonaCentroLng == null || zonaRadio == 0.0 ||
            ultimaPosLat == null || ultimaPosLng == null ||
            modoEdicionZonaSegura
        ) // No verificar cuando estamos en modo edición
            return

        val distancia = calcularDistanciaEnMetros(
            LatLng(zonaCentroLat!!, zonaCentroLng!!),
            LatLng(ultimaPosLat!!, ultimaPosLng!!)
        )

        if (distancia > zonaRadio) {
            enviarNotificacionZonaInsegura()
        }
    }

    /**
     * Envía una notificación al usuario indicando que el perro ha salido de la zona segura.
     */
    private fun enviarNotificacionZonaInsegura() {
        val channelId = "geofence_alert"
        val notificationId = 1001

        // Obtener nombre del perro si está disponible
        val perroNombre = if (perroActualId != null) {
            val perroSnapshot = DatosPrecargados.obtenerPerro(perroActualId!!)
            perroSnapshot?.child("nombre")?.getValue(String::class.java) ?: "Tu perro"
        } else {
            "Tu perro"
        }

        val intent = Intent(context, MapsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.alerta_geocerca))
            .setContentText(context.getString(R.string.mascota_fuera_zona_nombre, perroNombre))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear el canal de notificación (requerido para Android 8.0+)
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.geofence_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = context.getString(R.string.geofence_channel_description)
        notificationManager.createNotificationChannel(channel)

        // Verificar permiso de notificaciones en Android 13+
        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(notificationId, builder.build())
            }
        }
    }

    /**
     * Muestra la ubicación actual de un perro en el mapa.
     * Configura un listener para actualizaciones en tiempo real.
     *
     * @param perroId ID del perro cuya ubicación se mostrará
     */
    fun mostrarUbicacionPerro(perroId: String) {
        if (perroId.isEmpty()) return

        perroActualId = perroId
        Log.d("DogLocationManager", "Mostrando ubicación del perro: $perroId")

        // Eliminar listener anterior si existe
        eliminarDogLocationListener()

        // Limpiar el cluster manager
        clusterManager.limpiarMarcadores()

        // Primero intentar obtener la ubicación de datos precargados
        val ubicacionPreload = DatosPrecargados.obtenerUbicacionPerro(perroId)

        if (ubicacionPreload != null && ubicacionPreload.exists()) {
            Log.d("DogLocationManager", "Usando ubicación de datos precargados")

            val latitude = ubicacionPreload.child("latitude").getValue(Double::class.java)
            val longitude = ubicacionPreload.child("longitude").getValue(Double::class.java)

            if (latitude != null && longitude != null) {
                // Guardar la última posición conocida
                ultimaPosLat = latitude
                ultimaPosLng = longitude
                ultimoTimestamp = System.currentTimeMillis()

                // Mostrar marcador
                val position = LatLng(latitude, longitude)
                clusterManager.addDogMarker(perroId, position)

                // Actualizar color de zona segura si es necesario
                actualizarColorZonaSegura()
            }
        }

        // Configurar listener para actualizaciones en tiempo real
        val locationRef = database.child("locations").child(perroId)
        val locationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val latitude = snapshot.child("latitude").getValue(Double::class.java)
                val longitude = snapshot.child("longitude").getValue(Double::class.java)

                if (latitude != null && longitude != null) {
                    // Actualizar posición conocida
                    ultimaPosLat = latitude
                    ultimaPosLng = longitude
                    ultimoTimestamp = System.currentTimeMillis()

                    // Actualizar visualización
                    val position = LatLng(latitude, longitude)
                    clusterManager.limpiarMarcadores()
                    clusterManager.addDogMarker(perroId, position)

                    // Centrar mapa si seguimiento activado
                    if (seguirPerro) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 17f))
                    }

                    // Actualizar color de zona segura
                    actualizarColorZonaSegura()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DogLocationManager", "Error al escuchar ubicación: ${error.message}")
            }
        }

        // Registrar el listener
        locationRef.addValueEventListener(locationListener)
        dogLocationListener = locationListener
    }

    /**
     * Elimina el listener de ubicación del perro para evitar fugas de memoria.
     */
    private fun eliminarDogLocationListener() {
        if (perroActualId != null && dogLocationListener != null) {
            database.child("locations").child(perroActualId!!)
                .removeEventListener(dogLocationListener!!)
            dogLocationListener = null
        }
    }

    /**
     * Centra el mapa en la zona segura del perro especificado.
     *
     * @param perroId ID del perro cuya zona segura se mostrará
     */
    fun centrarMapaEnZonaSegura(perroId: String) {
        Log.d("DogLocationManager", "Intentando centrar mapa en zona segura del perro: $perroId")

        // Obtener zona segura desde datos precargados
        val snapshot = DatosPrecargados.obtenerZonaSeguraPerro(perroId)

        if (snapshot != null && snapshot.exists()) {
            val lat = snapshot.child("latitud").getValue(Double::class.java)
            val lng = snapshot.child("longitud").getValue(Double::class.java)

            Log.d("DogLocationManager", "Centrando mapa en zona segura - lat: $lat, lng: $lng")

            if (lat != null && lng != null) {
                val pos = LatLng(lat, lng)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
            } else {
                Log.e("DogLocationManager", "Zona segura con coordenadas nulas")
                Toast.makeText(
                    context,
                    "No hay zona segura definida correctamente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Log.d("DogLocationManager", "No se encontró zona segura para el perro: $perroId")
            Toast.makeText(context, "No hay zona segura definida", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Centra el mapa en la ubicación actual del dispositivo.
     */
    fun centrarMapaEnUbicacionActual() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        }
    }

    /**
     * Actualiza la ubicación del usuario en Firebase.
     */
    private fun updateOwnerLocationInFirebase(location: Location) {
        val locData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to System.currentTimeMillis()
        )

        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child("locations").child(userId).setValue(locData)
            .addOnSuccessListener {
                Log.d("DogLocationManager", "Ubicación del usuario actualizada")
            }
            .addOnFailureListener { e ->
                Log.e("DogLocationManager", "Error al actualizar ubicación: ${e.message}")
            }
    }

    /**
     * Inicia las actualizaciones periódicas de ubicación.
     */
    fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    /**
     * Detiene las actualizaciones periódicas de ubicación.
     */
    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Limpia los recursos utilizados por esta clase para evitar fugas de memoria.
     */
    fun limpiarRecursos() {
        eliminarDogLocationListener()

        if (ownerLocationListener != null) {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                database.child("locations").child(userId)
                    .removeEventListener(ownerLocationListener!!)
            }
        }

        stopLocationUpdates()
        handler.removeCallbacks(comprobacionZonaSeguraRunnable)
    }

    /**
     * Calcula la distancia en metros entre dos puntos geográficos.
     */
    private fun calcularDistanciaEnMetros(punto1: LatLng, punto2: LatLng): Float {
        val resultado = FloatArray(1)
        Location.distanceBetween(
            punto1.latitude, punto1.longitude,
            punto2.latitude, punto2.longitude,
            resultado
        )
        return resultado[0]
    }
} 