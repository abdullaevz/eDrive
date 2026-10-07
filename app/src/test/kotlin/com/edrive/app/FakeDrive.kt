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

    class Node(val id: String, val name: String, var parent: String?, val folder: Boolean, var bytes: ByteArray)

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

    override suspend fun createFolder(name: String, parentId: String?) = create(name, parentId, true, ByteArray(0)).toFile()

    override suspend fun rename(fileId: String, name: String) {
        val n = nodes.getValue(fileId)
        nodes[fileId] = Node(n.id, name, n.parent, n.folder, n.bytes)
    }

    /** Zibil: qovluq və bütün nəsilləri görünməz olur (burada sadəcə [trashed]-ə köçürülür). */
    val trashed = linkedMapOf<String, Node>()

    override suspend fun trash(fileId: String) {
        val n = nodes.remove(fileId) ?: return
        trashed[fileId] = n
        nodes.values.filter { it.parent == fileId }.map { it.id }.forEach { trash(it) }
    }

    override suspend fun listChildren(parentId: String) = nodes.values.filter { it.parent == parentId }.map { it.toFile() }

    override suspend fun uploadSmall(name: String, parentId: String, bytes: ByteArray, mime: String, existingId: String?): DriveFile {
        if (existingId != null) return nodes.getValue(existingId).also { it.bytes = bytes }.toFile()
        return create(name, parentId, false, bytes).toFile()
    }

    override suspend fun uploadLarge(name: String, parentId: String, file: File, onProgress: (Float) -> Unit): DriveFile {
        onProgress(0.5f); onProgress(1f)
        return create(name, parentId, false, file.readBytes()).toFile()
    }

    override suspend fun move(fileId: String, fromParentId: String, toParentId: String) {
        val n = nodes.getValue(fileId)
        check(n.parent == fromParentId) { "Fayl $fromParentId qovluğunda deyil" }
        n.parent = toParentId
    }

    override suspend fun download(fileId: String): InputStream = ByteArrayInputStream(nodes.getValue(fileId).bytes)

    override suspend fun delete(fileId: String) { nodes.remove(fileId) }

    private fun create(name: String, parent: String?, folder: Boolean, bytes: ByteArray) =
        Node("drive-${++seq}", name, parent, folder, bytes).also { nodes[it.id] = it }

    fun folder(name: String, parent: String?) = create(name, parent, true, ByteArray(0))

    private fun Node.toFile() = DriveFile(id, name, bytes.size.toString(), if (folder) DriveClient.FOLDER_MIME else DriveClient.OCTET)
}

