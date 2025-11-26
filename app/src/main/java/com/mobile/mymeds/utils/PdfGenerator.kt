package com.mobile.mymeds.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import com.mobile.mymeds.models.Invoice
import com.mobile.mymeds.models.OrderSnapshot
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * PDF GENERATOR
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Generador de PDFs profesionales para facturas.
 * Crea documentos con formato estándar de factura colombiana.
 */

class PdfGenerator(private val context: Context) {

    companion object {
        private const val TAG = "PdfGenerator"

        // Dimensiones de página A4 en puntos (72 DPI)
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842

        // Márgenes
        private const val MARGIN_LEFT = 40
        private const val MARGIN_RIGHT = 40
        private const val MARGIN_TOP = 40
        private const val MARGIN_BOTTOM = 40

        // Colores
        private const val COLOR_PRIMARY = 0xFF6B9BD8.toInt()
        private const val COLOR_SECONDARY = 0xFF9EC6F3.toInt()
        private const val COLOR_TEXT = 0xFF000000.toInt()
        private const val COLOR_GRAY = 0xFF666666.toInt()
        private const val COLOR_LIGHT_GRAY = 0xFFCCCCCC.toInt()
        private const val COLOR_BG_HEADER = 0xFFE3F2FD.toInt()

        // Tamaños de fuente
        private const val FONT_SIZE_TITLE = 24f
        private const val FONT_SIZE_SUBTITLE = 18f
        private const val FONT_SIZE_HEADING = 14f
        private const val FONT_SIZE_NORMAL = 12f
        private const val FONT_SIZE_SMALL = 10f
    }

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    /**
     * Genera un PDF de factura completo
     * @param invoice Factura a generar
     * @param orderSnapshot Snapshot del pedido con toda la información
     * @return File del PDF generado o null si hay error
     */
    suspend fun generateInvoicePdf(
        invoice: Invoice,
        orderSnapshot: OrderSnapshot
    ): File? {
        return try {
            Log.d(TAG, "📄 Generando PDF para factura ${invoice.invoiceNumber}")

            // Crear documento PDF
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Dibujar contenido
            var yPosition = MARGIN_TOP.toFloat()

            yPosition = drawHeader(canvas, invoice, yPosition)
            yPosition = drawCompanyInfo(canvas, yPosition)
            yPosition = drawInvoiceInfo(canvas, invoice, orderSnapshot, yPosition)
            yPosition = drawClientInfo(canvas, orderSnapshot, yPosition)
            yPosition = drawPharmacyInfo(canvas, orderSnapshot, yPosition)
            yPosition = drawItemsTable(canvas, orderSnapshot, yPosition)
            yPosition = drawTotals(canvas, orderSnapshot, yPosition)
            yPosition = drawNotes(canvas, orderSnapshot, yPosition)
            drawFooter(canvas, invoice)

            pdfDocument.finishPage(page)

            // Guardar PDF
            val file = createPdfFile(invoice.invoiceNumber)
            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()

            Log.d(TAG, "✅ PDF generado exitosamente: ${file.absolutePath}")
            file

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generando PDF", e)
            null
        }
    }

    /**
     * Dibuja el encabezado con logo y título
     */
    private fun drawHeader(canvas: Canvas, invoice: Invoice, startY: Float): Float {
        var y = startY

        // Rectángulo de fondo para el header
        val headerPaint = Paint().apply {
            color = COLOR_BG_HEADER
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 120f, headerPaint)

        // Título "FACTURA"
        val titlePaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = FONT_SIZE_TITLE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("FACTURA", MARGIN_LEFT.toFloat(), y + 30, titlePaint)

        // Número de factura
        val invoiceNumPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = FONT_SIZE_HEADING
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val invoiceNumText = "N° ${invoice.invoiceNumber}"
        val invoiceNumWidth = invoiceNumPaint.measureText(invoiceNumText)
        canvas.drawText(
            invoiceNumText,
            PAGE_WIDTH - MARGIN_RIGHT - invoiceNumWidth,
            y + 30,
            invoiceNumPaint
        )

        // Fecha de emisión
        val datePaint = Paint().apply {
            color = COLOR_GRAY
            textSize = FONT_SIZE_SMALL
            isAntiAlias = true
        }
        val dateText = "Fecha: ${dateOnlyFormat.format(Date(invoice.generatedAt))}"
        val dateWidth = datePaint.measureText(dateText)
        canvas.drawText(
            dateText,
            PAGE_WIDTH - MARGIN_RIGHT - dateWidth,
            y + 50,
            datePaint
        )

        return y + 140
    }

    /**
     * Dibuja información de la empresa
     */
    private fun drawCompanyInfo(canvas: Canvas, startY: Float): Float {
        var y = startY

        val companyPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = FONT_SIZE_HEADING
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("MyMeds S.A.S.", MARGIN_LEFT.toFloat(), y, companyPaint)

        y += 20
        val infoPaint = Paint().apply {
            color = COLOR_GRAY
            textSize = FONT_SIZE_SMALL
            isAntiAlias = true
        }

        canvas.drawText("NIT: 900.123.456-7", MARGIN_LEFT.toFloat(), y, infoPaint)
        y += 15
        canvas.drawText("Dirección: Calle 123 #45-67, Bogotá D.C.", MARGIN_LEFT.toFloat(), y, infoPaint)
        y += 15
        canvas.drawText("Teléfono: +57 (1) 234-5678", MARGIN_LEFT.toFloat(), y, infoPaint)
        y += 15
        canvas.drawText("Email: facturacion@mymeds.com", MARGIN_LEFT.toFloat(), y, infoPaint)

        return y + 25
    }

    /**
     * Dibuja información de la factura (pedido asociado)
     */
    private fun drawInvoiceInfo(
        canvas: Canvas,
        invoice: Invoice,
        order: OrderSnapshot,
        startY: Float
    ): Float {
        var y = startY

        // Línea divisoria
        drawLine(canvas, y)
        y += 15

        val labelPaint = Paint().apply {
            color = COLOR_GRAY
            textSize = FONT_SIZE_SMALL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val valuePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = FONT_SIZE_SMALL
            isAntiAlias = true
        }

        // Pedido N°
        canvas.drawText("PEDIDO N°:", MARGIN_LEFT.toFloat(), y, labelPaint)
        canvas.drawText(order.orderNumber, MARGIN_LEFT + 120f, y, valuePaint)

        // Fecha del pedido
        val dateWidth = labelPaint.measureText("FECHA PEDIDO:")
        canvas.drawText("FECHA PEDIDO:", PAGE_WIDTH - MARGIN_RIGHT - 200f, y, labelPaint)
        canvas.drawText(
            order.orderDate,
            PAGE_WIDTH - MARGIN_RIGHT - 80f,
            y,
            valuePaint
        )

        y += 20

        // Estado
        canvas.drawText("ESTADO:", MARGIN_LEFT.toFloat(), y, labelPaint)
        canvas.drawText(order.status, MARGIN_LEFT + 120f, y, valuePaint)

        // Tipo de entrega
        canvas.drawText("ENTREGA:", PAGE_WIDTH - MARGIN_RIGHT - 200f, y, labelPaint)
        val deliveryText = if (order.deliveryType == "HOME_DELIVERY") "Domicilio" else "Recoger en farmacia"
        canvas.drawText(deliveryText, PAGE_WIDTH - MARGIN_RIGHT - 120f, y, valuePaint)

        return y + 25
    }

    /**
     * Dibuja información del cliente
     */
    private fun drawClientInfo(canvas: Canvas, order: OrderSnapshot, startY: Float): Float {
        var y = startY

        drawLine(canvas, y)
        y += 15

        val sectionPaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = FONT_SIZE_HEADING
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DATOS DEL CLIENTE", MARGIN_LEFT.toFloat(), y, sectionPaint)

        y += 20
        val labelPaint = Paint().apply {
            color = COLOR_GRAY
            textSize = FONT_SIZE_SMALL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val valuePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = FONT_SIZE_SMALL
            isAntiAlias = true
        }

        canvas.drawText("Nombre:", MARGIN_LEFT.toFloat(), y, labelPaint)
        canvas.drawText(order.userName.ifEmpty { "Cliente MyMeds" }, MARGIN_LEFT + 80f, y, valuePaint)

        y += 15
        canvas.drawText("Email:", MARGIN_LEFT.toFloat(), y, labelPaint)
        canvas.drawText(order.userEmail.ifEmpty { "No especificado" }, MARGIN_LEFT + 80f, y, valuePaint)

        y += 15
        canvas.drawText("Teléfono:", MARGIN_LEFT.toFloat(), y, labelPaint)
        canvas.drawText(order.phoneNumber, MARGIN_LEFT + 80f, y, valuePaint)

        if (order.deliveryType == "HOME_DELIVERY" && order.deliveryAddress.isNotEmpty()) {
            y += 15
            canvas.drawText("Dirección:", MARGIN_LEFT.toFloat(), y, labelPaint)

            // Dividir dirección si es muy larga
            val address = order.deliveryAddress
            if (address.length > 60) {
                val lines = splitTextIntoLines(address, 60)
                lines.forEachIndexed { index, line ->
                    canvas.drawText(
                        line,
                        if (index == 0) MARGIN_LEFT + 80f else MARGIN_LEFT.toFloat(),
                        y,
                        valuePaint
                    )
                    if (index < lines.size - 1) y += 15
                }
            } else {
                canvas.drawText(address, MARGIN_LEFT + 80f, y, valuePaint)
            }
        }

        return y + 25
    }

    /**
     * Dibuja información de la farmacia
     */
    private fun drawPharmacyInfo(canvas: Canvas, order: OrderSnapshot, startY: Float): Float {
        var y = startY

        drawLine(canvas, y)
        y += 15

        val sectionPaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = FONT_SIZE_HEADING
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("FARMACIA", MARGIN_LEFT.toFloat(), y, sectionPaint)

        y += 20
        val labelPaint = Paint().apply {
            color = COLOR_GRAY
            textSize = FONT_SIZE_SMALL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val valuePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = FONT_SIZE_SMALL
            isAntiAlias = true
        }

        canvas.drawText("Nombre:", MARGIN_LEFT.toFloat(), y, labelPaint)
        canvas.drawText(order.pharmacyName, MARGIN_LEFT + 80f, y, valuePaint)

        y += 15
        canvas.drawText("Dirección:", MARGIN_LEFT.toFloat(), y, labelPaint)

        // Dividir dirección si es muy larga
        val address = order.pharmacyAddress
        if (address.length > 60) {
            val lines = splitTextIntoLines(address, 60)
            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    if (index == 0) MARGIN_LEFT + 80f else MARGIN_LEFT.toFloat(),
                    y,
                    valuePaint
                )
                if (index < lines.size - 1) y += 15
            }
        } else {
            canvas.drawText(address, MARGIN_LEFT + 80f, y, valuePaint)
        }

        if (order.pharmacyPhone.isNotEmpty()) {
            y += 15
            canvas.drawText("Teléfono:", MARGIN_LEFT.toFloat(), y, labelPaint)
            canvas.drawText(order.pharmacyPhone, MARGIN_LEFT + 80f, y, valuePaint)
        }

        return y + 25
    }

    /**
     * Dibuja la tabla de items (medicamentos)
     */
    private fun drawItemsTable(canvas: Canvas, order: OrderSnapshot, startY: Float): Float {
        var y = startY

        drawLine(canvas, y)
        y += 15

        val sectionPaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = FONT_SIZE_HEADING
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DETALLE DE PRODUCTOS", MARGIN_LEFT.toFloat(), y, sectionPaint)

        y += 25

        // Header de la tabla
        val headerPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val headerBgPaint = Paint().apply {
            color = COLOR_PRIMARY
            style = Paint.Style.FILL
        }

        val tableWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val headerHeight = 25f

        canvas.drawRect(
            MARGIN_LEFT.toFloat(),
            y,
            MARGIN_LEFT + tableWidth.toFloat(),
            y + headerHeight,
            headerBgPaint
        )

        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = FONT_SIZE_SMALL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Columnas: Producto (50%), Cantidad (15%), Precio Unit (17.5%), Subtotal (17.5%)
        val col1Width = tableWidth * 0.50f
        val col2Width = tableWidth * 0.15f
        val col3Width = tableWidth * 0.175f
        val col4Width = tableWidth * 0.175f

        canvas.drawText("Producto", MARGIN_LEFT + 5f, y + 17, headerTextPaint)
        canvas.drawText("Cant.", MARGIN_LEFT + col1Width + 5f, y + 17, headerTextPaint)
        canvas.drawText("Precio Unit.", MARGIN_LEFT + col1Width + col2Width + 5f, y + 17, headerTextPaint)
        canvas.drawText("Subtotal", MARGIN_LEFT + col1Width + col2Width + col3Width + 5f, y + 17, headerTextPaint)

        y += headerHeight

        // Filas de items
        val rowPaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val cellTextPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = FONT_SIZE_SMALL
            isAntiAlias = true
        }

        order.items.forEach { item ->
            val rowHeight = 40f

            // Líneas de la fila
            canvas.drawLine(MARGIN_LEFT.toFloat(), y, MARGIN_LEFT + tableWidth.toFloat(), y, rowPaint)

            // Contenido
            val textY = y + 15

            // Producto (con wrap si es muy largo)
            val productName = if (item.medicationName.length > 30) {
                item.medicationName.substring(0, 27) + "..."
            } else {
                item.medicationName
            }
            canvas.drawText(productName, MARGIN_LEFT + 5f, textY, cellTextPaint)

            // Presentación (si hay espacio)
            if (item.presentacion.isNotEmpty()) {
                val smallPaint = Paint().apply {
                    color = COLOR_GRAY
                    textSize = FONT_SIZE_SMALL - 2
                    isAntiAlias = true
                }
                canvas.drawText(item.presentacion, MARGIN_LEFT + 5f, textY + 12, smallPaint)
            }

            // Cantidad
            canvas.drawText("${item.quantity}", MARGIN_LEFT + col1Width + 15f, textY, cellTextPaint)

            // Precio unitario
            canvas.drawText(
                currencyFormat.format(item.pricePerUnit),
                MARGIN_LEFT + col1Width + col2Width + 5f,
                textY,
                cellTextPaint
            )

            // Subtotal
            canvas.drawText(
                currencyFormat.format(item.subtotal),
                MARGIN_LEFT + col1Width + col2Width + col3Width + 5f,
                textY,
                cellTextPaint
            )

            y += rowHeight
        }

        // Línea final de la tabla
        canvas.drawLine(MARGIN_LEFT.toFloat(), y, MARGIN_LEFT + tableWidth.toFloat(), y, rowPaint)

        return y + 20
    }

    /**
     * Dibuja los totales
     */
    private fun drawTotals(canvas: Canvas, order: OrderSnapshot, startY: Float): Float {
        var y = startY

        val labelPaint = Paint().apply {
            color = COLOR_GRAY
            textSize = FONT_SIZE_NORMAL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val valuePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = FONT_SIZE_NORMAL
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val totalPaint = Paint().apply {
            color = COLOR_PRIMARY
            textSize = FONT_SIZE_HEADING
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val labelX = PAGE_WIDTH - MARGIN_RIGHT - 150f
        val valueX = PAGE_WIDTH - MARGIN_RIGHT.toFloat()

        // Subtotal
        canvas.drawText("Subtotal:", labelX, y, labelPaint)
        canvas.drawText(currencyFormat.format(order.subtotal), valueX, y, valuePaint)

        y += 20

        // IVA
        canvas.drawText("IVA (19%):", labelX, y, labelPaint)
        canvas.drawText(currencyFormat.format(order.taxes), valueX, y, valuePaint)

        y += 20

        // Envío
        if (order.deliveryFee > 0) {
            canvas.drawText("Envío:", labelX, y, labelPaint)
            canvas.drawText(currencyFormat.format(order.deliveryFee), valueX, y, valuePaint)
            y += 20
        }

        // Línea antes del total
        drawLine(canvas, y)
        y += 15

        // Total
        canvas.drawText("TOTAL:", labelX, y, totalPaint)
        canvas.drawText(currencyFormat.format(order.totalAmount), valueX, y, totalPaint)

        return y + 30
    }

    /**
     * Dibuja las notas adicionales
     */
    private fun drawNotes(canvas: Canvas, order: OrderSnapshot, startY: Float): Float {
        var y = startY

        if (order.notes.isNotEmpty()) {
            drawLine(canvas, y)
            y += 15

            val labelPaint = Paint().apply {
                color = COLOR_GRAY
                textSize = FONT_SIZE_SMALL
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("NOTAS:", MARGIN_LEFT.toFloat(), y, labelPaint)

            y += 15
            val notesPaint = Paint().apply {
                color = COLOR_TEXT
                textSize = FONT_SIZE_SMALL
                isAntiAlias = true
            }

            val lines = splitTextIntoLines(order.notes, 80)
            lines.forEach { line ->
                canvas.drawText(line, MARGIN_LEFT.toFloat(), y, notesPaint)
                y += 15
            }
        }

        return y + 20
    }

    /**
     * Dibuja el pie de página
     */
    private fun drawFooter(canvas: Canvas, invoice: Invoice) {
        val footerY = PAGE_HEIGHT - MARGIN_BOTTOM.toFloat()

        // Línea superior
        val linePaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            strokeWidth = 1f
        }
        canvas.drawLine(
            MARGIN_LEFT.toFloat(),
            footerY - 30,
            PAGE_WIDTH - MARGIN_RIGHT.toFloat(),
            footerY - 30,
            linePaint
        )

        val footerPaint = Paint().apply {
            color = COLOR_GRAY
            textSize = FONT_SIZE_SMALL - 1
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        canvas.drawText(
            "Documento generado electrónicamente por MyMeds",
            PAGE_WIDTH / 2f,
            footerY - 15,
            footerPaint
        )

        canvas.drawText(
            "Este documento es válido sin firma ni sello",
            PAGE_WIDTH / 2f,
            footerY,
            footerPaint
        )
    }

    /**
     * Dibuja una línea horizontal
     */
    private fun drawLine(canvas: Canvas, y: Float) {
        val linePaint = Paint().apply {
            color = COLOR_LIGHT_GRAY
            strokeWidth = 1f
        }
        canvas.drawLine(
            MARGIN_LEFT.toFloat(),
            y,
            PAGE_WIDTH - MARGIN_RIGHT.toFloat(),
            y,
            linePaint
        )
    }

    /**
     * Divide un texto en líneas de longitud máxima
     */
    private fun splitTextIntoLines(text: String, maxLength: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        words.forEach { word ->
            if ((currentLine + word).length <= maxLength) {
                currentLine += "$word "
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.trim())
                currentLine = "$word "
            }
        }

        if (currentLine.isNotEmpty()) lines.add(currentLine.trim())

        return lines
    }

    /**
     * Crea el archivo PDF en el directorio de documentos
     */
    private fun createPdfFile(invoiceNumber: String): File {
        val invoicesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "invoices"
        )

        if (!invoicesDir.exists()) {
            invoicesDir.mkdirs()
        }

        val fileName = "invoice_${invoiceNumber.replace("-", "_")}_${System.currentTimeMillis()}.pdf"
        return File(invoicesDir, fileName)
    }

    /**
     * Obtiene el directorio de facturas
     */
    fun getInvoicesDirectory(): File {
        return File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "invoices"
        )
    }
}

/**
 * Extension function para Context
 */
fun Context.getPdfGenerator(): PdfGenerator {
    return PdfGenerator(this)
}