package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.data.model.MessageType
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaRepository(private val context: Context) {

    private val storage: FirebaseStorage? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseStorage.getInstance() else null
        } catch (e: Exception) {
            Log.w("MediaRepository", "Firebase Storage not initialized", e)
            null
        }
    }

    suspend fun saveAndPrepareAvatar(uri: Uri): File = withContext(Dispatchers.IO) {
        val avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val avatarFile = File(avatarsDir, "avatar_${System.currentTimeMillis()}.jpg")

        try {
            val bitmap = decodeSampledBitmapFromUri(uri, 800, 800)
            if (bitmap != null) {
                val rotatedBitmap = rotateBitmapIfRequired(uri, bitmap)
                val maxDim = 800
                val ratio = (rotatedBitmap.width.toFloat() / rotatedBitmap.height.toFloat())
                val targetWidth: Int
                val targetHeight: Int
                if (rotatedBitmap.width > rotatedBitmap.height) {
                    targetWidth = if (rotatedBitmap.width > maxDim) maxDim else rotatedBitmap.width
                    targetHeight = (targetWidth / ratio).toInt().coerceAtLeast(1)
                } else {
                    targetHeight = if (rotatedBitmap.height > maxDim) maxDim else rotatedBitmap.height
                    targetWidth = (targetHeight * ratio).toInt().coerceAtLeast(1)
                }

                val resized = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)
                FileOutputStream(avatarFile).use { out ->
                    resized.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                }
                if (resized != rotatedBitmap) resized.recycle()
                if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
                bitmap.recycle()
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(avatarFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error preparing avatar", e)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(avatarFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        avatarFile
    }

    suspend fun compressAndPrepareImage(uri: Uri): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "compressed_media").apply { mkdirs() }
        val compressedFile = File(cacheDir, "img_${UUID.randomUUID()}.jpg")

        try {
            val bitmap = decodeSampledBitmapFromUri(uri, 1280, 1280)
            if (bitmap != null) {
                val rotatedBitmap = rotateBitmapIfRequired(uri, bitmap)
                val maxDim = 1280
                val ratio = (rotatedBitmap.width.toFloat() / rotatedBitmap.height.toFloat())
                val targetWidth: Int
                val targetHeight: Int
                if (rotatedBitmap.width > rotatedBitmap.height) {
                    targetWidth = if (rotatedBitmap.width > maxDim) maxDim else rotatedBitmap.width
                    targetHeight = (targetWidth / ratio).toInt().coerceAtLeast(1)
                } else {
                    targetHeight = if (rotatedBitmap.height > maxDim) maxDim else rotatedBitmap.height
                    targetWidth = (targetHeight * ratio).toInt().coerceAtLeast(1)
                }

                val resized = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)
                FileOutputStream(compressedFile).use { outStream ->
                    resized.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
                    outStream.flush()
                }
                if (resized != rotatedBitmap) resized.recycle()
                if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
                bitmap.recycle()
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(compressedFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "compressAndPrepareImage failed, copying stream directly", e)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(compressedFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        compressedFile
    }

    /**
     * A small JPEG preview of an already-prepared image (longest side [maxDim] px), shown in chat
     * bubbles and the gallery instead of the full photo. Null if the image can't be decoded.
     */
    suspend fun createThumbnail(image: File, maxDim: Int = 720): File? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(image.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= maxDim && bounds.outHeight / (sampleSize * 2) >= maxDim) {
                sampleSize *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                image.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return@withContext null
            val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
            val preview = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            val thumbnail = File(image.parentFile, "thumb_${image.name}")
            FileOutputStream(thumbnail).use { out -> preview.compress(Bitmap.CompressFormat.JPEG, 72, out) }
            if (preview !== decoded) preview.recycle()
            decoded.recycle()
            thumbnail
        } catch (e: Exception) {
            Log.w("MediaRepository", "Thumbnail creation failed", e)
            null
        }
    }

    /**
     * A picked video or file (content:// link from the picker, or file://) as a file in the cache,
     * so it can be uploaded. Keeps the extension, so storage knows what kind of file it is.
     */
    suspend fun copyToCache(uri: Uri, name: String? = null): File = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            uri.path?.let(::File)?.takeIf { it.exists() }?.let { return@withContext it }
        }
        val extension = name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
            ?: context.contentResolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "bin"
        val dir = File(context.cacheDir, "outgoing_media").apply { mkdirs() }
        val target = File(dir, "media_${System.currentTimeMillis()}.$extension")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("Can't open $uri")
        input.use { source -> FileOutputStream(target).use { source.copyTo(it) } }
        target
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Failed to decode bitmap from URI", e)
            null
        }
    }

    private fun rotateBitmapIfRequired(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun uploadFile(
        file: File,
        type: MessageType,
        coupleId: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val storageInstance = storage
            if (storageInstance != null) {
                val folder = when (type) {
                    MessageType.IMAGE -> "images"
                    MessageType.VIDEO -> "videos"
                    MessageType.AUDIO -> "audio"
                    MessageType.DOCUMENT -> "documents"
                    else -> "others"
                }
                val storageRef = storageInstance.reference
                    .child("couples")
                    .child(coupleId)
                    .child(folder)
                    .child("${System.currentTimeMillis()}_${file.name}")

                val uploadTask = storageRef.putFile(Uri.fromFile(file))

                uploadTask.addOnProgressListener { taskSnapshot ->
                    val total = taskSnapshot.totalByteCount
                    if (total > 0) {
                        val progress = (taskSnapshot.bytesTransferred.toFloat() / total.toFloat())
                        onProgress(progress)
                    }
                }

                try {
                    uploadTask.await()
                } catch (e: CancellationException) {
                    // Cancelled from the chat: stop the upload itself as well
                    uploadTask.cancel()
                    throw e
                }
                val downloadUrl = storageRef.downloadUrl.await().toString()
                Result.success(downloadUrl)
            } else {
                fallbackToInlineOrLocal(file, type, onProgress)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("MediaRepository", "Failed to upload file to Cloud Storage, using robust data fallback", e)
            fallbackToInlineOrLocal(file, type, onProgress)
        }
    }

    private fun fallbackToInlineOrLocal(file: File, type: MessageType, onProgress: (Float) -> Unit): Result<String> {
        onProgress(1.0f)
        return try {
            if (type == MessageType.AUDIO && file.exists() && file.length() < 750 * 1024) {
                // High compression voice note: encode as base64 data URI so recipient receives it directly through Firestore
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                Result.success("data:audio/m4a;base64,$base64")
            } else if (type == MessageType.IMAGE && file.exists() && file.length() < 500 * 1024) {
                // Small compressed image: encode as base64 data URI
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                Result.success("data:image/jpeg;base64,$base64")
            } else {
                Result.success(Uri.fromFile(file).toString())
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Fallback encoding failed", e)
            Result.success(Uri.fromFile(file).toString())
        }
    }
}
