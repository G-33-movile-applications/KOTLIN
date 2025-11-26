package com.mobile.mymeds.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobile.mymeds.models.*
import com.mobile.mymeds.viewModels.InvoicesViewModel
import com.mobile.mymeds.viewModels.InvoiceUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Colores
val InvoiceBlue1 = Color(0xFF9FB3DF)
val InvoiceBlue2 = Color(0xFF9EC6F3)
val InvoiceBlue3 = Color(0xFFBDDDE4)
val InvoiceGreen = Color(0xFF4CAF50)
val InvoiceRed = Color(0xFFFF5252)
val InvoiceOrange = Color(0xFFFF9800)

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICES ACTIVITY
 * ═══════════════════════════════════════════════════════════════════════════
 */

@RequiresApi(Build.VERSION_CODES.O)
class InvoicesActivity : ComponentActivity() {

    private val viewModel: InvoicesViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return InvoicesViewModel(applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = InvoiceBlue2,
                    primaryContainer = InvoiceBlue3,
                    secondary = InvoiceBlue1,
                    tertiary = InvoiceBlue2,
                    surface = Color.White,
                    background = Color(0xFFF5F5F5)
                )
            ) {
                InvoicesScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * INVOICES SCREEN
 * ═══════════════════════════════════════════════════════════════════════════
 */

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    viewModel: InvoicesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Estados
    val userOrders by viewModel.userOrders.collectAsState()
    val generatedInvoices by viewModel.generatedInvoices.collectAsState()
    val selectedOrders by viewModel.selectedOrders.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val pendingCount by viewModel.pendingInvoicesCount.collectAsState()
    val generationProgress by viewModel.generationProgress.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Tab seleccionado
    var selectedTab by remember { mutableStateOf(0) }

    // Diálogo de confirmación
    var showGenerateDialog by remember { mutableStateOf(false) }

    // Permisos de almacenamiento
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "✅ Permiso concedido", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        // Verificar permisos de almacenamiento
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Gestión de Facturas",
                            fontWeight = FontWeight.Bold
                        )
                        // Indicador de conectividad
                        if (!isConnected) {
                            Text(
                                "📡 Sin conexión",
                                style = MaterialTheme.typography.labelSmall,
                                color = InvoiceRed
                            )
                        } else if (pendingCount > 0) {
                            Text(
                                "⏳ $pendingCount pendiente(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = InvoiceOrange
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // Botón de recarga
                    IconButton(onClick = {
                        viewModel.loadUserOrders()
                        viewModel.loadGeneratedInvoices(forceRefresh = true)
                    }) {
                        Icon(Icons.Filled.Refresh, "Refrescar")
                    }

                    // Botón de sincronización de cache (útil para resolver discrepancias)
                    IconButton(
                        onClick = {
                            viewModel.syncCacheWithFirebase { count ->
                                val message = when {
                                    count > 0 -> "✅ Cache sincronizado: $count factura(s)"
                                    count == 0 -> "ℹ️ No hay facturas en Firebase"
                                    else -> "❌ Error sincronizando cache"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Sync, "Sincronizar cache")
                    }

                    // Botón de sincronización manual
                    if (pendingCount > 0) {
                        IconButton(
                            onClick = {
                                viewModel.syncPendingInvoicesManually { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        ) {
                            BadgedBox(
                                badge = { Badge { Text("$pendingCount") } }
                            ) {
                                Icon(Icons.Filled.CloudUpload, "Sincronizar")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = InvoiceBlue1
                )
            )
        },
        floatingActionButton = {
            // FAB para generar facturas (solo en tab de pedidos)
            if (selectedTab == 0 && selectedOrders.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showGenerateDialog = true },
                    containerColor = InvoiceGreen,
                    icon = { Icon(Icons.Filled.Receipt, "Generar") },
                    text = { Text("Generar ${selectedOrders.size}") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = InvoiceBlue3
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pedidos", color = Color.Black) },
                    icon = { Icon(Icons.Filled.ShoppingCart, null, tint = Color.Black) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Facturas", color = Color.Black) },
                    icon = { Icon(Icons.Filled.Receipt, null, tint = Color.Black) }
                )
            }

            // Contenido según tab
            when (selectedTab) {
                0 -> OrdersTab(
                    orders = userOrders,
                    selectedOrders = selectedOrders,
                    uiState = uiState,
                    isGenerating = isGenerating,
                    generationProgress = generationProgress,
                    onOrderToggle = { orderId -> viewModel.toggleOrderSelection(orderId) },
                    onClearSelection = { viewModel.clearSelection() }
                )
                1 -> InvoicesTab(
                    invoices = generatedInvoices,
                    onOpenInvoice = { invoice ->
                        openPdf(context, invoice)
                    },
                    onDeleteInvoice = { invoice ->
                        viewModel.deleteInvoice(invoice.id) { success ->
                            if (success) {
                                Toast.makeText(context, "✅ Factura eliminada", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Error eliminando", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRetryInvoice = { invoice ->
                        viewModel.retryInvoice(invoice) { success ->
                            if (success) {
                                Toast.makeText(context, "✅ Factura reintentada", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Error reintentando", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    // Diálogo de confirmación de generación
    if (showGenerateDialog) {
        GenerateInvoicesDialog(
            orderCount = selectedOrders.size,
            isConnected = isConnected,
            onDismiss = { showGenerateDialog = false },
            onConfirm = {
                showGenerateDialog = false
                viewModel.generateInvoicesForSelectedOrders { success, failed ->
                    val message = when {
                        failed == 0 -> "✅ $success factura(s) generada(s)"
                        success == 0 -> "❌ Error: $failed factura(s) fallaron"
                        else -> "✅ $success generada(s), ❌ $failed fallida(s)"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * TAB DE PEDIDOS
 * ═══════════════════════════════════════════════════════════════════════════
 */

@Composable
fun OrdersTab(
    orders: List<MedicationOrder>,
    selectedOrders: Set<String>,
    uiState: InvoiceUiState,
    isGenerating: Boolean,
    generationProgress: Map<String, Float>,
    onOrderToggle: (String) -> Unit,
    onClearSelection: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header con info de selección
        if (selectedOrders.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = InvoiceBlue3)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedOrders.size} pedido(s) seleccionado(s)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClearSelection) {
                        Text("Limpiar", color = InvoiceRed)
                    }
                }
            }
        }

        // Estado de carga
        when (uiState) {
            is InvoiceUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = InvoiceBlue2)
                }
            }
            is InvoiceUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Error,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = InvoiceRed
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.message, color = InvoiceRed)
                    }
                }
            }
            else -> {
                if (orders.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.ShoppingCart,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = InvoiceBlue1.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No hay pedidos disponibles",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Puedes generar facturas de cualquier pedido",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(orders) { order ->
                            OrderCard(
                                order = order,
                                isSelected = selectedOrders.contains(order.id),
                                isGenerating = isGenerating && selectedOrders.contains(order.id),
                                progress = generationProgress[order.id] ?: 0f,
                                onToggle = { onOrderToggle(order.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * TAB DE FACTURAS
 * ═══════════════════════════════════════════════════════════════════════════
 */

@Composable
fun InvoicesTab(
    invoices: List<Invoice>,
    onOpenInvoice: (Invoice) -> Unit,
    onDeleteInvoice: (Invoice) -> Unit,
    onRetryInvoice: (Invoice) -> Unit
) {
    if (invoices.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Receipt,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = InvoiceBlue1.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No hay facturas generadas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(invoices) { invoice ->
                InvoiceCard(
                    invoice = invoice,
                    onOpen = { onOpenInvoice(invoice) },
                    onDelete = { onDeleteInvoice(invoice) },
                    onRetry = { onRetryInvoice(invoice) }
                )
            }
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * COMPONENTES
 * ═══════════════════════════════════════════════════════════════════════════
 */

@Composable
fun OrderCard(
    order: MedicationOrder,
    isSelected: Boolean,
    isGenerating: Boolean,
    progress: Float,
    onToggle: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGenerating) { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) InvoiceBlue3 else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Checkbox
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { if (!isGenerating) onToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = InvoiceGreen)
                    )

                    Spacer(Modifier.width(8.dp))

                    Column {
                        Text(
                            "Pedido #${order.id.take(8)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            order.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                // Badge de estado
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (order.status) {
                        OrderStatus.DELIVERED, OrderStatus.COMPLETED -> InvoiceGreen.copy(alpha = 0.2f)
                        OrderStatus.PENDING -> InvoiceOrange.copy(alpha = 0.2f)
                        OrderStatus.CONFIRMED, OrderStatus.IN_TRANSIT, OrderStatus.READY_PICKUP -> InvoiceBlue2.copy(alpha = 0.2f)
                        OrderStatus.CANCELLED -> InvoiceRed.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        order.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (order.status) {
                            OrderStatus.DELIVERED, OrderStatus.COMPLETED -> InvoiceGreen
                            OrderStatus.PENDING -> InvoiceOrange
                            OrderStatus.CONFIRMED, OrderStatus.IN_TRANSIT, OrderStatus.READY_PICKUP -> InvoiceBlue2
                            OrderStatus.CANCELLED -> InvoiceRed
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Info del pedido
            Text("🏪 ${order.pharmacyName}", style = MaterialTheme.typography.bodySmall)
            Text(
                "💰 Total: $${order.totalAmount}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = InvoiceBlue2
            )
            Text(
                "${order.items.size} medicamento(s)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            // Progreso de generación
            if (isGenerating && progress > 0f) {
                Spacer(Modifier.height(12.dp))
                Column {
                    Text(
                        "Generando factura... ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = InvoiceBlue2
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = InvoiceBlue2
                    )
                }
            }
        }
    }
}

@Composable
fun InvoiceCard(
    invoice: Invoice,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Receipt,
                        null,
                        modifier = Modifier.size(40.dp),
                        tint = InvoiceBlue2
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            invoice.invoiceNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            dateFormat.format(Date(invoice.generatedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                // Badge de estado
                InvoiceStatusBadge(invoice.status)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Info
            if (invoice.orderSnapshot != null) {
                Text(
                    "Pedido: ${invoice.orderSnapshot.orderNumber}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Total: $${invoice.orderSnapshot.totalAmount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = InvoiceBlue2
                )
            }

            // Estado de sincronización
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (invoice.syncedToCloud) "☁️ Sincronizada" else "📱 Solo local",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (invoice.syncedToCloud) InvoiceGreen else InvoiceOrange
                )

                if (invoice.fileSize > 0) {
                    Text(
                        invoice.getFileSizeFormatted(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            // Mensaje de error
            if (invoice.status == InvoiceStatus.ERROR && invoice.errorMessage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "❌ ${invoice.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InvoiceRed
                )
            }

            // Botones de acción
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Abrir PDF
                if (invoice.isAvailableLocally()) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = InvoiceBlue2)
                    ) {
                        Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ver PDF")
                    }
                }

                // Reintentar
                if (invoice.canRetry()) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reintentar")
                    }
                }

                // Eliminar
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Eliminar", tint = InvoiceRed)
                }
            }
        }
    }
}

@Composable
fun InvoiceStatusBadge(status: InvoiceStatus) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(android.graphics.Color.parseColor(status.getColorHex())).copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status.getEmoji(), fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                status.getDisplayName(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(android.graphics.Color.parseColor(status.getColorHex())),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GenerateInvoicesDialog(
    orderCount: Int,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generar Facturas") },
        text = {
            Column {
                Text("¿Generar $orderCount factura(s)?")
                Spacer(Modifier.height(8.dp))
                if (!isConnected) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = InvoiceOrange.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = InvoiceOrange)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Sin conexión. Las facturas se sincronizarán automáticamente cuando recuperes internet.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = InvoiceGreen)
            ) {
                Text("Generar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * FUNCIONES AUXILIARES
 * ═══════════════════════════════════════════════════════════════════════════
 */

fun openPdf(context: android.content.Context, invoice: Invoice) {
    try {
        val pdfFile = File(invoice.localPdfPath)

        if (!pdfFile.exists()) {
            Toast.makeText(context, "❌ Archivo no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(Intent.createChooser(intent, "Abrir PDF con:"))

    } catch (e: Exception) {
        Toast.makeText(context, "❌ Error abriendo PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}