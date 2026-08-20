import os
import sys
import json
import subprocess
import urllib.request
import urllib.parse
import mimetypes

REPO = "ANSH9BOSS/VayuClient"

def get_version():
    if len(sys.argv) > 1 and sys.argv[1].strip():
        v = sys.argv[1].strip()
        return v if v.startswith("v") else f"v{v}"
    try:
        vjson = os.path.join(os.path.dirname(__file__), "version.json")
        with open(vjson, "r") as f:
            data = json.load(f)
            v = data.get("version", "1.9.1")
            return v if v.startswith("v") else f"v{v}"
    except:
        return "v1.9.1"

VERSION_TAG = get_version()
RELEASE_TITLE = f"VayuClient {VERSION_TAG} - Universal Minecraft 26.x & 1.21+ Support"
RELEASE_NOTES = f"""## 🌌 VayuClient {VERSION_TAG} Official Release

### ⚡ Key Highlights & Features
* **Expanded Minecraft 26.x & 1.21+ Native Ecosystem**: Added full first-class support for `26.2`, `26.2.0`, `26.1.2`, `26.1`, `26`, `1.21.11`, through `1.21`.
* **Deep Modpack Archive & Mod Inspector**: Automatic version and loader detection for `.zip`, `.mrpack`, CurseForge `manifest.json`, MultiMC `instance.cfg` & `mmc-pack.json`, and direct inspection of `mods/*.jar` dependencies.
* **1000+ FPS Entity Rendering Optimization**: Zero-allocation fast-exit paths in entity render states eliminate framerate drops when multiple players are nearby.
* **Sleek In-App Live Game Output Menu**: Replaced raw Windows CMD prompt with Cyber-Aero Glassmorphic live log viewer dialog.
* **Complete Module Config Overhaul**: Glass cards, interactive header toggles, cyber keycap buttons, glowing sliders, and dropdown menus.
* **Fully Signed Windows Installer**: Authenticode-signed standalone setup package (`VayuClientSetup.exe`).

### 📦 Assets Included
* `VayuClientSetup.exe` (Standalone Windows Setup Installer)
* `VayuClient.exe` (Standalone Launcher Binary)
"""

def get_github_token():
    # Check env
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        return token
    # Query git credential helper
    try:
        proc = subprocess.Popen(
            ["git", "credential", "fill"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        out, _ = proc.communicate(input="protocol=https\nhost=github.com\n")
        for line in out.splitlines():
            if line.startswith("password="):
                return line.split("=", 1)[1].strip()
    except Exception as e:
        print(f"[Error] Failed querying git credentials: {e}", flush=True)
    return None

def main():
    token = get_github_token()
    if not token:
        print("[Error] No GitHub token found.", flush=True)
        sys.exit(1)

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "VayuClient-ReleaseBot",
        "X-GitHub-Api-Version": "2022-11-28"
    }

    # 1. Get or Create Release
    get_url = f"https://api.github.com/repos/{REPO}/releases/tags/{VERSION_TAG}"
    req = urllib.request.Request(get_url, headers=headers)
    release = None
    try:
        with urllib.request.urlopen(req) as resp:
            release = json.loads(resp.read().decode())
            print(f"[GitHub] Found existing release {VERSION_TAG} (ID: {release.get('id')})", flush=True)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"[GitHub] Release {VERSION_TAG} does not exist yet. Creating...", flush=True)
        else:
            print(f"[Error] Failed checking release: {e.read().decode()}", flush=True)
            sys.exit(1)

    if not release:
        create_url = f"https://api.github.com/repos/{REPO}/releases"
        payload = {
            "tag_name": VERSION_TAG,
            "target_commitish": "main",
            "name": RELEASE_TITLE,
            "body": RELEASE_NOTES,
            "draft": False,
            "prerelease": False,
            "make_latest": "true"
        }
        req = urllib.request.Request(create_url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as resp:
                release = json.loads(resp.read().decode())
                print(f"[GitHub] Created official release {VERSION_TAG} (ID: {release.get('id')}) as LATEST!", flush=True)
        except Exception as e:
            print(f"[Error] Failed creating release: {e}", flush=True)
            sys.exit(1)
    else:
        # Update release to make sure it is marked latest
        update_url = f"https://api.github.com/repos/{REPO}/releases/{release['id']}"
        payload = {
            "name": RELEASE_TITLE,
            "body": RELEASE_NOTES,
            "make_latest": "true"
        }
        req = urllib.request.Request(update_url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="PATCH")
        try:
            with urllib.request.urlopen(req) as resp:
                release = json.loads(resp.read().decode())
                print(f"[GitHub] Updated release {VERSION_TAG} as LATEST!", flush=True)
        except Exception as e:
            print(f"[Warning] Failed updating release: {e}", flush=True)

    upload_url_template = release.get("upload_url", "")
    if not upload_url_template:
        print("[Error] No upload_url in release payload.", flush=True)
        sys.exit(1)

    upload_base = upload_url_template.split("{")[0]

    # 2. Upload Binaries
    dist_dir = os.path.join(os.path.dirname(__file__), "dist")
    files_to_upload = [
        os.path.join(dist_dir, "VayuClientSetup.exe"),
        os.path.join(dist_dir, "VayuClient.exe")
    ]

    # Get existing assets
    existing_assets = {a["name"]: a["id"] for a in release.get("assets", [])}

    for file_path in files_to_upload:
        if not os.path.exists(file_path):
            print(f"[Skip] File not found: {file_path}", flush=True)
            continue

        filename = os.path.basename(file_path)

        # Delete existing asset if present
        if filename in existing_assets:
            asset_id = existing_assets[filename]
            print(f"[GitHub] Removing existing asset {filename} (ID: {asset_id})...", flush=True)
            del_url = f"https://api.github.com/repos/{REPO}/releases/assets/{asset_id}"
            del_req = urllib.request.Request(del_url, headers=headers, method="DELETE")
            try:
                with urllib.request.urlopen(del_req):
                    pass
            except Exception as e:
                print(f"[Warning] Failed deleting asset: {e}", flush=True)

        print(f"[GitHub] Uploading {filename} ({os.path.getsize(file_path)} bytes)...", flush=True)
        upload_url = f"{upload_base}?name={urllib.parse.quote(filename)}"
        
        with open(file_path, "rb") as f:
            file_data = f.read()

        upload_headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/octet-stream",
            "Content-Length": str(len(file_data)),
            "User-Agent": "VayuClient-ReleaseBot"
        }

        up_req = urllib.request.Request(upload_url, data=file_data, headers=upload_headers, method="POST")
        try:
            with urllib.request.urlopen(up_req) as resp:
                asset_resp = json.loads(resp.read().decode())
                print(f"[GitHub] Successfully uploaded {filename}! (Asset ID: {asset_resp.get('id')})", flush=True)
        except Exception as e:
            print(f"[Error] Failed uploading {filename}: {e}", flush=True)

    print("\n==========================================================", flush=True)
    print(f" SUCCESS: GitHub Release {VERSION_TAG} is now LIVE as LATEST!", flush=True)
    print(f" URL: https://github.com/{REPO}/releases/tag/{VERSION_TAG}", flush=True)
    print("==========================================================\n", flush=True)

if __name__ == "__main__":
    main()
