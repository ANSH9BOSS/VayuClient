import os
import re
import shutil

decomp_src = r'C:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\decompiled_src'
vayu_src_root = r'C:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java'

if os.path.exists(vayu_src_root):
    shutil.rmtree(vayu_src_root)
os.makedirs(vayu_src_root, exist_ok=True)

name_map = {
    'FastClientHUDClient.java': 'VayuHUDClient.java',
    'FastClientHUD.java': 'VayuHUD.java',
    'FastClientFonts.java': 'VayuFonts.java',
    'FastClientUI.java': 'VayuHUDUI.java',
    'FastClientSkinsModule.java': 'VayuSkinsModule.java',
    'FastClientUserCache.java': 'VayuUserCache.java',
}

class_replacements = [
    ('FastClientHUDClient', 'VayuHUDClient'),
    ('FastClientHUD', 'VayuHUD'),
    ('FastClientFonts', 'VayuFonts'),
    ('FastClientUI', 'VayuHUDUI'),
    ('FastClientSkinsModule', 'VayuSkinsModule'),
    ('FastClientUserCache', 'VayuUserCache'),
    ('net.fastclient.hud', 'com.vayuclient.hud'),
    ('net/fastclient/hud', 'com/vayuclient/hud'),
    ('fastclient-hud', 'vayuclient-hud'),
    ('fastclient', 'vayuclient'),
    ('FastClient', 'VayuClient'),
    ('Fast Client', 'VayuClient'),
    ('FASTCLIENT', 'VAYUCLIENT'),
    ('Fastclient', 'VayuClient'),
]

copied = 0
for root, dirs, files in os.walk(decomp_src):
    for f in files:
        if f.endswith('.java'):
            src_file = os.path.join(root, f)
            rel = os.path.relpath(src_file, decomp_src)
            
            # Replace net/fastclient/hud with com/vayuclient/hud
            rel_vayu = rel.replace(os.path.join('net', 'fastclient', 'hud'), os.path.join('com', 'vayuclient', 'hud'))
            
            base = os.path.basename(rel_vayu)
            if base in name_map:
                rel_vayu = os.path.join(os.path.dirname(rel_vayu), name_map[base])
                
            target_path = os.path.join(vayu_src_root, rel_vayu)
            os.makedirs(os.path.dirname(target_path), exist_ok=True)
            
            with open(src_file, 'r', encoding='utf-8', errors='ignore') as sf:
                content = sf.read()
                
            for old, new in class_replacements:
                content = content.replace(old, new)
                
            with open(target_path, 'w', encoding='utf-8') as tf:
                tf.write(content)
            copied += 1

print(f'Successfully transformed {copied} files to {vayu_src_root}')
