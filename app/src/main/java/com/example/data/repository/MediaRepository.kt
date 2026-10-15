package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.MessageType
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
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

    /**
     * A photo ready to send: WebP at quality 70 with its longest side at most 1280 px, about 50-90 KB
     * instead of a camera's 3-5 MB. Turned upright, and without the camera's EXIF data (where and on
     * which phone it was taken). A busy photo that still comes out large goes smaller again; one
     * that's small already is sent as it is.
     */
    suspend fun compressAndPrepareImage(uri: Uri): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "compressed_media").apply { mkdirs() }
        try {
            val originalSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            // Small already (a sent photo saved back, a screenshot): re-encoding would only lose quality
            if (originalSize in 1L..PHOTO_TARGET_BYTES && bounds.outWidth in 1..PHOTO_MAX_DIM && bounds.outHeight in 1..PHOTO_MAX_DIM) {
                return@withContext copyAsIs(uri, cacheDir)
            }

            val bitmap = decodeSampledBitmapFromUri(uri, PHOTO_MAX_DIM, PHOTO_MAX_DIM)
                ?: return@withContext copyAsIs(uri, cacheDir)
            val upright = rotateBitmapIfRequired(uri, bitmap)
            val photo = scaledDown(upright, PHOTO_MAX_DIM)
            val compressedFile = File(cacheDir, "img_${UUID.randomUUID()}.webp")
            writeWebp(photo, PHOTO_QUALITY, compressedFile)
            // A busy photo (leaves, a crowd) comes out bigger: lower quality, then a little smaller
            if (compressedFile.length() > PHOTO_TARGET_BYTES) writeWebp(photo, PHOTO_QUALITY_BUSY, compressedFile)
            if (compressedFile.length() > PHOTO_LIMIT_BYTES) {
                val smaller = scaledDown(photo, PHOTO_BUSY_MAX_DIM)
                writeWebp(smaller, PHOTO_QUALITY_BUSY, compressedFile)
                if (smaller !== photo) smaller.recycle()
            }
            if (photo !== upright) photo.recycle()
            if (upright !== bitmap) upright.recycle()
            bitmap.recycle()
            compressedFile
        } catch (e: Exception) {
            Log.e("MediaRepository", "compressAndPrepareImage failed, sending the photo as it is", e)
            copyAsIs(uri, cacheDir)
        }
    }

    /** The picked file unchanged, named for its own kind (so a small GIF still moves). */
    private fun copyAsIs(uri: Uri, dir: File): File {
        val extension = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: uri.path?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
            ?: "jpg"
        val target = File(dir, "img_${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Couldn't copy the photo", e)
        }
        return target
    }

    /** [bitmap] with its longest side at most [maxDim] px (itself when it's that small already). */
    private fun scaledDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /** Lossy WebP: about a third smaller than a JPEG that looks the same. */
    private fun writeWebp(bitmap: Bitmap, quality: Int, file: File) {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
    }

    /** [file]'s type from its extension (webp, ogg, m4a...), or null when that doesn't say. */
    private fun mimeTypeOf(file: File): String? = when (val extension = file.extension.lowercase()) {
        "webp" -> "image/webp"
        "ogg", "opus" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    /**
     * A small JPEG preview of an already-prepared image (longest side [maxDim] px), shown in chat
     * bubbles and the gallery instead of the full photo. Null if the image can't be decoded.
     */
    suspend fun createThumbnail(image: File, maxDim: Int = 480): File? = withContext(Dispatchers.IO) {
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
            FileOutputStream(thumbnail).use { out -> preview.compress(Bitmap.CompressFormat.JPEG, 68, out) }
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
            // Ensure anonymous authentication so storage.rules allow write
            try {
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
            } catch (authEx: Exception) {
                Log.w("MediaRepository", "Anonymous auth check/sign-in skipped: ${authEx.message}")
            }

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

                // Its type said outright (image/webp, audio/ogg...), so it's served and saved as what it is
                val uploadTask = mimeTypeOf(file)?.let { mime ->
                    storageRef.putFile(Uri.fromFile(file), StorageMetadata.Builder().setContentType(mime).build())
                } ?: storageRef.putFile(Uri.fromFile(file))

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
                Result.success("data:${mimeTypeOf(file) ?: "audio/mp4"};base64,$base64")
            } else if (type == MessageType.IMAGE && file.exists() && file.length() < 500 * 1024) {
                // Small compressed image: encode as base64 data URI
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                Result.success("data:${mimeTypeOf(file) ?: "image/jpeg"};base64,$base64")
            } else if (type == MessageType.IMAGE && file.exists()) {
                // Guaranteed fallback: down to 1080 px WebP so it's small enough to always be delivered
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    val scaled = scaledDown(bmp, PHOTO_BUSY_MAX_DIM)
                    val small = File(file.parentFile ?: context.cacheDir, "inline_${file.nameWithoutExtension}.webp")
                    writeWebp(scaled, PHOTO_QUALITY_BUSY, small)
                    if (scaled !== bmp) scaled.recycle()
                    bmp.recycle()
                    val bytes = small.readBytes()
                    small.delete()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    Result.success("data:image/webp;base64,$base64")
                } else {
                    Result.success(Uri.fromFile(file).toString())
                }
            } else {
                Result.success(Uri.fromFile(file).toString())
            }
        } catch (e: Exception) {
            Log.e("MediaRepository", "Fallback encoding failed", e)
            Result.success(Uri.fromFile(file).toString())
        }
    }

    private companion object {
        /** A sent photo's longest side, in px: plenty for a phone screen. */
        const val PHOTO_MAX_DIM = 1280
        const val PHOTO_QUALITY = 70
        /** What most photos come to (about 50-90 KB); a photo this small already is sent as it is. */
        const val PHOTO_TARGET_BYTES = 100L * 1024L
        /** A busy photo above [PHOTO_TARGET_BYTES] is written again at this quality... */
        const val PHOTO_QUALITY_BUSY = 60
        /** ...and if it's still above this, its longest side goes down to [PHOTO_BUSY_MAX_DIM]. */
        const val PHOTO_LIMIT_BYTES = 140L * 1024L
        const val PHOTO_BUSY_MAX_DIM = 1080
    }
}
