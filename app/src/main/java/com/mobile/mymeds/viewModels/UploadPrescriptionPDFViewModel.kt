package com.mobile.mymeds.viewModels

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel para UploadPrescriptionPDFActivity
 * Extrae texto de PDFs con OCR y los sube a Firebase.
 */
class UploadPrescriptionPDFViewModel : ViewModel() {

    data class UiState(
        val extractedText: String? = null,
        val uploading: Boolean = false,
        val pageCount: Int = 0,
        val lastError: String? = null,
        val uploadedUrl: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun runOcrFromPdf(activity: Activity, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(lastError = null, extractedText = null) }
            try {
                val temp = copyToTempFile(activity, uri, "rx_${System.currentTimeMillis()}.pdf")
                val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                val sb = StringBuilder()
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                        if (result.text.isNotBlank()) {
                            sb.appendLine("— Page ${i + 1} —")
                            sb.appendLine(result.text)
                            sb.appendLine()
                        }
                    }
                }
                renderer.close()
                pfd.close()
                _ui.update { it.copy(extractedText = sb.toString(), pageCount = renderer.pageCount) }
            } catch (e: Exception) {
                _ui.update { it.copy(lastError = e.message ?: "OCR failed") }
            }
        }
    }

    fun uploadPdf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(uploading = true, lastError = null, uploadedUrl = null) }
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val path = "prescriptions/pdfs/prescription_$ts.pdf"
                val ref = storage.reference.child(path)
                ref.putFile(uri).await()
                val url = ref.downloadUrl.await().toString()

                val doc = hashMapOf(
                    "type" to "pdf",
                    "pageCount" to _ui.value.pageCount,
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

    private fun copyToTempFile(activity: Activity, uri: Uri, name: String): File {
        val out = File(activity.cacheDir, name)
        activity.contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(out).use { output -> input!!.copyTo(output) }
        }
        return out
    }
}
