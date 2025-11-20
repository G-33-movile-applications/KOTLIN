package com.mobile.mymeds.viewModels

import android.nfc.NfcAdapter
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel para la pantalla principal "UploadPrescriptionActivity"
 * Se encarga de verificar el estado del NFC y exponerlo al UI.
 */
class UploadPrescriptionViewModel : ViewModel() {

    data class UiState(
        val nfcSupported: Boolean = false,
        val nfcEnabled: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    /** Inicializa el estado del NFC */
    fun initNfcState(adapter: NfcAdapter?) {
        _ui.update {
            it.copy(
                nfcSupported = adapter != null,
                nfcEnabled = adapter?.isEnabled == true
            )
        }
    }

    /** Refresca el estado (llamar en onResume del Activity) */
    fun refreshNfcState(adapter: NfcAdapter?) {
        _ui.update {
            it.copy(nfcEnabled = adapter?.isEnabled == true)
        }
    }
}
