import json
import os

path = os.path.expandvars(r"%APPDATA%\VayuClient\cache\version_manifest.json")
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

targets = [
    "26.2", "26.1.2", "26.1",
    "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5",
    "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
    "1.20.6", "1.20.4", "1.20.2", "1.20.1",
    "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5", "1.15.2", "1.14.4",
    "1.12.2", "1.8.9", "1.7.10"
]

out_lines = []
for t in targets:
    m = next((v for v in data.get("versions", []) if v["id"] == t), None)
    if m:
        out_lines.append(f'                new() {{ Id = "{m["id"]}", Type = "{m["type"]}", Url = "{m["url"]}", ReleaseDate = DateTime.Parse("{m["releaseTime"]}") }},')

with open("scratch/fallback_csharp.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(out_lines))
print(f"Wrote {len(out_lines)} fallback entries.")
