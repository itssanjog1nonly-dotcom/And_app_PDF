// FILE: app/src/main/java/com/sanjog/pdfscrollreader/util/AnnotationSerializer.kt
package com.sanjog.pdfscrollreader.util

import android.content.Context
import com.google.gson.Gson
import com.sanjog.pdfscrollreader.data.model.AnnotationData
import java.io.File
import java.security.MessageDigest

object AnnotationSerializer {
    private val gson: Gson = Gson()

    fun toJson(data: AnnotationData): String {
        return gson.toJson(data)
    }

    fun fromJson(json: String): AnnotationData? {
        return try {
            gson.fromJson(json, AnnotationData::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun saveToFile(context: Context, data: AnnotationData) {
        val pdfHash = md5(data.pdfPath)
        val annotationsDir = File(context.filesDir, "annotations")
        if (!annotationsDir.exists()) {
            annotationsDir.mkdirs()
        }
        val file = File(annotationsDir, "$pdfHash.json")
        file.writeText(toJson(data))
    }

    fun loadFromFile(context: Context, pdfHash: String): AnnotationData? {
        val file = File(File(context.filesDir, "annotations"), "$pdfHash.json")
        if (!file.exists()) {
            return null
        }
        return fromJson(file.readText())
    }

    fun deleteFile(context: Context, pdfHash: String) {
        val file = File(File(context.filesDir, "annotations"), "$pdfHash.json")
        if (file.exists()) {
            file.delete()
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
