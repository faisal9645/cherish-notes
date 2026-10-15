import os
import re
import sys
import json
import argparse
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
    with open(token_path, "r", encoding="utf-8") as f:
        return f.read().strip()


def get_version(project_root: str) -> tuple:
    gradle_path = os.path.join(project_root, "app", "build.gradle.kts")
    with open(gradle_path, "r", encoding="utf-8") as f:
        content = f.read()
    vcode = re.search(r"versionCode\s*=\s*(\d+)", content)
    vname = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not vcode or not vname:
        print("ERROR: Could not parse version from build.gradle.kts")
        sys.exit(1)
    return int(vcode.group(1)), vname.group(1)


def bump_version_in_gradle(project_root: str, target_code: int = None, target_name: str = None) -> tuple:
    """Auto-increments versionCode and patch version in app/build.gradle.kts before building."""
    gradle_path = os.path.join(project_root, "app", "build.gradle.kts")
    with open(gradle_path, "r", encoding="utf-8") as f:
        content = f.read()

    vcode_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    vname_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not vcode_match or not vname_match:
        print("ERROR: Could not parse version from build.gradle.kts to bump")
        sys.exit(1)

    current_code = int(vcode_match.group(1))
    current_name = vname_match.group(1)

    new_code = target_code if target_code is not None else (current_code + 1)

    if target_name is not None:
        new_name = target_name
    else:
        # Increment last dotted component (e.g. 1.7.68 -> 1.7.69)
        parts = current_name.split(".")
        if parts and parts[-1].isdigit():
            parts[-1] = str(int(parts[-1]) + 1)
            new_name = ".".join(parts)
        else:
            new_name = f"{current_name}.1"

    new_content = re.sub(
        r"versionCode\s*=\s*\d+",
        f"versionCode = {new_code}",
        content,
        count=1
    )
    new_content = re.sub(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{new_name}"',
        new_content,
        count=1
    )

    with open(gradle_path, "w", encoding="utf-8") as f:
        f.write(new_content)

    print(f"BUMPED VERSION: {current_name} (Build {current_code}) -> {new_name} (Build {new_code})", flush=True)
    return new_code, new_name


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
        "body": release_notes(version_name, version_code),
        "draft": False,
        "prerelease": False,
    }
    resp = requests.post(f"{GITHUB_API}/repos/{repo}/releases", headers=headers, json=payload, timeout=20)
    if resp.status_code not in (200, 201):
        print(f"ERROR creating GitHub release on {repo}: {resp.status_code} — {resp.text}")
        sys.exit(1)
    print(f"GitHub release created on {repo}: {tag}")
    return resp.json()


def upload_single_asset(token: str, repo: str, release: dict, apk_path: str, asset_name: str) -> str:
    upload_url = release["upload_url"].replace("{?name,label}", "")

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
            if pct != self.last_pct and pct % 10 == 0:
                print(f"Uploading {asset_name}... {pct}% ({self.read_bytes}/{self.total} bytes)", flush=True)
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
    print(f"Uploading {apk_path} -> {asset_name} ({apk_size} bytes)...", flush=True)

    import time
    success = False
    for attempt in range(1, 6):
        try:
            print(f"Upload attempt {attempt}/5 for {asset_name}...", flush=True)
            reader = ProgressReader(apk_path)
            try:
                resp = requests.post(url_with_param, headers=headers, data=reader, timeout=600)
            finally:
                try:
                    reader.f.close()
                except Exception:
                    pass
            if resp.status_code in (200, 201):
                print(f"Upload succeeded for {asset_name}!", flush=True)
                success = True
                break
            else:
                print(f"ERROR uploading APK {asset_name}: {resp.status_code} — {resp.text}", flush=True)
        except Exception as e:
            print(f"Exception during upload of {asset_name}: {e}", flush=True)

        if attempt < 5:
            time.sleep(5)

    if not success:
        print(f"Upload failed for {asset_name} after 5 attempts.", flush=True)
        sys.exit(1)

    download_url = f"https://github.com/{repo}/releases/download/{release['tag_name']}/{asset_name}"
    print(f"Upload complete for {asset_name}!\nDownload URL: {download_url}")
    return download_url


def upload_apk_to_release(token: str, repo: str, release: dict, apk_path: str, version_code: int) -> str:
    # One copy only (the user's choice): the app finds this name on every path (version.json's
    # downloadUrl, the release's only .apk, and the first name it tries when downloading)
    return upload_single_asset(token, repo, release, apk_path, f"app_update_{version_code}.apk")


def sync_git_and_push(project_root: str, token: str, version_code: int, version_name: str):
    import subprocess
    git_cmd = r"C:\Program Files\Git\cmd\git.exe"
    if not os.path.exists(git_cmd):
        git_cmd = "git"

    print("Syncing git working tree and pushing version update to origin main...", flush=True)
    subprocess.run([git_cmd, "add", "-A"], cwd=project_root)
    commit_msg = f"Bump version to {version_name} (build {version_code}) and release OTA"
    subprocess.run([git_cmd, "commit", "-m", commit_msg], cwd=project_root)

    push_url = f"https://x-access-token:{token}@github.com/{GITHUB_REPO}.git"
    res = subprocess.run([git_cmd, "push", push_url, "main"], cwd=project_root, capture_output=True, text=True)
    if res.returncode == 0:
        print("Pushed version bump and version.json to origin main successfully!", flush=True)
        subprocess.run([git_cmd, "fetch", "origin", "main"], cwd=project_root, capture_output=True)
    else:
        res2 = subprocess.run([git_cmd, "push", "origin", "main"], cwd=project_root, capture_output=True, text=True)
        if res2.returncode == 0:
            print("Pushed to origin main via existing git remote credentials!", flush=True)
        else:
            print(f"Git push notice: {res.stderr or res2.stderr}", flush=True)


def release_notes(version_name: str, version_code: int, custom_notes: str = None) -> str:
    """Public text (the release page and the in-app update dialog): a plain notes app only."""
    if custom_notes:
        return f"Notes v{version_name} (Build {version_code}):\n{custom_notes}"
    return (
        f"Notes v{version_name} (Build {version_code}):\n"
        "\u2022 Lighter on data and battery: fewer background requests\n"
        "\u2022 Top bar fits smaller screens better\n"
        "\u2022 Bug fixes and improvements"
    )


def write_version_json(project_root: str, version_code: int, version_name: str, download_url: str, apk_path: str, custom_notes: str = None):
    """Installed apps read version.json from main first (OtaUpdateManager.RAW_VERSION_JSON_URL) and
    only fall back to the releases API without it, so it must name this release and be pushed."""
    import datetime
    data = {
        "versionCode": version_code,
        "versionName": version_name,
        "tagName": f"v{version_name}-{version_code}",
        "downloadUrl": download_url,
        "assetSize": os.path.getsize(apk_path),
        "releaseNotes": release_notes(version_name, version_code, custom_notes),
        "releaseDate": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    with open(os.path.join(project_root, "version.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("version.json updated: commit and push it to main, or phones won't see this update", flush=True)


def update_firestore(project_root: str, version_code: int, version_name: str, download_url: str):
    key_path = os.path.join(project_root, "firebase_admin_key.json")
    if not os.path.exists(key_path):
        print("WARNING: firebase_admin_key.json not found \u2014 skipping Firestore update.")
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
        "releaseNotes": f"v{version_name} \u2014 Live couple avatars, Double-tap heartbursts, and Watch Party sync notifications",
    }, merge=True)
    print("Firestore app_config/version updated successfully!")


def build_apk(project_root: str):
    import subprocess
    print("Building release APK...", flush=True)
    gradlew = os.path.join(project_root, "gradlew.bat" if os.name == 'nt' else "gradlew")
    env = os.environ.copy()
    candidate_jdks = [
        r"C:\Program Files\Android\Android Studio\jbr",
        r"C:\Program Files\Android\Android Studio\jre",
        env.get("JAVA_HOME", "")
    ]
    for jdk in candidate_jdks:
        if jdk and os.path.exists(jdk):
            env["JAVA_HOME"] = jdk
            break
    try:
        subprocess.check_call(
            [gradlew, ":app:assembleRelease", "--daemon", "--parallel", "--build-cache"],
            cwd=project_root,
            env=env
        )
        print("Build successful!", flush=True)
    except subprocess.CalledProcessError as e:
        print(f"ERROR: Build failed with exit code {e.returncode}")
        sys.exit(1)


def main():
    sys.stdout.reconfigure(line_buffering=True)
    script_dir   = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.abspath(os.path.join(script_dir, "..", ".."))

    parser = argparse.ArgumentParser(description="Cherish Notes OTA Release and Upload Tool")
    parser.add_argument("--bump", dest="bump", action="store_true", default=True,
                        help="Automatically increment versionCode and patch version (default: True)")
    parser.add_argument("--no-bump", dest="bump", action="store_false",
                        help="Do not increment version, keep existing version in build.gradle.kts")
    parser.add_argument("--version-code", type=int, default=None,
                        help="Explicitly set a versionCode (e.g. 141)")
    parser.add_argument("--version-name", type=str, default=None,
                        help="Explicitly set a versionName (e.g. 1.7.69)")
    parser.add_argument("--notes", type=str, default=None,
                        help="Custom release notes string")
    parser.add_argument("--skip-build", action="store_true", default=False,
                        help="Skip running Gradle assembleRelease and reuse existing APK")
    parser.add_argument("--also", action="append", default=[],
                        help="Also publish to secondary GitHub repo (can be repeated)")

    args = parser.parse_args()

    token = get_github_token(project_root)

    # Version handling: auto-bump by default so client phones see the update
    if args.bump or args.version_code is not None or args.version_name is not None:
        version_code, version_name = bump_version_in_gradle(
            project_root,
            target_code=args.version_code,
            target_name=args.version_name
        )
    else:
        version_code, version_name = get_version(project_root)

    if not args.skip_build:
        build_apk(project_root)

    apk_path = get_apk_path(project_root)

    print(f"Version  : {version_name} (code {version_code})")
    print(f"APK      : {apk_path}")

    for repo in args.also:
        print(f"\nAlso publishing to {repo}...")
        extra_release = create_github_release(token, repo, version_code, version_name)
        upload_apk_to_release(token, repo, extra_release, apk_path, version_code)

    release      = create_github_release(token, GITHUB_REPO, version_code, version_name)
    download_url = upload_apk_to_release(token, GITHUB_REPO, release, apk_path, version_code)
    write_version_json(project_root, version_code, version_name, download_url, apk_path, args.notes)
    update_firestore(project_root, version_code, version_name, download_url)
    sync_git_and_push(project_root, token, version_code, version_name)

    # Purge jsDelivr CDN cache so phones get the new version.json globally with zero latency
    try:
        purge_url = f"https://purge.jsdelivr.net/gh/{GITHUB_REPO}@main/version.json"
        p_resp = requests.get(purge_url, timeout=10)
        print(f"Purged jsDelivr CDN cache for instant global update propagation: HTTP {p_resp.status_code}", flush=True)
    except Exception as e:
        print(f"jsDelivr CDN purge notice: {e}", flush=True)

    print("\nOTA update pushed successfully via GitHub Releases & jsDelivr Anycast CDN!")
    print(f"   Users will download from: {download_url}")


if __name__ == "__main__":
    main()
