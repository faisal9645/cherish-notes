package com.example.ui.chat

enum class ChatExperienceMode(val key: String) {
    NORMAL("normal"),
    PRIVATE("private");

    companion object {
        fun fromKey(key: String): ChatExperienceMode =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}
