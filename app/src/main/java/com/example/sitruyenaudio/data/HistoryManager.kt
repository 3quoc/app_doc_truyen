package com.example.sitruyenaudio.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("reader_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getStoryId(url: String): String {
        val parts = url.split("?")[0].split("#")[0].split("/")
        if (parts.size >= 5) {
            return parts.take(5).joinToString("/")
        }
        return url
    }

    fun addHistory(item: HistoryItem) {
        val currentList = getHistory().toMutableList()
        
        val itemStoryId = getStoryId(item.url)
        // Remove if exists to update to the top
        currentList.removeAll { getStoryId(it.url) == itemStoryId }
        
        // Add to top
        currentList.add(0, item)
        
        // Keep max 100 items
        if (currentList.size > 100) {
            currentList.removeAt(currentList.size - 1)
        }
        
        prefs.edit().putString("history_list", gson.toJson(currentList)).apply()
    }
    
    fun clearHistory() {
        prefs.edit().remove("history_list").apply()
    }
}
