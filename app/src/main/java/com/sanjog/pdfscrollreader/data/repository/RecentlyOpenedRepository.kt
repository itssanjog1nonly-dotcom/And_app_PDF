package com.sanjog.pdfscrollreader.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sanjog.pdfscrollreader.data.model.RecentlyOpenedEntry
import com.sanjog.pdfscrollreader.util.toDisplayName

class RecentlyOpenedRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("recently_opened", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "entries"

    fun getAll(): List<RecentlyOpenedEntry> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<RecentlyOpenedEntry>>() {}.type
        return gson.fromJson<List<RecentlyOpenedEntry>>(json, type)
            .sortedByDescending { it.lastOpened }
    }

    fun recordOpen(uri: Uri) {
        val current = getAll().toMutableList()
        val uriString = uri.toString()
        val existingIndex = current.indexOfFirst { it.uri == uriString }
        
        val displayName = uri.toDisplayName(context)
        val now = System.currentTimeMillis()
        
        if (existingIndex != -1) {
            val existing = current.removeAt(existingIndex)
            current.add(0, existing.copy(lastOpened = now, displayName = displayName))
        } else {
            current.add(0, RecentlyOpenedEntry(uriString, displayName, now))
        }
        
        // Keep only top 20
        val limited = current.take(20)
        save(limited)
    }

    fun recordEdit(uri: Uri) {
        val current = getAll().toMutableList()
        val uriString = uri.toString()
        val existingIndex = current.indexOfFirst { it.uri == uriString }
        
        val now = System.currentTimeMillis()
        if (existingIndex != -1) {
            val existing = current.removeAt(existingIndex)
            current.add(0, existing.copy(lastOpened = now, lastModified = now))
        } else {
            val displayName = uri.toDisplayName(context)
            current.add(0, RecentlyOpenedEntry(uriString, displayName, now, now))
        }
        
        save(current.take(20))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun save(entries: List<RecentlyOpenedEntry>) {
        val json = gson.toJson(entries)
        prefs.edit().putString(key, json).apply()
    }
}
