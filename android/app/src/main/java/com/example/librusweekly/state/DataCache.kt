package com.example.librusweekly.state

import android.content.Context
import com.example.librusweekly.librus.models.StudentData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.Date
import java.util.UUID

/// Last downloaded data per child, kept on internal storage so the app opens instantly
/// and works offline. Changes are stored next to the data so the NEW badges survive restarts.
class DataCache(context: Context) {
    private val dir: File = File(context.filesDir, "cache").apply { mkdirs() }
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Date::class.java, DateMillisAdapter())
        .create()

    fun loadData(id: UUID): StudentData? = read(dataFile(id), StudentData::class.java)
    fun saveData(id: UUID, data: StudentData) = write(dataFile(id), data)

    fun loadChanges(id: UUID): UpdateChanges? = read(changesFile(id), UpdateChanges::class.java)
    fun saveChanges(id: UUID, changes: UpdateChanges) = write(changesFile(id), changes)

    fun remove(id: UUID) {
        dataFile(id).delete()
        changesFile(id).delete()
    }

    fun removeAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun dataFile(id: UUID) = File(dir, "$id.json")
    private fun changesFile(id: UUID) = File(dir, "$id.changes.json")

    private fun <T> read(file: File, cls: Class<T>): T? =
        try { if (file.exists()) gson.fromJson(file.readText(), cls) else null }
        catch (e: Exception) { null }

    private fun write(file: File, value: Any) =
        try { file.writeText(gson.toJson(value)) }
        catch (e: Exception) { /* best-effort cache */ }
}

/// Dates round-trip as millis — Gson's default Date serialisation uses the device locale, which
/// breaks on parse when the device switches language between writes.
private class DateMillisAdapter : com.google.gson.JsonSerializer<Date>, com.google.gson.JsonDeserializer<Date> {
    override fun serialize(src: Date, type: java.lang.reflect.Type, ctx: com.google.gson.JsonSerializationContext) =
        com.google.gson.JsonPrimitive(src.time)

    override fun deserialize(json: com.google.gson.JsonElement, type: java.lang.reflect.Type, ctx: com.google.gson.JsonDeserializationContext): Date =
        Date(json.asLong)
}
