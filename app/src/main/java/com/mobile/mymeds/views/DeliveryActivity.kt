@file:Suppress("DEPRECATION")

package com.mobile.mymeds.views

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.mymeds.models.Prescription
import com.mobile.mymeds.ui.theme.MyMedsTheme
import com.mobile.mymeds.viewModels.DeliveryViewModel
import java.text.SimpleDateFormat
import java.util.*

class DeliveryActivity : ComponentActivity() {

    private val viewModel: DeliveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pharmacyName = intent.getStringExtra("PHARMACY_NAME") ?: "Farmacia"

        // Cargar las prescripciones del usuario
        viewModel.loadUserPrescriptions()

        setContent {
            MyMedsTheme {
                DeliveryScreen(
                    viewModel = viewModel,
                    pharmacyName = pharmacyName,
                    onBackClick = { finish() },
                    onRefresh = { viewModel.loadUserPrescriptions() },
                    onUploadPrescription = {
                        val intent = Intent(this, UploadPrescriptionActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recargar prescripciones automáticamente cuando el usuario regrese
        viewModel.loadUserPrescriptions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(
    viewModel: DeliveryViewModel,
    pharmacyName: String,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPrescription: () -> Unit
) {
    val prescriptions by viewModel.prescriptions.observeAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.observeAsState(initial = true)
    val errorMessage by viewModel.errorMessage.observeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DELIVERY - $pharmacyName",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6B9BD8)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF6B9BD8)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Error desconocido",
                            color = Color.Red,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6B9BD8)
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reintentar")
                        }
                    }
                }
                prescriptions.isEmpty() -> {
                    // Estado vacío - Sin prescripciones
                    EmptyPrescriptionsState(
                        onUploadClick = onUploadPrescription,
                        onRefresh = onRefresh
                    )
                }
                else -> {
                    // Lista de prescripciones
                    PrescriptionsList(
                        prescriptions = prescriptions
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyPrescriptionsState(
    onUploadClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono de maletín médico
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Sin prescripciones",
            modifier = Modifier.size(120.dp),
            tint = Color(0xFFB0C4DE)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No hay prescripciones disponibles",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6B9BD8),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No puedes crear un pedido porque no tienes prescripciones\ncargadas o asociadas con tu cuenta.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onUploadClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6B9BD8)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(50.dp)
        ) {
            Text(
                "Subir Prescripción",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Actualizar",
                tint = Color(0xFF6B9BD8)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Actualizar",
                color = Color(0xFF6B9BD8),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PrescriptionsList(
    prescriptions: List<Prescription>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(prescriptions) { prescription ->
            PrescriptionCard(prescription = prescription)
        }
    }
}

@Composable
fun PrescriptionCard(prescription: Prescription) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8E8E8)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Prescripción ${prescription.id.take(8)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                // Badge de estado
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (prescription.activa) Color(0xFF4CAF50) else Color.Gray
                ) {
                    Text(
                        text = if (prescription.activa) "Activa" else "Inactiva",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

            if (prescription.diagnostico.isNotEmpty()) {
                Text(
                    text = "Diagnóstico:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )
                Text(
                    text = prescription.diagnostico,
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }

            if (prescription.medico.isNotEmpty()) {
                Text(
                    text = "Médico: ${prescription.medico}",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
            }

            prescription.fechaCreacion?.let { timestamp ->
                val dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, h:mm a", Locale("es", "ES"))
                val date = dateFormat.format(timestamp.toDate())
                Text(
                    text = "Fecha: $date",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { /* TODO: Crear pedido con esta prescripción */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B9BD8)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Crear Pedido",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}