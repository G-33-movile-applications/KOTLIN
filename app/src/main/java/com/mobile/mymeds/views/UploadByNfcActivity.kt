package com.mobile.mymeds.views

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mobile.mymeds.viewModels.NfcViewModel
import com.mobile.mymeds.views.components.PrescriptionComponents.HeaderStatusCard
import com.mobile.mymeds.views.components.PrescriptionComponents.LargeActionCard
import com.mobile.mymeds.views.components.PrescriptionComponents.HelpBox
import com.google.firebase.auth.FirebaseAuth

class UploadByNfcActivity : ComponentActivity() {

    private val vm: NfcViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        vm.init(nfcAdapter)

        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "main_nfc_screen") {
                composable("main_nfc_screen") {
                    UploadByNfcScreen(
                        vm = vm,
                        onRead = { vm.startReading() },
                        onStopRead = { vm.stopReading() },
                        onWipe = { vm.prepareToWipe() },
                        onWrite = { navController.navigate("nfc_builder_screen") },
                        onSaveAll = {
                            val userId = FirebaseAuth.getInstance().currentUser?.uid
                            if (userId.isNullOrBlank()) {
                                Toast.makeText(this@UploadByNfcActivity, "Error: Usuario no autenticado.", Toast.LENGTH_LONG).show()
                            } else {
                                vm.saveAllPendingToFirebase(userId) { _, message ->
                                    Toast.makeText(this@UploadByNfcActivity, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onBack = { finish() }
                    )
                }
                composable("nfc_builder_screen") {
                    NfcBuilderActivity(
                        onBuildPrescription = { medJsonStrings ->
                            val finalJson = buildPrescriptionJson(medJsonStrings)
                            vm.prepareToWrite(finalJson)
                            navController.popBackStack()
                            Toast.makeText(this@UploadByNfcActivity, "Listo para escribir. Acerque el tag NFC.", Toast.LENGTH_LONG).show()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    override fun onResume() { super.onResume(); enableForegroundDispatch() }
    override fun onPause() { super.onPause(); disableForegroundDispatch() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            Log.d("UploadByNfcActivity", "Foreground Dispatch discovered a tag.")
            val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else { @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) }
            tag?.let { vm.onTagDiscovered(it) }
        }
    }
    private fun enableForegroundDispatch() {
        if (nfcAdapter == null) { Log.e("UploadByNfcActivity", "NFC Adapter not available."); return }
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
            val ndefFilter = arrayOf(IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply { addDataType("*/*") })
            val techLists = arrayOf(arrayOf(Ndef::class.java.name))
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, ndefFilter, techLists)
            Log.d("UploadByNfcActivity", "Foreground Dispatch Enabled")
        } catch (e: Exception) { Log.e("UploadByNfcActivity", "Error enabling foreground dispatch", e) }
    }
    private fun disableForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
            Log.d("UploadByNfcActivity", "Foreground Dispatch Disabled")
        } catch (e: Exception) { Log.e("UploadByNfcActivity", "Error disabling foreground dispatch", e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadByNfcScreen(
    vm: NfcViewModel,
    onRead: () -> Unit, onStopRead: () -> Unit, onWrite: () -> Unit,
    onWipe: () -> Unit, onSaveAll: () -> Unit, onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cargar Prescripción por NFC", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                HeaderStatusCard(
                    supported = ui.supported, enabled = ui.enabled, reading = ui.reading,
                    status = ui.status, lastReadData = null
                )
            }
            if (ui.isSaving) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                        Spacer(Modifier.height(16.dp))
                        Text(ui.status, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                if (ui.pendingPrescriptions.isNotEmpty()) {
                    item { Text("Prescripciones en cola:", style = MaterialTheme.typography.titleMedium) }
                    items(ui.pendingPrescriptions) { prescription ->
                        PrescriptionDetailsCard(prescription = prescription) // Usa el nuevo composable
                    }
                    item {
                        Button(
                            onClick = onSaveAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(top = 8.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = "Subir todo")
                            Spacer(Modifier.width(8.dp))
                            Text("Subir ${ui.pendingPrescriptions.size} prescripción(es)")
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { Text("Acciones NFC", style = MaterialTheme.typography.titleMedium) }
                item {
                    LargeActionCard(
                        title = "Leer Prescripción desde NFC", subtitle = "Escanea un tag para añadirlo a la cola",
                        enabled = ui.supported && ui.enabled, onClick = onRead,
                        leading = { Text("📖", style = MaterialTheme.typography.titleLarge) }
                    )
                }
                item {
                    LargeActionCard(
                        title = "Escribir Prescripción en NFC", subtitle = "Crea y guarda una prescripción en un tag",
                        enabled = ui.supported && ui.enabled, onClick = onWrite,
                        leading = { Text("✍️", style = MaterialTheme.typography.titleLarge) }
                    )
                }
                item {
                    LargeActionCard(
                        title = "Limpiar Tag NFC", subtitle = "Borra el contenido de un tag",
                        enabled = ui.supported && ui.enabled, onClick = onWipe,
                        leading = { Text("🧹", style = MaterialTheme.typography.titleLarge) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HelpBox(
                        title = "Cómo usar NFC",
                        bullets = listOf(
                            "Activa NFC en tu dispositivo.",
                            "Acerque el teléfono al tag NFC para leer o escribir.",
                            "Puedes escanear varias prescripciones antes de subirlas."
                        ),
                        footnote = null
                    )
                }
            }
        }
    }
}

@Composable
private fun PrescriptionDetailsCard(prescription: NfcViewModel.NfcData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ID: ${prescription.id}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Emitida: ${prescription.issuedTimestamp.substringBefore('T')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider(modifier = Modifier.padding(vertical = 10.dp))

            // Lista de medicamentos
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                prescription.medications.forEach { med ->
                    Column {
                        // Nombre del medicamento
                        Text(
                            text = med.drugName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Detalles con iconos
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DetailItem(icon = Icons.Filled.Vaccines, text = "${med.dose} mg")
                            DetailItem(icon = Icons.Filled.HourglassTop, text = med.frequency)
                            DetailItem(icon = Icons.Filled.CalendarToday, text = "${med.durationInDays} días")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


private fun buildPrescriptionJson(medJsonStrings: List<String>): String {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "default_user_id_error"
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    val currentTime = sdf.format(java.util.Date())
    val medsArrayString = medJsonStrings.joinToString(separator = ",")
    return """{"rxId":"RX-${System.currentTimeMillis()}","patient":"$currentUserId","meds":[$medsArrayString],"issuedAt":"$currentTime","signed":true}"""
}

