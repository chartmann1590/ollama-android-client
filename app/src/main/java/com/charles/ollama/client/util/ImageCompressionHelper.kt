package com.charles.ollama.client.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ImageCompressionHelper {
    // Maximum dimensions for images to prevent SQLite CursorWindow overflow
    // SQLite CursorWindow is typically 2MB, but we need to account for base64 encoding (+33%)
    // and multiple images per message. Limit to ~200KB per image (becomes ~267KB after base64)
    private const val MAX_IMAGE_DIMENSION = 800 // Max width or height in pixels (reduced from 1024)
    private const val MAX_IMAGE_SIZE_KB = 200 // Max size in KB (before base64 encoding, reduced from 500)
    private const val COMPRESSION_QUALITY = 75 // JPEG quality (0-100, reduced from 85)
    
    /**
     * Compresses and resizes an image to a reasonable size for storage in SQLite.
     * Returns base64 encoded string of the compressed image.
     */
    fun compressAndEncodeImage(imageBytes: ByteArray): String {
        // Decode the image
        val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalArgumentException("Could not decode image")
        
        // Calculate scaling to fit within MAX_IMAGE_DIMENSION
        val scale = min(
            1.0f,
            min(
                MAX_IMAGE_DIMENSION.toFloat() / originalBitmap.width,
                MAX_IMAGE_DIMENSION.toFloat() / originalBitmap.height
            )
        )
        
        // Resize if needed
        val scaledBitmap = if (scale < 1.0f) {
            val newWidth = (originalBitmap.width * scale).toInt()
            val newHeight = (originalBitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } else {
            originalBitmap
        }
        
        // Recycle original if we created a scaled version
        if (scaledBitmap != originalBitmap) {
            originalBitmap.recycle()
        }
        
        // Compress to JPEG with quality setting
        val outputStream = ByteArrayOutputStream()
        var quality = COMPRESSION_QUALITY
        var compressedBytes: ByteArray
        
        do {
            outputStream.reset()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedBytes = outputStream.toByteArray()
            
            // If still too large, reduce quality
            if (compressedBytes.size > MAX_IMAGE_SIZE_KB * 1024 && quality > 50) {
                quality -= 10
            } else {
                break
            }
        } while (quality > 50)
        
        // Clean up
        scaledBitmap.recycle()
        outputStream.close()
        
        // Log compression results
        val originalSizeKB = imageBytes.size / 1024
        val compressedSizeKB = compressedBytes.size / 1024
        android.util.Log.d("ImageCompressionHelper", "Compressed image: ${originalSizeKB}KB -> ${compressedSizeKB}KB (quality=$quality, dimensions=${scaledBitmap.width}x${scaledBitmap.height})")
        
        // Encode to base64
        val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        val base64SizeKB = base64.length / 1024
        android.util.Log.d("ImageCompressionHelper", "Base64 encoded size: ${base64SizeKB}KB")
        
        return base64
    }
}

