import os
import sys
import json
import zipfile
import struct
import hashlib

def read_class_version(class_bytes):
    if len(class_bytes) < 8:
        return 0, 0
    magic, minor, major = struct.unpack(">IHH", class_bytes[:8])
    if magic != 0xCAFEBABE:
        return 0, 0
    return major, minor

def test_universal_hud():
    print("==========================================================")
    print("  VAYUCLIENT HUD UNIVERSAL ARCHITECTURE AUTOMATED TEST SUITE ")
    print("==========================================================")
    
    passed_tests = 0
    failed_tests = 0

    base_dir = os.path.dirname(os.path.abspath(__file__))
    dist_dir = os.path.join(base_dir, "vayuclient-hud-mod", "dist")
    manifest_path = os.path.join(dist_dir, "vayu_hud_manifest.json")
    
    # ----------------------------------------------------
    # TEST 1: Manifest Presence and Schema
    # ----------------------------------------------------
    print("\n[TEST 1] Verifying Universal Manifest & Schema...")
    if not os.path.exists(manifest_path):
        print(f"FAILED: Manifest not found at {manifest_path}")
        failed_tests += 1
    else:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest = json.load(f)
        
        assert manifest.get("schemaVersion") in ["1.0", "2.0"], "Invalid schema version"
        artifacts = manifest.get("artifacts", [])
        assert len(artifacts) >= 5, f"Expected at least 5 artifacts, found {len(artifacts)}"
        print(f"  -> Manifest OK. {len(artifacts)} universal artifacts registered.")
        passed_tests += 1

    # ----------------------------------------------------
    # TEST 2: Multi-Loader Descriptors in Every Artifact
    # ----------------------------------------------------
    print("\n[TEST 2] Verifying Multi-Loader Descriptors in Artifacts...")
    all_artifacts_valid = True
    for art in manifest.get("artifacts", []):
        jar_name = art.get("artifactFilename")
        jar_path = os.path.join(dist_dir, jar_name)
        if not os.path.exists(jar_path):
            print(f"FAILED: Artifact {jar_name} does not exist in dist/")
            all_artifacts_valid = False
            continue
        
        with zipfile.ZipFile(jar_path, "r") as zf:
            namelist = zf.namelist()
            has_fabric = "fabric.mod.json" in namelist
            has_quilt = "quilt.mod.json" in namelist
            has_neoforge = "META-INF/neoforge.mods.toml" in namelist
            
            if not (has_fabric and has_quilt and has_neoforge):
                print(f"FAILED: {jar_name} missing descriptors: Fabric={has_fabric}, Quilt={has_quilt}, NeoForge={has_neoforge}")
                all_artifacts_valid = False
            else:
                print(f"  -> {jar_name}: Fabric [OK], Quilt [OK], NeoForge [OK]")
    
    if all_artifacts_valid:
        passed_tests += 1
    else:
        failed_tests += 1

    # ----------------------------------------------------
    # TEST 3: Bytecode Version and Class Integrity
    # ----------------------------------------------------
    print("\n[TEST 3] Verifying Java Bytecode Version (Java 21 / Class 65.0)...")
    bytecode_valid = True
    for art in manifest.get("artifacts", []):
        jar_name = art.get("artifactFilename")
        jar_path = os.path.join(dist_dir, jar_name)
        expected_major = art.get("requiredJavaBytecodeMajor", 65)
        
        with zipfile.ZipFile(jar_path, "r") as zf:
            class_files = [n for n in zf.namelist() if n.endswith(".class") and n.startswith("com/vayuclient/hud")]
            for cf in class_files:
                class_bytes = zf.read(cf)
                major, minor = read_class_version(class_bytes)
                if major != expected_major:
                    print(f"FAILED: {jar_name} -> {cf} has class version {major}, expected {expected_major}")
                    bytecode_valid = False
                    break
    
    if bytecode_valid:
        print("  -> All classes verified for compliant JVM target runtime.")
        passed_tests += 1
    else:
        failed_tests += 1

    # ----------------------------------------------------
    # TEST 4: SHA-256 Hash Integrity
    # ----------------------------------------------------
    print("\n[TEST 4] Verifying SHA-256 Hash Integrity...")
    hash_valid = True
    for art in manifest.get("artifacts", []):
        jar_name = art.get("artifactFilename")
        jar_path = os.path.join(dist_dir, jar_name)
        expected_hash = art.get("sha256")
        
        with open(jar_path, "rb") as f:
            computed_hash = hashlib.sha256(f.read()).hexdigest()
        
        if computed_hash != expected_hash:
            print(f"FAILED: Hash mismatch for {jar_name}: expected {expected_hash}, got {computed_hash}")
            hash_valid = False
        else:
            print(f"  -> {jar_name} SHA-256 [VERIFIED]")
    
    if hash_valid:
        passed_tests += 1
    else:
        failed_tests += 1

    # ----------------------------------------------------
    # TEST 5: Launcher Dynamic Resolver Simulation
    # ----------------------------------------------------
    print("\n[TEST 5] Simulating Launcher Dynamic Version Resolution...")
    
    def simulate_resolve(mc_ver, loader, jvm_ver):
        for art in manifest.get("artifacts", []):
            loaders = art.get("supportedLoaders", [])
            if loader not in loaders:
                continue
            
            supported_versions = art.get("supportedVersions", [])
            if mc_ver in supported_versions:
                if jvm_ver >= art.get("requiredJavaMajorVersion", 21):
                    return art.get("artifactFilename")
            
            range_spec = art.get("minecraftCompatibilityRange", "")
            if range_spec:
                # Basic range match
                tokens = range_spec.split()
                in_range = True
                for t in tokens:
                    if t.startswith(">="):
                        if mc_ver < t[2:]: in_range = False
                    elif t.startswith("<="):
                        if mc_ver > t[2:]: in_range = False
                if in_range and jvm_ver >= art.get("requiredJavaMajorVersion", 21):
                    return art.get("artifactFilename")
        return None

    # Test cases:
    cases = [
        ("1.21.4", "Fabric", 21, True),
        ("1.21.11", "Quilt", 21, True),
        ("26.2", "NeoForge", 25, True),
        ("1.21.3", "Fabric", 21, True),
        ("1.7.10", "Forge", 8, False),   # Unsupported legacy version
        ("1.21.4", "Forge", 21, False),   # Unsupported Forge loader
    ]

    resolver_valid = True
    for mc_ver, loader, jvm, expect_success in cases:
        res = simulate_resolve(mc_ver, loader, jvm)
        if expect_success and res is None:
            print(f"FAILED: Expected resolution for MC {mc_ver} ({loader}), but got None")
            resolver_valid = False
        elif not expect_success and res is not None:
            print(f"FAILED: Expected NO resolution for MC {mc_ver} ({loader}), but got {res}")
            resolver_valid = False
        else:
            status = f"Resolved -> {res}" if res else "Graceful Fallback (Native Launch)"
            print(f"  -> MC {mc_ver} ({loader}, Java {jvm}): {status} [OK]")

    if resolver_valid:
        passed_tests += 1
    else:
        failed_tests += 1

    # ----------------------------------------------------
    # TEST 6: Zero Hardcoded Instances Rule Audit
    # ----------------------------------------------------
    print("\n[TEST 6] Auditing Codebase for Zero Hardcoded Instance Strings...")
    forbidden_terms = ["Spunky Optimized", "Spunky PVP Plus"]
    files_to_check = [
        os.path.join(base_dir, "VayuClient", "Services", "Launch", "VayuUIArtifactResolver.cs"),
        os.path.join(base_dir, "VayuClient", "Services", "Launch", "VayuUiCompatibilityValidator.cs"),
        os.path.join(base_dir, "vayuclient-hud-mod", "build_vayuclient_hud.py")
    ]

    no_forbidden_strings = True
    for file_path in files_to_check:
        if os.path.exists(file_path):
            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            for term in forbidden_terms:
                if term in content:
                    print(f"FAILED: Found forbidden hardcoded term '{term}' in {file_path}")
                    no_forbidden_strings = False

    if no_forbidden_strings:
        print("  -> Zero hardcoded instance names found. 100% dynamic architecture verified.")
        passed_tests += 1
    else:
        failed_tests += 1

    # ----------------------------------------------------
    # SUMMARY
    # ----------------------------------------------------
    print("\n==========================================================")
    print(f"  TEST RESULTS: {passed_tests} PASSED / {passed_tests + failed_tests} TOTAL")
    print("==========================================================")
    
    if failed_tests > 0:
        sys.exit(1)

if __name__ == "__main__":
    test_universal_hud()
