package com.edrive.app.data.vault

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.edrive.app.work.UploadWorker
import java.util.concurrent.TimeUnit

/** Arxa fon yükləməsini planlaşdırmaq üçün abstraksiya (testlərdə saxtası istifadə olunur). */
fun interface UploadScheduler {
    fun schedule()
}

/** WorkManager: internet olanda işləyir, tətbiq bağlansa belə davam edir, xəta olarsa eksponensial gecikmə ilə təkrarlayır. */
class WorkManagerUploadScheduler(private val context: Context) : UploadScheduler {
    override fun schedule() {
        val req = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("edrive-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}
