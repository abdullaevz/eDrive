package com.edrive.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Tətbiqin qısa daxili jurnalı (son 300 hadisə, yalnız yaddaşda).
 * DİQQƏT: buraya parol, açar, fayl adı və ya fayl məzmunu YAZILMIR — yalnız texniki hadisələr.
 */
object AppLog {
    private const val MAX = 300
    private val lines = ArrayDeque<String>(MAX)
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun i(tag: String, msg: String) = add("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = add("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = add("E", tag, msg, t)

    @Synchronized
    private fun add(level: String, tag: String, msg: String, t: Throwable?) {
        val line = buildString {
            append(time.format(Date())).append(' ').append(level).append('/').append(tag).append(": ").append(msg)
            if (t != null) append(" — ").append(t.javaClass.simpleName).append(": ").append(t.message)
        }
        if (lines.size == MAX) lines.removeFirst()
        lines.addLast(line)
        when (level) {
            "E" -> Log.e("eDrive/$tag", msg, t)
            "W" -> Log.w("eDrive/$tag", msg, t)
            else -> Log.i("eDrive/$tag", msg)
        }
    }

    @Synchronized
    fun snapshot(): String = if (lines.isEmpty()) "(boşdur)" else lines.joinToString("\n")
}

/**
 * Gözlənilməz çökmələri tutur: hesabatı diskə yazır, növbəti açılışda istifadəçiyə göstərilir.
 * Hesabatda yalnız texniki məlumat var (xəta izi, cihaz, versiya) — şəxsi məlumat yoxdur.
 */
class CrashReporter(private val context: Context) {

    private val dir get() = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            if (previous != null) previous.uncaughtException(thread, error) else exitProcess(10)
        }
    }

    /** Ən son göstərilməmiş çökmə hesabatı (yoxdursa null). */
    fun pending(): File? = dir.listFiles { f -> f.name.endsWith(".txt") }?.maxByOrNull { it.lastModified() }

    /** "Bağla": göstərilmiş hesabat(lar) silinir ki, növbəti açılışda yenidən çıxmasın. */
    fun dismiss(@Suppress("UNUSED_PARAMETER") report: File) {
        dir.listFiles()?.forEach { it.delete() }
    }

    internal fun write(thread: Thread, error: Throwable): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(
            buildString {
                appendLine(header("eDrive — ÇÖKMƏ HESABATI"))
                appendLine("Mövzu (thread): ${thread.name}")
                appendLine()
                appendLine("──────── Xəta izi (stack trace) ────────")
                appendLine(error.stackTraceToString())
                appendLine("──────── Son hadisələr ────────")
                appendLine(AppLog.snapshot())
            },
        )
        // Köhnə hesabatlar yığılmasın — son 5-i saxlanılır
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        return file
    }

    /** İstənilən vaxt ixrac üçün: cihaz məlumatı + son hadisələr (çökmə olmadan). */
    fun diagnostics(): String = buildString {
        appendLine(header("eDrive — DİAQNOSTİKA JURNALI"))
        appendLine("──────── Son hadisələr ────────")
        appendLine(AppLog.snapshot())
        pending()?.let {
            appendLine()
            appendLine("──────── Son çökmə hesabatı (${it.name}) ────────")
            appendLine(it.readText())
        }
    }

    private fun header(title: String): String {
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pkg?.longVersionCode else pkg?.versionCode?.toLong()
        return buildString {
            appendLine(title)
            appendLine("Vaxt: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Tətbiq: ${pkg?.versionName} ($versionCode)")
            appendLine("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            append("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }
}
