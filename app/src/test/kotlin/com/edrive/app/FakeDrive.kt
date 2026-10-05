package com.edrive.app

import com.edrive.app.drive.AuthResult
import com.edrive.app.drive.DriveAbout
import com.edrive.app.drive.DriveAuthorizer
import com.edrive.app.drive.DriveClient
import com.edrive.app.drive.DriveClientProvider
import com.edrive.app.drive.DriveFile
import com.edrive.app.drive.DriveUser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Yaddaşda işləyən saxta Google Drive. [DriveClient] interfeysi sayəsində bütün Drive axını
 * (qoşulma, yükləmə, sinxronizasiya, bərpa, silmə) internet olmadan test oluna bilir.
 */
class FakeDrive(private val email: String = "natiq@gmail.com") : DriveClient, DriveClientProvider, DriveAuthorizer {

    class Node(val id: String, val name: String, val parent: String?, val folder: Boolean, var bytes: ByteArray)

    val nodes = linkedMapOf<String, Node>()
    val revoked = mutableListOf<String>()
    val forgotten = mutableListOf<String>()
    private var seq = 0

    fun filesNamed(suffix: String) = nodes.values.filter { !it.folder && it.name.endsWith(suffix) }

    // DriveClientProvider
    override fun forAccount(email: String): DriveClient = this
    override fun withToken(token: String): DriveClient = this

    // DriveAuthorizer
    override suspend fun authorize(email: String?) = AuthResult.Token("fake-token")
    override suspend fun revoke(token: String) { revoked += token }
    override fun tokenFromConsentResult(data: android.content.Intent?) = "fake-token"
    override suspend fun forgetAccount(email: String) { forgotten += email }

    // DriveClient
    override suspend fun about() = DriveAbout(DriveUser(email))

    override suspend fun findByName(name: String, parentId: String?, folder: Boolean): DriveFile? =
        nodes.values.firstOrNull { it.name == name && (parentId == null || it.parent == parentId) && (!folder || it.folder) }?.toFile()

    override suspend fun ensureFolder(name: String, parentId: String?): String =
        findByName(name, parentId, folder = true)?.id ?: create(name, parentId, true, ByteArray(0)).id

    override suspend fun listChildren(parentId: String) = nodes.values.filter { it.parent == parentId }.map { it.toFile() }

    override suspend fun uploadSmall(name: String, parentId: String, bytes: ByteArray, mime: String, existingId: String?): DriveFile {
        if (existingId != null) return nodes.getValue(existingId).also { it.bytes = bytes }.toFile()
        return create(name, parentId, false, bytes).toFile()
    }

    override suspend fun uploadLarge(name: String, parentId: String, file: File, onProgress: (Float) -> Unit): DriveFile {
        onProgress(0.5f); onProgress(1f)
        return create(name, parentId, false, file.readBytes()).toFile()
    }

    override suspend fun download(fileId: String): InputStream = ByteArrayInputStream(nodes.getValue(fileId).bytes)

    override suspend fun delete(fileId: String) { nodes.remove(fileId) }

    private fun create(name: String, parent: String?, folder: Boolean, bytes: ByteArray) =
        Node("drive-${++seq}", name, parent, folder, bytes).also { nodes[it.id] = it }

    private fun Node.toFile() = DriveFile(id, name, bytes.size.toString())
}

/** Robolectric-də Android Keystore yoxdur — barmaq izi üçün saxta implementasiya. */
class FakeBiometric : com.edrive.app.security.BiometricKeyStore {
    override fun isAvailable() = false
    override suspend fun enroll(activity: androidx.fragment.app.FragmentActivity, userId: Long, dek: ByteArray) =
        com.edrive.app.security.BiometricKeyStore.Wrapped(ByteArray(12), dek.copyOf())
    override suspend fun unlock(activity: androidx.fragment.app.FragmentActivity, userId: Long, wrapped: com.edrive.app.security.BiometricKeyStore.Wrapped) =
        wrapped.ciphertext.copyOf()
    override fun deleteKey(userId: Long) {}
}
