package com.example.perros

import android.content.Context
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ActivityMonitor(context: Context) {
    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("activity")

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
