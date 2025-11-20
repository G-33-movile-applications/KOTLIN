package com.mobile.mymeds.repository

import android.util.Log
import com.mobile.mymeds.models.ClaimTimingResult
import com.mobile.mymeds.models.DeliveryMode
import com.mobile.mymeds.models.DeliveryType
import com.mobile.mymeds.models.OrderAnalytics
import com.mobile.mymeds.models.OrderStatus
import com.mobile.mymeds.models.UserAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "UserAnalyticsRepo"

/**
 * REPOSITORIO DE ANALÍTICAS - BQT2 (usa OrdersRepository)
 */
class UserAnalyticsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ordersRepo: OrdersRepository = OrdersRepository()
) {

    /**
     * Obtener todas las analíticas del usuario
     */
    suspend fun getUserAnalytics(userId: String): UserAnalytics {
        return try {
            Log.d(TAG, "Calculando analíticas para usuario: $userId")

            // 1) Traer pedidos usando OrdersRepository (fuente de verdad)
            val ordersResult = ordersRepo.getUserOrders(userId)
            val orders = ordersResult.getOrNull().orEmpty()
            Log.d(TAG, "Pedidos encontrados: ${orders.size}")

            // 2) Mapear a OrderAnalytics para cálculos
            val analyticOrders: List<OrderAnalytics> = orders.map { o ->
                OrderAnalytics(
                    orderId = o.id,
                    deliveryMode = when (o.deliveryType) {
                        DeliveryType.HOME_DELIVERY -> "delivery"
                        DeliveryType.IN_PERSON_PICKUP -> "pickup"
                    },
                    totalAmount = o.totalAmount.toDouble(),
                    // BQT2-1: podemos contar "unidades" solicitadas (sum of quantities)
                    medicationCount = o.items.sumOf { it.quantity },
                    pharmacyName = o.pharmacyName,
                    status = o.status.name.lowercase(Locale.ROOT),
                    orderDate = o.createdAt?.toDate(),
                    // Heurística: si fue pickup y el pedido quedó entregado/completado, usamos updatedAt como "fecha de reclamo"
                    claimedDate =
                        if (o.deliveryType == DeliveryType.IN_PERSON_PICKUP &&
                            (o.status == OrderStatus.DELIVERED || o.status == OrderStatus.COMPLETED)
                        ) {
                            o.updatedAt?.toDate() ?: o.createdAt?.toDate()
                        } else null
                )
            }

            // 3) Delivery vs pickup
            val deliveryStats = calculateDeliveryStats(analyticOrders)

            // 4) BQT2-1: total y promedio de medicamentos pedidos
            val medicationStats = calculateMedicationRequests(analyticOrders)

            // 5) BQT2-2: tiempo desde último reclamo
            val claimTiming = calculateLastClaimTiming(analyticOrders)

            // 6) Extra
            val additionalStats = calculateAdditionalStats(analyticOrders)

            // BQT4: día del mes que se hizo más refill
            val refillsByDay = calculateRefillsByDay(analyticOrders)

            UserAnalytics(
                // pedidos
                totalOrders = analyticOrders.size,
                deliveryOrders = deliveryStats.deliveryCount,
                pickupOrders = deliveryStats.pickupCount,
                deliveryPercentage = deliveryStats.deliveryPercentage,
                pickupPercentage = deliveryStats.pickupPercentage,
                preferredMode = deliveryStats.preferredMode,

                // BQT2-1
                totalMedicationRequests = medicationStats.totalRequests,
                averageMedicationsPerOrder = medicationStats.averagePerOrder,

                // BQT2-2
                lastClaimDate = claimTiming.lastClaimDate,
                daysSinceLastClaim = claimTiming.daysSinceLastClaim,
                hasEverClaimed = claimTiming.hasEverClaimed,

                // extra
                totalSpent = additionalStats.totalSpent,
                averageOrderValue = additionalStats.averageOrderValue,
                mostFrequentPharmacy = additionalStats.mostFrequentPharmacy,
                activeOrders = additionalStats.activeOrders,
                completedOrders = additionalStats.completedOrders,
                cancelledOrders = additionalStats.cancelledOrders,

                // BQT4
                refillsByDayOfMonth = refillsByDay
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando analíticas", e)
            throw e
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Cálculos
    // ───────────────────────────────────────────────────────────────────────

    private fun calculateDeliveryStats(orders: List<OrderAnalytics>): DeliveryStatsResult {
        if (orders.isEmpty()) {
            return DeliveryStatsResult(0, 0, 0f, 0f, DeliveryMode.UNKNOWN)
        }

        val deliveryCount = orders.count {
            it.deliveryMode.equals("delivery", ignoreCase = true) ||
                    it.deliveryMode.equals("domicilio", ignoreCase = true)
        }
        val pickupCount = orders.count {
            it.deliveryMode.equals("pickup", ignoreCase = true) ||
                    it.deliveryMode.equals("recoger", ignoreCase = true)
        }

        val total = orders.size.toFloat()
        val deliveryPercentage = if (total > 0) (deliveryCount / total) * 100f else 0f
        val pickupPercentage = if (total > 0) (pickupCount / total) * 100f else 0f

        val preferredMode = when {
            deliveryCount > pickupCount -> DeliveryMode.DELIVERY
            pickupCount > deliveryCount -> DeliveryMode.PICKUP
            else -> DeliveryMode.UNKNOWN
        }

        return DeliveryStatsResult(deliveryCount, pickupCount, deliveryPercentage, pickupPercentage, preferredMode)
    }

    private fun calculateMedicationRequests(orders: List<OrderAnalytics>): MedicationStatsResult {
        val totalRequests = orders.sumOf { it.medicationCount }
        val averagePerOrder = if (orders.isNotEmpty()) totalRequests.toFloat() / orders.size else 0f
        return MedicationStatsResult(totalRequests, averagePerOrder)
    }

    private fun calculateLastClaimTiming(orders: List<OrderAnalytics>): ClaimTimingResult {
        val claimedOrders = orders.filter { it.claimedDate != null }
        if (claimedOrders.isEmpty()) {
            return ClaimTimingResult(
                lastClaimDate = null,
                daysSinceLastClaim = -1,
                hasEverClaimed = false,
                timingDescription = "Nunca has reclamado medicamentos"
            )
        }

        val lastClaimDate: Date = claimedOrders.maxByOrNull { it.claimedDate!! }!!.claimedDate!!
        val now = Date()
        val daysSince = TimeUnit.MILLISECONDS.toDays(now.time - lastClaimDate.time).toInt()

        val description = when {
            daysSince == 0 -> "Reclamaste hoy"
            daysSince == 1 -> "Reclamaste hace 1 día"
            daysSince < 7 -> "Reclamaste hace $daysSince días"
            daysSince < 30 -> "Reclamaste hace ${daysSince / 7} semanas"
            daysSince < 365 -> "Reclamaste hace ${daysSince / 30} meses"
            else -> "Reclamaste hace más de 1 año"
        }

        return ClaimTimingResult(lastClaimDate, daysSince, true, description)
    }

    private fun calculateAdditionalStats(orders: List<OrderAnalytics>): AdditionalStatsResult {
        val totalSpent = orders.sumOf { it.totalAmount }
        val averageOrderValue = if (orders.isNotEmpty()) totalSpent / orders.size else 0.0

        val mostFrequentPharmacy = orders
            .filter { it.pharmacyName.isNotBlank() }
            .groupingBy { it.pharmacyName }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: ""

        val activeOrders = orders.count {
            it.status.equals("active", true) ||
                    it.status.equals("en_proceso", true) ||
                    it.status.equals("pendiente", true) ||
                    it.status.equals("pending", true)
        }
        val completedOrders = orders.count {
            it.status.equals("completed", true) ||
                    it.status.equals("completado", true) ||
                    it.status.equals("entregado", true) ||
                    it.status.equals("delivered", true)
        }
        val cancelledOrders = orders.count {
            it.status.equals("cancelled", true) ||
                    it.status.equals("cancelado", true)
        }

        return AdditionalStatsResult(
            totalSpent = totalSpent,
            averageOrderValue = averageOrderValue,
            mostFrequentPharmacy = mostFrequentPharmacy,
            activeOrders = activeOrders,
            completedOrders = completedOrders,
            cancelledOrders = cancelledOrders
        )
    }


    // ───────────────────────────────────────────────────────────────────────
    // BQT4: Qué días del mes se hicieron más refill
    // ───────────────────────────────────────────────────────────────────────
    private fun calculateRefillsByDay(orders: List<OrderAnalytics>): List<Pair<Int, Int>> {
        return orders.mapNotNull { it.orderDate }
            .groupBy { date ->
                val calendar = java.util.Calendar.getInstance()
                calendar.time = date
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            }
            .map { (day, dates) -> Pair(day, dates.size) }
            .sortedBy { it.first }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Tipos internos
    // ───────────────────────────────────────────────────────────────────────

    private data class DeliveryStatsResult(
        val deliveryCount: Int,
        val pickupCount: Int,
        val deliveryPercentage: Float,
        val pickupPercentage: Float,
        val preferredMode: DeliveryMode
    )

    private data class MedicationStatsResult(
        val totalRequests: Int,
        val averagePerOrder: Float
    )

    private data class AdditionalStatsResult(
        val totalSpent: Double,
        val averageOrderValue: Double,
        val mostFrequentPharmacy: String,
        val activeOrders: Int,
        val completedOrders: Int,
        val cancelledOrders: Int
    )
}
