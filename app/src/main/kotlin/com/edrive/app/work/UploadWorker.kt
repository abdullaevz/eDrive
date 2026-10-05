package com.edrive.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.edrive.app.data.vault.UploadService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Şifrəli faylları arxa fonda Drive-a yükləyir. Tətbiq bağlansa belə WorkManager işi davam etdirir. */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploads: UploadService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (uploads.processQueue()) Result.success() else Result.retry()
}
