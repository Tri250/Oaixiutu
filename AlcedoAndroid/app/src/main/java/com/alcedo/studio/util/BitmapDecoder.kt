package com.alcedo.studio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

object BitmapDecoder {

    fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return decodeSampledBitmap(ContextProvider.context, path, reqWidth, reqHeight)
    }

    fun decodeSampledBitmap(context: Context, path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val uri = Uri.parse(path)
        val isContentUri = path.startsWith("content://")

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        if (isContentUri) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            BitmapFactory.decodeFile(path, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        return if (isContentUri) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            BitmapFactory.decodeFile(path, options)
        }
    }

    fun decodeJustBounds(path: String): Pair<Int, Int> {
        return decodeJustBounds(ContextProvider.context, path)
    }

    fun decodeJustBounds(context: Context, path: String): Pair<Int, Int> {
        val uri = Uri.parse(path)
        val isContentUri = path.startsWith("content://")

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        if (isContentUri) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            BitmapFactory.decodeFile(path, options)
        }

        return Pair(options.outWidth, options.outHeight)
    }

    fun decodeBitmap(path: String, options: BitmapFactory.Options? = null): Bitmap? {
        return decodeBitmap(ContextProvider.context, path, options)
    }

    fun decodeBitmap(context: Context, path: String, options: BitmapFactory.Options? = null): Bitmap? {
        val uri = Uri.parse(path)
        val isContentUri = path.startsWith("content://")

        return if (isContentUri) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            BitmapFactory.decodeFile(path, options)
        }
    }

    private fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (outHeight > reqHeight || outWidth > reqWidth) {
            val halfHeight = outHeight / 2
            val halfWidth = outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}