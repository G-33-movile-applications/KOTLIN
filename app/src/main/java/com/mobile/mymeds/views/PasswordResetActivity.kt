package com.mobile.mymeds.views

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.mymeds.models.PasswordResetRequest
import com.mobile.mymeds.ui.theme.MyMedsTheme
import com.mobile.mymeds.viewModels.PasswordResetViewModel

class PasswordResetActivity : ComponentActivity() {
    private val resetViewModel: PasswordResetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyMedsTheme {
                PasswordResetScreen(resetViewModel)
            }
        }
    }
}

@Composable
fun PasswordResetScreen(viewModel: PasswordResetViewModel) {
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current

    val primaryButtonColor = Color(0xFF1A2247)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Dentro de @Composable fun PasswordResetScreen(...)

        Image(
            // ✅ SOLUCIÓN: Cambia la referencia a 'R.drawable'
            painter = painterResource(id = com.mobile.mymeds.R.drawable.ic_launcher_foreground),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(140.dp)
                .padding(bottom = 32.dp)
        )


        Text(
            text = "Password Reset",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.sendPasswordReset(
                    PasswordResetRequest(email)
                ) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    if (success) {
                        // Cierra pantalla y vuelve al login
                        (context as? ComponentActivity)?.finish()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = primaryButtonColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("SEND", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
