package com.example.perros

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.location.Location

/**
 * # GeofencingService
 * 
 * Servicio en primer plano especializado en el monitoreo continuo de perros para detección
 * de salida de zonas seguras en el sistema de monitorización de mascotas.
 * 
 * ## Funcionalidad principal
 * Este servicio es el componente central del sistema de monitorización de zonas seguras, que:
 * - Ejecuta verificaciones periódicas de la posición de cada perro
 * - Calcula distancias para determinar si un perro está dentro o fuera de su zona segura
 * - Detecta eventos de entrada/salida de zona y actualiza el estado en tiempo real
 * - Envía notificaciones inmediatas cuando un perro sale de su zona de seguridad
 * - Funciona en segundo plano incluso cuando la aplicación está cerrada
 * - Mantiene actualizado el estado de monitorización en Firebase
 * 
 * ## Características técnicas implementadas:
 * - **Servicio en primer plano**: Prioridad elevada para evitar terminación por el sistema
 * - **Algoritmo de geofencing**: Cálculo preciso de distancias entre puntos geográficos
 * - **Firebase Realtime Database**: Actualización y monitoreo en tiempo real de posiciones
 * - **Sistema de notificaciones**: Alertas persistentes con información relevante
 * - **Optimización de recursos**: Intervalos configurables de verificación para balance entre precisión y batería
 * - **Compatibilidad multiplataforma**: Adaptación a diferentes versiones de Android
 * 
 * ## Comportamiento del servicio:
 * 1. Se inicia como servicio en primer plano con una notificación continua
 * 2. Recupera información sobre la zona segura y el perro a monitorear
 * 3. Configura un temporizador para realizar verificaciones periódicas
 * 4. Calcula si el perro está dentro o fuera de su zona segura
 * 5. Actualiza el estado en Firebase y envía notificaciones cuando es necesario
 * 6. Continúa el monitoreo hasta que el servicio es detenido explícitamente
 * 
 * @property TAG Etiqueta para los mensajes de log generados por el servicio
 * @property NOTIFICATION_ID Identificador único para la notificación del servicio en primer plano
 * @property CHANNEL_ID Identificador del canal de notificaciones para Android 8.0 o superior
 * @property handler Manejador para programar las verificaciones periódicas en el hilo principal
 * @property database Referencia a Firebase para almacenar y recuperar datos de ubicación
 * @property perroActualId Identificador único del perro que está siendo monitoreado
 * @property verificacionRunnable Tarea que ejecuta periódicamente la verificación de posición
 * 
 * @see GeofenceBroadcastReciver Receptor complementario para eventos de geofencing
 * @see MapsActivity Actividad principal donde se visualizan las ubicaciones y zonas seguras
 */
class GeofencingService : Service() {
    
    private val TAG = "GeofencingService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "geofencing_channel"
    
    private val handler = Handler(Looper.getMainLooper())
    private val database = FirebaseDatabase.getInstance().reference
    
    // Perro actualmente monitoreado
    private var perroActualId: String? = null
    
    // Variables de zona segura
    private var zonaCentroLat: Double? = null
    private var zonaCentroLng: Double? = null
    private var zonaRadio: Double = 0.0
    
    // Variables de ubicación del perro
    private var ultimaPosLat: Double? = null
    private var ultimaPosLng: Double? = null
    private var perroFueraDeZona: Boolean = false
    
    /**
     * Tarea ejecutada periódicamente para verificar la posición del perro.
     * 
     * Esta implementación de Runnable contiene la lógica principal para:
     * - Consultar la última posición conocida del perro en Firebase
     * - Comparar esa posición con los límites de la zona segura
     * - Determinar si el perro ha entrado o salido de la zona
     * - Tomar las acciones apropiadas según el cambio de estado detectado
     * 
     * La ejecución periódica asegura que se detecten los cambios de posición
     * en tiempo real, incluso cuando la aplicación no está en primer plano.
     */
    private val verificacionRunnable = object : Runnable {
        override fun run() {
            verificarPosicionPerro()
            
            // Programar la próxima verificación
            // El intervalo puede ajustarse según las necesidades de la aplicación
            // y consideraciones de batería
            handler.postDelayed(this, 30000) // 30 segundos
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio de Geofencing creado")
        
        // Crear canal de notificación (requerido para Android O y posteriores)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio de Geofencing iniciado")
        
        // Obtener ID del perro desde el intent
        intent?.let {
            perroActualId = it.getStringExtra("perroId")
            Log.d(TAG, "Monitoreando perro ID: $perroActualId")
            
            if (perroActualId != null) {
                // Iniciar servicio en primer plano
                startForeground(NOTIFICATION_ID, buildNotification("Monitoreando a tu perro"))
                
                // Cargar datos de zona segura
                cargarZonaSegura()
                
                // Iniciar verificación periódica
                handler.post(verificacionRunnable)
            } else {
                Log.e(TAG, "No se proporcionó ID de perro, deteniendo servicio")
                stopSelf()
            }
        } ?: run {
            Log.e(TAG, "Intent nulo, deteniendo servicio")
            stopSelf()
        }
        
        // Si el servicio se mata, reiniciarlo
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Servicio de Geofencing destruido")
        // Detener verificaciones periódicas
        handler.removeCallbacks(verificacionRunnable)
        super.onDestroy()
    }
    
    /**
     * Carga los datos de la zona segura del perro desde Firebase
     */
    private fun cargarZonaSegura() {
        if (perroActualId == null) return
        
        database.child("users").child(perroActualId!!).child("zonaSegura")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        zonaCentroLat = snapshot.child("latitud").getValue(Double::class.java)
                        zonaCentroLng = snapshot.child("longitud").getValue(Double::class.java)
                        zonaRadio = snapshot.child("radio").getValue(Double::class.java) ?: 0.0
                        
                        Log.d(TAG, "Zona segura cargada: Lat=$zonaCentroLat, Lng=$zonaCentroLng, Radio=$zonaRadio")
                    } else {
                        Log.d(TAG, "No existe zona segura para este perro")
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error al cargar zona segura: ${error.message}")
                }
            })
    }
    
    /**
     * Verifica si el perro está dentro o fuera de la zona segura
     */
    private fun verificarPosicionPerro() {
        if (perroActualId == null) return
        
        // Obtener la ubicación actual del perro
        database.child("locations").child(perroActualId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val latitude = snapshot.child("latitude").getValue(Double::class.java)
                        val longitude = snapshot.child("longitude").getValue(Double::class.java)
                        
                        if (latitude != null && longitude != null) {
                            ultimaPosLat = latitude
                            ultimaPosLng = longitude
                            
                            // Verificar si está dentro o fuera
                            verificarZonaSegura()
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error al obtener ubicación del perro: ${error.message}")
                }
            })
    }
    
    /**
     * Verifica si el perro está dentro o fuera de la zona segura y toma acciones
     */
    private fun verificarZonaSegura() {
        if (zonaCentroLat == null || zonaCentroLng == null || zonaRadio <= 0.0 ||
            ultimaPosLat == null || ultimaPosLng == null) {
            Log.d(TAG, "Datos insuficientes para verificar zona segura")
            return
        }
        
        val centroPosicion = LatLng(zonaCentroLat!!, zonaCentroLng!!)
        val perroPosicion = LatLng(ultimaPosLat!!, ultimaPosLng!!)
        
        // Calcular la distancia entre la ubicación del perro y el centro de la zona segura
        val distancia = calcularDistanciaEnMetros(centroPosicion, perroPosicion)
        val estaDentro = distancia <= zonaRadio
        
        // Actualizar el estado de perro fuera/dentro de zona
        val estadoAnterior = perroFueraDeZona
        perroFueraDeZona = !estaDentro
        
        Log.d(TAG, "Verificación: Perro ${if(estaDentro) "DENTRO" else "FUERA"} de la zona segura (distancia: $distancia m)")
        
        // Si el estado cambió, actualizar en Firebase
        if (estadoAnterior != perroFueraDeZona) {
            // Actualizar estado en Firebase
            val dbRef = FirebaseDatabase.getInstance().getReference("geofencing_status")
            val nuevoEstado = if (perroFueraDeZona) "OUT" else "IN"
            
            dbRef.child(perroActualId!!).setValue(nuevoEstado)
                .addOnSuccessListener {
                    Log.d(TAG, "Estado de geofencing actualizado a $nuevoEstado")
                    
                    // Actualizar notificación del servicio
                    val mensaje = if (perroFueraDeZona) 
                        "¡Alerta! Tu perro está fuera de la zona segura"
                    else
                        "Tu perro está dentro de la zona segura"
                    
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(mensaje))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al actualizar estado en Firebase: ${e.message}")
                }
        }
        
        // Si el perro está fuera, enviar notificación
        if (perroFueraDeZona) {
            val receiver = GeofenceBroadcastReciver()
            val sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
            
            if (notificationsEnabled) {
                receiver.enviarNotificacion(this)
                Log.d(TAG, "Notificación enviada desde el servicio")
            }
        }
    }
    
    /**
     * Calcular la distancia en metros entre dos puntos geográficos
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
    
    /**
     * Crear canal de notificación para Android O y posteriores
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Geofencing Service"
            val description = "Notificaciones del servicio de monitoreo de zona segura"
            val importance = NotificationManager.IMPORTANCE_LOW
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Canal de notificación creado")
        }
    }
    
    /**
     * Construye la notificación del servicio en primer plano
     */
    private fun buildNotification(mensaje: String): android.app.Notification {
        val intent = Intent(this, MapsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Monitoreo de zona segura")
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
} 