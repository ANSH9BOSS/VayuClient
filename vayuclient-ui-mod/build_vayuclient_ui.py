import os
import sys
import glob
import shutil
import zipfile
import subprocess

def main():
    print("==========================================================")
    print(" Building VayuClient Minecraft UI Mod v1.6.0 (ANSH9BOSS)")
    print("==========================================================")

    root_dir = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(root_dir, "src", "main", "java")
    res_dir = os.path.join(root_dir, "src", "main", "resources")
    build_dir = os.path.join(root_dir, "build", "classes")
    dist_dir = os.path.join(root_dir, "dist")

    if os.path.exists(build_dir):
        shutil.rmtree(build_dir)
    os.makedirs(build_dir, exist_ok=True)
    os.makedirs(dist_dir, exist_ok=True)

    javac = r"C:\Users\ANSH\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\javac.exe"
    if not os.path.exists(javac):
        javac = r"C:\Users\ANSH\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows-x64\java-runtime-delta\bin\javac.exe"
    if not os.path.exists(javac):
        javac = "javac"

    # Core classpath
    mc_jar = r"C:\Users\ANSH\AppData\Roaming\VayuClient\versions\26.2\26.2.jar"
    if not os.path.exists(mc_jar):
        mc_jar = r"C:\Users\ANSH\AppData\Roaming\.minecraft\versions\26.2\26.2.jar"

    sponge_mixin = r"C:\Users\ANSH\AppData\Roaming\VayuClient\libraries\net\fabricmc\sponge-mixin\0.17.3+mixin.0.8.7\sponge-mixin-0.17.3+mixin.0.8.7.jar"
    fabric_loader = r"C:\Users\ANSH\AppData\Roaming\VayuClient\libraries\net\fabricmc\fabric-loader\0.19.3\fabric-loader-0.19.3.jar"

    filtered_jars_file = r"C:\Users\ANSH\.gemini\antigravity-ide\scratch\filtered_jars.txt"
    lib_jars = []
    if os.path.exists(filtered_jars_file):
        with open(filtered_jars_file, "r", encoding="utf-8") as f:
            for line in f:
                p = line.strip()
                if p and os.path.exists(p):
                    lib_jars.append(p)

    all_jars = [mc_jar, sponge_mixin, fabric_loader] + lib_jars
    classpath = ";".join(all_jars)

    # Gather sources
    sources = []
    for r, d, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".java"):
                sources.append(os.path.join(r, f))

    print(f"Found {len(sources)} Java sources to compile.")

    options_file = os.path.join(root_dir, "options.txt")
    with open(options_file, "w", encoding="utf-8") as f:
        f.write("--release\n21\n")
        f.write("-proc:none\n")
        f.write("-encoding\nUTF-8\n")
        f.write("-cp\n")
        f.write(classpath + "\n")
        f.write("-d\n")
        f.write(build_dir + "\n")
        for s in sources:
            f.write(s + "\n")

    cmd = [javac, f"@{options_file}"]
    print("Compiling Java classes targeting Java 21 (class version 65.0)...")
    res = subprocess.run(cmd, capture_output=True, text=True)

    if res.returncode != 0:
        print("COMPILATION FAILED!")
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
        sys.exit(1)

    print("Compilation succeeded with 0 errors!")

    # Copy resources to build directory
    if os.path.exists(res_dir):
        for r, d, files in os.walk(res_dir):
            for f in files:
                src_path = os.path.join(r, f)
                rel_path = os.path.relpath(src_path, res_dir)
                dest_path = os.path.join(build_dir, rel_path)
                os.makedirs(os.path.dirname(dest_path), exist_ok=True)
                shutil.copy2(src_path, dest_path)

    # Package jar
    out_jar = os.path.join(dist_dir, "vayuclient-ui-1.6.0.jar")
    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as z:
        for r, d, files in os.walk(build_dir):
            for f in files:
                full_path = os.path.join(r, f)
                rel_path = os.path.relpath(full_path, build_dir)
                z.write(full_path, rel_path)

    print(f"SUCCESSFULLY PACKAGED JAR: {out_jar}")

    # Bytecode Verification Gate: Read actual class file header from the generated JAR
    with zipfile.ZipFile(out_jar, "r") as z:
        class_entry = "com/vayuclient/ui/VayuClientUI.class"
        if class_entry not in z.namelist():
            raise RuntimeError(f"Entrypoint class {class_entry} missing from {out_jar}")
        
        class_bytes = z.read(class_entry)
        if len(class_bytes) < 8:
            raise RuntimeError("Corrupted class file: length < 8 bytes")
        
        magic = class_bytes[0:4]
        if magic != b'\xca\xfe\xba\xbe':
            raise RuntimeError(f"Invalid magic number in compiled class: {magic.hex()}")
        
        minor_ver = int.from_bytes(class_bytes[4:6], byteorder='big')
        major_ver = int.from_bytes(class_bytes[6:8], byteorder='big')
        
        print(f"[BYTECODE VERIFIED] Class file major version: {major_ver} (minor: {minor_ver})")
        if major_ver != 65:
            raise RuntimeError(f"CRITICAL BYTECODE MISMATCH: Expected major version 65 (Java 21), got {major_ver}!")
        
        manifest_entry = "vayuclient-ui-manifest.json"
        if manifest_entry not in z.namelist():
            raise RuntimeError(f"Required artifact manifest {manifest_entry} missing from {out_jar}")
        print(f"[MANIFEST VERIFIED] {manifest_entry} is present in archive.")

    # Copy to all VayuClient instance mods folders & assets folders
    target_dirs = [
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\Instances\Spunky Optimized 1.0.0\game\mods",
        r"C:\Users\ANSH\AppData\Roaming\VayuClient\Instances\Spunky Optimized\mods",
        r"c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\VayuClient\Assets\Mods",
        r"c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\dist\Assets\Mods",
        r"c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\VayuClient\bin\Release\net8.0-windows\Assets\Mods",
        r"c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\VayuClient\bin\Release\net8.0-windows\win-x64\Assets\Mods"
    ]

    for t in target_dirs:
        os.makedirs(t, exist_ok=True)
        dest = os.path.join(t, "vayuclient-ui-1.6.0.jar")
        shutil.copy2(out_jar, dest)
        print(f"-> Installed to: {dest}")

if __name__ == "__main__":
    main()
