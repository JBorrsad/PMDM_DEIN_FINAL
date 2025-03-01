package com.example.perros

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.example.perros.databinding.ActivityRegisterBinding

/**
 * Actividad para el registro de nuevos usuarios.
 *
 * Esta actividad permite crear una nueva cuenta de usuario utilizando:
 * - Firebase Authentication para la autenticación
 * - Firebase Realtime Database para almacenar datos adicionales del usuario
 *
 * Estructura de datos en Realtime Database:
 * ```
 * users/
 *   └── {userId}/
 *         ├── email: String
 *         └── createdAt: Long (timestamp)
 * ```
 *
 * @property auth Instancia de Firebase Authentication
 * @property database Instancia de Firebase Realtime Database
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase Auth y Realtime Database
        auth = Firebase.auth
        database = FirebaseDatabase.getInstance()

        // Configurar botón de registro
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            // Validaciones
            if (!validarCampos(email, password, confirmPassword)) {
                return@setOnClickListener
            }

            // Si todas las validaciones pasan, crear usuario
            crearUsuario(email, password)
        }

        // Botón para volver a login
        binding.btnBackToLogin.setOnClickListener {
            finish()
        }

        // Opcional: Listener para términos y condiciones
        binding.termsLink.setOnClickListener {
            // Abrir términos y condiciones (puede ser una actividad o un diálogo)
            mostrarTerminos()
        }
    }

    private fun validarCampos(email: String, password: String, confirmPassword: String): Boolean {
        // Validar que ningún campo esté vacío
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validar que el email tenga formato correcto (incluyendo @)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Email inválido")
                .setMessage("Por favor, introduce una dirección de correo electrónico válida que incluya '@'.")
                .setPositiveButton("Aceptar", null)
                .show()
            return false
        }

        // Validar que las contraseñas coincidan
        if (password != confirmPassword) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Las contraseñas no coinciden")
                .setMessage("Por favor, verifica que ambas contraseñas sean iguales.")
                .setPositiveButton("Aceptar", null)
                .show()
            return false
        }

        // Validar formato de contraseña (mayúsculas, minúsculas y números)
        if (!validarFormatoContraseña(password)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Contraseña insegura")
                .setMessage("La contraseña debe contener al menos:\n- Una letra mayúscula\n- Una letra minúscula\n- Un número")
                .setPositiveButton("Aceptar", null)
                .show()
            return false
        }

        // Validar aceptación de términos y condiciones
        if (!binding.termsCheckbox.isChecked) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun validarFormatoContraseña(password: String): Boolean {
        val tieneMayuscula = password.any { it.isUpperCase() }
        val tieneMinuscula = password.any { it.isLowerCase() }
        val tieneNumero = password.any { it.isDigit() }

        return tieneMayuscula && tieneMinuscula && tieneNumero
    }

    private fun crearUsuario(email: String, password: String) {
        // Mostrar progreso
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Creando cuenta..."

        // Crear usuario con email y contraseña en Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Registro en Authentication exitoso, ahora guardar en Realtime Database
                    val user = auth.currentUser
                    
                    user?.let { firebaseUser ->
                        // Datos a guardar en la base de datos
                        val userId = firebaseUser.uid
                        val userData = HashMap<String, Any>()
                        userData["email"] = email
                        userData["createdAt"] = System.currentTimeMillis()
                        
                        // Guardar en Realtime Database
                        database.reference.child("users").child(userId).setValue(userData)
                            .addOnSuccessListener {
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = "Crear cuenta"
                                
                                // Todo el proceso exitoso
                                Toast.makeText(baseContext, "¡Cuenta creada exitosamente!", Toast.LENGTH_SHORT).show()
                                
                                // Volver a la pantalla de login
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = "Crear cuenta"
                                
                                // Error al guardar en Realtime Database
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("Error al guardar datos")
                                    .setMessage("Tu cuenta se creó, pero hubo un problema al guardar tus datos: ${e.message}")
                                    .setPositiveButton("Aceptar", null)
                                    .show()
                            }
                    }
                } else {
                    // Error en Authentication
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Crear cuenta"
                    
                    val errorMessage = when {
                        task.exception?.message?.contains("email") == true -> "Este correo ya está registrado"
                        task.exception?.message?.contains("network") == true -> "Error de conexión a internet"
                        else -> "Error al crear la cuenta: ${task.exception?.message}"
                    }
                    
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Error")
                        .setMessage(errorMessage)
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
    }

    private fun mostrarTerminos() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Términos y Condiciones")
            .setMessage("Al aceptar estos términos, usted acepta las políticas de uso y privacidad de PawTracker. Sus datos serán tratados según lo establecido en nuestra política de privacidad.")
            .setPositiveButton("Aceptar", null)
            .show()
    }
}
