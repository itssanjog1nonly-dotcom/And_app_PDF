package com.sanjog.pdfscrollreader.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.sanjog.pdfscrollreader.data.model.AnnotationData
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object AnnotationSerializer {
    private const val TAG = "AnnotationSerializer"
    
    // Configured Gson to handle complex generic types securely
    private val gson: Gson = GsonBuilder().create()

    private fun getFile(context: Context, pdfHash: String): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$pdfHash.json")
    }

    fun saveToFile(context: Context, data: AnnotationData) {
        try {
            val file = getFile(context, data.pdfPath)
            val json = gson.toJson(data)
            FileWriter(file).use { writer ->
                writer.write(json)
            }
            Log.d(TAG, "Successfully saved annotations to ${file.absolutePath}. Size: ${json.length} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to save annotations", e)
        }
    }

    fun loadFromFile(context: Context, pdfHash: String): AnnotationData? {
        try {
            val file = getFile(context, pdfHash)
            if (!file.exists()) {
                Log.w(TAG, "No annotation file found at ${file.absolutePath}")
                return null
            }

            FileReader(file).use { reader ->
                // Use explicit TypeToken to force Map<Int, ...> instead of Map<String, ...>
                val type = object : TypeToken<AnnotationData>() {}.type
                val data: AnnotationData? = gson.fromJson(reader, type)
                
                if (data != null) {
                    Log.d(TAG, "Successfully loaded annotations from ${file.absolutePath}")
                    return data
                } else {
                    Log.e(TAG, "Gson returned null during deserialization!")
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR: Failed to load annotations", e)
            return null
        }
    }

    fun deleteFile(context: Context, pdfHash: String) {
        try {
            val file = getFile(context, pdfHash)
            if (file.exists() && file.delete()) {
                Log.d(TAG, "Deleted annotation file: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete annotation file", e)
        }
    }
}
