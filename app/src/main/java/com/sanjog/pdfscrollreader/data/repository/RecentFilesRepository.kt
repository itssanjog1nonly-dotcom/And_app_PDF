// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/repository/RecentFilesRepository.kt
package com.sanjog.pdfscrollreader.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sanjog.pdfscrollreader.data.model.RecentFile

class RecentFilesRepository(
    context: Context
) {
    private val sharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<RecentFile>>() {}.type

    fun add(file: RecentFile) {
        val current = getMutable()
        val filtered = current.filterNot { it.pdfPath == file.pdfPath }.toMutableList()
        filtered.add(0, file.copy(lastOpened = System.currentTimeMillis()))
        val capped = filtered.take(MAX_RECENT_FILES).toMutableList()
        write(capped)
    }

    fun getAll(): List<RecentFile> {
        return getMutable().sortedByDescending { it.lastOpened }
    }

    fun remove(pdfPath: String) {
        val updated = getMutable().filterNot { it.pdfPath == pdfPath }.toMutableList()
        write(updated)
    }

    fun clear() {
        sharedPreferences.edit().remove(KEY_RECENT_FILES_JSON).apply()
    }

    private fun getMutable(): MutableList<RecentFile> {
        val json = sharedPreferences.getString(KEY_RECENT_FILES_JSON, null) ?: return mutableListOf()
        return try {
            gson.fromJson(json, listType) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun write(files: MutableList<RecentFile>) {
        sharedPreferences.edit().putString(KEY_RECENT_FILES_JSON, gson.toJson(files)).apply()
    }

    private companion object {
        const val PREFS_NAME = "pdfscrollreader_prefs"
        const val KEY_RECENT_FILES_JSON = "recent_files_json"
        const val MAX_RECENT_FILES = 20
    }
}
