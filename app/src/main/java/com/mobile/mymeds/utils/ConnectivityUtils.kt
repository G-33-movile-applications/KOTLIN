package com.mobile.mymeds.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "ConnectivityUtils"

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CONNECTIVITY UTILS
 * ═══════════════════════════════════════════════════════════════════════════
 */
object ConnectivityUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    fun getNetworkType(context: Context): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.NONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                else -> NetworkType.OTHER
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return NetworkType.NONE

            @Suppress("DEPRECATION")
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR
                ConnectivityManager.TYPE_ETHERNET -> NetworkType.ETHERNET
                else -> NetworkType.OTHER
            }
        }
    }

    fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            private val networks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                networks.add(network)
                trySend(true)
            }

            override fun onLost(network: Network) {
                networks.remove(network)
                trySend(networks.isNotEmpty())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet && isValidated) {
                    trySend(true)
                } else {
                    networks.remove(network)
                    trySend(networks.isNotEmpty())
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        trySend(isNetworkAvailable(context))
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()


    fun isMeteredConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // Asumir metered si no se puede determinar

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return true
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.isActiveNetworkMetered
        }
    }

    fun getNetworkInfo(context: Context): NetworkInfo {
        val isConnected = isNetworkAvailable(context)
        val networkType = getNetworkType(context)
        val isMetered = isMeteredConnection(context)

        return NetworkInfo(
            isConnected = isConnected,
            networkType = networkType,
            isMetered = isMetered
        )
    }
}

/**
 * Enum para tipos de red. Esta es la ÚNICA definición necesaria.
 */
enum class NetworkType {
    NONE,       // Sin conexión
    WIFI,       // WiFi
    CELLULAR,   // Datos móviles
    ETHERNET,   // Cable ethernet
    OTHER       // Otro tipo (Bluetooth, VPN, etc)
}

/**
 * Data class con información completa de la red. Esta es la ÚNICA definición necesaria.
 */
data class NetworkInfo(
    val isConnected: Boolean,
    val networkType: NetworkType,
    val isMetered: Boolean,
) {
    fun shouldSyncData(): Boolean {
        return isConnected && (networkType == NetworkType.WIFI || networkType == NetworkType.ETHERNET)
    }

    fun getDescription(): String {
        return when {
            !isConnected -> "Sin conexión"
            networkType == NetworkType.WIFI -> "WiFi"
            networkType == NetworkType.CELLULAR -> "Datos móviles${if (isMetered) " (Con límite)" else ""}"
            networkType == NetworkType.ETHERNET -> "Ethernet"
            else -> "Conectado"
        }
    }

    fun getEmoji(): String {
        return when {
            !isConnected -> "📡"
            networkType == NetworkType.WIFI -> "📶"
            networkType == NetworkType.CELLULAR -> "📱"
            networkType == NetworkType.ETHERNET -> "🔌"
            else -> "🌐"
        }
    }
}

/**
 * Extension functions para Context
 */
fun Context.isNetworkAvailable(): Boolean = ConnectivityUtils.isNetworkAvailable(this)

fun Context.getNetworkType(): NetworkType = ConnectivityUtils.getNetworkType(this)

fun Context.isMeteredConnection(): Boolean = ConnectivityUtils.isMeteredConnection(this)

fun Context.getNetworkInfo(): NetworkInfo = ConnectivityUtils.getNetworkInfo(this)

fun Context.observeNetworkConnectivity(): Flow<Boolean> = ConnectivityUtils.observeNetworkConnectivity(this)
