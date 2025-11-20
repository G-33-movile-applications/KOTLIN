package com.mobile.mymeds.views.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.mymeds.viewModels.NfcViewModel
import java.text.SimpleDateFormat
import java.util.Locale

object PrescriptionComponents {
    
    @Composable
    fun HeaderStatusCard(
        supported: Boolean,
        enabled: Boolean,
        reading: Boolean,
        status: String,
        lastReadData: NfcViewModel.NfcData?
    ) {
        // Se cambia el fondo si se lee exitosamente
        val bgColor = if (lastReadData != null) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFB8C7EE)

        Surface(
            color = bgColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // NFC Status Text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val nfcIcon = when {
                        !supported -> "❌"
                        !enabled -> "⚠️"
                        reading -> "📡"
                        else -> "✅"
                    }
                    Text(nfcIcon, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !supported -> "NFC No Soportado"
                            !enabled -> "NFC Desactivado"
                            reading -> "Escaneando..."
                            else -> "NFC Listo"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                if (lastReadData != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PrescriptionInfoCard(prescription = lastReadData)
                }
            }
        }
    }

    /**
     * Composable con detalle de la prescripción
     */
    @Composable
    private fun PrescriptionInfoCard(prescription: NfcViewModel.NfcData) {
        val issuedDate = remember(prescription.issuedTimestamp) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                parser.parse(prescription.issuedTimestamp)?.let { formatter.format(it) } ?: "Fecha inválida"
            } catch (e: Exception) {
                "Fecha inválida"
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Info de la prescripción conteniendo los meds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoRow(icon = Icons.Default.Description, text = prescription.id)
                Text(
                    text = issuedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Lista de meds
            prescription.medications.forEach { med ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InfoRow(icon = Icons.Default.MedicalServices, text = med.drugName, isTitle = true)
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                InfoRow(icon = Icons.Default.Vaccines, text = "Dosis ${med.dose}mg")
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                InfoRow(icon = Icons.Default.Schedule, text = "Cada ${med.frequency}")
                            }
                        }
                        InfoRow(icon = Icons.Default.CalendarToday, text = "${med.durationInDays} días de tratamiento")
                    }
                }
            }
        }
    }

    /**
     * Para crear las filas de icono y texto
     */
    @Composable
    private fun InfoRow(icon: ImageVector, text: String, isTitle: Boolean = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(if (isTitle) 20.dp else 18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = if (isTitle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isTitle) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    fun LargeActionCard(
        title: String,
        subtitle: String,
        enabled: Boolean,
        onClick: () -> Unit,
        leading: @Composable (() -> Unit)? = null
    ) {
        val bg = if (enabled) Color(0xFFB8C7EE) else Color(0xFFCBD5E1)
        val content = if (enabled) Color(0xFF1A2247) else Color(0xFF475569)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bg,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    leading?.invoke()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.75f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("›", color = content, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }

    @Composable
    fun HelpBox(title: String, bullets: List<String>, footnote: String?) {
        Surface(
            color = Color(0xFFE8F2FF),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ℹ️")
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bullets.forEach {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✅")
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                footnote?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                }
            }
        }
    }
}
