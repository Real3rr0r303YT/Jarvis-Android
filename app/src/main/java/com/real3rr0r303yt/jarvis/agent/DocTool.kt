package com.real3rr0r303yt.jarvis.agent

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.real3rr0r303yt.jarvis.core.JarvisLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DocAgent: generates a PDF document locally on device storage using Android's
 * built-in [PdfDocument] (no third-party dependency). The planner calls this to
 * fulfil "generate a PDF / document" sub-tasks.
 *
 * Input: the first line is the title, the remaining lines are the body. The PDF
 * is written to the app's external Documents dir (no runtime permission needed)
 * and the tool returns the absolute path.
 */
class DocTool(private val context: Context) : JarvisTool {
    override val name = "generate_pdf"
    override val description =
        "Generate a PDF document saved on the device from text. Input: first line = title, " +
            "following lines = body content. Returns the saved file path."

    override suspend fun run(input: String): String = withContext(Dispatchers.IO) {
        try {
            val lines = input.trim().lines()
            val title = lines.firstOrNull()?.trim().orEmpty().ifBlank { "Jarvis Document" }
            val body = lines.drop(1).joinToString("\n").trim()
            val file = writePdf(title, body)
            JarvisLog.action("DOC AGENT wrote PDF -> ${file.absolutePath}")
            "PDF saved to ${file.absolutePath}"
        } catch (e: Exception) {
            JarvisLog.error("DOC AGENT failed: ${e.message}")
            "PDF generation failed: ${e.message}"
        }
    }

    private fun writePdf(title: String, body: String): File {
        val pageWidth = 595  // A4 @ 72dpi
        val pageHeight = 842
        val margin = 48f
        val doc = PdfDocument()

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 12f }
        val lineHeight = 18f
        val maxWidth = pageWidth - 2 * margin

        val wrapped = buildList {
            if (title.isNotBlank()) { add(title); add("") }
            body.split("\n").forEach { paragraph ->
                if (paragraph.isBlank()) add("") else addAll(wrapLine(paragraph, bodyPaint, maxWidth))
            }
        }

        var pageNumber = 1
        var index = 0
        while (index < wrapped.size || pageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            var y = margin + lineHeight
            var drewTitle = false
            while (index < wrapped.size && y < pageHeight - margin) {
                val line = wrapped[index]
                val paint = if (pageNumber == 1 && index == 0) titlePaint else bodyPaint
                canvas.drawText(line, margin, y, paint)
                if (pageNumber == 1 && index == 0) drewTitle = true
                y += if (drewTitle && index == 0) lineHeight + 8f else lineHeight
                index++
            }
            doc.finishPage(page)
            pageNumber++
            if (index >= wrapped.size) break
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "jarvis-$stamp.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Greedy word-wrap of [text] to fit [maxWidth] using [paint] measurements. */
    private fun wrapLine(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) out.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out.ifEmpty { listOf("") }
    }
}
