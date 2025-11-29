package com.mobile.mymeds.views

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
                    pharmacyId = pharmacyId,
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
    pharmacyId: String,
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
                        text = "No hay medicamentos disponibles en esta farmacia",
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
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
                            MedicationCard(
                                medication = medication,
                                pharmacyId = pharmacyId,
                                pharmacyName = pharmacyName,
                                onFollowClick = { inventoryId, medicationName ->
                                    viewModel.followOutOfStockItem(
                                        inventoryId = inventoryId,
                                        medicationName = medicationName,
                                        pharmacyId = pharmacyId,
                                        pharmacyName = pharmacyName
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationCard(
    medication: InventoryMedication,
    pharmacyId: String,
    pharmacyName: String,
    onFollowClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = medication.nombre,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            if (medication.descripcion.isNotEmpty()) {
                Text(text = medication.descripcion, fontSize = 13.sp, color = Color(0xFF454545))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- SECCIÓN DE STOCK, PRECIO Y BOTÓN "AVÍSAME" ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Columna para Stock y precio (ocupa el espacio disponible)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Stock: ${medication.stock}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            medication.stock > 10 -> Color(0xFF388E3C) // Verde oscuro
                            medication.stock > 0 -> Color(0xFFFFA000) // Ámbar oscuro
                            else -> Color(0xFFD32F2F) // Rojo oscuro
                        }
                    )
                    Text(
                        text = "$${medication.precioUnidad}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2) // Azul oscuro
                    )
                }

                // Botón "Avísame" (solo si no hay stock)
                if (medication.stock <= 0) {
                    Button(
                        onClick = {
                            val inventoryId = "${pharmacyId}_${medication.id}"
                            onFollowClick(inventoryId, medication.nombre)
                            Toast.makeText(context, "Se te notificará cuando haya stock", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B9BD8)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notificar", modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Avísame", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
            // -------------------------------------------------------------

            // --- RESTO DE DETALLES DEL MEDICAMENTO ---
            if (medication.principioActivo.isNotEmpty()) {
                Text(text = "Principio activo: ${medication.principioActivo}", fontSize = 13.sp)
            }

            if (medication.presentacion.isNotEmpty()) {
                Text(
                    text = "Presentación: ${medication.presentacion}",
                    fontSize = 13.sp,
                    color = Color(0xFF3C3C3C)
                )
            }

            if (medication.laboratorio.isNotEmpty()) {
                Text(
                    text = "Laboratorio: ${medication.laboratorio}",
                    fontSize = 13.sp,
                    color = Color(0xFF3C3C3C)
                )
            }

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
