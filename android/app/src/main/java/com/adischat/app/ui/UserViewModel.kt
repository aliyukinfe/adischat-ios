package com.adischat.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adischat.app.data.model.AppUser
import com.adischat.app.data.repository.AuthRepository
import com.adischat.app.network.CloudflareClient
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Holds the signed-in user's profile + wallet. Scoped to the "main" nav entry,
 * so Home and Profile observe the same live [AppUser].
 */
class UserViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository()

    private val _user = MutableStateFlow<AppUser?>(null)
    val user: StateFlow<AppUser?> = _user

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _uploadingPhoto = MutableStateFlow(false)
    val uploadingPhoto: StateFlow<Boolean> = _uploadingPhoto

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    init { loadUser() }

    fun loadUser() {
        viewModelScope.launch {
        try {
        val loaded = repo.getCurrentAppUser()
        val uid = repo.currentUser?.uid
        if (loaded != null && loaded.uid == uid) _user.value = loaded
        else if (_user.value?.uid != uid) _user.value = null
        if (loaded == null && uid != null) _toast.value = CloudflareClient.CONNECTION_ERROR_MESSAGE
        } catch (e: CancellationException) {
        throw e
        } catch (e: Exception) {
        if (_user.value?.uid != repo.currentUser?.uid) _user.value = null
        _toast.value = CloudflareClient.friendlyErrorMessage(e, "Could not load your profile")
        }
        }
    }

    fun saveProfile(name: String, username: String, dob: String) {
        val current = _user.value ?: return
        _saving.value = true
        viewModelScope.launch {
        val updated = current.copy(
        name     = name.trim().ifBlank { current.name },
        username = username.trim().ifBlank { current.username },
        dob      = dob.ifBlank { current.dob },
        )
        val result = repo.updateUser(updated)
        _saving.value = false
        if (result.isSuccess) {
        _user.value = updated
        _toast.value = "Profile updated!"
        } else {
        val e = result.exceptionOrNull()
        _toast.value = when {
        e is HttpException && e.code() == 409 -> "Username already taken. Please choose another."
        else -> CloudflareClient.friendlyErrorMessage(e, "Failed to save")
        }
        }
        }
    }

    /**
     * Compresses then uploads the picked image. Runs entirely in [viewModelScope]
     * so it survives the edit sheet being dismissed.
     */
    fun uploadPhoto(uri: Uri) {
        if (_uploadingPhoto.value) return
        _uploadingPhoto.value = true
        viewModelScope.launch {
        val bytes = withContext(Dispatchers.IO) { compressImage(uri) }
        if (bytes == null) {
        _uploadingPhoto.value = false
        _toast.value = "Could not read that image"
        return@launch
        }
        val result = repo.uploadProfilePhoto(bytes)
        _uploadingPhoto.value = false
        if (result.isSuccess) {
        _user.value = _user.value?.copy(photoUrl = result.getOrThrow())
        _toast.value = "Photo updated!"
        } else {
        _toast.value = CloudflareClient.friendlyErrorMessage(result.exceptionOrNull(), "Photo upload failed")
        }
        }
    }

    /** Decodes, downscales (longest side <= 512px) and JPEG-compresses an image. */
    private fun compressImage(uri: Uri): ByteArray? {
        return try {
        val resolver = getApplication<Application>().contentResolver
        val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        ?: return null
        val max = 512
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > max) {
        val ratio = max.toFloat() / longest
        Bitmap.createScaledBitmap(
        bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
        )
        } else bitmap
        ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        out.toByteArray()
        }
        } catch (e: Exception) {
        null
        }
    }

    fun clearToast() { _toast.value = null }
}
