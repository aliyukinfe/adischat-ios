package com.adischat.app.feature.restaurants.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Signed upload bucket policy matching `domain.ts` and `202611100007_storage_buckets.sql`.
 */
enum class RestaurantsUploadKind(
    val maxBytes: Long,
    val allowedMimeTypes: Set<String>,
) {
    APPLICATION_DOCUMENT(
        maxBytes = 10L * 1024L * 1024L,
        allowedMimeTypes = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
        ),
    ),
    PUBLIC_MEDIA(
        maxBytes = 5L * 1024L * 1024L,
        allowedMimeTypes = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
        ),
    ),
}

enum class RestaurantMediaSlot {
    LOGO,
    COVER,
}

/**
 * Streamable binary payload source for Android Compose file/URI pickers and unit tests.
 */
data class RestaurantsBinaryUploadSource(
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val openStream: () -> InputStream,
) {
    companion object {
        fun fromBytes(
            fileName: String,
            mimeType: String,
            bytes: ByteArray,
        ): RestaurantsBinaryUploadSource = RestaurantsBinaryUploadSource(
            fileName = fileName,
            mimeType = mimeType,
            byteSize = bytes.size.toLong(),
            openStream = { ByteArrayInputStream(bytes) },
        )

        fun fromFile(
            file: File,
            mimeType: String,
            fileName: String = file.name,
        ): RestaurantsBinaryUploadSource = RestaurantsBinaryUploadSource(
            fileName = fileName,
            mimeType = mimeType,
            byteSize = if (file.isFile) file.length() else -1L,
            openStream = {
                if (!file.isFile) {
                    throw IOException("Selected file does not exist.")
                }
                FileInputStream(file)
            },
        )

        fun fromContentUri(
            contentResolver: ContentResolver,
            uri: Uri,
            fileName: String,
            mimeType: String,
            byteSize: Long,
        ): RestaurantsBinaryUploadSource = RestaurantsBinaryUploadSource(
            fileName = fileName,
            mimeType = mimeType,
            byteSize = byteSize,
            openStream = {
                contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open selected file stream.")
            },
        )
    }
}

data class ValidatedUploadMetadata(
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
)

object RestaurantsUploadValidator {
    private val allowedDocTypes = setOf(
        "business_license",
        "tin_certificate",
        "owner_id",
        "health_permit",
        "other",
    )

    private val loopbackHosts = setOf("localhost", "127.0.0.1", "::1")

    fun validateDocType(docType: String): String {
        val normalized = docType.trim()
        if (normalized !in allowedDocTypes) {
            throw RestaurantsFailure.Validation(
                "invalid_doc_type",
                "Unsupported verification document type.",
            )
        }
        return normalized
    }

    fun validateMetadata(
        kind: RestaurantsUploadKind,
        source: RestaurantsBinaryUploadSource,
    ): ValidatedUploadMetadata {
        val cleanName = source.fileName.trim()
        if (cleanName.isEmpty()) {
            throw RestaurantsFailure.Validation(
                "invalid_file_name",
                "File name is required before uploading.",
            )
        }
        val cleanMime = source.mimeType.trim().lowercase()
        if (cleanMime !in kind.allowedMimeTypes) {
            throw RestaurantsFailure.Validation(
                "invalid_upload_mime_type",
                "Unsupported file format for this upload.",
            )
        }
        if (source.byteSize <= 0L) {
            throw RestaurantsFailure.Validation(
                "invalid_upload_size",
                "Selected file is empty or unreadable.",
            )
        }
        if (source.byteSize > kind.maxBytes) {
            val maxMb = kind.maxBytes / (1024L * 1024L)
            throw RestaurantsFailure.Validation(
                "file_too_large",
                "Selected file exceeds the $maxMb MB limit.",
            )
        }
        return ValidatedUploadMetadata(
            fileName = cleanName,
            mimeType = cleanMime,
            byteSize = source.byteSize,
        )
    }

    fun validateStoragePath(rawPath: String?): String {
        val clean = rawPath?.trim().orEmpty()
        if (clean.isEmpty() ||
            clean.startsWith("/") ||
            clean.contains("..") ||
            clean.contains("\\")
        ) {
            throw RestaurantsFailure.Server(
                "invalid_upload_response",
                "Invalid storage path returned by server.",
            )
        }
        return clean
    }

    fun validateSignedUploadUrl(
        rawUrl: String?,
        allowLoopbackHttp: Boolean = false,
    ): HttpUrl {
        val trimmed = rawUrl?.trim().orEmpty()
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw RestaurantsFailure.Server(
                "invalid_upload_url",
                "Upload link returned by server was malformed.",
            )
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw RestaurantsFailure.Server(
                "invalid_upload_url",
                "Upload link contained invalid credentials.",
            )
        }
        val isAllowedLoopback = allowLoopbackHttp &&
            parsed.scheme.equals("http", ignoreCase = true) &&
            parsed.host.lowercase() in loopbackHosts
        if (!parsed.isHttps && !isAllowedLoopback) {
            throw RestaurantsFailure.Server(
                "insecure_upload_url",
                "Upload link must use HTTPS.",
            )
        }
        return parsed
    }
}

fun interface RestaurantsSignedUploader {
    suspend fun uploadToSignedUrl(
        uploadUrl: String,
        kind: RestaurantsUploadKind,
        source: RestaurantsBinaryUploadSource,
    )
}

/**
 * Credential-isolated streaming OkHttp uploader for Supabase `createSignedUploadUrl` targets.
 *
 * Uses `PUT` with raw streamed binary payload, `Content-Type`, `x-upsert: false`, and
 * explicit credential header stripping so `Authorization`, `apikey`, or cookies never leave
 * to signed storage URLs. Signed URLs and payload data are never logged or embedded in errors.
 */
class OkHttpRestaurantsSignedUploader(
    private val okHttpClient: OkHttpClient = RestaurantsApiClientFactory.buildSignedUploadOkHttpClient(),
    private val allowInsecureLoopbackHttp: Boolean = false,
) : RestaurantsSignedUploader {

    override suspend fun uploadToSignedUrl(
        uploadUrl: String,
        kind: RestaurantsUploadKind,
        source: RestaurantsBinaryUploadSource,
    ) {
        val metadata = RestaurantsUploadValidator.validateMetadata(kind, source)
        val validatedUrl = RestaurantsUploadValidator.validateSignedUploadUrl(
            rawUrl = uploadUrl,
            allowLoopbackHttp = allowInsecureLoopbackHttp,
        )

        val requestBody = StreamingBinaryUploadRequestBody(
            source = source,
            mediaType = metadata.mimeType.toMediaType(),
            expectedByteSize = metadata.byteSize,
            maxAllowedBytes = kind.maxBytes,
        )

        val request = Request.Builder()
            .url(validatedUrl)
            .put(requestBody)
            .header("x-upsert", "false")
            .header("cache-control", "max-age=3600")
            // Signed upload URLs authenticate solely via query `?token=...`; strip ambient creds.
            .removeHeader("Authorization")
            .removeHeader("apikey")
            .removeHeader("Cookie")
            .build()

        executePut(okHttpClient.newCall(request))
    }

    private suspend fun executePut(call: Call) = suspendCancellableCoroutine<Unit> { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isActive) return
                    val failure = when (e) {
                        is UploadSizeExceededException -> RestaurantsFailure.Validation(
                            "file_too_large",
                            "Selected file exceeds the maximum allowed size.",
                        )
                        is UploadSizeMismatchException -> RestaurantsFailure.Validation(
                            "invalid_upload_size",
                            "Selected file size could not be verified during upload.",
                        )
                        else -> RestaurantsFailure.Network(
                            "File upload could not complete. Check your connection and try again.",
                            e,
                        )
                    }
                    cont.resumeWithException(failure)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!cont.isActive) return
                        if (res.isSuccessful) {
                            cont.resume(Unit)
                        } else {
                            cont.resumeWithException(mapStorageHttpStatus(res.code))
                        }
                    }
                }
            },
        )
    }

    private fun mapStorageHttpStatus(statusCode: Int): RestaurantsFailure = when (statusCode) {
        401, 403 -> RestaurantsFailure.Forbidden(
            "signed_upload_rejected",
            "Upload link expired or was rejected. Please try again.",
        )
        404 -> RestaurantsFailure.NotFound(
            "upload_target_not_found",
            "Upload destination could not be found.",
        )
        409 -> RestaurantsFailure.Conflict(
            "upload_already_exists",
            "This upload link has already been used.",
        )
        413 -> RestaurantsFailure.Validation(
            "file_too_large",
            "Selected file exceeds the maximum allowed size.",
        )
        400, 415, 422 -> RestaurantsFailure.Validation(
            "invalid_upload_request",
            "Uploaded file format or request was rejected.",
        )
        else -> RestaurantsFailure.Server(
            "storage_upload_failed",
            "File storage service is temporarily unavailable ($statusCode).",
        )
    }

    private class StreamingBinaryUploadRequestBody(
        private val source: RestaurantsBinaryUploadSource,
        private val mediaType: MediaType,
        private val expectedByteSize: Long,
        private val maxAllowedBytes: Long,
    ) : RequestBody() {
        override fun contentType(): MediaType = mediaType

        override fun contentLength(): Long = expectedByteSize

        // ContentResolver / one-shot file streams must never be replayed automatically on sockets.
        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var totalWritten = 0L
            try {
                source.openStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        totalWritten += read
                        if (totalWritten > maxAllowedBytes || totalWritten > expectedByteSize) {
                            throw UploadSizeExceededException()
                        }
                        sink.write(buffer, 0, read)
                    }
                }
            } catch (ioe: IOException) {
                throw ioe
            } catch (t: Throwable) {
                throw IOException("Failed while streaming binary upload.", t)
            }
            if (totalWritten <= 0L || totalWritten != expectedByteSize) {
                throw UploadSizeMismatchException()
            }
        }
    }

    private class UploadSizeExceededException : IOException("Upload exceeded allowed byte size.")
    private class UploadSizeMismatchException : IOException("Upload size did not match metadata.")

    private companion object {
        const val STREAM_BUFFER_BYTES = 16 * 1024
    }
}
