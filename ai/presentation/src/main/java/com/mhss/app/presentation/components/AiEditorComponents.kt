package com.mhss.app.presentation.components

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.mhss.app.core.util.toBase64
import com.mhss.app.presentation.AiEditorActions
import com.mhss.app.presentation.AiTextAction
import com.mhss.app.ui.R
import com.mhss.app.ui.toUserMessage
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * The AI chip row (summarize, auto format, spelling, camera, gallery) shared by
 * the note and follow-up/well-being editors.
 */
@Composable
fun AiActionsRow(
    onSummarize: () -> Unit,
    onAutoFormat: () -> Unit,
    onCorrectSpelling: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LazyRow(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            GradientIconButton(
                text = stringResource(id = R.string.summarize),
                iconPainter = painterResource(id = R.drawable.ic_summarize),
            ) {
                onSummarize()
                keyboardController?.hide()
            }
        }
        item {
            GradientIconButton(
                text = stringResource(id = R.string.auto_format),
                iconPainter = painterResource(id = R.drawable.ic_auto_format),
            ) {
                onAutoFormat()
                keyboardController?.hide()
            }
        }
        item {
            GradientIconButton(
                text = stringResource(id = R.string.correct_spelling),
                iconPainter = painterResource(id = R.drawable.ic_spelling),
            ) {
                onCorrectSpelling()
                keyboardController?.hide()
            }
        }
        item {
            GradientIconButton(
                text = "",
                iconPainter = painterResource(id = R.drawable.ic_camera),
            ) {
                onCameraClick()
                keyboardController?.hide()
            }
        }
        item {
            GradientIconButton(
                text = "",
                iconPainter = painterResource(id = android.R.drawable.ic_menu_gallery),
            ) {
                onGalleryClick()
                keyboardController?.hide()
            }
        }
    }
}

/**
 * Bottom sheet showing the loading/result/error state of an AI text action.
 * Rendered on top of the editor; [onContentChange] receives the new content when the result replaces or is added to it.
 */
@Composable
fun AiResultOverlay(
    actions: AiEditorActions,
    content: String,
    onContentChange: (String) -> Unit
) {
    val context = LocalContext.current
    val aiState = actions.state
    AnimatedVisibility(
        visible = aiState.showAiSheet,
        enter = slideInVertically(
            initialOffsetY = { it }, animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        ),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(700))
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = interactionSource, indication = null) {
                    actions.dismiss()
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            AiResultSheet(
                loading = aiState.loading,
                result = aiState.result,
                error = aiState.error?.toUserMessage(),
                onCopyClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ai result", aiState.result.toString()))
                    actions.dismiss()
                },
                onReplaceClick = {
                    onContentChange(aiState.result.toString())
                    actions.dismiss()
                },
                onAddToNoteClick = {
                    onContentChange(aiState.result + "\n" + content)
                    actions.dismiss()
                }
            )
        }
    }
}

class OcrLauncher(val onCameraClick: () -> Unit, val onGalleryClick: () -> Unit)

/**
 * Camera / gallery -> crop -> preprocessing -> vision OCR flow. Renders the camera and
 * "processing" dialogs when needed and returns the click handlers for the AI row.
 * Recognized text is prepended to [content] through [onContentChange].
 */
@Composable
fun rememberOcrLauncher(
    actions: AiEditorActions,
    content: String,
    onContentChange: (String) -> Unit
): OcrLauncher {
    val context = LocalContext.current
    val currentContent by rememberUpdatedState(content)
    val currentOnContentChange by rememberUpdatedState(onContentChange)
    var isOcrRunning by rememberSaveable { mutableStateOf(false) }
    var showCameraX by rememberSaveable { mutableStateOf(false) }

    fun newImageUri(): Uri {
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val photo = File.createTempFile("ocr_", ".jpg", imagesDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
    }

    fun runOcrOnUri(uri: Uri) {
        isOcrRunning = true
        try {
            val bmp = loadBitmapCorrectRotation(context, uri)
            val pre = preprocessBitmap(bmp, contrast = 1.25f)
            actions.runVisionOcr(
                base64Image = pre.toBase64(),
                onRecognized = { recognized ->
                    currentOnContentChange(
                        if (currentContent.isBlank()) recognized else "$recognized\n$currentContent"
                    )
                },
                onDone = { isOcrRunning = false },
                onError = { isOcrRunning = false }
            )
        } catch (_: Exception) {
            isOcrRunning = false
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.let { UCrop.getOutput(it) }?.let { runOcrOnUri(it) }
        }
    }

    fun startCrop(source: Uri) {
        val activity = (context as? Activity) ?: return
        val destUri = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg"))
        val options = UCrop.Options().apply {
            setHideBottomControls(false)
            setFreeStyleCropEnabled(true)
            setShowCropGrid(true)
            setCompressionQuality(95)
        }
        val intent = UCrop.of(source, destUri)
            .withOptions(options)
            .withMaxResultSize(4096, 4096)
            .getIntent(activity)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        cropLauncher.launch(intent)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { startCrop(it) } }
    val getContent = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { startCrop(it) } }
    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showCameraX = true }

    if (isOcrRunning) {
        Dialog(onDismissRequest = { }) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.ocr_processing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (showCameraX) {
        CameraXCaptureDialog(
            onDismiss = { showCameraX = false },
            onPhoto = { uri -> startCrop(uri) },
            newImageUri = { newImageUri() }
        )
    }

    return OcrLauncher(
        onCameraClick = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) showCameraX = true
            else requestCameraPermission.launch(Manifest.permission.CAMERA)
        },
        onGalleryClick = {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                getContent.launch("image/*")
            }
        }
    )
}

private fun exifRotationDegrees(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }
}

private fun loadBitmapCorrectRotation(context: Context, uri: Uri): Bitmap {
    val src = if (Build.VERSION.SDK_INT >= 28) {
        val srcImage = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(srcImage) { decoder, _, _ -> decoder.isMutableRequired = true }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val deg = exifRotationDegrees(context, uri)
    if (deg == 0) return src
    val m = Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun preprocessBitmap(src: Bitmap, contrast: Float = 1.25f): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val cm = ColorMatrix().apply {
        setSaturation(0f)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, 0f,
                    0f, contrast, 0f, 0f, 0f,
                    0f, 0f, contrast, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
}

@Composable
private fun CameraXCaptureDialog(
    onDismiss: () -> Unit,
    onPhoto: (Uri) -> Unit,
    newImageUri: () -> Uri
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black),
        ) {
            val context = LocalContext.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp)
            ) {
                Button(
                    shape = CircleShape,
                    onClick = {
                        val ic = imageCapture ?: return@Button
                        val output = ImageCapture.OutputFileOptions.Builder(
                            context.contentResolver,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "MyBrain_${System.currentTimeMillis()}.jpg")
                                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyBrain")
                            }
                        ).build()
                        ic.takePicture(
                            output,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(exception: ImageCaptureException) {
                                    onDismiss()
                                }

                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    onPhoto(outputFileResults.savedUri ?: newImageUri())
                                    onDismiss()
                                }
                            }
                        )
                    }
                ) { Text(stringResource(R.string.take_photo)) }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, shape = CircleShape) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
