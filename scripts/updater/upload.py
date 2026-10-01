import os
import re
import sys
import json
import requests
import firebase_admin
from firebase_admin import credentials, firestore

GITHUB_REPO = "faisal9645/notes"
GITHUB_API  = "https://api.github.com"


def get_github_token(project_root: str) -> str:
    token_path = os.path.join(project_root, "github_token.txt")
    if not os.path.exists(token_path):
        print("ERROR: github_token.txt not found in project root!")
        print("Create it with just your GitHub PAT inside.")
        sys.exit(1)
    with open(token_path, "r") as f:
        return f.read().strip()


def get_version(project_root: str):
    gradle_path = os.path.join(project_root, "app", "build.gradle.kts")
    with open(gradle_path, "r", encoding="utf-8") as f:
        content = f.read()
    vcode = re.search(r"versionCode\s*=\s*(\d+)", content)
    vname = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not vcode or not vname:
        print("ERROR: Could not parse version from build.gradle.kts")
        sys.exit(1)
    return int(vcode.group(1)), vname.group(1)


def get_apk_path(project_root: str) -> str:
    release = os.path.join(project_root, "app", "build", "outputs", "apk", "release", "app-release.apk")
    debug   = os.path.join(project_root, "app", "build", "outputs", "apk", "debug",   "app-debug.apk")
    
    release_time = os.path.getmtime(release) if os.path.exists(release) else 0
    debug_time = os.path.getmtime(debug) if os.path.exists(debug) else 0

    if release_time == 0 and debug_time == 0:
        print("ERROR: No APK found. Build the project first.")
        sys.exit(1)
        
    if release_time > debug_time:
        return release
    else:
        return debug


def create_github_release(token: str, version_code: int, version_name: str) -> dict:
    tag = f"v{version_name}-{version_code}"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github+json",
    }
    # Check if release already exists
    check = requests.get(f"{GITHUB_API}/repos/{GITHUB_REPO}/releases/tags/{tag}", headers=headers)
    if check.status_code == 200:
        print(f"Release {tag} already exists — reusing it.")
        return check.json()

    payload = {
        "tag_name": tag,
        "name": f"v{version_name} (build {version_code})",
        "body": f"Automatic OTA release — v{version_name} (build {version_code})",
        "draft": False,
        "prerelease": False,
    }
    resp = requests.post(f"{GITHUB_API}/repos/{GITHUB_REPO}/releases", headers=headers, json=payload)
    if resp.status_code not in (200, 201):
        print(f"ERROR creating GitHub release: {resp.status_code} — {resp.text}")
        sys.exit(1)
    print(f"GitHub release created: {tag}")
    return resp.json()


def upload_apk_to_release(token: str, release: dict, apk_path: str, version_code: int) -> str:
    upload_url = release["upload_url"].replace("{?name,label}", "")
    asset_name = f"app_update_{version_code}.apk"
    headers = {
        "Authorization": f"token {token}",
        "Content-Type": "application/vnd.android.package-archive",
    }

    # Delete existing asset with same name if present
    for asset in release.get("assets", []):
        if asset["name"] == asset_name:
            print(f"Deleting existing asset: {asset_name}")
            requests.delete(
                f"{GITHUB_API}/repos/{GITHUB_REPO}/releases/assets/{asset['id']}",
                headers={"Authorization": f"token {token}", "Accept": "application/vnd.github+json"},
            )

    print(f"Uploading {apk_path} -> {asset_name} ...")
    with open(apk_path, "rb") as f:
        resp = requests.post(
            f"{upload_url}?name={asset_name}",
            headers=headers,
            data=f,
        )
    if resp.status_code not in (200, 201):
        print(f"ERROR uploading APK: {resp.status_code} — {resp.text}")
        sys.exit(1)

    # Direct download URL (no login required)
    download_url = f"https://github.com/{GITHUB_REPO}/releases/download/{release['tag_name']}/{asset_name}"
    print(f"Upload complete!\nDownload URL: {download_url}")
    return download_url


def update_firestore(project_root: str, version_code: int, version_name: str, download_url: str):
    key_path = os.path.join(project_root, "firebase_admin_key.json")
    if not os.path.exists(key_path):
        print("WARNING: firebase_admin_key.json not found — skipping Firestore update.")
        return
    cred = credentials.Certificate(key_path)
    if not firebase_admin._apps:
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    db.collection("app_config").document("version").set({
        "versionCode":  version_code,
        "versionName":  version_name,
        "downloadUrl":  download_url,
        "releaseDate":  firestore.SERVER_TIMESTAMP,
        "releaseNotes": f"v{version_name} — automatic OTA via GitHub Releases",
    }, merge=True)
    print("Firestore app_config/version updated successfully!")


def main():
    script_dir   = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, "..", ".."))

    token                    = get_github_token(project_root)
    version_code, version_name = get_version(project_root)
    apk_path                 = get_apk_path(project_root)

    print(f"Version  : {version_name} (code {version_code})")
    print(f"APK      : {apk_path}")

    release      = create_github_release(token, version_code, version_name)
    download_url = upload_apk_to_release(token, release, apk_path, version_code)
    update_firestore(project_root, version_code, version_name, download_url)

    print("\nOTA update pushed successfully via GitHub Releases!")
    print(f"   Users will download from: {download_url}")


if __name__ == "__main__":
    main()
