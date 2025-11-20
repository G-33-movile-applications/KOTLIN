package com.mobile.mymeds.views

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.mymeds.viewModels.RegisterViewModel

class RegisterActivity : ComponentActivity() {
    private val registerViewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RegisterScreen(registerViewModel) }
    }
}

/** Helper de conectividad (Wi-Fi / Celular / Ethernet). */
private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(viewModel: RegisterViewModel = viewModel()) {
    val context = LocalContext.current

    // Campos
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }

    // Checkboxes
    var acceptTerms by remember { mutableStateOf(false) }
    var acceptDataPolicy by remember { mutableStateOf(false) }

    // Errores y control de validación
    var errors by remember { mutableStateOf(RegisterViewModel.ValidationErrors()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }

    // "Touched" por campo
    var touchedFullName by remember { mutableStateOf(false) }
    var touchedEmail by remember { mutableStateOf(false) }
    var touchedPassword by remember { mutableStateOf(false) }
    var touchedPhone by remember { mutableStateOf(false) }
    var touchedAddress by remember { mutableStateOf(false) }
    var touchedCity by remember { mutableStateOf(false) }
    var touchedDepartment by remember { mutableStateOf(false) }
    var touchedZip by remember { mutableStateOf(false) }

    fun revalidate() {
        errors = viewModel.validate(
            fullName, email, password, phoneNumber, address, city, department, zipCode
        )
    }

    // Revalidar en cada cambio
    LaunchedEffect(fullName, email, password, phoneNumber, address, city, department, zipCode) {
        revalidate()
    }

    fun showError(err: String?, touched: Boolean): Boolean =
        err != null && (touched || submitAttempted)

    // Estado de conectividad (evaluado en cada recomposición)
    val online = isOnline(context)

    // Reglas del botón: formulario válido + no enviando + online
    val formValid = errors.isClean() && acceptTerms && acceptDataPolicy && !isSubmitting
    val canSubmit = formValid && online

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Banner de conectividad (idéntico patrón al login)
        if (!online) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFE6E6))
                    .padding(12.dp)
            ) {
                // Si quieres el MISMO texto del login, cambia esta línea:
                // "Sin conexión. Inicia sesión cuando vuelva el internet."
                Text(
                    text = "Sin conexión. Completa el registro cuando vuelva el internet.",
                    color = Color(0xFF8A0000)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "REGISTER",
            style = MaterialTheme.typography.headlineLarge,
            color = Color(0xFF1A2247),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Full Name
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it; touchedFullName = true },
            label = { Text("Full Name") },
            isError = showError(errors.fullName, touchedFullName),
            supportingText = { if (showError(errors.fullName, touchedFullName)) Text(errors.fullName!!) },
            modifier = Modifier.fillMaxWidth()
        )

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; touchedEmail = true },
            label = { Text("Email") },
            isError = showError(errors.email, touchedEmail),
            supportingText = { if (showError(errors.email, touchedEmail)) Text(errors.email!!) },
            modifier = Modifier.fillMaxWidth()
        )

        // Phone
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it; touchedPhone = true },
            label = { Text("Phone Number") },
            isError = showError(errors.phoneNumber, touchedPhone),
            supportingText = { if (showError(errors.phoneNumber, touchedPhone)) Text(errors.phoneNumber!!) },
            modifier = Modifier.fillMaxWidth()
        )

        // Address
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; touchedAddress = true },
            label = { Text("Address") },
            isError = showError(errors.address, touchedAddress),
            supportingText = { if (showError(errors.address, touchedAddress)) Text(errors.address!!) },
            modifier = Modifier.fillMaxWidth()
        )

        // City
        OutlinedTextField(
            value = city,
            onValueChange = { city = it; touchedCity = true },
            label = { Text("City") },
            isError = showError(errors.city, touchedCity),
            supportingText = { if (showError(errors.city, touchedCity)) Text(errors.city!!) },
            modifier = Modifier.fillMaxWidth()
        )

        // Department
        OutlinedTextField(
            value = department,
            onValueChange = { department = it; touchedDepartment = true },
            label = { Text("Department") },
            isError = showError(errors.department, touchedDepartment),
            supportingText = { if (showError(errors.department, touchedDepartment)) Text(errors.department!!) },
            modifier = Modifier.fillMaxWidth()
        )

        // Zip
        OutlinedTextField(
            value = zipCode,
            onValueChange = { zipCode = it; touchedZip = true },
            label = { Text("Zip Code") },
            isError = showError(errors.zipCode, touchedZip),
            supportingText = { if (showError(errors.zipCode, touchedZip)) Text(errors.zipCode!!) },
            modifier = Modifier.fillMaxWidth()
        )

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; touchedPassword = true },
            label = { Text("Password") },
            isError = showError(errors.password, touchedPassword),
            supportingText = { if (showError(errors.password, touchedPassword)) Text(errors.password!!) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acceptTerms, onCheckedChange = { acceptTerms = it })
            Text("I accept the Terms and Conditions")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acceptDataPolicy, onCheckedChange = { acceptDataPolicy = it })
            Text("I agree to the Data Processing Policy")
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                // Igual que login: bloquear si no hay internet y avisar
                if (!isOnline(context)) {
                    // Si prefieres EXACTAMENTE el mismo texto del login, usa esta línea:
                    // Toast.makeText(context, "Sin conexión. Inicia sesión cuando vuelva el internet.", Toast.LENGTH_LONG).show()
                    Toast.makeText(context, "Sin conexión. Completa el registro cuando vuelva el internet.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                submitAttempted = true
                if (!errors.isClean()) return@Button
                isSubmitting = true

                viewModel.register(
                    context,
                    fullName, email, password, phoneNumber, address, city, department, zipCode
                ) { success, message ->
                    isSubmitting = false
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (success) (context as? Activity)?.finish()
                }
            },
            enabled = canSubmit, // 🔒 Deshabilitado si falta validación o no hay red
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Register")
        }
    }
}
