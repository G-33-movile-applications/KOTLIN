package com.mobile.mymeds.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mobile.mymeds.data.local.room.converters.Converters
import com.mobile.mymeds.data.local.room.dao.MedicationStockDao
import com.mobile.mymeds.data.local.room.entitites.MedicationStockEntity
import com.mobile.mymeds.data.local.room.dao.GlobalMedicationDao
import com.mobile.mymeds.data.local.room.dao.MedicationDao
import com.mobile.mymeds.data.local.room.dao.NfcPrescriptionDao
import com.mobile.mymeds.data.local.room.dao.UserInteractionDao
import com.mobile.mymeds.data.local.room.entitites.NfcPrescriptionEntity
import com.mobile.mymeds.data.local.room.entitites.MedicationEntity
import com.mobile.mymeds.models.GlobalMedication
import com.mobile.mymeds.models.UserInteraction

/**
 * Clase main para la app, es Singleton para evitar tener varias instancias
 * al mismo tiempo
 */
@Database(
    entities = [
        GlobalMedication::class,
        MedicationEntity::class,
        NfcPrescriptionEntity::class,
        UserInteraction::class,
        MedicationStockEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun globalMedicationDao(): GlobalMedicationDao
    abstract fun medicationDao(): MedicationDao
    abstract fun nfcPrescriptionDao(): NfcPrescriptionDao
    abstract fun userInteractionDao(): UserInteractionDao

    abstract fun medicationStockDao(): MedicationStockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE
                if (instance != null) {
                    return instance
                }

                val newInstance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "mymeds-application"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = newInstance
                newInstance
            }
        }
    }
}
