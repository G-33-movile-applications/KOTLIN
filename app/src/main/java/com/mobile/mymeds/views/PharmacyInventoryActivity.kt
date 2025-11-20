package com.mobile.mymeds.views

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.mymeds.models.InventoryMedication
import com.mobile.mymeds.ui.theme.MyMedsTheme
import com.mobile.mymeds.viewModels.PharmacyInventoryViewModel

class PharmacyInventoryActivity : ComponentActivity() {

    private val viewModel: PharmacyInventoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pharmacyName = intent.getStringExtra("PHARMACY_NAME") ?: "Farmacia"
        val pharmacyId = intent.getStringExtra("PHARMACY_ID") ?: return finish()

        viewModel.loadPharmacyInventory(pharmacyId)

        setContent {
            MyMedsTheme {
                PharmacyInventoryScreen(
                    viewModel = viewModel,
                    pharmacyName = pharmacyName,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyInventoryScreen(
    viewModel: PharmacyInventoryViewModel,
    pharmacyName: String,
    onBackClick: () -> Unit
) {
    val medications by viewModel.medications.observeAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.observeAsState(initial = true)
    val errorMessage by viewModel.errorMessage.observeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pharmacyName,
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
                    Text(
                        text = errorMessage ?: "Error desconocido",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Red
                    )
                }
                medications.isEmpty() -> {
                    Text(
                        text = "No hay medicamentos disponibles",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(medications) { medication ->
                            MedicationCard(medication = medication)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationCard(medication: InventoryMedication) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8E8E8)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = medication.nombre,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            // Stock y precio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stock: ${medication.stock}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (medication.stock > 10) Color(0xFF4CAF50) else Color(0xFFFF5722)
                )
                Text(
                    text = "$${medication.precioUnidad}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
            }

            if (medication.descripcion.isNotEmpty()) {
                Text(text = medication.descripcion, fontSize = 13.sp, color = Color(0xFF2C2C2C))
            }

            if (medication.principioActivo.isNotEmpty()) {
                Text(text = "Principio activo: ${medication.principioActivo}", fontSize = 13.sp)
            }

            // Presentación
            if (medication.presentacion.isNotEmpty()) {
                Text(
                    text = "Presentación: ${medication.presentacion}",
                    fontSize = 13.sp,
                    color = Color(0xFF3C3C3C)
                )
            }

            // Laboratorio
            if (medication.laboratorio.isNotEmpty()) {
                Text(
                    text = "Laboratorio: ${medication.laboratorio}",
                    fontSize = 13.sp,
                    color = Color(0xFF3C3C3C)
                )
            }

            // Contraindicaciones
            if (medication.contraindicaciones.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Contraindicaciones:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8B4513)
                )
                medication.contraindicaciones.forEach { contraindicacion ->
                    Text(
                        text = "• $contraindicacion",
                        fontSize = 12.sp,
                        color = Color(0xFF8B4513),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}