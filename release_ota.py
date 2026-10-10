import json
import urllib.request
import os
import sys

TOKEN_FILE = 'github_token.txt'
REPO = 'faisal9645/cherish-notes'
APK_PATH = 'app/build/outputs/apk/release/app-release.apk'

def main():
    if not os.path.exists(TOKEN_FILE):
        print(f"Error: {TOKEN_FILE} not found.")
        sys.exit(1)
    
    with open(TOKEN_FILE, 'r') as f:
        token = f.read().strip()
        
    if not os.path.exists(APK_PATH):
        print(f"Error: {APK_PATH} not found. Make sure the release build finished.")
        sys.exit(1)

    version_code = 115
    version_name = "1.7.43"
    
    tag_name = f"v{version_name}-{version_code}"
    release_name = f"Release {version_name} (Build {version_code})"
    
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "Content-Type": "application/json"
    }

    # Create a release
    release_data = {
        "tag_name": tag_name,
        "name": release_name,
        "body": "Notifications: Replaced heart icon with discreet note icon in status bar notifications, and suppress all system notifications when the user is actively inside the Cherish app.",
        "draft": False,
        "prerelease": False
    }

    print(f"Creating release {tag_name}...")
    req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases", data=json.dumps(release_data).encode('utf-8'), headers=headers, method='POST')
    
    upload_url = None
    import time
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                resp_data = json.loads(response.read().decode('utf-8'))
                upload_url = resp_data['upload_url'].replace("{?name,label}", "")
                print(f"Release created successfully! ID: {resp_data['id']}")
                break
        except Exception as e:
            print(f"Attempt {attempt + 1} failed to create release: {e}")
            if attempt < 4:
                time.sleep(2)
            else:
                sys.exit(1)

    print("Uploading APK asset...")
    
    with open(APK_PATH, 'rb') as apk_file:
        apk_data = apk_file.read()
        
    upload_headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "Content-Type": "application/vnd.android.package-archive"
    }

    asset_name = f"cherish-notes-{tag_name}.apk"
    upload_req = urllib.request.Request(f"{upload_url}?name={asset_name}", data=apk_data, headers=upload_headers, method='POST')
    
    for attempt in range(5):
        try:
            with urllib.request.urlopen(upload_req, timeout=60) as response:
                print("APK uploaded successfully!")
                break
        except Exception as e:
            print(f"Attempt {attempt + 1} failed to upload APK: {e}")
            if attempt < 4:
                time.sleep(3)
            else:
                sys.exit(1)

if __name__ == "__main__":
    main()
