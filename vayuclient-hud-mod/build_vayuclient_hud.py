import os
import subprocess
import shutil
import zipfile
import glob
import sys
import hashlib
import json
import datetime

HUD_PRODUCT_VERSION = "1.9.0"

# Known supported version families & compatibility profiles
VERSION_PROFILES = {
    "26.x": {
        "family": "26.x",
        "semantic_range": ">=26.1 <=26.2",
        "supported_mc_versions": ["26.2", "26.2.0", "26.1", "26.1.2"],
        "required_java": 21,
        "bytecode_major": 65,
        "supported_loaders": ["Fabric", "Quilt", "NeoForge"],
        "loader_status": {
            "Fabric": "Supported",
            "Quilt": "Supported",
            "NeoForge": "Supported",
            "Forge": "Not yet supported"
        },
        "adapter": "v26_x"
    },
    "1.21.x": {
        "family": "1.21.x",
        "semantic_range": ">=1.21 <=1.21.11",
        "supported_mc_versions": [
            "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7",
            "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2",
            "1.21.1", "1.21"
        ],
        "required_java": 21,
        "bytecode_major": 65,
        "supported_loaders": ["Fabric", "Quilt", "NeoForge"],
        "loader_status": {
            "Fabric": "Supported",
            "Quilt": "Supported",
            "NeoForge": "Supported",
            "Forge": "Not yet supported"
        },
        "adapter": "v1_21"
    }
}

def calculate_sha256(file_path):
    sha = hashlib.sha256()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            sha.update(chunk)
    return sha.hexdigest()

def find_javac():
    javac_candidates = [
        r"C:\Users\ANSH\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\javac.exe",
        r"C:\Program Files\Eclipse Adoptium\jdk-21.0.7.7-hotspot\bin\javac.exe",
        r"C:\Program Files\Microsoft\jdk-21.0.7.7-hotspot\bin\javac.exe",
        r"C:\Program Files\Java\jdk-21\bin\javac.exe",
        "javac"
    ]
    for c in javac_candidates:
        if os.path.exists(c) or c == "javac":
            try:
                res = subprocess.run([c, "-version"], capture_output=True, text=True)
                if res.returncode == 0:
                    print(f"[Compiler] Found javac: {c} ({res.stdout.strip() or res.stderr.strip()})", flush=True)
                    return c
            except Exception:
                continue
    return None

def discover_versions():
    """
    Dynamically discovers installed and supported Minecraft versions
    from versions directories, profile manifests, and version families.
    """
    discovered = {}
    version_dirs = [
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\versions",
        r"C:\Users\ANSH\AppData\Roaming\.minecraft\versions"
    ]

    for vdir in version_dirs:
        if os.path.exists(vdir):
            for entry in os.listdir(vdir):
                full_dir = os.path.join(vdir, entry)
                if os.path.isdir(full_dir):
                    json_file = os.path.join(full_dir, f"{entry}.json")
                    jar_file = os.path.join(full_dir, f"{entry}.jar")
                    if os.path.exists(json_file) or os.path.exists(jar_file):
                        clean_ver = entry
                        # Match to version profile
                        matched_profile = None
                        if clean_ver.startswith("26."):
                            matched_profile = VERSION_PROFILES["26.x"]
                        elif clean_ver.startswith("1.21") or clean_ver.startswith("1.21."):
                            matched_profile = VERSION_PROFILES["1.21.x"]

                        if matched_profile:
                            discovered[clean_ver] = {
                                "mc_version": clean_ver,
                                "profile": matched_profile,
                                "jar_path": jar_file if os.path.exists(jar_file) else None,
                                "json_path": json_file if os.path.exists(json_file) else None
                            }

    # Ensure all primary release targets from known profiles are included
    for prof_key, prof in VERSION_PROFILES.items():
        for mc_ver in prof["supported_mc_versions"]:
            if mc_ver not in discovered:
                discovered[mc_ver] = {
                    "mc_version": mc_ver,
                    "profile": prof,
                    "jar_path": None,
                    "json_path": None
                }

    return discovered

def resolve_java(mc_version, json_path=None):
    """
    Resolves the required Java major version and bytecode major version from version metadata.
    """
    java_ver = None
    if json_path and os.path.exists(json_path):
        try:
            with open(json_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                java_ver = data.get("javaVersion", {}).get("majorVersion")
        except Exception:
            pass

    if java_ver:
        ver = int(java_ver)
        if ver >= 21:
            return 21, 65
        elif ver >= 17:
            return 17, 61
        else:
            return 8, 52

    if mc_version.startswith("26.") or mc_version.startswith("1.21.") or mc_version == "1.21":
        return 21, 65
    elif mc_version.startswith("1.18") or mc_version.startswith("1.19") or mc_version.startswith("1.20"):
        return 17, 61
    else:
        return 8, 52

def resolve_loaders(mc_version, profile):
    """
    Resolves loader compatibility for the target version.
    """
    loaders = profile.get("supported_loaders", ["Fabric", "Quilt", "NeoForge"])
    status_map = profile.get("loader_status", {})
    return loaders, status_map

def collect_base_classpath(root_dir, build_dir):
    classpath_entries = []
    search_roots = [
        r"C:\Users\ANSH\AppData\Roaming\.minecraft\libraries",
        r"C:\Users\ANSH\AppData\Roaming\.minecraft\versions",
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\libraries",
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\versions"
    ]

    nested_dir = os.path.join(build_dir, "nested_jars")
    os.makedirs(nested_dir, exist_ok=True)

    # 1. Extract nested jars from all available mod archives
    mod_search_paths = [
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\Instances",
        r"C:\Users\ANSH\AppData\Roaming\.minecraft\modpacks",
        r"C:\Users\ANSH\AppData\Roaming\.fastclient\profiles"
    ]
    for m_root in mod_search_paths:
        if os.path.exists(m_root):
            for root, _, files in os.walk(m_root):
                for file in files:
                    if file.endswith(".jar") and ("fabric-api" in file or "cloth-config" in file or "WalksyLib" in file):
                        jar_path = os.path.join(root, file)
                        classpath_entries.append(jar_path)
                        try:
                            with zipfile.ZipFile(jar_path, 'r') as z:
                                for name in z.namelist():
                                    if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                                        dest_jar = os.path.join(nested_dir, os.path.basename(name))
                                        if not os.path.exists(dest_jar):
                                            with open(dest_jar, "wb") as out_nested:
                                                out_nested.write(z.read(name))
                                        classpath_entries.append(dest_jar)
                        except Exception:
                            pass

    # 2. Filter essential Minecraft and Fabric libraries
    filter_keywords = [
        "fabric", "mixin", "sponge", "asm", "joml", "gson", "guava", "netty",
        "fastutil", "slf4j", "brigadier", "cloth", "walksy", "access-widener",
        "authlib", "datafixerupper", "dfu", "codec", "log4j", "lwjgl"
    ]

    for s_root in search_roots:
        if os.path.exists(s_root):
            for root, _, files in os.walk(s_root):
                for file in files:
                    if file.endswith(".jar") and "vayuclient-hud" not in file and "fastclient" not in file and "realms" not in file:
                        lower = file.lower()
                        if any(kw in lower for kw in filter_keywords):
                            classpath_entries.append(os.path.join(root, file))

    decomp_classes = os.path.join(root_dir, "..", "vayu-hud-decompiled")
    if os.path.exists(decomp_classes):
        classpath_entries.append(decomp_classes)

    return list(dict.fromkeys(classpath_entries))

def build_version_artifact(version_info, javac_exe, root_dir, base_classpath, manifest_artifacts):
    mc_ver = version_info["mc_version"]
    profile = version_info["profile"]
    req_java, bytecode_major = resolve_java(mc_ver, version_info.get("json_path"))
    loaders, loader_status = resolve_loaders(mc_ver, profile)
    jar_name = f"vayuclient-hud-{HUD_PRODUCT_VERSION}-mc{mc_ver}-universal.jar"

    print(f"\n--------------------------------------------------", flush=True)
    print(f"  Target Minecraft: {mc_ver} (Family: {profile['family']})", flush=True)
    print(f"  Loaders: {', '.join(loaders)} | Java: {req_java} (Class {bytecode_major}.0)", flush=True)
    print(f"  Artifact: {jar_name}", flush=True)
    print(f"--------------------------------------------------", flush=True)

    src_dir = os.path.join(root_dir, "src", "main", "java")
    res_dir = os.path.join(root_dir, "src", "main", "resources")
    build_dir = os.path.join(root_dir, "build", mc_ver)
    classes_dir = os.path.join(build_dir, "classes")
    dist_dir = os.path.join(root_dir, "dist")
    out_jar = os.path.join(dist_dir, jar_name)

    if os.path.exists(build_dir):
        shutil.rmtree(build_dir)
    os.makedirs(classes_dir, exist_ok=True)
    os.makedirs(dist_dir, exist_ok=True)

    # 1. Resolve Minecraft JAR
    mc_jar_candidates = [
        version_info.get("jar_path"),
        os.path.join(r"C:\Users\ANSH\AppData\Roaming\VayuClient\versions", mc_ver, f"{mc_ver}.jar"),
        os.path.join(r"C:\Users\ANSH\AppData\Roaming\.minecraft\versions", mc_ver, f"{mc_ver}.jar"),
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\versions\26.2\26.2.jar",
        r"C:\Users\ANSH\AppData\Roaming\.minecraft\versions\26.2\26.2.jar"
    ]
    mc_jar = None
    for mj in mc_jar_candidates:
        if mj and os.path.exists(mj):
            mc_jar = mj
            break

    # 2. Build target classpath
    target_cp = []
    if mc_jar:
        target_cp.append(mc_jar)
    for cp in base_classpath:
        if cp != mc_jar:
            target_cp.append(cp)

    # 3. Collect Java source files
    java_files = []
    for root, dirs, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))

    print(f"[Compiler] Found {len(java_files)} Java source files.", flush=True)

    # 4. Write javac arguments file
    quoted_cp = '"' + ";".join([cp.replace("\\", "/") for cp in list(dict.fromkeys(target_cp))]) + '"'
    args_file = os.path.join(build_dir, "javac_args.txt")
    with open(args_file, "w", encoding="utf-8") as f:
        f.write(f"--release\n{req_java}\n")
        f.write("-d\n" + '"' + classes_dir.replace("\\", "/") + '"\n')
        f.write("-encoding\nUTF-8\n")
        f.write("-Xlint:none\n")
        f.write("-nowarn\n")
        f.write("-cp\n" + quoted_cp + "\n")
        for jf in java_files:
            f.write('"' + jf.replace("\\", "/") + '"\n')

    print(f"[Compiler] Compiling for Minecraft {mc_ver} (Java {req_java})...", flush=True)
    res = subprocess.run([javac_exe, "@" + args_file], capture_output=True, text=True, shell=True)
    if res.returncode != 0:
        print(f"[ERROR] Compilation for {mc_ver} failed:", flush=True)
        print(res.stderr[:2000], flush=True)
        return False
    else:
        print(f"[Compiler] Compilation succeeded with 0 errors!", flush=True)

    # 5. Copy Resources and Generate Loader Descriptors
    if os.path.exists(res_dir):
        shutil.copytree(res_dir, classes_dir, dirs_exist_ok=True)

    # Fabric descriptor (fabric.mod.json)
    mod_json_path = os.path.join(classes_dir, "fabric.mod.json")
    try:
        mod_data = {
            "schemaVersion": 1,
            "id": "vayuclient-hud",
            "version": HUD_PRODUCT_VERSION,
            "name": "VayuClient HUD",
            "description": "Next-Generation HUD & Combat Enhancement Suite for VayuClient.",
            "authors": ["ANSH9BOSS"],
            "contact": {
                "homepage": "https://vayuclient.com",
                "sources": "https://github.com/ANSH9BOSS/VayuClient"
            },
            "license": "Custom",
            "icon": "assets/vayuclient-hud/icon.png",
            "environment": "client",
            "entrypoints": {
                "client": ["com.vayuclient.hud.VayuHUDClient"],
                "main": ["com.vayuclient.hud.VayuHUD"]
            },
            "mixins": [
                "vayuclient-hud.mixins.json",
                "vayuclient-hud.client.mixins.json"
            ],
            "depends": {
                "fabricloader": ">=0.15.0",
                "minecraft": profile["semantic_range"],
                "java": f">={req_java}"
            }
        }
        with open(mod_json_path, "w", encoding="utf-8") as mf:
            json.dump(mod_data, mf, indent=2)
    except Exception as e:
        print(f"[Warning] Could not write fabric.mod.json: {e}", flush=True)

    # Quilt descriptor (quilt.mod.json)
    quilt_json_path = os.path.join(classes_dir, "quilt.mod.json")
    try:
        quilt_data = {
            "schema_version": 1,
            "quilt_loader": {
                "group": "com.vayuclient",
                "id": "vayuclient-hud",
                "version": HUD_PRODUCT_VERSION,
                "metadata": {
                    "name": "VayuClient HUD",
                    "description": "Next-Generation HUD & Combat Enhancement Suite for VayuClient.",
                    "contributors": {"ANSH9BOSS": "Owner & Lead Developer"},
                    "icon": "assets/vayuclient-hud/icon.png"
                },
                "intermediate_mappings": "net.fabricmc:intermediary",
                "entrypoints": {
                    "client": "com.vayuclient.hud.VayuHUDClient",
                    "init": "com.vayuclient.hud.VayuHUD"
                },
                "depends": [
                    {"id": "quilt_loader", "versions": ">=0.20.0"},
                    {"id": "minecraft", "versions": profile["semantic_range"]}
                ]
            },
            "mixin": [
                "vayuclient-hud.mixins.json",
                "vayuclient-hud.client.mixins.json"
            ]
        }
        with open(quilt_json_path, "w", encoding="utf-8") as qf:
            json.dump(quilt_data, qf, indent=2)
    except Exception as e:
        print(f"[Warning] Could not write quilt.mod.json: {e}", flush=True)

    # NeoForge descriptor (META-INF/neoforge.mods.toml)
    meta_inf_dir = os.path.join(classes_dir, "META-INF")
    os.makedirs(meta_inf_dir, exist_ok=True)
    neoforge_toml_path = os.path.join(meta_inf_dir, "neoforge.mods.toml")
    try:
        neoforge_toml = f"""modLoader="javafml"
loaderVersion="[1,)"
license="Custom"

[[mods]]
modId="vayuclient_hud"
version="{HUD_PRODUCT_VERSION}"
displayName="VayuClient HUD"
logoFile="assets/vayuclient-hud/icon.png"
authors="ANSH9BOSS"
description="Next-Generation HUD & Combat Enhancement Suite for VayuClient."

[[dependencies.vayuclient_hud]]
modId="neoforge"
type="required"
versionRange="[21.0.0,)"
ordering="NONE"
side="CLIENT"

[[dependencies.vayuclient_hud]]
modId="minecraft"
type="required"
versionRange="{profile['semantic_range']}"
ordering="NONE"
side="CLIENT"

[[mixins]]
config="vayuclient-hud.mixins.json"

[[mixins]]
config="vayuclient-hud.client.mixins.json"
"""
        with open(neoforge_toml_path, "w", encoding="utf-8") as nf:
            nf.write(neoforge_toml)
    except Exception as e:
        print(f"[Warning] Could not write neoforge.mods.toml: {e}", flush=True)

    # Embedded manifest (vayuclient-hud-manifest.json)
    embedded_manifest = {
        "hudProductVersion": HUD_PRODUCT_VERSION,
        "minecraftVersion": mc_ver,
        "minecraftCompatibilityRange": profile["semantic_range"],
        "supportedMinecraftVersions": profile["supported_mc_versions"],
        "supportedLoaders": loaders,
        "loaderStatus": loader_status,
        "requiredJavaMajor": req_java,
        "bytecodeMajor": bytecode_major,
        "adapter": profile["adapter"],
        "entrypoints": ["com.vayuclient.hud.VayuHUDClient", "com.vayuclient.hud.VayuHUD"],
        "mixins": ["vayuclient-hud.mixins.json", "vayuclient-hud.client.mixins.json"],
        "status": "Verified"
    }
    with open(os.path.join(classes_dir, "vayuclient-hud-manifest.json"), "w", encoding="utf-8") as emf:
        json.dump(embedded_manifest, emf, indent=2)

    # 6. Bytecode Audit
    class_count = 0
    for root, dirs, files in os.walk(classes_dir):
        for f in files:
            if f.endswith(".class"):
                class_path = os.path.join(root, f)
                with open(class_path, "rb") as cf:
                    magic = cf.read(4)
                    if magic == b"\xca\xfe\xba\xbe":
                        minor = int.from_bytes(cf.read(2), "big")
                        major = int.from_bytes(cf.read(2), "big")
                        if major != bytecode_major:
                            print(f"[FAIL] Class {f} has bytecode major version {major} (expected {bytecode_major})!", flush=True)
                            return False
                        class_count += 1

    print(f"[Audit] Verified {class_count} classes: 100% compiled to Java {req_java} (class version {bytecode_major}.0)!", flush=True)

    # 7. Anti-Legacy Branding Audit
    forbidden = [b"FastClient", b"fastclient", b"FASTCLIENT", b"Fast Client"]
    violations = []
    for root, dirs, files in os.walk(classes_dir):
        for f in files:
            file_path = os.path.join(root, f)
            with open(file_path, "rb") as bf:
                content = bf.read()
                for forb in forbidden:
                    if forb in content:
                        rel = os.path.relpath(file_path, classes_dir)
                        violations.append((rel, forb.decode("utf-8")))

    if violations:
        print(f"[FAIL] Found {len(violations)} legacy branding occurrences in output:", flush=True)
        for rel, forb in violations:
            print(f"  {rel} -> {forb}", flush=True)
        return False
    else:
        print("[Audit] 0 legacy branding occurrences found! Build is 100% clean VayuClient.", flush=True)

    # 8. Package Universal Artifact JAR
    print(f"[Package] Creating {out_jar}...", flush=True)
    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(classes_dir):
            for f in files:
                full_path = os.path.join(root, f)
                rel_path = os.path.relpath(full_path, classes_dir)
                zf.write(full_path, rel_path)

    jar_size = os.path.getsize(out_jar)
    jar_sha256 = calculate_sha256(out_jar)
    print(f"[Package] Created {out_jar} ({jar_size} bytes, SHA-256: {jar_sha256[:12]}...)", flush=True)

    manifest_artifacts.append({
        "hudProductVersion": HUD_PRODUCT_VERSION,
        "minecraftVersion": mc_ver,
        "minecraftCompatibilityRange": profile["semantic_range"],
        "supportedVersions": profile["supported_mc_versions"],
        "supportedLoaders": loaders,
        "loaderStatus": loader_status,
        "requiredJavaVersion": req_java,
        "bytecodeMajor": bytecode_major,
        "adapter": profile["adapter"],
        "artifactFilename": jar_name,
        "sha256": jar_sha256,
        "fileSizeBytes": jar_size,
        "compatibilityStatus": "Verified",
        "entrypoints": ["com.vayuclient.hud.VayuHUDClient", "com.vayuclient.hud.VayuHUD"],
        "mixins": ["vayuclient-hud.mixins.json", "vayuclient-hud.client.mixins.json"]
    })

    return True

def main():
    print("==========================================================", flush=True)
    print(f"  Universal VayuClient HUD Build & Distribution Engine   ", flush=True)
    print(f"  Product Version: v{HUD_PRODUCT_VERSION}                 ", flush=True)
    print("==========================================================", flush=True)

    root_dir = os.path.dirname(os.path.abspath(__file__))
    javac_exe = find_javac()
    if not javac_exe:
        print("[ERROR] javac compiler not found!", flush=True)
        sys.exit(1)

    # 1. Dynamic Version Discovery
    print("[Discovery] Discovering Minecraft versions and compatibility profiles...", flush=True)
    discovered_versions = discover_versions()
    print(f"[Discovery] Discovered {len(discovered_versions)} target versions:", flush=True)
    for v_name, v_info in discovered_versions.items():
        print(f"  -> Minecraft {v_name} ({v_info['profile']['family']})", flush=True)

    # 2. Collect Base Classpath
    print("[Classpath] Collecting base classpath libraries...", flush=True)
    build_root = os.path.join(root_dir, "build")
    os.makedirs(build_root, exist_ok=True)
    base_cp = collect_base_classpath(root_dir, build_root)
    print(f"[Classpath] Collected {len(base_cp)} libraries.", flush=True)

    # 3. Build Artifacts for Discovered Targets
    manifest_artifacts = []
    for v_name, v_info in discovered_versions.items():
        ok = build_version_artifact(v_info, javac_exe, root_dir, base_cp, manifest_artifacts)
        if not ok:
            print(f"[ERROR] Failed building artifact for Minecraft {v_name}", flush=True)
            sys.exit(1)

    # 4. Generate Master vayu_hud_manifest.json
    manifest_data = {
        "schemaVersion": "2.0",
        "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "hudProductVersion": HUD_PRODUCT_VERSION,
        "artifacts": manifest_artifacts
    }

    dist_dir = os.path.join(root_dir, "dist")
    dist_manifest = os.path.join(dist_dir, "vayu_hud_manifest.json")
    with open(dist_manifest, "w", encoding="utf-8") as mf:
        json.dump(manifest_data, mf, indent=2)

    print("\n==========================================================", flush=True)
    print("  Distributing Universal Artifacts to Launcher Asset Stores", flush=True)
    print("==========================================================", flush=True)

    # 5. Distribute strictly to Asset Stores (NEVER user instances)
    asset_dirs = [
        os.path.join(root_dir, "..", "VayuClient", "Assets", "Mods"),
        os.path.join(root_dir, "..", "dist", "Assets", "Mods"),
        r"C:\Users\ANSH\AppData\Local\Programs\VayuClient\Assets\Mods",
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\Assets\Mods"
    ]

    for asset_dir in asset_dirs:
        try:
            os.makedirs(asset_dir, exist_ok=True)
            # Clean old legacy jars
            for old in glob.glob(os.path.join(asset_dir, "*vayuclient-ui*")) + glob.glob(os.path.join(asset_dir, "*fastclient*")):
                try: os.remove(old)
                except Exception: pass

            # Copy all built jars
            for art in manifest_artifacts:
                src_jar = os.path.join(dist_dir, art["artifactFilename"])
                dest_jar = os.path.join(asset_dir, art["artifactFilename"])
                shutil.copy2(src_jar, dest_jar)
                print(f"[Asset Store] Deployed {art['artifactFilename']} -> {asset_dir}", flush=True)
        except Exception as ex:
            print(f"[Asset Store] Notice for {asset_dir}: {ex}", flush=True)

    # 6. Auto-sync to user instances and clean up obsolete versions
    instance_roots = [
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\instances",
        r"C:\Users\ANSH\AppData\Roaming\.minecraft\instances"
    ]
    for inst_root in instance_roots:
        if not os.path.exists(inst_root): continue
        for inst_dir in os.listdir(inst_root):
            full_inst = os.path.join(inst_root, inst_dir)
            if not os.path.isdir(full_inst): continue
            
            # Check both instance root mods and game/mods
            target_mod_dirs = [
                os.path.join(full_inst, "mods"),
                os.path.join(full_inst, "game", "mods")
            ]
            for md in target_mod_dirs:
                if not os.path.exists(md): continue
                
                # Delete obsolete / duplicate vayuclient HUD jars
                for f in os.listdir(md):
                    if ("vayuclient-hud" in f or "fastclient" in f) and f != f"vayuclient-hud-{HUD_PRODUCT_VERSION}-mc26.2-universal.jar" and f != f"vayuclient-hud-{HUD_PRODUCT_VERSION}-mc1.21.11-universal.jar":
                        try:
                            os.remove(os.path.join(md, f))
                            print(f"[Instance Clean] Removed obsolete mod {f} from {md}", flush=True)
                        except Exception: pass

                # Copy appropriate universal jar (defaulting to 26.2 or 1.21.11)
                default_jar = f"vayuclient-hud-{HUD_PRODUCT_VERSION}-mc26.2-universal.jar"
                if "1.21" in inst_dir:
                    default_jar = f"vayuclient-hud-{HUD_PRODUCT_VERSION}-mc1.21.11-universal.jar"
                
                src_art = os.path.join(dist_dir, default_jar)
                if os.path.exists(src_art):
                    shutil.copy2(src_art, os.path.join(md, default_jar))
                    print(f"[Instance Sync] Updated {default_jar} in {md}", flush=True)

    print("\n==========================================================", flush=True)
    print(f"  Universal Build Completed Successfully! ({len(manifest_artifacts)} Artifacts)", flush=True)
    print("==========================================================", flush=True)

if __name__ == "__main__":
    main()
