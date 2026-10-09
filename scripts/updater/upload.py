import os
import re
import sys
import json
import requests
import firebase_admin
from firebase_admin import credentials, firestore

# Where installed apps look for updates (OtaUpdateManager.GITHUB_API_LATEST_RELEASE must match).
# `--also owner/repo` publishes the same release there too, e.g. an old home that phones on an
# older version still check.
GITHUB_REPO = "faisal9645/cherish-notes"
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


def create_github_release(token: str, repo: str, version_code: int, version_name: str) -> dict:
    tag = f"v{version_name}-{version_code}"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github+json",
    }
    # Check if release already exists
    try:
        check = requests.get(f"{GITHUB_API}/repos/{repo}/releases/tags/{tag}", headers=headers, timeout=15)
        if check.status_code == 200:
            print(f"Release {tag} already exists — reusing it.")
            return check.json()
    except Exception as e:
        print(f"Warning: Failed to check existing release: {e}")

    payload = {
        "tag_name": tag,
        "name": f"v{version_name} (build {version_code})",
        "body": f"Notes v{version_name} (Build {version_code}):\n• Smoother photo opening and gallery\n• Sound volume and vibration settings\n• Choose your own notification text\n• New home cards and a favourites book\n• Bug fixes and improvements",
        "draft": False,
        "prerelease": False,
    }
    resp = requests.post(f"{GITHUB_API}/repos/{repo}/releases", headers=headers, json=payload, timeout=20)
    if resp.status_code not in (200, 201):
        print(f"ERROR creating GitHub release on {repo}: {resp.status_code} — {resp.text}")
        sys.exit(1)
    print(f"GitHub release created on {repo}: {tag}")
    return resp.json()


def upload_apk_to_release(token: str, repo: str, release: dict, apk_path: str, version_code: int) -> str:
    upload_url = release["upload_url"].replace("{?name,label}", "")
    asset_name = f"app_update_{version_code}.apk"

    # Refresh release details to get up-to-date asset list
    try:
        rel_resp = requests.get(
            f"{GITHUB_API}/repos/{repo}/releases/{release['id']}",
            headers={"Authorization": f"token {token}", "Accept": "application/vnd.github+json"},
            timeout=15
        )
        if rel_resp.status_code == 200:
            release = rel_resp.json()
    except Exception as e:
        print(f"Note: Could not refresh release info: {e}")

    # Delete existing asset with same name if present
    for asset in release.get("assets", []):
        if asset["name"] == asset_name:
            print(f"Deleting existing asset: {asset_name} (id {asset['id']})")
            requests.delete(
                f"{GITHUB_API}/repos/{repo}/releases/assets/{asset['id']}",
                headers={"Authorization": f"token {token}", "Accept": "application/vnd.github+json"},
                timeout=15
            )

    class ProgressReader:
        def __init__(self, path):
            self.f = open(path, 'rb')
            self.total = os.path.getsize(path)
            self.read_bytes = 0
            self.last_pct = -1

        def read(self, size=-1):
            chunk = self.f.read(size if size > 0 else 65536)
            self.read_bytes += len(chunk)
            pct = int(self.read_bytes * 100 / self.total) if self.total > 0 else 0
            if pct != self.last_pct and pct % 5 == 0:
                print(f"Uploading... {pct}% ({self.read_bytes}/{self.total} bytes)", flush=True)
                self.last_pct = pct
            return chunk

        def __len__(self):
            return self.total

    apk_size = os.path.getsize(apk_path)
    url_with_param = f"{upload_url}?name={asset_name}"
    headers = {
        "Authorization": f"token {token}",
        "Content-Type": "application/vnd.android.package-archive",
        "Content-Length": str(apk_size),
        "Accept": "application/vnd.github+json",
    }
    print(f"Uploading {apk_path} -> {asset_name} ({os.path.getsize(apk_path)} bytes)...", flush=True)
    
    import time
    success = False
    for attempt in range(1, 4):
        try:
            print(f"Upload attempt {attempt}/3...", flush=True)
            resp = requests.post(url_with_param, headers=headers, data=ProgressReader(apk_path), timeout=600)
            if resp.status_code in (200, 201):
                print("Upload succeeded!", flush=True)
                success = True
                break
            else:
                print(f"ERROR uploading APK: {resp.status_code} — {resp.text}", flush=True)
        except Exception as e:
            print(f"Exception during upload: {e}", flush=True)
        
        if attempt < 3:
            time.sleep(15)
            
    if not success:
        print("Upload failed after 3 attempts.", flush=True)
        sys.exit(1)

    # Direct download URL (no login required)
    download_url = f"https://github.com/{repo}/releases/download/{release['tag_name']}/{asset_name}"
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
        "releaseNotes": f"v{version_name} — Multi-select delete in chat & gallery, Recover All fixed, smaller APK",
    }, merge=True)
    print("Firestore app_config/version updated successfully!")


def build_apk(project_root: str):
    import subprocess
    print("Building release APK...", flush=True)
    gradlew = os.path.join(project_root, "gradlew.bat" if os.name == 'nt' else "gradlew")
    try:
        subprocess.check_call([gradlew, ":app:assembleRelease"], cwd=project_root)
        print("Build successful!", flush=True)
    except subprocess.CalledProcessError as e:
        print(f"ERROR: Build failed with exit code {e.returncode}")
        sys.exit(1)


def main():
    sys.stdout.reconfigure(line_buffering=True)
    script_dir   = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, "..", ".."))

    args = sys.argv[1:]
    extra_repos = [args[i + 1] for i, a in enumerate(args[:-1]) if a == "--also"]

    build_apk(project_root)

    token                    = get_github_token(project_root)
    version_code, version_name = get_version(project_root)
    apk_path                 = get_apk_path(project_root)

    print(f"Version  : {version_name} (code {version_code})")
    print(f"APK      : {apk_path}")

    for repo in extra_repos:
        print(f"\nAlso publishing to {repo}...")
        extra_release = create_github_release(token, repo, version_code, version_name)
        upload_apk_to_release(token, repo, extra_release, apk_path, version_code)

    release      = create_github_release(token, GITHUB_REPO, version_code, version_name)
    download_url = upload_apk_to_release(token, GITHUB_REPO, release, apk_path, version_code)
    update_firestore(project_root, version_code, version_name, download_url)

    print("\nOTA update pushed successfully via GitHub Releases!")
    print(f"   Users will download from: {download_url}")


if __name__ == "__main__":
    main()
