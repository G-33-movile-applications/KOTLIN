package com.mobile.mymeds.views

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mobile.mymeds.models.AnalyticsUiState
import com.mobile.mymeds.models.DeliveryMode
import com.mobile.mymeds.models.UserAnalytics
import com.mobile.mymeds.viewModels.UserAnalyticsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * =============================================================================
 * ANALYTICS ACTIVITY
 * =============================================================================
 *
 * Actividad principal para la visualización de analíticas de usuario con
 * soporte offline mediante LRU Cache y monitoreo de conectividad en tiempo real.
 *
 * CARACTERÍSTICAS PRINCIPALES:
 * ---------------------------
 * 1. **LRU Cache Persistente**: Sistema de caché que almacena las últimas
 *    analíticas consultadas, permitiendo acceso offline a datos previamente cargados.
 *    - Capacidad máxima configurable (default: 10 entradas)
 *    - Persistencia en DataStore para sobrevivir reinicios de la app
 *    - Política de desalojo LRU (Least Recently Used)
 *
 * 2. **Indicador de Conectividad**: Texto discreto debajo del título
 *    que muestra el estado de la conexión a internet.
 *    - Solo visible cuando NO hay conexión
 *    - Diseño minimalista y no intrusivo
 *
 * 3. **Filtros Avanzados**: Sistema de filtrado por período y modo de entrega
 *    - Períodos: 7, 30, 90 días o todos
 *    - Modos: Domicilio, Recoger, o Ambos
 *
 * @author Mariana - MyMeds Analytics Team
 * @version 2.0.1
 * @since 2025-11-20
 */
class AnalyticsActivity : ComponentActivity() {

    private val viewModel: UserAnalyticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnalyticsTheme { AnalyticsScreen(viewModel) { finish() } } }
    }
}

// =============================================================================
// CONNECTIVITY INDICATOR - VERSIÓN DISCRETA
// =============================================================================

/**
 * Composable que monitorea el estado de conectividad y muestra un texto
 * discreto solo cuando NO hay conexión.
 *
 * DISEÑO:
 * ------
 * - Texto pequeño (10sp) en color rojo suave
 * - Se muestra debajo del título principal
 * - Solo visible cuando no hay internet
 *
 * @return String con el estado ("Sin conexión a internet" o vacío)
 */
@Composable
fun rememberNetworkState(): Boolean {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(checkNetworkConnection(context)) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                isConnected = true
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                isConnected = false
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    return isConnected
}

/**
 * Función auxiliar que verifica el estado actual de la conexión a internet.
 *
 * @param context Contexto de la aplicación
 * @return true si hay conexión activa, false en caso contrario
 */
private fun checkNetworkConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// =============================================================================
// LRU CACHE IMPLEMENTATION
// =============================================================================

/**
 * Extensión del Context para crear una instancia de DataStore.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "analytics_cache")

/**
 * =============================================================================
 * ANALYTICS LRU CACHE
 * =============================================================================
 *
 * Implementación de un caché LRU (Least Recently Used) persistente para
 * almacenar analíticas de usuario y permitir acceso offline.
 *
 * @param context Contexto de la aplicación para acceder a DataStore
 * @param maxSize Capacidad máxima del caché (número de entradas)
 */
class AnalyticsLruCache(
    private val context: Context,
    private val maxSize: Int = MAX_CACHE_SIZE
) {

    /**
     * LinkedHashMap que implementa el comportamiento LRU.
     */
    private val cache = object : LinkedHashMap<String, UserAnalytics>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UserAnalytics>?): Boolean {
            return size > maxSize
        }
    }

    private val gson = Gson()
    private val CACHE_KEY = stringPreferencesKey("analytics_cache_data")

    companion object {
        private const val MAX_CACHE_SIZE = 10
    }

    /**
     * Recupera un valor del caché en memoria.
     */
    @Synchronized
    fun get(key: String): UserAnalytics? {
        return cache[key]
    }

    /**
     * Almacena un valor en el caché en memoria.
     */
    @Synchronized
    fun put(key: String, value: UserAnalytics) {
        cache[key] = value
    }

    /**
     * Persiste el estado completo del caché a disco usando DataStore.
     */
    suspend fun saveToDisk() {
        val cacheMap = synchronized(cache) {
            cache.toMap()
        }
        val json = gson.toJson(cacheMap)
        context.dataStore.edit { preferences ->
            preferences[CACHE_KEY] = json
        }
    }

    /**
     * Carga el estado del caché desde disco a memoria.
     */
    suspend fun loadFromDisk() {
        try {
            val json = context.dataStore.data.map { preferences ->
                preferences[CACHE_KEY]
            }.first()

            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<Map<String, UserAnalytics>>() {}.type
                val savedCache: Map<String, UserAnalytics> = gson.fromJson(json, type)

                synchronized(cache) {
                    cache.clear()
                    cache.putAll(savedCache)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Limpia completamente el caché (memoria y disco).
     */
    suspend fun clear() {
        synchronized(cache) {
            cache.clear()
        }
        context.dataStore.edit { preferences ->
            preferences.remove(CACHE_KEY)
        }
    }

    /**
     * Retorna el número de entradas actualmente en el caché.
     */
    fun size(): Int = synchronized(cache) { cache.size }
}

// =============================================================================
// MAIN ANALYTICS SCREEN
// =============================================================================

/**
 * Composable principal que renderiza la pantalla de analíticas completa.
 *
 * @param viewModel ViewModel que gestiona la lógica de negocio
 * @param onBack Callback para navegación hacia atrás
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsScreen(
    viewModel: UserAnalyticsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState
    val isRefreshing by viewModel.isRefreshing
    val context = LocalContext.current

    // Estado de conectividad
    val isConnected = rememberNetworkState()

    // Instancia del LRU Cache
    val analyticsCache = remember { AnalyticsLruCache(context) }

    // ---------- Estados de Filtros ----------
    val periodOptions = listOf("Últimos 7 días", "Últimos 30 días", "Últimos 90 días", "Todo")
    var selectedPeriod by remember { mutableStateOf(periodOptions[1]) }
    var showDelivery by remember { mutableStateOf(true) }
    var showPickup by remember { mutableStateOf(true) }
    var menuPeriodExpanded by remember { mutableStateOf(false) }
    var analyticsTab by remember { mutableStateOf(0) }

    // Cargar caché al iniciar
    LaunchedEffect(Unit) {
        analyticsCache.loadFromDisk()
        viewModel.loadAnalytics()
    }

    // Reaccionar a cambios en filtros
    LaunchedEffect(selectedPeriod, showDelivery, showPickup, analyticsTab) {
        val days = when (selectedPeriod) {
            "Últimos 7 días" -> 7
            "Últimos 30 días" -> 30
            "Últimos 90 días" -> 90
            else -> null
        }
        val mode = when {
            showDelivery && showPickup -> null
            showDelivery && !showPickup -> DeliveryMode.DELIVERY
            !showDelivery && showPickup -> DeliveryMode.PICKUP
            else -> null
        }

        // CARGA NORMAL: Siempre carga desde el ViewModel
        viewModel.loadAnalytics(days, mode)
    }

    // Guardar en caché cuando hay datos exitosos
    LaunchedEffect(uiState) {
        if (uiState is AnalyticsUiState.Success) {
            val analytics = (uiState as AnalyticsUiState.Success).analytics
            val days = when (selectedPeriod) {
                "Últimos 7 días" -> 7
                "Últimos 30 días" -> 30
                "Últimos 90 días" -> 90
                else -> null
            }
            val mode = when {
                showDelivery && showPickup -> null
                showDelivery && !showPickup -> DeliveryMode.DELIVERY
                !showDelivery && showPickup -> DeliveryMode.PICKUP
                else -> null
            }
            val cacheKey = "analytics_${days ?: "all"}_${mode?.name ?: "all"}"

            // Guardar en caché para uso offline futuro
            analyticsCache.put(cacheKey, analytics)
            analyticsCache.saveToDisk()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📊 Analíticas", fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            ActiveFiltersPill(selectedPeriod, buildString {
                                append(
                                    when {
                                        showDelivery && showPickup -> "Todos"
                                        showDelivery -> "Domicilio"
                                        showPickup -> "Recoger"
                                        else -> "Todos"
                                    }
                                )
                            })
                        }
                        // Indicador discreto de conectividad
                        if (!isConnected) {
                            Text(
                                text = "⚠️ Sin conexión a internet",
                                fontSize = 10.sp,
                                color = Color(0xFFFFCDD2),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refrescar", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6B9BD8))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF6F8FB))
        ) {

            CompactFiltersBar(
                analyticsTab = analyticsTab,
                onTabChange = { analyticsTab = it },
                periodOptions = periodOptions,
                selectedPeriod = selectedPeriod,
                onOpenPeriod = { menuPeriodExpanded = true },
                onSelectPeriod = { selectedPeriod = it; menuPeriodExpanded = false },
                periodExpanded = menuPeriodExpanded,
                onDismissPeriod = { menuPeriodExpanded = false },
                showDelivery = showDelivery,
                onToggleDelivery = { showDelivery = !showDelivery },
                showPickup = showPickup,
                onTogglePickup = { showPickup = !showPickup }
            )

            Box(Modifier.fillMaxSize().weight(1f)) {
                when (val state = uiState) {
                    is AnalyticsUiState.Loading -> LoadingView()
                    is AnalyticsUiState.Error -> ErrorView(state.message) { viewModel.loadAnalytics() }
                    is AnalyticsUiState.Success -> {
                        when (analyticsTab) {
                            0 -> DeliveryPickupTab(state.analytics, showDelivery, showPickup)
                            1 -> BQT2Tab(state.analytics)
                            2 -> RefillsByDayTab(state.analytics)
                        }
                    }
                }
            }
        }
    }
}

/* =============================================================================
   COMPACT FILTERS BAR
   ============================================================================= */

@Composable
private fun CompactFiltersBar(
    analyticsTab: Int,
    onTabChange: (Int) -> Unit,
    periodOptions: List<String>,
    selectedPeriod: String,
    onOpenPeriod: () -> Unit,
    onSelectPeriod: (String) -> Unit,
    periodExpanded: Boolean,
    onDismissPeriod: () -> Unit,
    showDelivery: Boolean,
    onToggleDelivery: () -> Unit,
    showPickup: Boolean,
    onTogglePickup: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        TabRow(
            selectedTabIndex = analyticsTab,
            containerColor = Color(0xFFEAF2FE),
            indicator = {},
            divider = {}
        ) {
            Tab(
                selected = analyticsTab == 0,
                onClick = { onTabChange(0) },
                modifier = Modifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (analyticsTab == 0) Color.White
                        else Color.Transparent
                    )
            ) {
                Text(
                    "🚚 Entregas",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (analyticsTab == 0) Color(0xFF6B9BD8)
                    else Color(0xFF7F8C8D),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            Tab(
                selected = analyticsTab == 1,
                onClick = { onTabChange(1) },
                modifier = Modifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (analyticsTab == 1) Color.White
                        else Color.Transparent
                    )
            ) {
                Text(
                    "📦 Estados",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (analyticsTab == 1) Color(0xFF6B9BD8)
                    else Color(0xFF7F8C8D),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            Tab(
                selected = analyticsTab == 2,
                onClick = { onTabChange(2) },
                modifier = Modifier
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (analyticsTab == 2) Color.White
                        else Color.Transparent
                    )
            ) {
                Text(
                    "📅 Por Día",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (analyticsTab == 2) Color(0xFF6B9BD8)
                    else Color(0xFF7F8C8D),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                OutlinedButton(
                    onClick = onOpenPeriod,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        selectedPeriod,
                        fontSize = 12.sp,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF2C3E50)
                    )
                }

                DropdownMenu(
                    expanded = periodExpanded,
                    onDismissRequest = onDismissPeriod
                ) {
                    periodOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, fontSize = 13.sp) },
                            onClick = { onSelectPeriod(option) }
                        )
                    }
                }
            }

            FilterChip(
                selected = showDelivery,
                onClick = onToggleDelivery,
                label = {
                    Text(
                        "Domicilio",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = if (showDelivery) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF6B9BD8),
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.height(36.dp)
            )

            FilterChip(
                selected = showPickup,
                onClick = onTogglePickup,
                label = {
                    Text(
                        "Recoger",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = if (showPickup) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF6B9BD8),
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.height(36.dp)
            )
        }
    }
}

@Composable
private fun ActiveFiltersPill(period: String, mode: String) {
    Surface(
        color = Color.White.copy(alpha = 0.25f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            "$period • $mode",
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/* =============================================================================
   STATE VIEWS (Loading, Error)
   ============================================================================= */

@Composable
private fun LoadingView() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF6B9BD8),
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Cargando analíticas...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7F8C8D)
            )
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.Error,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = Color(0xFFE74C3C)
            )
            Text(
                "Error al cargar analíticas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7F8C8D),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B9BD8)
                )
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reintentar")
            }
        }
    }
}

/* =============================================================================
   TAB VIEWS - MANTENER CÓDIGO ORIGINAL
   ============================================================================= */

@Composable
private fun DeliveryPickupTab(
    analytics: UserAnalytics,
    showDelivery: Boolean,
    showPickup: Boolean
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OverviewKPI(analytics)
        DonutDeliveryPickupCard(analytics, showDelivery, showPickup)
        StatusKpiRow(analytics)
        PharmacyKpiRow(analytics)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusKpiRow(analytics: UserAnalytics) {
    SectionTitleChip("Estados de pedidos", "✅")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Timelapse,
            title = "Activos",
            value = analytics.activeOrders.toString(),
            color = Color(0xFF3498DB)
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CheckCircle,
            title = "Completados",
            value = analytics.completedOrders.toString(),
            color = Color(0xFF2ECC71)
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Cancel,
            title = "Cancelados",
            value = analytics.cancelledOrders.toString(),
            color = Color(0xFFE74C3C)
        )
    }
}

@Composable
private fun PharmacyKpiRow(analytics: UserAnalytics) {
    SectionTitleChip("Farmacias", "🏪")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.LocalPharmacy,
            title = "Preferida",
            value = analytics.mostFrequentPharmacy.ifBlank { "—" },
            color = Color(0xFF9B59B6)
        )
    }
}

@Composable
private fun OverviewKPI(analytics: UserAnalytics) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.ShoppingCart,
            title = "Total Pedidos",
            value = analytics.totalOrders.toString(),
            color = Color(0xFF6C8CF2)
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.LocalShipping,
            title = "Domicilios",
            value = "${analytics.deliveryOrders} (${analytics.deliveryPercentage.toInt()}%)",
            color = Color(0xFF47C1BF)
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Store,
            title = "Recogidas",
            value = "${analytics.pickupOrders} (${analytics.pickupPercentage.toInt()}%)",
            color = Color(0xFF4AC06B)
        )
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, color = color.darken(0.2f), fontSize = 12.sp)
            Text(value, color = color.darken(0.35f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun DonutDeliveryPickupCard(
    analytics: UserAnalytics,
    showDelivery: Boolean,
    showPickup: Boolean
) {
    val rawDelivery = if (showDelivery) analytics.deliveryPercentage.coerceIn(0f, 100f) else 0f
    val rawPickup   = if (showPickup)   analytics.pickupPercentage.coerceIn(0f, 100f)   else 0f
    val sum = (rawDelivery + rawPickup).takeIf { it > 0f } ?: 1f
    val deliveryPct = (rawDelivery / sum) * 100f
    val pickupPct   = (rawPickup   / sum) * 100f

    SectionTitleChip("Distribución", "📈")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DonutChart(
                deliveryFraction = deliveryPct / 100f,
                pickupFraction   = pickupPct   / 100f,
                deliveryColor = Color(0xFF3498DB),
                pickupColor   = Color(0xFF2ECC71)
            )
            Column {
                Text("Distribución", fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                Spacer(Modifier.height(6.dp))
                if (showDelivery) LegendItem("Domicilio", deliveryPct.toInt(), Color(0xFF3498DB))
                if (showPickup)   LegendItem("Recoger",  pickupPct.toInt(),   Color(0xFF2ECC71))
            }
        }
    }
}

@Composable
private fun DonutChart(
    deliveryFraction: Float,
    pickupFraction: Float,
    deliveryColor: Color,
    pickupColor: Color,
    strokeWidth: Float = 20f
) {
    val clampedDelivery = deliveryFraction.coerceIn(0f, 1f)
    val clampedPickup   = pickupFraction.coerceIn(0f, 1f)

    var played by remember { mutableStateOf(false) }
    val deliverySweep by animateFloatAsState(
        targetValue = if (played) 360f * clampedDelivery else 0f,
        animationSpec = tween(800),
        label = "deliverySweep"
    )
    val pickupSweep by animateFloatAsState(
        targetValue = if (played) 360f * clampedPickup else 0f,
        animationSpec = tween(800),
        label = "pickupSweep"
    )
    LaunchedEffect(Unit) { played = true }

    Canvas(modifier = Modifier.size(100.dp)) {
        val start = -90f
        drawArc(
            color = deliveryColor,
            startAngle = start,
            sweepAngle = deliverySweep,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = pickupColor,
            startAngle = start + deliverySweep,
            sweepAngle = pickupSweep,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun LegendItem(label: String, pct: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label: $pct%", color = Color(0xFF5D6B82), fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitleChip(title: String, emoji: String) {
    Row(
        Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(title, color = Color(0xFF2C3E50), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BQT2Tab(analytics: UserAnalytics) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitleChip("BQT2 – Resumen", "🧪")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TinyKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.MedicalServices,
                title = "Total solicitados",
                value = analytics.totalMedicationRequests.toString(),
                color = Color(0xFFE74C3C)
            )
            TinyKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Calculate,
                title = "Prom. por pedido",
                value = String.format("%.1f", analytics.averageMedicationsPerOrder),
                color = Color(0xFF9B59B6)
            )
            TinyKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.ShoppingCart,
                title = "Total pedidos",
                value = analytics.totalOrders.toString(),
                color = Color(0xFF6C8CF2)
            )
        }

        SectionTitleChip("Último reclamo", "📦")
        val claimed = analytics.hasEverClaimed
        val chipColor = if (claimed) Color(0xFF2ECC71) else Color(0xFFF39C12)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = chipColor.copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (claimed) "📦" else "⏳", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    if (claimed) {
                        Text("Hace ${analytics.daysSinceLastClaim} día(s)", fontWeight = FontWeight.Bold, color = chipColor.darken(0.35f))
                        analytics.lastClaimDate?.let {
                            val sdf = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("es"))
                            Text(sdf.format(it), fontSize = 12.sp, color = chipColor.darken(0.25f))
                        }
                    } else {
                        Text("Nunca has reclamado", fontWeight = FontWeight.Bold, color = chipColor.darken(0.35f))
                        Text("Aún no hay reclamos registrados", fontSize = 12.sp, color = chipColor.darken(0.25f))
                    }
                }
            }
        }

        SectionTitleChip("Finanzas", "💰")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val fmt = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
            TinyKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.AttachMoney,
                title = "Total gastado",
                value = fmt.format(analytics.totalSpent),
                color = Color(0xFF16A085)
            )
            TinyKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.TrendingUp,
                title = "Promedio",
                value = fmt.format(analytics.averageOrderValue),
                color = Color(0xFF27AE60)
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TinyKpiCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Column {
                Text(title, color = color.darken(0.2f), fontSize = 10.sp)
                Text(value, color = color.darken(0.35f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RefillsByDayTab(analytics: UserAnalytics) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitleChip("BQT4 – Análisis temporal", "📅")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            RefillBarChart(analytics.refillsByDayOfMonth)
        }

        SectionTitleChip("Días con más actividad", "🔥")
        DensityAnalysisSection(analytics.refillsByDayOfMonth)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RefillBarChart(data: List<Pair<Int, Int>>) {
    val maxRefills = data.maxOfOrNull { it.second } ?: 1

    var animationTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = data) {
        animationTrigger = true
    }

    val animatedProgress = animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(800),
        label = "barAnimation"
    ).value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .drawBehind {
                drawLine(
                    color = Color.LightGray,
                    start = Offset(40f, 0f),
                    end = Offset(40f, size.height),
                    strokeWidth = 2f
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(maxRefills.toString(), fontSize = 10.sp, color = Color.Gray)
                Text((maxRefills / 2).toString(), fontSize = 10.sp, color = Color.Gray)
                Text("0", fontSize = 10.sp, color = Color.Gray)
            }

            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Spacer(modifier = Modifier.width(4.dp))

                data.forEach { (day, count) ->
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .width(28.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction = (count.toFloat() / maxRefills) * animatedProgress)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF9B59B6),
                                                Color(0xFF8E44AD)
                                            )
                                        ),
                                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                        }
                        Text(
                            text = day.toString(),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            color = Color(0xFF2C3E50)
                        )
                    }
                }
            }
        }
        Text(
            "Día del Mes",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
    }
}

@Composable
private fun DensityAnalysisSection(data: List<Pair<Int, Int>>) {
    if (data.isEmpty()) return

    val avg = data.map { it.second }.average()
    val highActivityDays = data.filter { it.second > avg }.sortedByDescending { it.second }.take(5)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                highActivityDays.forEachIndexed { index, (day, count) ->
                    DensityDayItem(
                        rank = index + 1,
                        day = day,
                        count = count,
                        maxCount = highActivityDays.firstOrNull()?.second ?: 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DensityDayItem(rank: Int, day: Int, count: Int, maxCount: Int) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedWidth by animateFloatAsState(
        targetValue = if (animationPlayed) count.toFloat() / maxCount else 0f,
        animationSpec = tween(600, delayMillis = rank * 100),
        label = "densityAnim"
    )
    LaunchedEffect(Unit) { animationPlayed = true }

    val medalEmoji = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "🏅"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(medalEmoji, fontSize = 20.sp, modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Día $day", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF2C3E50))
                Text("$count pedidos", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7F8C8D))
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFECF0F1))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedWidth)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (rank) {
                                1 -> Color(0xFFE74C3C)
                                2 -> Color(0xFFE67E22)
                                3 -> Color(0xFFF39C12)
                                else -> Color(0xFF3498DB)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun AnalyticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6B9BD8),
            secondary = Color(0xFF3498DB),
            background = Color(0xFFF6F8FB),
            surface = Color.White
        ),
        content = content
    )
}

// Extension function para oscurecer colores
fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}