import sys
with open('app/src/main/java/com/example/ui/chat/ChatScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target_filter = '''    val displayedMessages = remember(uiState.messages, uiState.searchQuery, uiState.filterStarredOnly) {
        var list = uiState.messages
        if (uiState.filterStarredOnly) {
            list = list.filter { it.isStarred }
        }
        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim()
            list = list.filter { it.text.contains(q, ignoreCase = true) }
        }
        list
    }'''
if '\r\n' in content:
    target_filter = target_filter.replace('\n', '\r\n')

rep_filter = '''    val displayedMessages = remember(uiState.messages, uiState.searchQuery, uiState.filterStarredOnly, uiState.showPreviousChats) {
        var list = uiState.messages
        if (uiState.filterStarredOnly) {
            list = list.filter { it.isStarred }
        }
        if (!uiState.showPreviousChats) {
            val now = java.util.Calendar.getInstance()
            if (now.get(java.util.Calendar.HOUR_OF_DAY) < 6) {
                now.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            now.set(java.util.Calendar.HOUR_OF_DAY, 6)
            now.set(java.util.Calendar.MINUTE, 0)
            now.set(java.util.Calendar.SECOND, 0)
            now.set(java.util.Calendar.MILLISECOND, 0)
            val today6am = now.timeInMillis
            list = list.filter { it.timestamp >= today6am }
        }
        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim()
            list = list.filter { it.text.contains(q, ignoreCase = true) }
        }
        list
    }'''
if '\r\n' in content:
    rep_filter = rep_filter.replace('\n', '\r\n')

if target_filter in content:
    content = content.replace(target_filter, rep_filter)
    print("Replaced displayedMessages filter")
else:
    print("target_filter not found")

target_format = '''fun formatDateSeparator(timestamp: Long): String {
    val now = System.currentTimeMillis()
    if (isSameDay(timestamp, now)) return "Today"'''
if '\r\n' in content:
    target_format = target_format.replace('\n', '\r\n')

rep_format = '''fun formatDateSeparator(timestamp: Long): String {
    val now = System.currentTimeMillis()
    if (isSameDay(timestamp, now)) return ""'''
if '\r\n' in content:
    rep_format = rep_format.replace('\n', '\r\n')

if target_format in content:
    content = content.replace(target_format, rep_format)
    print("Replaced formatDateSeparator")
else:
    print("target_format not found")

with open('app/src/main/java/com/example/ui/chat/ChatScreen.kt', 'w', encoding='utf-8', newline='') as f:
    f.write(content)
print("Done")
