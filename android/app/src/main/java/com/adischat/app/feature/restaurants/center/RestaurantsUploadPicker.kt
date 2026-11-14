package com.adischat.app.feature.restaurants.center

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.adischat.app.feature.restaurants.data.RestaurantMediaSlot
import com.adischat.app.feature.restaurants.data.RestaurantsBinaryUploadSource
import com.adischat.app.feature.restaurants.data.RestaurantsFailure
import com.adischat.app.feature.restaurants.data.RestaurantsUploadKind
import com.adischat.app.feature.restaurants.data.RestaurantsUploadValidator
import java.io.InputStream
import java.util.Locale

sealed interface RestaurantsPendingPickerTarget {
    val uploadKind: RestaurantsUploadKind
    val targetKey: String
    val displayLabel: String

    data class ApplicationDocument(
        val applicationId: String,
        val docType: String,
        val label: String,
    ) : RestaurantsPendingPickerTarget {
        override val uploadKind: RestaurantsUploadKind = RestaurantsUploadKind.APPLICATION_DOCUMENT
        override val targetKey: String = "app-doc:$applicationId:$docType"
        override val displayLabel: String = label
    }

    data class ApplicationMedia(
        val application: CenterApplicationUi,
        val slot: RestaurantMediaSlot,
    ) : RestaurantsPendingPickerTarget {
        override val uploadKind: RestaurantsUploadKind = RestaurantsUploadKind.PUBLIC_MEDIA
        override val targetKey: String = "app-media:${application.id}:${slot.name}"
        override val displayLabel: String =
            if (slot == RestaurantMediaSlot.LOGO) "Application Logo" else "Application Cover"
    }

    data class RestaurantMedia(
        val restaurantId: String,
        val slot: RestaurantMediaSlot,
        val description: String? = null,
    ) : RestaurantsPendingPickerTarget {
        override val uploadKind: RestaurantsUploadKind = RestaurantsUploadKind.PUBLIC_MEDIA
        override val targetKey: String = "rest-media:$restaurantId:${slot.name}"
        override val displayLabel: String =
            if (slot == RestaurantMediaSlot.LOGO) "Restaurant Logo" else "Restaurant Cover"
    }

    data class MenuItemPhoto(
        val restaurantId: String,
        val menuItemId: String,
        val itemName: String,
    ) : RestaurantsPendingPickerTarget {
        override val uploadKind: RestaurantsUploadKind = RestaurantsUploadKind.PUBLIC_MEDIA
        override val targetKey: String = "menu-photo:$restaurantId:$menuItemId"
        override val displayLabel: String = "$itemName Photo"
    }
}

object RestaurantsPickerMetadataHelper {
    val DOCUMENT_PICKER_MIME_TYPES = arrayOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp",
    )

    private val extensionToMime = mapOf(
        "pdf" to "application/pdf",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
    )

    private val mimeToDefaultExtension = mapOf(
        "application/pdf" to "pdf",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
    )

    fun normalizeMimeType(
        kind: RestaurantsUploadKind,
        reportedMime: String?,
        fileName: String?,
    ): Result<String> {
        val cleaned = reportedMime?.trim()?.lowercase(Locale.US).orEmpty()
        val mapped = when {
            cleaned == "image/jpg" || cleaned == "image/pjpeg" -> "image/jpeg"
            cleaned.isEmpty() || cleaned == "application/octet-stream" -> {
                val ext = fileName?.substringAfterLast('.', "")?.trim()?.lowercase(Locale.US).orEmpty()
                extensionToMime[ext].orEmpty()
            }
            else -> cleaned
        }

        if (mapped !in kind.allowedMimeTypes) {
            val expectedDesc = if (kind == RestaurantsUploadKind.APPLICATION_DOCUMENT) {
                "PDF, JPEG, PNG, or WEBP"
            } else {
                "JPEG, PNG, or WEBP"
            }
            return Result.failure(
                RestaurantsFailure.Validation(
                    "invalid_upload_mime_type",
                    "Unsupported file type. Choose a $expectedDesc file.",
                ),
            )
        }
        return Result.success(mapped)
    }

    fun prepareSelection(
        kind: RestaurantsUploadKind,
        fileName: String?,
        reportedMime: String?,
        byteSize: Long?,
        openStream: () -> InputStream,
    ): Result<RestaurantsBinaryUploadSource> {
        val mime = normalizeMimeType(kind, reportedMime, fileName)
            .getOrElse { return Result.failure(it) }
        val ext = mimeToDefaultExtension[mime] ?: "bin"
        val resolvedName = fileName?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (kind == RestaurantsUploadKind.APPLICATION_DOCUMENT) "document.$ext" else "image.$ext"
        val resolvedSize = byteSize ?: -1L
        val candidate = RestaurantsBinaryUploadSource(
            fileName = resolvedName,
            mimeType = mime,
            byteSize = resolvedSize,
            openStream = openStream,
        )
        return runCatching {
            RestaurantsUploadValidator.validateMetadata(kind, candidate)
            candidate
        }
    }

    fun fromContentUri(
        contentResolver: ContentResolver,
        uri: Uri,
        kind: RestaurantsUploadKind,
    ): Result<RestaurantsBinaryUploadSource> {
        var displayName: String? = null
        var queriedSize: Long? = null

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                    displayName = cursor.getString(nameIdx)
                }
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    queriedSize = cursor.getLong(sizeIdx)
                }
            }
        }

        val resolvedSize = queriedSize?.takeIf { it > 0L } ?: runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.length.takeIf { it > 0L }
            }
        }.getOrNull()

        val reportedMime = contentResolver.getType(uri)
        return prepareSelection(
            kind = kind,
            fileName = displayName ?: uri.lastPathSegment,
            reportedMime = reportedMime,
            byteSize = resolvedSize,
            openStream = {
                contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Unable to open selected file stream.")
            },
        )
    }

    fun formatByteSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return ""
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        } else {
            String.format(Locale.US, "%.1f MB", kb / 1024.0)
        }
    }
}

class RestaurantsUploadPickerHandle internal constructor(
    private val onLaunchTarget: (RestaurantsPendingPickerTarget) -> Unit,
) {
    fun pickApplicationDocument(applicationId: String, docType: String, label: String) {
        onLaunchTarget(RestaurantsPendingPickerTarget.ApplicationDocument(applicationId, docType, label))
    }

    fun pickApplicationMedia(application: CenterApplicationUi, slot: RestaurantMediaSlot) {
        onLaunchTarget(RestaurantsPendingPickerTarget.ApplicationMedia(application, slot))
    }

    fun pickRestaurantMedia(restaurantId: String, slot: RestaurantMediaSlot, description: String? = null) {
        onLaunchTarget(RestaurantsPendingPickerTarget.RestaurantMedia(restaurantId, slot, description))
    }

    fun pickMenuItemPhoto(restaurantId: String, menuItemId: String, itemName: String) {
        onLaunchTarget(RestaurantsPendingPickerTarget.MenuItemPhoto(restaurantId, menuItemId, itemName))
    }
}

@Composable
fun rememberRestaurantsUploadPicker(
    onFileSelected: (RestaurantsPendingPickerTarget, RestaurantsBinaryUploadSource) -> Unit,
    onSelectionFailed: (RestaurantsPendingPickerTarget, String) -> Unit,
    onPickerCancelled: () -> Unit = {},
): RestaurantsUploadPickerHandle {
    val context = LocalContext.current
    var pendingTarget by remember { mutableStateOf<RestaurantsPendingPickerTarget?>(null) }

    fun handlePickerUri(uri: Uri?) {
        val target = pendingTarget
        pendingTarget = null
        if (uri == null || target == null) {
            onPickerCancelled()
            return
        }
        val resolved = RestaurantsPickerMetadataHelper.fromContentUri(
            contentResolver = context.contentResolver,
            uri = uri,
            kind = target.uploadKind,
        )
        resolved.onSuccess { source ->
            onFileSelected(target, source)
        }.onFailure { err ->
            val msg = (err as? RestaurantsFailure)?.userMessage
                ?: err.message
                ?: "Selected file could not be prepared for upload."
            onSelectionFailed(target, msg)
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> handlePickerUri(uri) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> handlePickerUri(uri) }

    return remember(documentLauncher, photoLauncher) {
        RestaurantsUploadPickerHandle { target ->
            pendingTarget = target
            when (target.uploadKind) {
                RestaurantsUploadKind.APPLICATION_DOCUMENT ->
                    documentLauncher.launch(RestaurantsPickerMetadataHelper.DOCUMENT_PICKER_MIME_TYPES)
                RestaurantsUploadKind.PUBLIC_MEDIA ->
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
            }
        }
    }
}
