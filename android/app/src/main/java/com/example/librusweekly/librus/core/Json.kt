package com.example.librusweekly.librus.core

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/// Lenient JSON reader mirroring the Swift `JSON` wrapper: `getString`/`getLong`/`getJsonObject`
/// helpers that convert freely between numbers and numeric strings.
sealed class Json {
    object Null : Json()
    data class Bool(val value: Boolean) : Json()
    data class Num(val value: Double) : Json()
    data class Str(val value: String) : Json()
    data class Arr(val value: List<Json>) : Json()
    data class Obj(val value: Map<String, Json>) : Json()

    operator fun get(key: String): Json = when (this) {
        is Obj -> value[key] ?: Null
        else -> Null
    }

    val string: String?
        get() = when (this) {
            is Str -> value
            is Num -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            is Bool -> value.toString()
            else -> null
        }

    val int: Int?
        get() = when (this) {
            is Num -> value.toInt()
            is Str -> value.trim().toIntOrNull()
            is Bool -> if (value) 1 else 0
            else -> null
        }

    val long: Long?
        get() = when (this) {
            is Num -> value.toLong()
            is Str -> value.trim().toLongOrNull()
            else -> null
        }

    val double: Double?
        get() = when (this) {
            is Num -> value
            is Str -> value.replace(',', '.').toDoubleOrNull()
            else -> null
        }

    val bool: Boolean?
        get() = when (this) {
            is Bool -> value
            is Num -> value != 0.0
            is Str -> value.lowercase() in listOf("1", "true")
            else -> null
        }

    val array: List<Json>
        get() = (this as? Arr)?.value ?: emptyList()

    val obj: Map<String, Json>?
        get() = (this as? Obj)?.value

    val isNull: Boolean get() = this is Null

    /// `{"Id": 123, "Url": "..."}` reference → 123.
    val refId: Int? get() = this["Id"].int

    companion object {
        fun parse(raw: String): Json = of(JSONTokener(raw).nextValue())

        private fun of(any: Any?): Json = when (any) {
            null, JSONObject.NULL -> Null
            is Boolean -> Bool(any)
            is Int -> Num(any.toDouble())
            is Long -> Num(any.toDouble())
            is Double -> Num(any)
            is Number -> Num(any.toDouble())
            is String -> Str(any)
            is JSONArray -> Arr(List(any.length()) { of(any.get(it)) })
            is JSONObject -> {
                val map = LinkedHashMap<String, Json>()
                val keys = any.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = of(any.get(k))
                }
                Obj(map)
            }
            else -> Null
        }
    }
}
