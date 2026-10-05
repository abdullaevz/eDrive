package com.edrive.app.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Drive icazəsi (OAuth 2.0) — Google Identity Services "AuthorizationClient".
 *
 * Scope: `drive.file` — tətbiq YALNIZ özünün yaratdığı faylları görə bilir, istifadəçinin
 * digər Drive fayllarına çıxışı yoxdur. Bu, həm təhlükəsizlik, həm də Google-un yoxlama
 * tələbləri baxımından ən düzgün seçimdir ("restricted" scope deyil).
 */
class DriveAuth(private val context: Context, private val http: OkHttpClient) : DriveAuthorizer {

    private val client get() = Identity.getAuthorizationClient(context)

    override suspend fun authorize(email: String?): AuthResult {
        val req = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .apply { if (email != null) setAccount(Account(email, "com.google")) }
            .build()
        val r: AuthorizationResult = client.authorize(req).await()
        val pi = r.pendingIntent
        return if (r.hasResolution() && pi != null) AuthResult.NeedsConsent(pi)
        else AuthResult.Token(r.accessToken ?: error("Access token alınmadı"))
    }

    fun tokenFromConsentResult(data: Intent?): String {
        val r = client.getAuthorizationResultFromIntent(data)
        return r.accessToken ?: error("Access token alınmadı")
    }

    /** Səhv/vaxtı keçmiş token-i keşdən silir (401 cavabından sonra). */
    suspend fun invalidate(token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
    }

    /** "Ayır": Google tərəfində bu tətbiqə verilmiş icazəni ləğv edir. */
    override suspend fun revoke(token: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(
                    Request.Builder().url("https://oauth2.googleapis.com/revoke")
                        .post(FormBody.Builder().add("token", token).build()).build(),
                ).execute().close()
            }
        }
        invalidate(token)
    }

    override suspend fun forgetAccount(email: String) {
        runCatching {
            val req = RevokeAccessRequest.builder()
                .setAccount(Account(email, "com.google"))
                .setScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            client.revokeAccess(req).await()
        }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
