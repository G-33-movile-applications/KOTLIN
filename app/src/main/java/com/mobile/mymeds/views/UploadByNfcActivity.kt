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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                        onWrite = {
                            navController.navigate("nfc_builder_screen")
                        },
                        onSave = {
                            val userId = FirebaseAuth.getInstance().currentUser?.uid
                            if (userId.isNullOrBlank()) {
                                Toast.makeText(this@UploadByNfcActivity, "Error: Usuario no autenticado.", Toast.LENGTH_LONG).show()
                            } else {
                                vm.saveLastReadDataToFirebase(userId) { success, message ->
                                    Toast.makeText(this@UploadByNfcActivity, message, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        finish()
                                    }
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
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }

   override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            Log.d("UploadByNfcActivity", "Foreground Dispatch discovered a tag.")
            val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            tag?.let {
                vm.onTagDiscovered(it)
            }
        }
    }

    private fun enableForegroundDispatch() {
        if (nfcAdapter == null) {
            Log.e("UploadByNfcActivity", "NFC Adapter not available.")
            return
        }
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE) // Using MUTABLE as in your original file
            val ndefFilter = arrayOf(IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply { addDataType("*/*") })
            val techLists = arrayOf(arrayOf(Ndef::class.java.name))
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, ndefFilter, techLists)
            Log.d("UploadByNfcActivity", "Foreground Dispatch Enabled")
        } catch (e: Exception) {
            Log.e("UploadByNfcActivity", "Error enabling foreground dispatch", e)
        }
    }

    private fun disableForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
            Log.d("UploadByNfcActivity", "Foreground Dispatch Disabled")
        } catch (e: Exception) {
            Log.e("UploadByNfcActivity", "Error disabling foreground dispatch", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadByNfcScreen(
    vm: NfcViewModel,
    onRead: () -> Unit,
    onStopRead: () -> Unit,
    onWrite: () -> Unit,
    onWipe: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cargar Prescripción por NFC", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderStatusCard(
                supported = ui.supported,
                enabled = ui.enabled,
                reading = ui.reading,
                status = ui.status,
                lastReadData = ui.parsedData
            )

            if (ui.parsedData != null && !ui.isSaving) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.Save, contentDescription = "Guardar")
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar prescripción")
                }
            }

            if (ui.isSaving) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(ui.status, style = MaterialTheme.typography.bodyMedium)
            }

            Text("Acciones NFC", style = MaterialTheme.typography.titleMedium)

            LargeActionCard(
                title = "Leer Prescripción desde NFC",
                subtitle = "Escanea un tag NFC para cargar una prescripción",
                enabled = ui.supported && ui.enabled && !ui.reading,
                onClick = onRead,
                leading = { Text("📖", style = MaterialTheme.typography.titleLarge) }
            )

            LargeActionCard(
                title = "Escribir Prescripción en NFC",
                subtitle = "Selecciona medicamentos para guardar en un tag NFC",
                enabled = ui.supported && ui.enabled,
                onClick = onWrite,
                leading = { Text("✍️", style = MaterialTheme.typography.titleLarge) }
            )

            LargeActionCard(
                title = "Limpiar Tag NFC",
                subtitle = "Borra todo el contenido de un tag NFC",
                enabled = ui.supported && ui.enabled,
                onClick = onWipe, // This works as before
                leading = { Text("🧹", style = MaterialTheme.typography.titleLarge) }
            )

            if (ui.reading) {
                Button(onClick = onStopRead, modifier = Modifier.fillMaxWidth()) {
                    Text("Detener lectura")
                }
            }

            Spacer(Modifier.weight(1f))

            HelpBox(
                title = "Cómo usar NFC",
                bullets = listOf("Activa NFC en tu dispositivo", "Acerque el teléfono al tag NFC", "Espere la confirmación de lectura o escritura"),
                footnote = null
            )
        }
    }
}

// Funcion para terminar de armar el String que será el JSON
private fun buildPrescriptionJson(medJsonStrings: List<String>): String {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "default_user_id_error"
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    val currentTime = sdf.format(java.util.Date())
    val medsArrayString = medJsonStrings.joinToString(separator = ",")

    return """
    {
      "rxId": "RX-${System.currentTimeMillis()}",
      "patient": "$currentUserId",
      "meds": [$medsArrayString],
      "issuedAt": "$currentTime",
      "signed": true
    }
    """.trimIndent()
}
