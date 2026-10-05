package com.edrive.app.util

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
 * Qeydiyyatdan sonra istifadəçinin seçdiyi yerə yazılan bərpa sənədi (PDF).
 * Android-in daxili PdfDocument API-si ilə — əlavə kitabxana yoxdur.
 */
object RecoveryPdf {

    fun write(out: OutputStream, username: String, password: CharArray) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()) // A4, 72 dpi
        val c = page.canvas
        val left = 56f
        var y = 80f

        val accent = Color.rgb(43, 180, 122)
        val ink = Color.rgb(20, 24, 32)
        val muted = Color.rgb(110, 118, 132)

        fun paint(size: Float, color: Int = ink, bold: Boolean = false, mono: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        c.drawRect(0f, 0f, 595f, 8f, Paint().apply { color = accent })
        c.drawText("eDrive", left, y, paint(26f, accent, bold = true)); y += 28
        c.drawText("Hesab bərpa sənədi", left, y, paint(16f, bold = true)); y += 20
        c.drawText("Yaradılıb: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()), left, y, paint(11f, muted)); y += 40

        val box = RectF(left, y, 595f - left, y + 130f)
        c.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 246, 249) })
        c.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.rgb(214, 219, 227) })
        var by = y + 36
        c.drawText("İSTİFADƏÇİ ADI", left + 20, by, paint(9f, muted, bold = true)); by += 20
        c.drawText(username, left + 20, by, paint(16f, mono = true, bold = true)); by += 32
        c.drawText("MASTER PAROL", left + 20, by, paint(9f, muted, bold = true)); by += 20
        c.drawText(String(password), left + 20, by, paint(16f, mono = true, bold = true))
        y += 170

        val warnings = listOf(
            "Bu parol fayllarınızı şifrələyən açarın mənbəyidir və heç bir serverdə saxlanılmır.",
            "Parolu unutsanız, Google Drive-dakı şifrəli fayllarınızı bərpa etmək MÜMKÜN DEYİL —",
            "nə siz, nə də tətbiq tərtibatçısı onları aça bilər.",
            "",
            "Bu sənədi təhlükəsiz yerdə saxlayın: çap edib seyfdə, parol menecerində və ya",
            "şifrəli USB yaddaşda. Telefonda və ya buludda açıq formada saxlamayın.",
            "",
            "Yeni telefonda bərpa: eDrive-ı quraşdırın → eyni istifadəçi adı və parolla qeydiyyatdan",
            "keçin → Google Drive-a qoşulun. Tətbiq Drive-dakı vault-u tapıb sizin parolla açacaq.",
        )
        c.drawText("Vacib", left, y, paint(13f, Color.rgb(196, 120, 20), bold = true)); y += 22
        val body = paint(11f)
        warnings.forEach { line -> c.drawText(line, left, y, body); y += 17 }

        c.drawText("eDrive · AES-256-GCM · Argon2id", left, 800f, paint(9f, muted))
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
