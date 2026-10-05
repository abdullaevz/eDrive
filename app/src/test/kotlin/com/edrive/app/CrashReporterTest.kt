package com.edrive.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.edrive.app.util.AppLog
import com.edrive.app.util.CrashReporter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CrashReporterTest {
    @Test fun writesReportWithStackTraceAndRecentEvents() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val reporter = CrashReporter(ctx)
        AppLog.i("test", "Yükləmə başladı")
        val error = IllegalStateException("Sınaq çökməsi")
        reporter.write(Thread.currentThread(), error)

        val report = reporter.pending()
        assertNotNull(report)
        val text = report!!.readText()
        assertTrue(text.contains("ÇÖKMƏ HESABATI"))
        assertTrue(text.contains("IllegalStateException: Sınaq çökməsi"))
        assertTrue(text.contains("CrashReporterTest")) // stack trace sətri
        assertTrue(text.contains("Yükləmə başladı"))   // son hadisələr
        assertTrue(reporter.diagnostics().contains("DİAQNOSTİKA"))

        reporter.dismiss(report)
        assertNull(reporter.pending())
    }
}
