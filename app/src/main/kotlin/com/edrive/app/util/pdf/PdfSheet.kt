package com.edrive.app.util.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * eDrive üslubunda sadə A4 sənəd qurucusu (Android-in daxili [PdfDocument] API-si, əlavə kitabxana yoxdur).
 * Mətn avtomatik sətirlərə bölünür, səhifə dolanda yenisi açılır. PIN və Təhlükəsizlik açarı sənədləri bunu istifadə edir.
 */
class PdfSheet private constructor(private val doc: PdfDocument) {
    private var pageNo = 0
    private lateinit var page: PdfDocument.Page
    private val canvas: Canvas get() = page.canvas
    private var y = 0f

    private fun newPage() {
        if (pageNo > 0) doc.finishPage(page)
        page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++pageNo).create())
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 8f, Paint().apply { color = ACCENT })
        canvas.drawText("eDrive · AES-256-GCM · Argon2id", LEFT, HEIGHT - 42f, paint(9f, MUTED))
        y = 80f
    }

    private fun ensureSpace(h: Float) {
        if (y + h > HEIGHT - 70f) newPage()
    }

    fun title(text: String, subtitle: String) {
        canvas.drawText("eDrive", LEFT, y, paint(26f, ACCENT, bold = true)); y += 28
        canvas.drawText(text, LEFT, y, paint(16f, bold = true)); y += 20
        canvas.drawText("$subtitle · " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()), LEFT, y, paint(11f, MUTED))
        y += 36
    }

    /** Çərçivəli blok: etiket + dəyər cütləri (dəyər monoşrift, lazım olsa bir neçə sətir). */
    fun fields(vararg items: Pair<String, String>) {
        val valuePaint = paint(15f, mono = true, bold = true)
        val wrapped = items.map { (label, value) -> label to wrap(value, valuePaint, CONTENT_WIDTH - 40) }
        val h = 24f + wrapped.sumOf { (_, lines) -> 20.0 + lines.size * 20.0 + 12.0 }.toFloat()
        ensureSpace(h)
        val box = RectF(LEFT, y, WIDTH - LEFT, y + h)
        canvas.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 246, 249) })
        canvas.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = LINE })
        var by = y + 32
        for ((label, lines) in wrapped) {
            canvas.drawText(label.uppercase(), LEFT + 20, by, paint(9f, MUTED, bold = true)); by += 20
            lines.forEach { canvas.drawText(it, LEFT + 20, by, valuePaint); by += 20 }
            by += 12
        }
        y += h + 24
    }

    /** Başlıqlı mətn bölməsi (abzaslar avtomatik bölünür; boş sətir = abzas arası). */
    fun section(heading: String, paragraphs: List<String>, warning: Boolean = false) {
        ensureSpace(40f)
        canvas.drawText(heading, LEFT, y, paint(13f, if (warning) AMBER else INK, bold = true)); y += 22
        val body = paint(11f)
        for (p in paragraphs) {
            if (p.isEmpty()) { y += 8; continue }
            for (line in wrap(p, body, CONTENT_WIDTH)) {
                ensureSpace(17f)
                canvas.drawText(line, LEFT, y, body); y += 17
            }
        }
        y += 14
    }

    /** Kiçik monoşrift blok (məs. vault.json-un tam mətni). */
    fun code(text: String) {
        val p = paint(7.5f, mono = true)
        for (raw in text.lines()) {
            for (line in wrap(raw, p, CONTENT_WIDTH)) {
                ensureSpace(11f)
                canvas.drawText(line, LEFT, y, p); y += 11
            }
        }
        y += 14
    }

    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            var n = p.breakText(rest, true, width, null).coerceAtLeast(1)
            if (n < rest.length) rest.lastIndexOf(' ', n).takeIf { it > 0 }?.let { n = it + 1 }
            out += rest.substring(0, n).trimEnd()
            rest = rest.substring(n)
        }
        return out
    }

    private fun paint(size: Float, color: Int = INK, bold: Boolean = false, mono: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    companion object {
        private const val WIDTH = 595 // A4, 72 dpi
        private const val HEIGHT = 842
        private const val LEFT = 56f
        private const val CONTENT_WIDTH = WIDTH - 2 * LEFT
        private val ACCENT = Color.rgb(43, 180, 122)
        private val INK = Color.rgb(20, 24, 32)
        private val MUTED = Color.rgb(110, 118, 132)
        private val AMBER = Color.rgb(196, 120, 20)
        private val LINE = Color.rgb(214, 219, 227)

        fun write(out: OutputStream, build: PdfSheet.() -> Unit) {
            val doc = PdfDocument()
            try {
                PdfSheet(doc).apply {
                    newPage()
                    build()
                    doc.finishPage(page)
                }
                doc.writeTo(out)
            } finally {
                doc.close()
            }
        }
    }
}
