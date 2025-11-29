package com.mobile.mymeds.initializers

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.mymeds.MyMedsApplication
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
        Log.d("MY_MEDS_DEBUG", "CacheInitializer: create() INICIADO.")
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("MY_MEDS_DEBUG", "CacheInitializer: Dentro de la corutina, a punto de llamar a refreshMedications.")
            val app = context.applicationContext as MyMedsApplication
            app.globalMedicationRepository.refreshMedications()
            Log.d("MY_MEDS_DEBUG", "CacheInitializer: La llamada a refreshMedications() ha terminado.")
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
