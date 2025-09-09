package zean.green.multiplePhotos.ui.features.camera.photo_capture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import zean.green.multiplePhotos.data.usecases.SavePhotoToGalleryUseCase

data class CameraState(
    val capturedImages: List<Bitmap> = emptyList()
)

@KoinViewModel
class CameraViewModel(
    private val savePhotoToGalleryUseCase: SavePhotoToGalleryUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state = _state.asStateFlow()

    fun storePhotoInGallery(bitmap: Bitmap) {
        viewModelScope.launch {
            savePhotoToGalleryUseCase.call(bitmap)
            updateCapturedPhotoState(bitmap)
        }
    }

    private fun updateCapturedPhotoState(newPhoto: Bitmap) {
        _state.value = _state.value.copy(
            capturedImages = _state.value.capturedImages + newPhoto
        )
    }

    fun removePhotoAtIndex(index: Int) {
        val currentImages = _state.value.capturedImages
        if (index in currentImages.indices) {
            currentImages[index].recycle()
            val updatedImages = currentImages.toMutableList().apply { removeAt(index) }
            _state.value = _state.value.copy(capturedImages = updatedImages)
        }
    }

    override fun onCleared() {
        _state.value.capturedImages.forEach { bitmap ->
            bitmap.recycle()
        }
        super.onCleared()
    }
}
