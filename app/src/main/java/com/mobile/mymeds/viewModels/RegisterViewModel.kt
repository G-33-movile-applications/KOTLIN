package com.mobile.mymeds.viewModels

import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.InetAddress
import java.util.Date
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

class RegisterViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Errores por campo para mostrar inline
    data class ValidationErrors(
        val fullName: String? = null,
        val email: String? = null,
        val password: String? = null,
        val phoneNumber: String? = null,
        val address: String? = null,
        val city: String? = null,
        val department: String? = null,
        val zipCode: String? = null,
    ) {
        fun isClean() = listOf(
            fullName, email, password, phoneNumber, address, city, department, zipCode
        ).all { it.isNullOrBlank() }
    }

    // Reglas
    private val passwordRegex = Regex("""^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d]).{10,}$""") // ≥10 caracteres, al menos 1 letra, 1 número y 1 símbolo o espacio
    private val cityDeptRegex  = Regex("""^[\p{L}\s]+$""") // solo letras (con acentos) y espacios
    private val zipRegex       = Regex("""^\d{4,10}$""")  // solo dígitos (ajusta rango si quieres)

    fun validate(
        fullName: String,
        email: String,
        password: String,
        phoneNumber: String,
        address: String,
        city: String,
        department: String,
        zipCode: String
    ): ValidationErrors {
        var eFullName: String? = null
        var eEmail: String? = null
        var ePassword: String? = null
        var ePhone: String? = null
        var eAddress: String? = null
        var eCity: String? = null
        var eDept: String? = null
        var eZip: String? = null

        if (fullName.isBlank()) eFullName = "Full name is required."

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            eEmail = "Enter a valid email address."
        } else {
            val domain = email.substringAfter('@', "")
            if (!domain.contains('.')) eEmail = "Email domain looks invalid."
        }

        if (!passwordRegex.matches(password)) {
            ePassword = "Password must be at least 10 characters, include a letter, a number, and a special symbol or space."
        }

        if (phoneNumber.isBlank() || !phoneNumber.matches(Regex("""^\+?[0-9\-\s]{7,20}$"""))) {
            ePhone = "Enter a valid phone number."
        }

        if (address.isBlank()) eAddress = "Address is required."

        if (city.isBlank() || !cityDeptRegex.matches(city)) {
            eCity = "City must contain letters only."
        }

        if (department.isBlank() || !cityDeptRegex.matches(department)) {
            eDept = "Department must contain letters only."
        }

        if (zipCode.isBlank() || !zipRegex.matches(zipCode)) {
            eZip = "Zip/Postal code must contain digits only."
        }

        return ValidationErrors(eFullName, eEmail, ePassword, ePhone, eAddress, eCity, eDept, eZip)
    }

    private fun checkDomainExists(domain: String, cb: (Boolean) -> Unit) {
        io.execute {
            val ok = try { InetAddress.getByName(domain).hostAddress != null } catch (_: Exception) { false }
            main.post { cb(ok) }
        }
    }

    // MISMA FIRMA QUE TU CÓDIGO ORIGINAL
    fun register(
        context: android.content.Context,
        fullName: String,
        email: String,
        password: String,
        phoneNumber: String,
        address: String,
        city: String,
        department: String,
        zipCode: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val f = fullName.trim()
        val e = email.trim()
        val p = password
        val ph = phoneNumber.trim()
        val a = address.trim()
        val c = city.trim()
        val d = department.trim()
        val z = zipCode.trim()

        val errors = validate(f, e, p, ph, a, c, d, z)
        if (!errors.isClean()) {
            val first = listOfNotNull(
                errors.fullName, errors.email, errors.password, errors.phoneNumber,
                errors.address, errors.city, errors.department, errors.zipCode
            ).firstOrNull() ?: "Invalid data."
            onResult(false, first)
            return
        }

        val domain = e.substringAfter('@', "")
        checkDomainExists(domain) { exists ->
            if (!exists) {
                onResult(false, "Email domain doesn't appear to exist.")
                return@checkDomainExists
            }

            auth.createUserWithEmailAndPassword(e, p).addOnCompleteListener { authTask ->
                if (!authTask.isSuccessful) {
                    onResult(false, authTask.exception?.message ?: "Registration failed.")
                    return@addOnCompleteListener
                }
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    onResult(false, "Unexpected error creating user.")
                    return@addOnCompleteListener
                }

                val userMap = hashMapOf(
                    "uid" to userId,
                    "fullName" to f,
                    "email" to e,
                    "phoneNumber" to ph,
                    "address" to a,
                    "city" to c,
                    "department" to d,
                    "zipCode" to z,
                    "createdAt" to Date()
                )

                firestore.collection("usuarios").document(userId).set(userMap)
                    .addOnSuccessListener {
                         onResult(true, "User registered successfully.")
                    }
                    .addOnFailureListener { ex ->
                        onResult(false, "Failed to save user: ${ex.message}")
                    }
            }
        }
    }
}
