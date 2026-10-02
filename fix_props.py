import os

prop_file = "c:/Users/karth/Desktop/notesapp/gradle.properties"

# Read keeping in mind it might be utf-16 or utf-8 with weird bytes
with open(prop_file, "rb") as f:
    content = f.read()

# Try to decode
try:
    text = content.decode('utf-8')
except UnicodeDecodeError:
    text = content.decode('utf-16le', errors='replace')
    
# Remove malformed lines
lines = text.split('\n')
clean_lines = []
for line in lines:
    line = line.strip('\r\n\0')
    # Filter out weird characters like spaced-out ones
    if 'o r g' in line:
        continue
    # Replace auto-download
    if 'org.gradle.java.installations.auto-download=' in line:
        line = 'org.gradle.java.installations.auto-download=true'
    if 'org.gradle.java.installations.auto-detect=' in line:
        line = 'org.gradle.java.installations.auto-detect=true'
        
    if line.strip():  # Skip empty lines
        # Only keep ascii text
        clean_line = ''.join([c for c in line if ord(c) < 128])
        clean_lines.append(clean_line)
        
with open(prop_file, "w", encoding='utf-8') as f:
    f.write('\n'.join(clean_lines))

print("Fixed gradle.properties")
