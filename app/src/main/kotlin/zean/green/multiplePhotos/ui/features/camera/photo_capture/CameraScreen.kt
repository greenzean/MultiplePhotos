package zean.green.multiplePhotos.ui.features.camera.photo_capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zean.green.multiplePhotos.core.utils.rotateBitmap
import zean.green.multiplePhotos.ui.theme.Black
import zean.green.multiplePhotos.ui.theme.LightGray
import zean.green.multiplePhotos.ui.theme.MultiplePhotosTheme
import zean.green.multiplePhotos.ui.theme.White

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = koinViewModel()
) {
    val cameraState: CameraState by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues: PaddingValues ->
        CameraContent(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            onPhotoCaptured = viewModel::storePhotoInGallery,
            capturedPhotos = cameraState.capturedImages,
            onPhotoRemoved = viewModel::removePhotoAtIndex
        )
    }

}

@Composable
private fun CameraContent(
    modifier: Modifier = Modifier,
    onPhotoCaptured: (Bitmap) -> Unit,
    capturedPhotos: List<Bitmap> = emptyList(),
    onPhotoRemoved: (Int) -> Unit = {}
) {
    val context = LocalContext.current

    val imageCaptureUseCase = remember { ImageCapture.Builder().build() }

    CameraPreview(imageCaptureUseCase = imageCaptureUseCase)

    Box(
        modifier = modifier
    ) {
        CameraShutterButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            onClick = {
                captureImage(
                    context,
                    imageCaptureUseCase,
                    onPhotoCaptured
                )
            }
        )
        PhotosPreview(
            modifier = Modifier
                .align(alignment = Alignment.BottomStart)
                .padding(bottom = 96.dp),
            capturedPhotos = capturedPhotos,
            onPhotoRemoved = onPhotoRemoved
        )
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCaptureUseCase: ImageCapture
) {
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoomLevel by remember { mutableFloatStateOf(0.0f) }

    val previewUseCase = remember { androidx.camera.core.Preview.Builder().build() }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val localContext = LocalContext.current

    fun rebindCameraProvider() {
        cameraProvider?.let { cameraProvider ->
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner = localContext as LifecycleOwner,
                cameraSelector = cameraSelector,
                previewUseCase, imageCaptureUseCase
            )
            cameraControl = camera.cameraControl
        }
    }

    LaunchedEffect(Unit) {
        cameraProvider = ProcessCameraProvider.awaitInstance(localContext)
        rebindCameraProvider()
    }

    LaunchedEffect(lensFacing) {
        rebindCameraProvider()
    }

    LaunchedEffect(zoomLevel) {
        cameraControl?.setLinearZoom(zoomLevel)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }.also {
                previewUseCase.surfaceProvider = it.surfaceProvider
                rebindCameraProvider()
            }
        }
    )
}

@Composable
private fun CameraShutterButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.92f else 1f
    Surface(
        modifier = modifier
            .size(72.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = White,
        tonalElevation = if (isPressed) 2.dp else 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(LightGray, CircleShape)
            )
        }
    }
}

private fun captureImage(
    localContext: Context,
    imageCaptureUseCase: ImageCapture,
    onPhotoCaptured: (Bitmap) -> Unit
) {

    imageCaptureUseCase.takePicture(
        ContextCompat.getMainExecutor(localContext),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val correctedBitmap: Bitmap = image
                    .toBitmap()
                    .rotateBitmap(image.imageInfo.rotationDegrees)

                onPhotoCaptured(correctedBitmap)
                image.close()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraContent", "Error capturing image", exception)
            }
        }
    )
}

@Composable
private fun PhotosPreview(
    modifier: Modifier = Modifier,
    capturedPhotos: List<Bitmap>,
    onPhotoRemoved: (Int) -> Unit = {}
) {
    LazyRow(
        modifier = modifier.padding(top = 16.dp, start = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        capturedPhotos.forEachIndexed { index, photo ->
            item {
                val capturedPhoto = remember(photo.hashCode()) { photo.asImageBitmap() }

                Box(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier
                            .size(96.dp, 128.dp)
                            .padding(4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = MaterialTheme.shapes.large
                    ) {

                        Image(
                            modifier = Modifier.fillMaxSize(),
                            bitmap = capturedPhoto,
                            contentDescription = "Captured photo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                    RemovePhotoButton(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = { onPhotoRemoved(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RemovePhotoButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove photo",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Preview
@Composable
private fun Preview_CameraContent() {
    MultiplePhotosTheme {
        fun createSampleBitmap(): Bitmap {
            val bmp = createBitmap(96, 128)
            val canvas = Canvas(bmp)
            val paint = Paint().apply { color = Color.CYAN }
            canvas.drawRect(0f, 0f, 128f, 128f, paint)
            paint.color = Color.BLACK
            paint.textSize = 32f
            canvas.drawText("Dem", 16f, 64f, paint)
            return bmp
        }

        val samplePhotos = List(4) { createSampleBitmap() }
        CameraContent(
            onPhotoCaptured = {},
            capturedPhotos = samplePhotos,
            onPhotoRemoved = {}
        )
    }
}
