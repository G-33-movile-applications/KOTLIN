package com.mobile.mymeds.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.mymeds.MyMedsApplication
import com.mobile.mymeds.models.GlobalMedication
import com.mobile.mymeds.viewModels.NfcBuilderViewModel
import com.mobile.mymeds.viewModels.NfcBuilderViewModelFactory

private data class EditableMedication(
    val medication: GlobalMedication,
    var frequency: Int = 8,
    var days: Int = 7
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcBuilderActivity(
    onBuildPrescription: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as MyMedsApplication
    val viewModel: NfcBuilderViewModel = viewModel(
        factory = NfcBuilderViewModelFactory(application.globalMedicationRepository)
    )
    val allMedications by viewModel.allMedications.collectAsState()

    val selectedMeds = remember { mutableStateListOf<EditableMedication>() }

    // Extrae la dosis del nombre del med
    fun getDoseFromString(name: String): String {
        val dosePart = name.split(" ").find { it.isNotEmpty() && it.first().isDigit() }
        return dosePart?.filter { it.isDigit() } ?: "0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear Prescripción") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedMeds.isNotEmpty()) {
                        // Crea la lista de jsons de meds
                        val medJsonStrings = selectedMeds.map { editableMed ->
                            // Construye el med
                            """{"drug":"${editableMed.medication.nombre}","dose":"${getDoseFromString(editableMed.medication.nombre)}","freq":"${editableMed.frequency}h","days":${editableMed.days}}"""
                        }
                        onBuildPrescription(medJsonStrings)
                    }
                }
            ) {
                Icon(Icons.Default.Done, contentDescription = "Finalizar")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (allMedications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Cargando medicamentos...")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allMedications, key = { it.id }) { medication ->
                        val index = selectedMeds.indexOfFirst { it.medication.id == medication.id }
                        val isSelected = index != -1

                        SelectableMedicationItem(
                            medication = medication,
                            editableMed = if (isSelected) selectedMeds[index] else null,
                            isSelected = isSelected,
                            onToggle = {
                                if (isSelected) {
                                    selectedMeds.removeAt(index)
                                } else {
                                    selectedMeds.add(EditableMedication(medication = medication))
                                }
                            },
                            onFrequencyChange = { newFreq ->
                                if (isSelected) selectedMeds[index] = selectedMeds[index].copy(frequency = newFreq)
                            },
                            onDaysChange = { newDays ->
                                if (isSelected) selectedMeds[index] = selectedMeds[index].copy(days = newDays)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectableMedicationItem(
    medication: GlobalMedication,
    editableMed: EditableMedication?,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onFrequencyChange: (Int) -> Unit,
    onDaysChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(medication.nombre, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(medication.laboratorio, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            }

            AnimatedVisibility(visible = isSelected && editableMed != null) {
                Column {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = "Frecuencia", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Frecuencia (h)", modifier = Modifier.weight(1f))
                        NumberStepper(
                            value = editableMed?.frequency ?: 8,
                            onValueChange = onFrequencyChange,
                            range = 1..24
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Días", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Días de tratamiento", modifier = Modifier.weight(1f))
                        NumberStepper(
                            value = editableMed?.days ?: 7,
                            onValueChange = onDaysChange,
                            range = 1..30
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Text(
            text = value.toString(),
            modifier = Modifier.widthIn(min = 24.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}
