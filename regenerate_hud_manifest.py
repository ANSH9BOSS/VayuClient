"""
Regenerates vayu_hud_manifest.json from actual JAR files in Assets/Mods.
Scans every JAR, reads its embedded fabric.mod.json or vayuclient-hud-manifest.json,
and builds a fresh manifest that the VayuUIArtifactResolver can use.
"""
import os
import json
import zipfile
import hashlib
import re
import datetime
import sys

MODS_DIR = os.path.join(os.path.dirname(__file__), "VayuClient", "Assets", "Mods")
MANIFEST_PATH = os.path.join(MODS_DIR, "vayu_hud_manifest.json")

VERSION_RE = re.compile(
    r"vayuclient-hud-([0-9.]+)-mc([0-9a-z.]+)-?(universal|fabric|quilt|forge|neoforge)?\.jar",
    re.IGNORECASE
)

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def bytecode_to_java(major):
    # Java 8 = 52, 11 = 55, 17 = 61, 21 = 65
    return max(8, major - 44) if major >= 52 else 8

def read_jar_meta(jar_path):
    """Read embedded fabric.mod.json or vayuclient-hud-manifest.json from jar."""
    meta = {}
    try:
        with zipfile.ZipFile(jar_path, "r") as zf:
            names = zf.namelist()

            # Try vayuclient-hud-manifest.json first (most authoritative)
            for mname in ["vayuclient-hud-manifest.json", "vayuclient-ui-manifest.json"]:
                if mname in names:
                    try:
                        data = json.loads(zf.read(mname))
                        meta["vayuUiVersion"] = data.get("vayuUiVersion") or data.get("version", "")
                        meta["minecraftCompatibility"] = data.get("minecraftCompatibility", "")
                        meta["requiredJavaMajor"] = data.get("requiredJavaMajor", 0)
                        meta["bytecodeMajor"] = data.get("bytecodeMajor", 0)
                        loaders = data.get("supportedLoaders", [])
                        meta["supportedLoaders"] = loaders if loaders else ["Fabric", "Quilt", "NeoForge"]
                        break
                    except Exception:
                        pass

            # Fabric meta as fallback
            if "fabric.mod.json" in names and not meta.get("vayuUiVersion"):
                try:
                    data = json.loads(zf.read("fabric.mod.json"))
                    meta["vayuUiVersion"] = data.get("version", "")
                    meta["minecraftCompatibility"] = (
                        data.get("depends", {}).get("minecraft", "")
                        if isinstance(data.get("depends"), dict) else ""
                    )
                    if not meta.get("supportedLoaders"):
                        meta["supportedLoaders"] = ["Fabric", "Quilt"]
                except Exception:
                    pass

            # Bytecode from first .class file
            if not meta.get("bytecodeMajor"):
                for entry in zf.infolist():
                    if entry.filename.endswith(".class"):
                        raw = zf.read(entry.filename)
                        if len(raw) >= 8 and raw[:4] == b"\xca\xfe\xba\xbe":
                            major = (raw[6] << 8) | raw[7]
                            meta["bytecodeMajor"] = major
                            if not meta.get("requiredJavaMajor"):
                                meta["requiredJavaMajor"] = bytecode_to_java(major)
                        break
    except Exception as e:
        print(f"  WARN: Could not read JAR meta from {os.path.basename(jar_path)}: {e}")
    return meta

def parse_mc_versions_from_filename(mc_ver_str):
    """
    Given 'mc1.21.11', 'mc26.2', etc. infer a supportedVersions list
    and a compatibilityRange string.
    """
    v = mc_ver_str.lower()

    # 26.x family
    if v.startswith("26"):
        parts = v.split(".")
        if parts[0] == "26":
            base = "26"
            all_26 = ["26", "26.1", "26.1.2", "26.2", "26.2.0"]
            # Return the exact version plus close siblings
            return [mc_ver_str], f">={mc_ver_str} <={mc_ver_str}", all_26

    # 1.21.x family
    if v.startswith("1.21"):
        parts = v.split(".")
        patch = int(parts[2]) if len(parts) > 2 else 0
        # All subversions from 1.21 up to specified patch
        supported = []
        for i in range(0, patch + 1):
            if i == 0:
                supported.append("1.21")
            else:
                supported.append(f"1.21.{i}")
        compat_range = f">=1.21 <={mc_ver_str}"
        return supported, compat_range, supported

    # Fallback
    return [mc_ver_str], f"={mc_ver_str}", [mc_ver_str]

def build_manifest():
    artifacts = []
    hud_product_version = "0.0.0"

    jar_files = sorted(
        [f for f in os.listdir(MODS_DIR) if f.endswith(".jar")],
        reverse=True  # newest first (1.9.1 before 1.9.0 before 1.8.x)
    )

    print(f"Scanning {len(jar_files)} JARs in {MODS_DIR}...")

    for jar_name in jar_files:
        jar_path = os.path.join(MODS_DIR, jar_name)
        m = VERSION_RE.match(jar_name)
        if not m:
            print(f"  SKIP (no match): {jar_name}")
            continue

        hud_ver = m.group(1)
        mc_ver  = m.group(2)
        flavor  = (m.group(3) or "universal").lower()

        # Track highest HUD product version
        try:
            hv_parts = tuple(int(x) for x in hud_ver.split("."))
            hp_parts = tuple(int(x) for x in hud_product_version.split("."))
            if hv_parts > hp_parts:
                hud_product_version = hud_ver
        except Exception:
            pass

        print(f"  Processing: {jar_name} (HUD {hud_ver}, MC {mc_ver}, {flavor})")

        meta = read_jar_meta(jar_path)

        supported_versions, compat_range, _ = parse_mc_versions_from_filename(mc_ver)

        # Loaders from filename flavor
        if flavor == "universal":
            loaders = meta.get("supportedLoaders") or ["Fabric", "Quilt", "NeoForge"]
        elif flavor == "fabric":
            loaders = ["Fabric", "Quilt"]
        elif flavor == "quilt":
            loaders = ["Quilt"]
        elif flavor in ("forge", "neoforge"):
            loaders = ["Forge", "NeoForge"]
        else:
            loaders = meta.get("supportedLoaders") or ["Fabric", "Quilt", "NeoForge"]

        bytecode = meta.get("bytecodeMajor", 65)
        req_java = meta.get("requiredJavaMajor", 0) or bytecode_to_java(bytecode)
        file_size = os.path.getsize(jar_path)
        sha = sha256_file(jar_path)

        artifact = {
            "hudProductVersion": hud_ver,
            "minecraftVersion": mc_ver,
            "minecraftCompatibilityRange": compat_range,
            "supportedVersions": supported_versions,
            "supportedLoaders": loaders,
            "loaderStatus": {l: "Supported" for l in loaders},
            "requiredJavaVersion": req_java,
            "bytecodeMajor": bytecode,
            "adapter": f"v{mc_ver.replace('.', '_')}",
            "artifactFilename": jar_name,
            "sha256": sha,
            "fileSizeBytes": file_size,
            "compatibilityStatus": "Verified",
            "entrypoints": [
                "com.vayuclient.hud.VayuHUDClient",
                "com.vayuclient.hud.VayuHUD"
            ],
            "mixins": [
                "vayuclient-hud.mixins.json",
                "vayuclient-hud.client.mixins.json"
            ]
        }
        artifacts.append(artifact)

    manifest = {
        "schemaVersion": "2.0",
        "generatedAt": datetime.datetime.utcnow().isoformat() + "+00:00",
        "hudProductVersion": hud_product_version,
        "artifacts": artifacts
    }

    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    print(f"\nManifest written: {MANIFEST_PATH}")
    print(f"  Total artifacts: {len(artifacts)}")
    print(f"  HUD product version: {hud_product_version}")
    return len(artifacts)

if __name__ == "__main__":
    count = build_manifest()
    print(f"\nDone. {count} artifacts registered.")
    sys.exit(0)
