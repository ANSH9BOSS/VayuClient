import os

root_dir = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.join(root_dir, "src", "main", "java")

# Color mappings from old FastClient orange palette to official VayuClient Cyan palette
color_map = {
    "-39373": "-16723201",  # 0xFFFF6633 (Orange) -> 0xFF00D2FF (Vayu Cyan)
    "-34227": "-13058312",  # 0xFFFF7A4D (Orange Hover) -> 0xFF38BDF8 (Vayu Light Cyan)
    "-30106": "-9967111",   # 0xFFFF8A66 (Orange Border) -> 0xFF67E8F9 (Vayu Glow)
    "-34995": "-13058312",  # 0xFFFF774D -> 0xFF38BDF8
}

modified_files = []

for root, dirs, files in os.walk(src_dir):
    for f in files:
        if f.endswith(".java"):
            path = os.path.join(root, f)
            with open(path, "r", encoding="utf-8") as file:
                content = file.read()
            
            orig = content
            for old_c, new_c in color_map.items():
                content = content.replace(old_c, new_c)
            
            if content != orig:
                with open(path, "w", encoding="utf-8") as file:
                    file.write(content)
                modified_files.append(os.path.relpath(path, root_dir))

print(f"Successfully updated colors in {len(modified_files)} files:")
for mf in modified_files:
    print(f"  - {mf}")
