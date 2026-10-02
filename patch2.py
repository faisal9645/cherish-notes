import sys
with open('app/src/main/java/com/example/ui/profile/ProfileViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target_init = "securityPreferences.isSecretHistoryRevealed.collect { revealed ->\n                _uiState.update { it.copy(isSecretHistoryRevealed = revealed) }\n            }\n        }"
if "\r\n" in content:
    target_init = target_init.replace("\n", "\r\n")

if target_init in content and "showPreviousChats.collect" not in content:
    rep_init = target_init + "\n\n        viewModelScope.launch {\n            securityPreferences.showPreviousChats.collect { show ->\n                _uiState.update { it.copy(showPreviousChats = show) }\n            }\n        }"
    if "\r\n" in content:
        rep_init = rep_init.replace("\n", "\r\n")
    content = content.replace(target_init, rep_init)
    print("Replaced target_init")

target_method = "fun hideSecretHistory() {\n        securityPreferences.hideSecretHistory()\n    }"
if "\r\n" in content:
    target_method = target_method.replace("\n", "\r\n")

if target_method in content and "setShowPreviousChatsEnabled" not in content:
    rep_method = target_method + "\n\n    fun setShowPreviousChatsEnabled(enabled: Boolean) {\n        securityPreferences.setShowPreviousChatsEnabled(enabled)\n    }"
    if "\r\n" in content:
        rep_method = rep_method.replace("\n", "\r\n")
    content = content.replace(target_method, rep_method)
    print("Replaced target_method")

with open('app/src/main/java/com/example/ui/profile/ProfileViewModel.kt', 'w', encoding='utf-8', newline='') as f:
    f.write(content)
print("Done")
