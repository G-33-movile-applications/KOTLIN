package com.mobile.mymeds.views

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mobile.mymeds.ui.theme.MyMedsTheme
import com.mobile.mymeds.viewModels.ProfileViewModel
import com.mobile.mymeds.viewModels.ProfileViewModelFactory
import com.mobile.mymeds.viewModels.UserProfile

class ProfileActivity : ComponentActivity() {
    private val profileViewModel: ProfileViewModel by viewModels {
        ProfileViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyMedsTheme {
                ProfileScreen(
                    vm = profileViewModel,
                    onBack = { finish() },
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val profile by vm.profile.observeAsState()
    val loading by vm.loading.observeAsState(false)
    val message by vm.message.observeAsState()

    var isEditing by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var profilePicUrl by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        profile?.let { u ->
            fullName = u.fullName
            email = u.email
            phone = u.phoneNumber
            address = u.address
            city = u.city
            department = u.department
            zipCode = u.zipCode
            profilePicUrl = u.profilePictureUrl
        }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil de Usuario", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF94B8FF))
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .background(Color(0xFFF8FAFF))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(profilePicUrl.ifBlank { "https://cdn-icons-png.flaticon.com/512/847/847969.png" })
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )

            Spacer(Modifier.height(24.dp))

            // -------- Campos de perfil --------
            FieldRow("Nombre", fullName, { fullName = it }, isEditing)
            // ✅ CORRECCIÓN: Añadido el parámetro 'enabled = false'
            FieldRow("Correo electrónico", email, { email = it }, isEditing)
            FieldRow("Teléfono", phone, { phone = it }, isEditing)
            FieldRow("Dirección", address, { address = it }, isEditing)
            FieldRow("Ciudad", city, { city = it }, isEditing)
            FieldRow("Departamento", department, { department = it }, isEditing)
            FieldRow("Código ZIP", zipCode, { zipCode = it }, isEditing)

            Spacer(Modifier.height(24.dp))

            // Botón de Ajustes
            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ir a Ajustes de la App")
            }

            Spacer(Modifier.height(16.dp))

            // Botón principal (Editar / Guardar)
            Button(
                onClick = {
                    if (!isEditing) {
                        isEditing = true
                    } else {
                        val updatedProfile = UserProfile(
                            fullName = fullName,
                            email = email,
                            phoneNumber = phone,
                            address = address,
                            city = city,
                            department = department,
                            zipCode = zipCode,
                            profilePictureUrl = profilePicUrl,
                            notificationsEnabled = profile?.notificationsEnabled ?: true
                        )
                        vm.saveProfile(updatedProfile) { ok, _ ->
                            if (ok) isEditing = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF94B8FF)
                )
            ) {
                Text(
                    if (isEditing) "Guardar cambios" else "Editar perfil",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean,
    enabled: Boolean = true // El parámetro que faltaba en la llamada
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = Color(0xFF4A4A4A))
        Spacer(Modifier.height(4.dp))

        if (isEditing && enabled) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
        } else {
            Surface(
                color = if (enabled) Color.White else Color(0xFFF0F0F0),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (value.isNotBlank()) value else "—",
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = if (enabled) Color.Black else Color.Gray
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
