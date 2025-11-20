package com.mobile.mymeds.views

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.mymeds.data.reminders.RemindersDatabase
import com.mobile.mymeds.data.reminders.MedicationReminderEntity
import com.mobile.mymeds.data.reminders.ReminderSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ════════════════════════════════════════════════════════════════════════
// MODELOS (DOMINIO)
// ════════════════════════════════════════════════════════════════════════

enum class ReminderRecurrence {
    ONCE,
    DAILY,
    WEEKLY;

    fun label(): String = when (this) {
        ONCE -> "Una vez"
        DAILY -> "Diario"
        WEEKLY -> "Semanal"
    }
}

data class MedicationReminder(
    val id: String = "",
    val medicationName: String = "",
    val time: String = "", // HH:mm
    val recurrence: ReminderRecurrence = ReminderRecurrence.ONCE,
    val isActive: Boolean = true,
    val createdAtMillis: Long? = null
)

// ════════════════════════════════════════════════════════════════════════
// REPOSITORIO LOCAL (Room + Preferences)
// ════════════════════════════════════════════════════════════════════════

class MedicationReminderRepository(
    context: Context
) {

    private val roomDb = RemindersDatabase.getInstance(context)
    private val dao = roomDb.medicationRemindersDao()
    private val settingsManager = ReminderSettingsManager(context) // para futuros ajustes globales

    // Helpers de mapeo Entity ↔ Domain
    private fun MedicationReminderEntity.toDomain(): MedicationReminder =
        MedicationReminder(
            id = id,
            medicationName = medicationName,
            time = time,
            recurrence = when (recurrence) {
                ReminderRecurrence.DAILY.name -> ReminderRecurrence.DAILY
                ReminderRecurrence.WEEKLY.name -> ReminderRecurrence.WEEKLY
                else -> ReminderRecurrence.ONCE
            },
            isActive = isActive,
            createdAtMillis = createdAtMillis
        )

    private fun MedicationReminder.toEntity(): MedicationReminderEntity =
        MedicationReminderEntity(
            id = if (id.isBlank()) UUID.randomUUID().toString() else id,
            medicationName = medicationName,
            time = time,
            recurrence = recurrence.name,
            isActive = isActive,
            createdAtMillis = createdAtMillis ?: System.currentTimeMillis()
        )

    suspend fun getReminders(): List<MedicationReminder> {
        val entities = dao.getAllOnce()
        return entities.map { it.toDomain() }
    }

    suspend fun createReminder(
        medicationName: String,
        time: String,
        recurrence: ReminderRecurrence
    ): Result<Unit> {
        return try {
            val entity = MedicationReminderEntity(
                medicationName = medicationName,
                time = time,
                recurrence = recurrence.name,
                isActive = true,
                createdAtMillis = System.currentTimeMillis()
            )
            dao.upsert(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setReminderActive(reminderId: String, active: Boolean) {
        dao.setActive(reminderId, active)
        // Aquí en el futuro: usar settingsManager.globalEnabled y programar / cancelar alarmas.
    }

    suspend fun deleteReminder(reminderId: String) {
        dao.deleteById(reminderId)
    }
}

// ════════════════════════════════════════════════════════════════════════
// UI STATE
// ════════════════════════════════════════════════════════════════════════

sealed class RemindersUiState {
    object Loading : RemindersUiState()
    data class Success(val reminders: List<MedicationReminder>) : RemindersUiState()
    data class Error(val message: String) : RemindersUiState()
}

// ════════════════════════════════════════════════════════════════════════
// VIEWMODEL + FACTORY
// ════════════════════════════════════════════════════════════════════════

class MedicationRemindersViewModel(
    private val repository: MedicationReminderRepository
) : ViewModel() {

    private val _uiState = mutableStateOf<RemindersUiState>(RemindersUiState.Loading)
    val uiState: State<RemindersUiState> get() = _uiState

    fun loadReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = RemindersUiState.Loading
                val list = repository.getReminders()
                _uiState.value = RemindersUiState.Success(list)
            } catch (e: Exception) {
                _uiState.value =
                    RemindersUiState.Error(e.message ?: "Error al cargar recordatorios")
            }
        }
    }

    fun refresh() = loadReminders()

    fun toggleReminder(
        reminder: MedicationReminder,
        onError: (String) -> Unit
    ) {
        val currentState = _uiState.value
        if (currentState !is RemindersUiState.Success) return

        val updatedList = currentState.reminders.map {
            if (it.id == reminder.id) it.copy(isActive = !reminder.isActive) else it
        }
        _uiState.value = RemindersUiState.Success(updatedList)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.setReminderActive(reminder.id, !reminder.isActive)
            } catch (e: Exception) {
                val revertedList = updatedList.map {
                    if (it.id == reminder.id) it.copy(isActive = reminder.isActive) else it
                }
                _uiState.value = RemindersUiState.Success(revertedList)
                onError(e.message ?: "No se pudo actualizar el recordatorio")
            }
        }
    }

    fun createReminder(
        medicationName: String,
        time: String,
        recurrence: ReminderRecurrence,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.createReminder(medicationName, time, recurrence)
                if (result.isSuccess) {
                    val list = repository.getReminders()
                    _uiState.value = RemindersUiState.Success(list)
                    onResult(true, null)
                } else {
                    onResult(false, result.exceptionOrNull()?.message)
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Error creando recordatorio")
            }
        }
    }
}

class MedicationRemindersViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationRemindersViewModel::class.java)) {
            val repo = MedicationReminderRepository(appContext)
            return MedicationRemindersViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// ════════════════════════════════════════════════════════════════════════
// ACTIVITY
// ════════════════════════════════════════════════════════════════════════

class MedicationRemindersActivity : ComponentActivity() {

    private val vm: MedicationRemindersViewModel by viewModels {
        MedicationRemindersViewModelFactory(applicationContext)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val uiState by vm.uiState
            var showCreateDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                vm.loadReminders()
            }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6B9BD8),
                    surface = Color.White
                )
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Recordatorios",
                                    color = Color.White
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.Filled.ArrowBack,
                                        contentDescription = "Atrás",
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { vm.refresh() }) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Refrescar",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF6B9BD8)
                            )
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            containerColor = Color(0xFF6B9BD8),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Agregar recordatorio")
                        }
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { paddingValues ->
                    Box(
                        Modifier
                            .padding(paddingValues)
                            .fillMaxSize()
                            .background(Color(0xFFF5F5F5))
                    ) {
                        when (uiState) {
                            is RemindersUiState.Loading -> {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            }

                            is RemindersUiState.Error -> {
                                val msg = (uiState as RemindersUiState.Error).message
                                ErrorRemindersView(
                                    message = msg,
                                    onRetry = { vm.loadReminders() }
                                )
                            }

                            is RemindersUiState.Success -> {
                                val reminders =
                                    (uiState as RemindersUiState.Success).reminders
                                if (reminders.isEmpty()) {
                                    EmptyRemindersView()
                                } else {
                                    RemindersList(
                                        reminders = reminders,
                                        onToggle = { reminder ->
                                            vm.toggleReminder(reminder) { errorMsg ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(errorMsg)
                                                }
                                            }
                                        },
                                        onEdit = { _ ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Edición de recordatorios próximamente"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (showCreateDialog) {
                            CreateReminderDialog(
                                onDismiss = { showCreateDialog = false },
                                onSave = { name, time, recurrence ->
                                    vm.createReminder(
                                        medicationName = name,
                                        time = time,
                                        recurrence = recurrence
                                    ) { success, errorMsg ->
                                        showCreateDialog = false
                                        scope.launch {
                                            if (success) {
                                                snackbarHostState.showSnackbar("Recordatorio creado")
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    errorMsg ?: "Error creando recordatorio"
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// COMPOSABLES
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun RemindersList(
    reminders: List<MedicationReminder>,
    onToggle: (MedicationReminder) -> Unit,
    onEdit: (MedicationReminder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reminders, key = { it.id }) { reminder ->
            ReminderCard(
                reminder = reminder,
                onToggle = { onToggle(reminder) },
                onEdit = { onEdit(reminder) }
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: MedicationReminder,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.isActive) Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Medication,
                        contentDescription = null,
                        tint = if (reminder.isActive) Color(0xFF1976D2) else Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = reminder.medicationName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "⏰ ${reminder.time} • ${reminder.recurrence.label()}",
                            fontSize = 13.sp,
                            color = Color(0xFF555555)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reminder.isActive) {
                        Badge(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Activo",
                                color = Color(0xFF4CAF50),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Badge(
                            containerColor = Color(0xFFB0BEC5).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "Inactivo",
                                color = Color(0xFF607D8B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar",
                            tint = Color(0xFF455A64)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Switch(
                        checked = reminder.isActive,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.Gray
                        )
                    )
                }
            }

            reminder.createdAtMillis?.let { millis ->
                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFFCFD8DC), thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))

                val sdf = remember {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                }
                Text(
                    text = "Creado: ${sdf.format(Date(millis))}",
                    fontSize = 11.sp,
                    color = Color(0xFF78909C)
                )
            }
        }
    }
}

@Composable
private fun EmptyRemindersView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Medication,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(50.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Aún no tienes recordatorios",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Toca el botón + para agregar uno nuevo",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ErrorRemindersView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Error al cargar recordatorios",
                color = Color(0xFFFF5252),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                color = Color(0xFFB71C1C),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, ReminderRecurrence) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") } // formato HH:mm
    var recurrence by remember { mutableStateOf(ReminderRecurrence.ONCE) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo recordatorio") },
        text = {
            Column {
                androidx.compose.material3.TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicamento") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Hora (HH:mm)") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Recurrencia",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurrenceChip(
                        label = "Una vez",
                        selected = recurrence == ReminderRecurrence.ONCE,
                        onClick = { recurrence = ReminderRecurrence.ONCE }
                    )
                    RecurrenceChip(
                        label = "Diario",
                        selected = recurrence == ReminderRecurrence.DAILY,
                        onClick = { recurrence = ReminderRecurrence.DAILY }
                    )
                    RecurrenceChip(
                        label = "Semanal",
                        selected = recurrence == ReminderRecurrence.WEEKLY,
                        onClick = { recurrence = ReminderRecurrence.WEEKLY }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (name.isBlank() || time.isBlank()) return@TextButton
                    onSave(name.trim(), time.trim(), recurrence)
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RecurrenceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF6B9BD8) else Color(0xFFE0E0E0)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp
        )
    }
}
