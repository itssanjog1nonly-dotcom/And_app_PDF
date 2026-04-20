// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/repository/SectionBookmarkRepository.kt
package com.sanjog.pdfscrollreader.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sanjog.pdfscrollreader.data.model.SectionBookmark

class SectionBookmarkRepository(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, MutableList<SectionBookmark>>>() {}.type

    fun add(bookmark: SectionBookmark) {
        val map = readMap()
        val list = map.getOrPut(bookmark.pdfHash) { mutableListOf() }
        list.add(bookmark)
        writeMap(map)
    }

    fun getAll(pdfHash: String): List<SectionBookmark> {
        return readMap()[pdfHash].orEmpty().sortedBy { it.pageNumber }
    }

    fun delete(id: String) {
        val map = readMap()
        val updated = map.mapValues { (_, value) ->
            value.filterNot { it.id == id }.toMutableList()
        }.toMutableMap()
        writeMap(updated)
    }

    fun update(bookmark: SectionBookmark) {
        val map = readMap()
        val list = map[bookmark.pdfHash].orEmpty().toMutableList()
        val index = list.indexOfFirst { it.id == bookmark.id }
        if (index >= 0) {
            list[index] = bookmark
            map[bookmark.pdfHash] = list
            writeMap(map)
        }
    }

    fun clear(pdfHash: String) {
        val map = readMap()
        map.remove(pdfHash)
        writeMap(map)
    }

    private fun readMap(): MutableMap<String, MutableList<SectionBookmark>> {
        val json = prefs.getString(KEY_SECTION_BOOKMARKS_JSON, null) ?: return mutableMapOf()
        return try {
            gson.fromJson(json, mapType) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun writeMap(map: MutableMap<String, MutableList<SectionBookmark>>) {
        prefs.edit().putString(KEY_SECTION_BOOKMARKS_JSON, gson.toJson(map)).apply()
    }

    private companion object {
        const val PREFS_NAME = "pdfscrollreader_prefs"
        const val KEY_SECTION_BOOKMARKS_JSON = "section_bookmarks_json"
    }
}
