package com.charles.ollama.client.data.database.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StringListConverter {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return if (value == null || value.isEmpty()) {
            null
        } else {
            gson.toJson(value)
        }
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return if (value == null || value.isBlank()) {
            null
        } else {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, listType)
        }
    }
}

