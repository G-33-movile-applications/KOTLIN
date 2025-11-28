package com.mobile.mymeds.initializers

import android.content.Context
import androidx.startup.Initializer
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.mymeds.data.local.room.AppDatabase
import com.mobile.mymeds.repository.GlobalMedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 *  Este Initializer se encarga de pre-calentar la caché de medicamentos
 *  cuando la aplicación se inicia
 */
class CacheInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val repository = GlobalMedicationRepository(
                FirebaseFirestore.getInstance(),
                db.globalMedicationDao(),
                context
            )
            repository.refreshMedications()
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
