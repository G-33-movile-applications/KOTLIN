package com.mobile.mymeds.views

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.mymeds.ui.theme.MyMedsTheme
import com.mobile.mymeds.viewModels.MainViewModel
import com.mobile.mymeds.views.components.DrivingModeOverlay
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Detecta si se está conduciendo
        mainViewModel.startDrivingDetection(applicationContext)

        setContent {
            MyMedsTheme {
                val isDriving by mainViewModel.isDriving.collectAsState()

                Scaffold(
                    floatingActionButton = {
                        // Botón para simular conducción (solo debug)
                        FloatingActionButton(
                            onClick = { mainViewModel.toggleDrivingModeForDebug() },
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Toggle Driving Mode"
                            )
                        }
                    },
                    floatingActionButtonPosition = FabPosition.End
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        HomeScreen(
                            onMapClick = { navigateToMap() },

                            // Bloqueadas si va conduciendo
                            onUploadPrescriptionClick = { if (!isDriving) navigateToUploadPrescription() },
                            onProfileClick = { if (!isDriving) navigateToProfile() },
                            onOrdersClick = { if (!isDriving) navigateToOrders() },
                            onPrescriptionsClick = { if (!isDriving) navigateToPrescriptions() },
                            onAnalyticsClick = { if (!isDriving) navigateToAnalytics() },

                            // Siempre disponibles
                            onNotificationsClick = { showNotifications() },
                            onLogoutClick = { logout() }
                        )

                        if (isDriving) {
                            DrivingModeOverlay(
                                message = "Por tu seguridad, algunas funciones están desactivadas mientras conduces."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun navigateToMap() {
        startActivity(Intent(this, MapActivity::class.java))
    }

    private fun navigateToUploadPrescription() {
        startActivity(Intent(this, UploadPrescriptionActivity::class.java))
    }

    private fun navigateToProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    private fun navigateToOrders() {
        startActivity(Intent(this, OrdersManagementActivity::class.java))
    }

    private fun navigateToPrescriptions() {
        startActivity(Intent(this, PrescriptionsActivity::class.java))
    }

    private fun navigateToAnalytics() {
        startActivity(Intent(this, AnalyticsActivity::class.java))
    }

    private fun showNotifications() {
        // TODO: Implementar lógica de notificaciones
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMapClick: () -> Unit = {},
    onUploadPrescriptionClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onOrdersClick: () -> Unit = {},
    onPrescriptionsClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {} // NUEVO: icono de analíticas en la barra
) {
    // Tabs: "Tus pedidos" / "Tus prescripciones"
    val tabs = listOf("Tus pedidos", "Tus prescripciones")
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HOME",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    // Icono de Analíticas (arriba a la derecha)
                    IconButton(onClick = onAnalyticsClick) {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = "Analíticas",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onNotificationsClick) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notificaciones",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Cerrar sesión",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
        ) {
            // ===== Selector justo debajo del header =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFCBDEF3)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gestionar",
                        fontSize = 12.sp,
                        color = Color(0xFF5A7A9B),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFFB3CEE8),
                        contentColor = Color.Black,
                        indicator = { /* sin indicador para estilo plano */ }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        color = Color.Black,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                icon = {
                                    val icon: ImageVector =
                                        if (index == 0) Icons.Filled.ShoppingCart else Icons.Filled.Description
                                    Icon(icon, contentDescription = null, tint = Color.Black)
                                },
                                selectedContentColor = Color.Black,
                                unselectedContentColor = Color.Black
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // CTA principal dependiente de la pestaña
                    Button(
                        onClick = { if (selectedTab == 0) onOrdersClick() else onPrescriptionsClick() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.ShoppingCart else Icons.Filled.Description,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (selectedTab == 0) "Ver pedidos" else "Ver prescripciones",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ===== Resto de funcionalidades =====
            Spacer(modifier = Modifier.height(4.dp))
            FunctionalityCard(
                title = "Ver mapa de farmacias",
                description = "Encuentra sucursales EPS cercanas, horarios y stock estimado.",
                icon = Icons.Filled.Place,
                buttonText = "Abrir mapa",
                onClick = onMapClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            FunctionalityCard(
                title = "Sube tu prescripción",
                description = "Escanea o carga la fórmula para validar y agilizar tu pedido.",
                icon = Icons.Filled.Add,
                buttonText = "Subir",
                onClick = onUploadPrescriptionClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            FunctionalityCard(
                title = "Ver tu perfil",
                description = "Datos del usuario, preferencias y accesibilidad.",
                icon = Icons.Filled.Person,
                buttonText = "Ver perfil",
                onClick = onProfileClick,
                isAccount = true
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FunctionalityCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonText: String,
    onClick: () -> Unit,
    isAccount: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAccount) Color(0xFFB3CEE8) else Color(0xFFCBDEF3)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isAccount) "CUENTA" else "FUNCIONALIDAD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5A7A9B),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        fontSize = 14.sp,
                        color = Color(0xFF444444),
                        lineHeight = 20.sp
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(start = 8.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = buttonText,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    MyMedsTheme {
        HomeScreen()
    }
}
