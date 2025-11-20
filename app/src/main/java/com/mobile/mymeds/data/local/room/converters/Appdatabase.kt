package com.mobile.mymeds.data.local.room.converters

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mobile.mymeds.data.local.room.dao.MedicationDao
import com.mobile.mymeds.data.local.room.dao.OrderDao
import com.mobile.mymeds.data.local.room.dao.OrderItemDao
import com.mobile.mymeds.data.local.room.entitites.MedicationEntity
import com.mobile.mymeds.data.local.room.entities.OrderEntity
import com.mobile.mymeds.data.local.room.entitites.OrderItemEntity


/**
 * Base de datos Room principal de la aplicación
 *
 * Incluye 3 tablas:
 * - medications: Medicamentos del usuario
 * - orders: Pedidos realizados
 * - order_items: Ítems dentro de cada pedido
 */
@Database(
    entities = [
        MedicationEntity::class,
        OrderEntity::class, // <-- FIX: Removed the extra colon
        OrderItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymeds_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
