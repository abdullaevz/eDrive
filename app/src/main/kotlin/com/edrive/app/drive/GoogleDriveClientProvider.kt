package com.edrive.app.drive

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Hər Google hesabı üçün [GoogleDriveClient] yaradır və access token-ləri keşləyir.
 * Token vaxtı keçəndə (401) keşdən silinib yenisi alınır; icazə lazım olarsa [DriveConsentRequired] atılır.
 */
@Singleton
class GoogleDriveClientProvider @Inject constructor(
    private val http: OkHttpClient,
    private val auth: DriveAuth,
) : DriveClientProvider {

    private val tokens = ConcurrentHashMap<String, String>()

    override fun forAccount(email: String): DriveClient = GoogleDriveClient(http) { force -> tokenFor(email, force) }

    override fun withToken(token: String): DriveClient = GoogleDriveClient(http) { _ -> token }

    private suspend fun tokenFor(email: String, forceRefresh: Boolean): String {
        if (forceRefresh) tokens.remove(email)?.let { auth.invalidate(it) }
        tokens[email]?.takeIf { !forceRefresh }?.let { return it }
        return when (val r = auth.authorize(email)) {
            is AuthResult.Token -> r.accessToken.also { tokens[email] = it }
            is AuthResult.NeedsConsent -> throw DriveConsentRequired(r.pendingIntent)
        }
    }
}
