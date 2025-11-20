package com.mobile.mymeds.viewModels

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class LoginViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // ✅ Usuario autenticado correctamente
                    // Nunca mostramos ni devolvemos el UID ni el token
                    onResult(true, "Login successful")
                } else {
                    // ❌ Falló el login
                    val message = task.exception?.localizedMessage ?: "Login failed"
                    onResult(false, message)
                }
            }
    }

    fun register(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, "User registered successfully")
                } else {
                    val message = task.exception?.localizedMessage ?: "Register failed"
                    onResult(false, message)
                }
            }
    }

    fun logout() {
        auth.signOut()
    }
}
