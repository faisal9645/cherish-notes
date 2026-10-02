import sys
with open('app/src/main/java/com/example/ui/chat/ChatViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target1 = "val isSecretHistoryRevealed: Boolean = true,"
if "\r\n" in content:
    target1 = target1.replace("\n", "\r\n")

if target1 in content:
    content = content.replace(target1, target1 + "\n    val showPreviousChats: Boolean = false,")
else:
    print("target1 not found in ChatUiState")

target2 = "viewModelScope.launch {\n            securityPreferences.isSecretHistoryRevealed.collect { revealed ->\n                _uiState.update { it.copy(isSecretHistoryRevealed = revealed) }\n            }\n        }"
if "\r\n" in content:
    target2 = target2.replace("\n", "\r\n")

rep2 = target2 + "\n\n        viewModelScope.launch {\n            securityPreferences.showPreviousChats.collect { show ->\n                _uiState.update { it.copy(showPreviousChats = show) }\n            }\n        }"
if "\r\n" in content:
    rep2 = rep2.replace("\n", "\r\n")

if target2 in content:
    content = content.replace(target2, rep2)
else:
    print("target2 not found in init")

with open('app/src/main/java/com/example/ui/chat/ChatViewModel.kt', 'w', encoding='utf-8', newline='') as f:
    f.write(content)
print("Done")
