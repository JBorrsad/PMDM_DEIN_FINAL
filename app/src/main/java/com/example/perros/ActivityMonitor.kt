package com.example.perros

import android.content.Context
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Monitor de actividad física para perros.
 *
 * Esta clase se encarga de registrar y almacenar la actividad física de los perros
 * en Firebase Realtime Database. Mantiene un registro de:
 * - Pasos realizados
 * - Calorías quemadas
 * - Tiempo de actividad
 *
 * Los datos se almacenan en la siguiente estructura en Firebase:
 * ```
 * activity/
 *   ├── {perroId}/
 *   │     ├── steps: Int
 *   │     ├── calories: Double
 *   │     └── time_active: Long
 * ```
 *
 * @property database Referencia a la base de datos de Firebase para almacenar la actividad
 * @constructor Crea un monitor de actividad inicializando la conexión con Firebase
 */
class ActivityMonitor(context: Context) {
    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("activity")

    /**
     * Registra una nueva actividad para un perro específico.
     *
     * Este método crea una nueva entrada en la base de datos con los datos de actividad
     * proporcionados. Cada registro se guarda con una marca de tiempo única.
     *
     * @param petId Identificador único del perro en la base de datos
     * @param steps Número de pasos realizados en esta sesión de actividad
     * @param calories Calorías quemadas durante la actividad, calculadas según el peso y la actividad
     * @param timeActive Duración de la actividad en milisegundos
     *
     * @throws FirebaseException si hay un error al escribir en la base de datos
     */
    fun logActivity(petId: String, steps: Int, calories: Double, timeActive: Long) {
        val activityData = mapOf(
            "steps" to steps,
            "calories" to calories,
            "time_active" to timeActive
        )
        database.child(petId).push().setValue(activityData)
            .addOnSuccessListener {
                Log.d("ActivityMonitor", "Actividad registrada en Firebase")
            }
            .addOnFailureListener {
                Log.e("ActivityMonitor", "Error al registrar actividad", it)
            }
    }
}
