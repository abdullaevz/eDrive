package com.edrive.app.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream

/**
 * [DriveClient]-in Google Drive REST API v3 implementasiyası — birbaşa OkHttp ilə
 * (ağır Google Java client kitabxanası olmadan). Drive-a yalnız ŞİFRƏLİ baytlar göndərilir.
 */
class GoogleDriveClient(
    private val http: OkHttpClient,
    private val token: suspend (forceRefresh: Boolean) -> String,
) : DriveClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://www.googleapis.com/drive/v3"
    private val upload = "https://www.googleapis.com/upload/drive/v3/files"

    override suspend fun about(): DriveAbout = get("$base/about?fields=user(emailAddress,displayName)")

    override suspend fun findByName(name: String, parentId: String?, folder: Boolean): DriveFile? {
        val q = buildString {
            append("name = '").append(name.replace("\\", "\\\\").replace("'", "\\'")).append("' and trashed = false")
            if (parentId != null) append(" and '").append(parentId).append("' in parents")
            if (folder) append(" and mimeType = '$FOLDER_MIME'")
        }
        val url = "$base/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id,name,size,mimeType)")
            .build().toString()
        return get<DriveFileList>(url).files.firstOrNull()
    }

    private suspend fun createFolder(name: String, parentId: String?): DriveFile {
        val body = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            if (parentId != null) putJsonArray("parents") { add(parentId) }
        }.toString().toRequestBody(JSON)
        return send(Request.Builder().url("$base/files?fields=id,name").post(body))
    }

    override suspend fun ensureFolder(name: String, parentId: String?): String =
        (findByName(name, parentId, folder = true) ?: createFolder(name, parentId)).id

    override suspend fun listChildren(parentId: String): List<DriveFile> {
        val all = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = "$base/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$parentId' in parents and trashed = false")
                .addQueryParameter("fields", "nextPageToken,files(id,name,size)")
                .addQueryParameter("pageSize", "1000")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build().toString()
            val res: DriveFileList = get(url)
            all += res.files
            page = res.nextPageToken
        } while (page != null)
        return all
    }

    /** Kiçik fayllar (meta, vault.json) — multipart yükləmə. `existingId` verilsə, fayl yenilənir. */
    override suspend fun uploadSmall(name: String, parentId: String, bytes: ByteArray, mime: String, existingId: String?): DriveFile {
        val meta = buildJsonObject {
            put("name", name)
            if (existingId == null) putJsonArray("parents") { add(parentId) }
        }.toString()
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta.toRequestBody(JSON))
            .addPart(bytes.toRequestBody(mime.toMediaType()))
            .build()
        val req = if (existingId == null) Request.Builder().url("$upload?uploadType=multipart&fields=id,name").post(body)
        else Request.Builder().url("$upload/$existingId?uploadType=multipart&fields=id,name").patch(body)
        return send(req)
    }

    /** Böyük fayllar — resumable session + axınla PUT (fayl yaddaşa tam yüklənmir). */
    override suspend fun uploadLarge(name: String, parentId: String, file: File, onProgress: (Float) -> Unit): DriveFile {
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", OCTET)
            putJsonArray("parents") { add(parentId) }
        }.toString().toRequestBody(JSON)
        val location = execute(
            Request.Builder().url("$upload?uploadType=resumable&fields=id,name")
                .header("X-Upload-Content-Type", OCTET)
                .header("X-Upload-Content-Length", file.length().toString())
                .post(meta),
        ).use { it.header("Location") ?: throw DriveException(it.code, "Resumable session yaradılmadı") }

        val body = ProgressBody(file, OCTET.toMediaType(), onProgress)
        return send(Request.Builder().url(location).put(body))
    }

    /** Faylın məzmunu axın kimi. Çağıran tərəf bağlamalıdır. */
    override suspend fun download(fileId: String): InputStream {
        val res = execute(Request.Builder().url("$base/files/$fileId?alt=media").get())
        return res.body.byteStream()
    }

    override suspend fun delete(fileId: String) {
        try {
            execute(Request.Builder().url("$base/files/$fileId").delete()).close()
        } catch (e: DriveException) {
            if (e.code != 404) throw e
        }
    }

    // ---------------------------------------------------------------- internals

    private suspend inline fun <reified T> get(url: String): T = send(Request.Builder().url(url).get())

    private suspend inline fun <reified T> send(builder: Request.Builder): T =
        execute(builder).use { json.decodeFromString<T>(it.body.string()) }

    /** Token əlavə edir; 401 olarsa token-i yeniləyib bir dəfə təkrar cəhd edir. */
    private suspend fun execute(builder: Request.Builder): Response = withContext(Dispatchers.IO) {
        var res = http.newCall(builder.header("Authorization", "Bearer ${token(false)}").build()).execute()
        if (res.code == 401) {
            res.close()
            res = http.newCall(builder.header("Authorization", "Bearer ${token(true)}").build()).execute()
        }
        if (!res.isSuccessful) {
            val msg = res.body.string().take(300)
            res.close()
            throw DriveException(res.code, "Drive xətası ${res.code}: $msg")
        }
        res
    }

    private class ProgressBody(private val file: File, private val type: MediaType, private val onProgress: (Float) -> Unit) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            val total = file.length().coerceAtLeast(1)
            var sent = 0L
            var lastReport = 0L
            file.source().use { src ->
                val buf = okio.Buffer()
                while (true) {
                    val n = src.read(buf, 256 * 1024)
                    if (n == -1L) break
                    sink.write(buf, n)
                    sent += n
                    if (sent - lastReport > 512 * 1024 || sent == total) {
                        lastReport = sent
                        onProgress(sent.toFloat() / total)
                    }
                }
            }
        }
    }

    private companion object {
        const val FOLDER_MIME = DriveClient.FOLDER_MIME
        const val OCTET = DriveClient.OCTET
        val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
