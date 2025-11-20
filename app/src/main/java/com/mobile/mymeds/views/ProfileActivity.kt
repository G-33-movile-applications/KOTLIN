package com.mobile.mymeds.views

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import com.mobile.mymeds.viewModels.UserProfile

class ProfileActivity : ComponentActivity() {
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileViewModel.loadProfile()

        setContent {
            MyMedsTheme {
                ProfileScreen(
                    vm = profileViewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val profile by vm.profile.observeAsState()
    val loading by vm.loading.observeAsState(false)
    val message by vm.message.observeAsState()

    var isEditing by remember { mutableStateOf(false) }

    // Estados locales de los campos
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var notifications by remember { mutableStateOf(true) }
    var profilePicUrl by remember { mutableStateOf("") }

    // Cargar datos desde el VM a estados locales
    LaunchedEffect(profile) {
        profile?.let { u ->
            fullName = u.fullName
            email = u.email
            phone = u.phoneNumber
            address = u.address
            city = u.city
            department = u.department
            zipCode = u.zipCode
            notifications = u.notificationsEnabled
            profilePicUrl = u.profilePictureUrl
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Perfil de Usuario",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
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
            if (loading && profile == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            // -------- Foto de perfil --------
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
            FieldRow("Correo electrónico", email, { email = it }, isEditing)
            FieldRow("Teléfono", phone, { phone = it }, isEditing)
            FieldRow("Dirección", address, { address = it }, isEditing)
            FieldRow("Ciudad", city, { city = it }, isEditing)
            FieldRow("Departamento", department, { department = it }, isEditing)
            FieldRow("Código ZIP", zipCode, { zipCode = it }, isEditing)

            Spacer(Modifier.height(8.dp))

            // -------- Switch de notificaciones --------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Recibir notificaciones", fontWeight = FontWeight.SemiBold)
                    Text("Activa/desactiva avisos de la app", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = notifications,
                    onCheckedChange = {
                        notifications = it
                        vm.toggleNotifications(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF8DB4FF)
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // -------- Botón principal (Editar / Guardar) --------
            Button(
                onClick = {
                    if (!isEditing) {
                        // Cambiar a modo edición
                        isEditing = true
                    } else {
                        // Guardar cambios
                        val updated = UserProfile(
                            fullName = fullName,
                            email = email,
                            phoneNumber = phone,
                            address = address,
                            city = city,
                            department = department,
                            zipCode = zipCode,
                            profilePictureUrl = profilePicUrl.ifBlank { "https://cdn-icons-png.flaticon.com/512/847/847969.png" },
                            notificationsEnabled = notifications
                        )
                        vm.saveProfile(updated) { ok, msg ->
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                            if (ok) isEditing = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEditing) Color(0xFF94B8FF) else Color(0xFFE3E7F0)
                )
            ) {
                Text(
                    if (isEditing) "Guardar cambios" else "Editar perfil",
                    color = if (isEditing) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold
                )
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFF374151))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ---------- Helper Composable ---------- */
@Composable
fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = Color(0xFF4A4A4A))
        Spacer(Modifier.height(4.dp))

        if (isEditing) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
        } else {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (value.isNotBlank()) value else "—",
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
