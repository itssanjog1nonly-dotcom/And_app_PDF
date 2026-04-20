// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/repository/BookmarkRepository.kt
package com.sanjog.pdfscrollreader.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sanjog.pdfscrollreader.data.model.BookmarkEntry

class BookmarkRepository(
    context: Context
) {
    private val sharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, MutableList<BookmarkEntry>>>() {}.type

    fun add(entry: BookmarkEntry) {
        val data = readMap()
        val list = data.getOrPut(entry.pdfPath) { mutableListOf() }
        list.add(entry)
        writeMap(data)
    }

    fun getAll(pdfPath: String): List<BookmarkEntry> {
        return readMap()[pdfPath].orEmpty().sortedByDescending { it.timestamp }
    }

    fun delete(entry: BookmarkEntry) {
        val data = readMap()
        val list = data[entry.pdfPath].orEmpty().toMutableList()
        val updated = list.filterNot {
            it.pageNumber == entry.pageNumber &&
                it.timestamp == entry.timestamp &&
                it.note == entry.note
        }.toMutableList()
        if (updated.isEmpty()) {
            data.remove(entry.pdfPath)
        } else {
            data[entry.pdfPath] = updated
        }
        writeMap(data)
    }

    fun clear(pdfPath: String) {
        val data = readMap()
        data.remove(pdfPath)
        writeMap(data)
    }

    private fun readMap(): MutableMap<String, MutableList<BookmarkEntry>> {
        val json = sharedPreferences.getString(KEY_BOOKMARKS_JSON, null) ?: return mutableMapOf()
        return try {
            gson.fromJson(json, mapType) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun writeMap(map: MutableMap<String, MutableList<BookmarkEntry>>) {
        sharedPreferences.edit().putString(KEY_BOOKMARKS_JSON, gson.toJson(map)).apply()
    }

    private companion object {
        const val PREFS_NAME = "pdfscrollreader_prefs"
        const val KEY_BOOKMARKS_JSON = "bookmarks_json"
    }
}
