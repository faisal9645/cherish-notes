import sys
with open('app/src/main/java/com/example/ui/profile/ProfileScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target = 'SettingsSection(\n                title = "Privacy & Stealth Vault",'
if "\r\n" in content:
    target = target.replace("\n", "\r\n")

if target in content:
    idx = content.find(target)
    opening_brace = content.find('{', idx)
    insertion = '\n                ListItem(\n                    headlineContent = { Text("Show Previous Chats", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },\n                    supportingContent = { Text("Show chats from previous days (before 6 AM today)", fontSize = 13.sp) },\n                    leadingContent = { Icon(Icons.Default.History, contentDescription = null, tint = primaryAccent) },\n                    trailingContent = {\n                        Switch(\n                            checked = uiState.showPreviousChats,\n                            onCheckedChange = { viewModel.setShowPreviousChatsEnabled(it) }\n                        )\n                    }\n                )\n                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)\n'
    if "\r\n" in content:
        insertion = insertion.replace("\n", "\r\n")
    content = content[:opening_brace+1] + insertion + content[opening_brace+1:]
else:
    print("SettingsSection not found")

with open('app/src/main/java/com/example/ui/profile/ProfileScreen.kt', 'w', encoding='utf-8', newline='') as f:
    f.write(content)
print("Done")
