import sys
with open('app/src/main/java/com/example/ui/chat/ChatScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target_lazy_col = "LazyColumn(\n                    state = listState,\n                    modifier = Modifier.fillMaxSize(),\n                    reverseLayout = true,"
if "\r\n" in content:
    target_lazy_col = target_lazy_col.replace("\n", "\r\n")

rep_lazy_col = "LazyColumn(\n                    state = listState,\n                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {\n                        androidx.compose.foundation.gestures.detectTapGestures(\n                            onDoubleTap = { onQuickDisguise() }\n                        )\n                    },\n                    reverseLayout = true,"
if "\r\n" in content:
    rep_lazy_col = rep_lazy_col.replace("\n", "\r\n")

if target_lazy_col in content:
    content = content.replace(target_lazy_col, rep_lazy_col)
    print("Replaced lazy column")
else:
    print("target_lazy_col not found")

target_date = '''                            if (isFirstOfDay) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 1.dp
                                    ) {
                                        Text(
                                            text = formatDateSeparator(message.timestamp),'''
if '\r\n' in content:
    target_date = target_date.replace('\n', '\r\n')

rep_date = '''                            val dateSep = formatDateSeparator(message.timestamp)
                            if (isFirstOfDay && dateSep.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 1.dp
                                    ) {
                                        Text(
                                            text = dateSep,'''
if '\r\n' in content:
    rep_date = rep_date.replace('\n', '\r\n')

if target_date in content:
    content = content.replace(target_date, rep_date)
    print("Replaced date separator")
else:
    print("target_date not found")

with open('app/src/main/java/com/example/ui/chat/ChatScreen.kt', 'w', encoding='utf-8', newline='') as f:
    f.write(content)
print("Done final patch")
