package com.mobile.mymeds.viewModels

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel para UploadPrescriptionPhotoActivity
 * Analiza texto de imágenes y las sube a Firebase.
 */
class UploadPrescriptionPhotoViewModel : ViewModel() {

    data class UiState(
        val extractedText: String? = null,
        val uploading: Boolean = false,
        val lastError: String? = null,
        val uploadedUrl: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun runOcrFromUri(activity: Activity, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(lastError = null) }
            try {
                val image = InputImage.fromFilePath(activity, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = recognizer.process(image).await()
                _ui.update { it.copy(extractedText = result.text.ifBlank { null }) }
            } catch (e: Exception) {
                _ui.update { it.copy(extractedText = null, lastError = e.message ?: "OCR failed") }
            }
        }
    }

    fun uploadImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(uploading = true, lastError = null, uploadedUrl = null) }
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val path = "prescriptions/images/prescription_$ts.jpg"
                val ref = storage.reference.child(path)
                ref.putFile(uri).await()
                val url = ref.downloadUrl.await().toString()

                val doc = hashMapOf(
                    "type" to "image",
                    "downloadUrl" to url,
                    "extractedText" to (_ui.value.extractedText ?: ""),
                    "createdAt" to Date()
                )
                firestore.collection("prescriptions").add(doc).await()
                _ui.update { it.copy(uploading = false, uploadedUrl = url) }
            } catch (e: Exception) {
                _ui.update { it.copy(uploading = false, lastError = e.message ?: "Upload failed") }
            }
        }
    }
}
