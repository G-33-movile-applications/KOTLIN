package com.mobile.mymeds.viewModels

import androidx.lifecycle.ViewModel
import com.mobile.mymeds.models.PasswordResetRequest
import com.google.firebase.auth.FirebaseAuth

class PasswordResetViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun sendPasswordReset(
        request: PasswordResetRequest,
        onResult: (Boolean, String) -> Unit
    ) {
        auth.sendPasswordResetEmail(request.email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, "A reset link was sent to ${request.email}")
                } else {
                    onResult(false, task.exception?.message ?: "Failed to send reset email")
                }
            }
    }
}
