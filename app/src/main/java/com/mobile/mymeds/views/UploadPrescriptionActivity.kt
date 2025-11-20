package com.mobile.mymeds.views

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.mymeds.views.components.PrescriptionComponents.LargeActionCard
import com.mobile.mymeds.views.components.PrescriptionComponents.HelpBox

class UploadPrescriptionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(this)
        val nfcSupported = nfcAdapter != null
        val nfcEnabled = nfcAdapter?.isEnabled == true

        setContent {
            UploadPrescriptionHome(
                nfcSupported = nfcSupported,
                nfcEnabled = nfcEnabled,
                onOpenNfc = { startActivity(Intent(this, UploadByNfcActivity::class.java)) },
                onOpenPhoto = { startActivity(Intent(this, UploadPrescriptionPhotoActivity::class.java)) },
                onOpenPdf = { startActivity(Intent(this, UploadPrescriptionPDFActivity::class.java)) },
                onBack = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPrescriptionHome(
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    onOpenNfc: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenPdf: () -> Unit,
    onBack: () -> Unit
) {
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cargar Prescripción", fontWeight = FontWeight.Bold) },
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
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // NFC card
            LargeActionCard(
                title = "Cargar por NFC",
                subtitle = "Lee o escribe prescripciones mediante tags NFC",
                enabled = nfcSupported && nfcEnabled,
                onClick = onOpenNfc,
                leading = { Text("📶", style = MaterialTheme.typography.titleLarge) }
            )

            if (!nfcSupported || !nfcEnabled) {
                BannerInfo(
                    title = if (!nfcSupported) "NFC No Disponible" else "NFC Desactivado",
                    message = if (!nfcSupported)
                        "Este dispositivo no soporta NFC"
                    else
                        "Activa NFC en ajustes para usar esta función"
                )
            }

            // Imagen
            LargeActionCard(
                title = "Cargar por Imagen",
                subtitle = "Toma o selecciona una foto de tu prescripción",
                enabled = true,
                onClick = onOpenPhoto,
                leading = { Text("🖼️", style = MaterialTheme.typography.titleLarge) }
            )

            // PDF
            LargeActionCard(
                title = "Cargar por PDF",
                subtitle = "Selecciona un archivo PDF de tu prescripción médica",
                enabled = true,
                onClick = onOpenPdf,
                leading = { Text("📄", style = MaterialTheme.typography.titleLarge) }
            )

            HelpBox(
                title = "¿Cómo subir mi prescripción?",
                bullets = listOf(
                    "NFC: intercambia datos entre dispositivos médicos o farmacias",
                    "Imagen: analiza el texto automáticamente con IA",
                    "PDF: sube documentos digitales directamente"
                ),
                footnote = "Todas las prescripciones se almacenan de forma segura en la nube."
            )
        }
    }
}

@Composable
private fun BannerInfo(title: String, message: String) {
    Surface(
        color = Color(0xFFE2EAFD),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF6B7280))
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280))
        }
    }
}
