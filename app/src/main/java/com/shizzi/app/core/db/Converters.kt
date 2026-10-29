package com.shizzi.app.core.db

import androidx.room.TypeConverter
import org.json.JSONObject

/** Stores the free-form params map of [com.shizzi.app.core.model.ServerConfig] as JSON text. */
class Converters {

    @TypeConverter
    fun fromParamsMap(map: Map<String, String>): String {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, value)
        }
        return json.toString()
    }

    @TypeConverter
    fun toParamsMap(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val json = JSONObject(raw)
        val result = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = json.optString(key, "")
        }
        return result
    }
}
