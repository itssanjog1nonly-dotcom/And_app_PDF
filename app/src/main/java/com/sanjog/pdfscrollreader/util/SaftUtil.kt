// FILE: app/src/main/java/com/sanjog/pdfscrollreader/util/SaftUtil.kt
package com.sanjog.pdfscrollreader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.security.MessageDigest

object SaftUtil {
    fun openPdfPicker(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
    }

    fun persistUriPermission(context: Context, uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    fun getFileDisplayName(context: Context, uri: Uri): String {
        return uri.toDisplayName(context)
    }

    fun getPdfHash(context: Context, uri: Uri): String {
        val input = uri.toString()
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
