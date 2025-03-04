package com.example.perros

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etCurrentPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmNewPassword: EditText
    private lateinit var btnChangePassword: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        initializeViews()
        setupFirebase()
        setupListeners()
    }

    private fun initializeViews() {
        etEmail = findViewById(R.id.etEmail)
        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        // Establecer el email actual
        etEmail.setText(auth.currentUser?.email)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            onBackPressed()
        }

        btnChangePassword.setOnClickListener {
            if (validateInputs()) {
                changePassword()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val currentPassword = etCurrentPassword.text.toString()
        val newPassword = etNewPassword.text.toString()
        val confirmNewPassword = etConfirmNewPassword.text.toString()

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            return false
        }

        if (newPassword != confirmNewPassword) {
            Toast.makeText(this, "Las nuevas contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            return false
        }

        if (newPassword.length < 6) {
            Toast.makeText(this, "La nueva contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun changePassword() {
        val user = auth.currentUser
        if (user != null && user.email != null) {
            // Crear credenciales con el email y la contraseña actual
            val credential = EmailAuthProvider.getCredential(
                user.email!!,
                etCurrentPassword.text.toString()
            )

            // Reautenticar al usuario
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    // Si la reautenticación es exitosa, cambiar la contraseña
                    user.updatePassword(etNewPassword.text.toString())
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Contraseña actualizada correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Error al actualizar la contraseña: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "La contraseña actual es incorrecta",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_right_to_left, R.anim.slide_left_to_right)
    }
} 