package com.edrive.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.edrive.app.EDriveApp

/** Şifrəli faylları arxa fonda Drive-a yükləyir. Tətbiq bağlansa belə WorkManager işi davam etdirir. */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uploads = (applicationContext as EDriveApp).container.uploads
        return if (uploads.processQueue()) Result.success() else Result.retry()
    }
}
