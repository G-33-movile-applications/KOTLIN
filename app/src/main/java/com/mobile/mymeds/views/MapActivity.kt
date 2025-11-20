package com.mobile.mymeds.views

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import com.mobile.mymeds.models.PhysicalPoint
import com.mobile.mymeds.viewModels.MapViewModel
import com.mobile.mymeds.viewModels.LoadingState
import com.mobile.mymeds.ui.theme.MyMedsTheme
import com.mobile.mymeds.utils.LocationUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

class MapActivity : ComponentActivity() {

    private val mapViewModel: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyMedsTheme {
                MapScreen(
                    viewModel = mapViewModel,
                    onBackClick = { finish() },
                    onViewInventory = { pharmacyName, pharmacyId ->
                        val intent = Intent(this, PharmacyInventoryActivity::class.java).apply {
                            putExtra("PHARMACY_NAME", pharmacyName)
                            putExtra("PHARMACY_ID", pharmacyId)
                        }
                        startActivity(intent)
                    },
                    onMakeOrder = { pharmacyName, pharmacyId ->
                        val intent = Intent(this, OrdersManagementActivity::class.java).apply {
                            putExtra("PHARMACY_NAME", pharmacyName)
                            putExtra("PHARMACY_ID", pharmacyId)
                            putExtra("FROM_MAP", true)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onBackClick: () -> Unit,
    onViewInventory: (String, String) -> Unit,
    onMakeOrder: (String, String) -> Unit
) {
    val context = LocalContext.current
    val visiblePharmacies by viewModel.visiblePharmacies.observeAsState(initial = emptyList())
    val nearestPharmacies by viewModel.nearestPharmacies.observeAsState(initial = emptyList())
    val userLocation by viewModel.userLocation.observeAsState()
    val loadingState by viewModel.loadingState.observeAsState(initial = LoadingState.LOADING)

    var selectedPoint by remember { mutableStateOf<PhysicalPoint?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val defaultLocation = LatLng(4.710989, -74.072092) // Centro de Bogotá
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 11f)
    }

    val coroutineScope = rememberCoroutineScope()

    // Actualizar cámara según el caso
    LaunchedEffect(userLocation, nearestPharmacies, loadingState, hasLocationPermission, visiblePharmacies) {
        when {
            // Caso 1: Usuario con permisos y farmacias cercanas calculadas
            userLocation != null && nearestPharmacies.isNotEmpty() -> {
                coroutineScope.launch {
                    val bounds = calculateBounds(userLocation!!, nearestPharmacies)
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(bounds, 150)
                    )
                }
            }
            // Caso 2: Usuario con permisos pero aún no hay farmacias cercanas
            userLocation != null -> {
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f)
                    )
                }
            }
            // Caso 3: Sin permisos, mostrar todas las farmacias
            loadingState == LoadingState.SUCCESS && !hasLocationPermission && visiblePharmacies.isNotEmpty() -> {
                coroutineScope.launch {
                    val bounds = calculateBoundsForAllPharmacies(visiblePharmacies)
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(bounds, 100)
                    )
                }
            }
        }
    }

    // Launcher para permisos de ubicación
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        hasLocationPermission = granted

        if (granted) {
            getCurrentLocation(context, viewModel)
        } else {
            Log.e("MapScreen", "Location permission denied")
        }
    }

    // Verificar permisos al cargar
    LaunchedEffect(Unit) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = hasFineLocation || hasCoarseLocation

        if (hasLocationPermission) {
            getCurrentLocation(context, viewModel)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MAP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6B9BD8))
            )
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            when (loadingState) {
                LoadingState.LOADING -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                LoadingState.ERROR -> {
                    Text(
                        text = "Error loading points",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Red
                    )
                }
                LoadingState.SUCCESS -> {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                        uiSettings = MapUiSettings(
                            myLocationButtonEnabled = true,
                            zoomControlsEnabled = true
                        ),
                        onMapClick = {
                            // Cerrar el panel de información al tocar el mapa
                            selectedPoint = null
                        }
                    ) {
                        // Marcadores de farmacias (solo las visibles)
                        visiblePharmacies.forEach { point ->
                            val isNearby = nearestPharmacies.any { it.first == point }
                            val color = if (isNearby) {
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE
                            } else {
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED
                            }

                            Marker(
                                state = MarkerState(position = LatLng(point.location.latitude, point.location.longitude)),
                                title = point.name,
                                snippet = point.address,
                                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(color),
                                onClick = {
                                    selectedPoint = point
                                    true
                                }
                            )
                        }
                    }

                    // Panel inferior con información
                    selectedPoint?.let { point ->
                        val distanceText = userLocation?.let { location ->
                            val distance = LocationUtils.calculateDistance(
                                location.latitude,
                                location.longitude,
                                point.location.latitude,
                                point.location.longitude
                            )
                            " • ${LocationUtils.formatDistance(distance)}"
                        } ?: ""

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        point.name,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (distanceText.isNotEmpty()) {
                                        Text(
                                            distanceText,
                                            fontSize = 14.sp,
                                            color = Color(0xFF6B9BD8),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(point.address, fontSize = 14.sp, color = Color.Gray)
                                Text("Localidad: ${point.locality}", fontSize = 14.sp, color = Color.DarkGray)
                                Text("Horario: ${point.openingHours}", fontSize = 14.sp, color = Color.DarkGray)
                                if (point.phone.isNotEmpty()) {
                                    Text("Teléfono: ${point.phone}", fontSize = 14.sp, color = Color.DarkGray)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = { onMakeOrder(point.name, point.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B9BD8))
                                    ) {
                                        Text("Hacer Pedido", color = Color.White)
                                    }
                                    Button(
                                        onClick = { onViewInventory(point.name, point.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B9BD8))
                                    ) {
                                        Text("Ver inventario", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calcula los límites que incluyen la ubicación del usuario y las 5 farmacias más cercanas
 */
private fun calculateBounds(
    userLocation: LatLng,
    nearestPharmacies: List<Pair<PhysicalPoint, Double>>
): LatLngBounds {
    val builder = LatLngBounds.Builder()

    // Agregar ubicación del usuario
    builder.include(userLocation)

    // Agregar las 5 farmacias más cercanas
    nearestPharmacies.forEach { (pharmacy, _) ->
        builder.include(LatLng(pharmacy.location.latitude, pharmacy.location.longitude))
    }

    return builder.build()
}

/**
 * Calcula los límites para mostrar todas las farmacias visibles
 */
private fun calculateBoundsForAllPharmacies(pharmacies: List<PhysicalPoint>): LatLngBounds {
    val builder = LatLngBounds.Builder()

    pharmacies.forEach { pharmacy ->
        builder.include(LatLng(pharmacy.location.latitude, pharmacy.location.longitude))
    }

    return builder.build()
}

@Suppress("MissingPermission")
private fun getCurrentLocation(context: android.content.Context, viewModel: MapViewModel) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val cancellationTokenSource = CancellationTokenSource()

    fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationTokenSource.token
    ).addOnSuccessListener { location ->
        if (location != null) {
            val userLatLng = LatLng(location.latitude, location.longitude)
            viewModel.updateUserLocation(userLatLng)
            Log.d("MapScreen", "Location obtained: ${location.latitude}, ${location.longitude}")
        } else {
            Log.e("MapScreen", "Location is null")
        }
    }.addOnFailureListener { exception ->
        Log.e("MapScreen", "Error getting location: ${exception.message}")
    }
}