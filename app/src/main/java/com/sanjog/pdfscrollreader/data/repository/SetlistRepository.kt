// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/repository/SetlistRepository.kt
package com.sanjog.pdfscrollreader.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sanjog.pdfscrollreader.data.model.Setlist
import com.sanjog.pdfscrollreader.data.model.SetlistEntry

class SetlistRepository(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<Setlist>>() {}.type

    fun createSetlist(name: String): Setlist {
        val list = readAll()
        val setlist = Setlist(name = name)
        list.add(setlist)
        writeAll(list)
        return setlist
    }

    fun getAll(): List<Setlist> = readAll().sortedBy { it.createdAt }

    fun getById(id: String): Setlist? = readAll().firstOrNull { it.id == id }

    fun save(setlist: Setlist) {
        val list = readAll()
        val index = list.indexOfFirst { it.id == setlist.id }
        if (index >= 0) list[index] = setlist else list.add(setlist)
        writeAll(list)
    }

    fun delete(id: String) {
        val list = readAll().filterNot { it.id == id }.toMutableList()
        writeAll(list)
    }

    fun rename(id: String, newName: String) {
        val setlist = getById(id) ?: return
        save(setlist.copy(name = newName))
    }

    fun addEntry(setlistId: String, entry: SetlistEntry) {
        val setlist = getById(setlistId) ?: return
        val updatedEntries = setlist.entries.toMutableList().apply {
            add(entry.copy(orderIndex = size))
        }
        save(setlist.copy(entries = updatedEntries))
    }

    fun removeEntry(setlistId: String, entryId: String) {
        val setlist = getById(setlistId) ?: return
        val updatedEntries = setlist.entries
            .filterNot { it.id == entryId }
            .mapIndexed { index, e -> e.copy(orderIndex = index) }
        save(setlist.copy(entries = updatedEntries))
    }

    fun reorderEntries(setlistId: String, fromIndex: Int, toIndex: Int) {
        val setlist = getById(setlistId) ?: return
        val list = setlist.entries.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        save(setlist.copy(entries = list.mapIndexed { index, entry -> entry.copy(orderIndex = index) }))
    }

    fun updateLastPage(setlistId: String, entryId: String, page: Int) {
        val setlist = getById(setlistId) ?: return
        val updatedEntries = setlist.entries.map { entry ->
            if (entry.id == entryId) entry.copy(lastPage = page.coerceAtLeast(0)) else entry
        }
        save(setlist.copy(entries = updatedEntries))
    }

    private fun readAll(): MutableList<Setlist> {
        val json = prefs.getString(KEY_SETLISTS_JSON, null) ?: return mutableListOf()
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writeAll(setlists: MutableList<Setlist>) {
        prefs.edit().putString(KEY_SETLISTS_JSON, gson.toJson(setlists)).apply()
    }

    private companion object {
        const val PREFS_NAME = "pdfscrollreader_prefs"
        const val KEY_SETLISTS_JSON = "setlists_json"
    }
}
