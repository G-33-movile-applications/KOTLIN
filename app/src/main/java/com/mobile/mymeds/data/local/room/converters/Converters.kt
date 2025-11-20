package com.mobile.mymeds.data.local.room.converters

import androidx.room.TypeConverter
import com.mobile.mymeds.models.DeliveryType
import com.mobile.mymeds.models.OrderStatus
import com.google.gson.Gson // <-- 1. ADD THIS IMPORT
import com.google.gson.reflect.TypeToken // <-- 2. ADD THIS IMPORT


/**
 * TypeConverter para Room
 * Convierte enums y otros tipos complejos a/desde base de datos
 */
class Converters {
    // We can reuse a single Gson instance
    private val gson = Gson()

    // --- Converters for Enums ---
    @TypeConverter
    fun fromDeliveryType(value: DeliveryType): String = value.name

    @TypeConverter
    fun toDeliveryType(value: String): DeliveryType = DeliveryType.valueOf(value)

    @TypeConverter
    fun fromOrderStatus(value: OrderStatus): String = value.name

    @TypeConverter
    fun toOrderStatus(value: String): OrderStatus = OrderStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun toStringList(jsonString: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(jsonString, listType)
    }
}
