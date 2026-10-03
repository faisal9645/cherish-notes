package com.example.data.local.notes

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isDone: Boolean = false
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
    val category: String = "Personal",
    val colorHex: String = "#EFF5FF",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val checklistJson: String = "[]",
    val reminderTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun hasActiveReminder(): Boolean = reminderTime != null && reminderTime > System.currentTimeMillis()
    fun isReminderPast(): Boolean = reminderTime != null && reminderTime <= System.currentTimeMillis()

    fun getChecklist(): List<ChecklistItem> {
        if (checklistJson.isBlank() || checklistJson == "[]") return emptyList()
        return try {
            val array = JSONArray(checklistJson)
            val items = mutableListOf<ChecklistItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    ChecklistItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.optString("text", ""),
                        isDone = obj.optBoolean("isDone", false)
                    )
                )
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        fun encodeChecklist(items: List<ChecklistItem>): String {
            val array = JSONArray()
            items.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("text", item.text)
                obj.put("isDone", item.isDone)
                array.put(obj)
            }
            return array.toString()
        }
    }
}
