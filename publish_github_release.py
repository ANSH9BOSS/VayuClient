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
        base_dir = os.path.dirname(os.path.abspath(__file__))
        vjson = os.path.join(base_dir, "version.json")
        with open(vjson, "r", encoding="utf-8-sig") as f:
            data = json.load(f)
            v = data.get("version", "3.1.2")
            return v if v.startswith("v") else f"v{v}"
    except Exception as e:
        print(f"[Warning] Could not read version.json: {e}", flush=True)
        return "v3.1.2"

VERSION_TAG = get_version()
RELEASE_TITLE = f"VayuClient {VERSION_TAG} - Next-Gen 3D Game Library & Setup"
RELEASE_NOTES = f"""## 🌌 VayuClient {VERSION_TAG} Official Release

### ⚡ Key Highlights & Features
* **Brand New Hardware 3D Installation Manager & Game Library**:
  - Obsidian Black & Subtle Purple cyber pod chassis with glowing purple side fin light brackets.
  - Interactive 3D carousel stage: Active center hero pod with full hardware telemetry and flanking inactive pods with smooth perspective morphing on selection.
  - Real-time instance telemetry: Minecraft versions, mod loaders (Fabric, Forge, NeoForge, Quilt, Vanilla), real mod counts, RAM allocation, relative last played timestamps, and favorite stars.
  - Contextual tools: Edit, Clone, Open Game Folder, Version Converter, Custom Launch, and Safe Deletion.
* **Direct3D Real-Time 3D Player Stage**:
  - Full player skin rendering on multi-mesh voxel characters with responsive mouse cursor look-at tracking.
  - Custom expressive poses (Love / Cradle, Bedwars PVP Stance, Wave, Sitting, Relaxed, Salute).
  - Sub-pixel breathing bob dynamics.
* **Atmospheric Dynamic Wallpaper Engine**:
  - Multi-image pack URI loading with smooth crossfade animations across 10+ themes.
* **Universal Modrinth Sync & Mod Loader Suite**:
  - 1-click downloads for Mods, Modpacks, Resource Packs, Shaders, and Data Packs with automatic `.jar` icon extraction.
* **Official Windows Setup Package**:
  - Authenticode SHA256-signed standalone installer with desktop shortcuts, start menu registration, and automatic update capabilities.

### 📦 Assets Included
* `VayuClientSetup.exe` (Standalone Windows Setup Installer)
* `vayu_hud_manifest.json` (HUD Artifact Manifest v2.1.0)
* `vayuclient-hud-2.1.0-mc*.jar` — Universal HUD JARs for all supported Minecraft versions (1.21 – 1.21.11, 26.x)
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
        # Update release to make sure it is marked latest and has updated notes
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

    # 2. Upload Binaries: VayuClientSetup.exe + all HUD JARs + manifest
    base_dir = os.path.dirname(os.path.abspath(__file__))
    dist_dir = os.path.join(base_dir, "dist")
    mods_dir = os.path.join(base_dir, "VayuClient", "Assets", "Mods")

    # Primary installer
    files_to_upload = [
        os.path.join(dist_dir, "VayuClientSetup.exe")
    ]

    # Add the HUD manifest
    manifest_path = os.path.join(mods_dir, "vayu_hud_manifest.json")
    if os.path.exists(manifest_path):
        files_to_upload.append(manifest_path)

    # Add all HUD JAR files
    if os.path.isdir(mods_dir):
        for f in sorted(os.listdir(mods_dir)):
            if f.endswith(".jar") and "vayuclient-hud" in f:
                files_to_upload.append(os.path.join(mods_dir, f))

    target_filenames = {os.path.basename(p) for p in files_to_upload}
    print(f"[Info] Will upload {len(files_to_upload)} assets: VayuClientSetup.exe + {len(files_to_upload)-1} HUD files", flush=True)

    # Clean up any unexpected / unwanted assets (things NOT in our target set)
    for asset in release.get("assets", []):
        asset_name = asset["name"]
        asset_id = asset["id"]
        if asset_name not in target_filenames:
            print(f"[GitHub] Removing unwanted asset {asset_name} (ID: {asset_id})...", flush=True)
            del_url = f"https://api.github.com/repos/{REPO}/releases/assets/{asset_id}"
            del_req = urllib.request.Request(del_url, headers=headers, method="DELETE")
            try:
                with urllib.request.urlopen(del_req):
                    print(f"-> Removed {asset_name} from release!", flush=True)
            except Exception as e:
                print(f"[Warning] Failed deleting {asset_name}: {e}", flush=True)

    # Re-fetch release asset list
    try:
        with urllib.request.urlopen(urllib.request.Request(get_url, headers=headers)) as resp:
            release = json.loads(resp.read().decode())
    except:
        pass

    existing_assets = {a["name"]: a["id"] for a in release.get("assets", [])}

    for file_path in files_to_upload:
        if not os.path.exists(file_path):
            print(f"[Skip] File not found: {file_path}", flush=True)
            continue

        filename = os.path.basename(file_path)

        # Delete existing asset if present to ensure fresh overwrite
        if filename in existing_assets:
            asset_id = existing_assets[filename]
            print(f"[GitHub] Removing existing asset {filename} (ID: {asset_id}) for fresh upload...", flush=True)
            del_url = f"https://api.github.com/repos/{REPO}/releases/assets/{asset_id}"
            del_req = urllib.request.Request(del_url, headers=headers, method="DELETE")
            try:
                with urllib.request.urlopen(del_req):
                    pass
            except Exception as e:
                print(f"[Warning] Failed deleting asset: {e}", flush=True)

        upload_url = f"{upload_base}?name={urllib.parse.quote(filename)}"
        print(f"[GitHub] Uploading {filename} ({os.path.getsize(file_path)} bytes) via curl...", flush=True)

        curl_cmd = [
            "curl.exe",
            "-X", "POST",
            "-H", f"Authorization: Bearer {token}",
            "-H", "Content-Type: application/octet-stream",
            "--data-binary", f"@{file_path}",
            "--progress-bar",
            upload_url
        ]

        try:
            res = subprocess.run(curl_cmd, capture_output=True, text=True, check=True)
            print(f"[GitHub] Successfully uploaded {filename}!", flush=True)
        except subprocess.CalledProcessError as err:
            print(f"[Error] Failed uploading {filename}: {err.stderr or err.stdout}", flush=True)

    print("\n==========================================================", flush=True)
    print(f" SUCCESS: GitHub Release {VERSION_TAG} is now LIVE!", flush=True)
    print(f"   VayuClientSetup.exe + HUD JARs + manifest uploaded.", flush=True)
    print(f" URL: https://github.com/{REPO}/releases/tag/{VERSION_TAG}", flush=True)
    print("==========================================================\n", flush=True)

if __name__ == "__main__":
    main()
