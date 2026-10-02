import sys
with open('app/src/main/java/com/example/security/SecurityPreferences.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target1_rn = 'fun hideSecretHistory() {\n        _isSecretHistoryRevealed.value = false\n    }'
if '\r\n' in content:
    target1_rn = target1_rn.replace('\n', '\r\n')

rep1_n = target1_rn + '\n\n    private val _showPreviousChats = MutableStateFlow(isShowPreviousChatsEnabled())\n    val showPreviousChats: StateFlow<Boolean> = _showPreviousChats.asStateFlow()\n\n    fun isShowPreviousChatsEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_PREVIOUS_CHATS, false)\n\n    fun setShowPreviousChatsEnabled(enabled: Boolean) {\n        prefs.edit().putBoolean(KEY_SHOW_PREVIOUS_CHATS, enabled).apply()\n        _showPreviousChats.value = enabled\n    }'
if '\r\n' in content:
    rep1_n = rep1_n.replace('\n', '\r\n')

if target1_rn in content:
    content = content.replace(target1_rn, rep1_n)
else:
    print("target1 not found")

target2 = 'private const val KEY_INITIAL_PERMS_REQUESTED = "initial_perms_requested"'
rep2 = target2 + '\n        private const val KEY_SHOW_PREVIOUS_CHATS = "show_previous_chats"'
if '\r\n' in content:
    rep2 = rep2.replace('\n', '\r\n')

if target2 in content:
    content = content.replace(target2, rep2)
else:
    print("target2 not found")

with open('app/src/main/java/com/example/security/SecurityPreferences.kt', 'w', encoding='utf-8', newline='') as f:
    f.write(content)
print("Done")
