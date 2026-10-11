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

    import re
    gradle_path = 'app/build.gradle.kts'
    version_code = 117
    version_name = "1.7.45"
    if os.path.exists(gradle_path):
        with open(gradle_path, 'r', encoding='utf-8') as f:
            gc = f.read()
            vc_m = re.search(r"versionCode\s*=\s*(\d+)", gc)
            vn_m = re.search(r'versionName\s*=\s*"([^"]+)"', gc)
            if vc_m and vn_m:
                version_code = int(vc_m.group(1))
                version_name = vn_m.group(1)
    
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
        "body": "Fixed chat auto-scroll when receiving partner's messages and during typing bar animations, ensuring latest messages are never hidden or positioned low.",
        "draft": False,
        "prerelease": False,
        "make_latest": "true"
    }

    # Generate version.json for high-speed rate-limit-free updates
    with open(APK_PATH, 'rb') as af:
        apk_len = len(af.read())
    version_json_data = {
        "versionCode": version_code,
        "versionName": version_name,
        "tagName": tag_name,
        "downloadUrl": f"https://github.com/{REPO}/releases/download/{tag_name}/cherish-notes-{tag_name}.apk",
        "assetSize": apk_len,
        "releaseNotes": release_data["body"]
    }
    with open("version.json", "w", encoding="utf-8") as vf:
        json.dump(version_json_data, vf, indent=2)
    print(f"Generated version.json for build {version_code} ({version_name})")

    # Check if release already exists, or create it
    upload_url = None
    # Record existing assets with size
    existing_asset_sizes = {}
    print(f"Checking if release {tag_name} already exists...")
    check_req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/tags/{tag_name}", headers=headers, method='GET')
    try:
        proxy_handler = urllib.request.ProxyHandler({'http': 'http://127.0.0.1:8899', 'https': 'http://127.0.0.1:8899'})
        opener = urllib.request.build_opener(proxy_handler)
        with opener.open(check_req, timeout=30) as response:
            resp_data = json.loads(response.read().decode('utf-8'))
            upload_url = resp_data['upload_url'].replace("{?name,label}", "")
            print(f"Found existing release! ID: {resp_data['id']}")
            for a in resp_data.get('assets', []):
                existing_assets[a['name']] = a['id']
                existing_asset_sizes[a['name']] = a.get('size', 0)
    except Exception as e:
        print(f"Release {tag_name} does not exist yet ({e}). Will create new one.")

    if not upload_url:
        print(f"Creating release {tag_name}...")
        req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases", data=json.dumps(release_data).encode('utf-8'), headers=headers, method='POST')
        for attempt in range(5):
            try:
                with opener.open(req, timeout=30) as response:
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

    assets_to_upload = [f"cherish-notes-{tag_name}.apk", "app-release.apk"]
    for asset_name in assets_to_upload:
        if asset_name in existing_assets and existing_asset_sizes.get(asset_name) == apk_len:
            print(f"Asset {asset_name} is already uploaded with matching size ({apk_len} bytes). Skipping.")
            continue
        elif asset_name in existing_assets:
            # Delete corrupted or mismatched size asset before re-uploading
            old_id = existing_assets[asset_name]
            print(f"Deleting outdated/incomplete asset {asset_name} (ID: {old_id})...")
            del_req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/assets/{old_id}", headers=headers, method='DELETE')
            try:
                with opener.open(del_req, timeout=30):
                    print(f"Deleted old asset {asset_name}")
            except Exception as e:
                print(f"Could not delete asset {asset_name}: {e}")

        print(f"Uploading APK asset {asset_name} with curl...")
        target_url = f"{upload_url}?name={asset_name}"
        curl_cmd = [
            "curl.exe",
            *proxy_args,
            "-sS",
            "-X", "POST",
            "-H", f"Authorization: token {token}",
            "-H", "Accept: application/vnd.github.v3+json",
            "-H", "Content-Type: application/vnd.android.package-archive",
            "--data-binary", f"@{APK_PATH}",
            "--connect-timeout", "60",
            "--max-time", "600",
            target_url
        ]
        for attempt in range(5):
            res = subprocess.run(curl_cmd, capture_output=True, text=True)
            if res.returncode == 0 and "browser_download_url" in res.stdout:
                print(f"APK asset {asset_name} uploaded successfully!")
                break
            else:
                print(f"Attempt {attempt + 1} failed to upload {asset_name}: {res.stderr or res.stdout[:200]}")
                time.sleep(3)

if __name__ == "__main__":
    main()
