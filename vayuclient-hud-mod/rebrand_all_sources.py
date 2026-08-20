import os
import re

src_dir = r'C:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java'

replacements = [
    # Packages & classes
    ('net.fastclient.hud', 'com.vayuclient.hud'),
    ('net/fastclient/hud', 'com/vayuclient/hud'),
    ('FastClientHUDClient', 'VayuHUDClient'),
    ('FastClientHUD', 'VayuHUD'),
    ('FastClientFonts', 'VayuFonts'),
    ('FastClientUI', 'VayuHUDUI'),
    ('FastClientSkinsModule', 'VayuSkinsModule'),
    ('FastClientUserCache', 'VayuUserCache'),
    
    # FastClient branding variations
    ('FastClient HUD', 'VayuClient HUD'),
    ('FastClient Hud', 'VayuClient HUD'),
    ('Fastclient HUD', 'VayuClient HUD'),
    ('FastClient', 'VayuClient'),
    ('FASTCLIENT', 'VAYUCLIENT'),
    ('Fast Client', 'VayuClient'),
    ('Fast client', 'VayuClient'),
    ('fastclient-hud', 'vayuclient-hud'),
    ('fast-client-hud', 'vayuclient-hud'),
    ('fastclient', 'vayuclient'),
    ('fastClient', 'vayuClient'),
    ('fast_client', 'vayu_client'),
    ('FastClient Team', 'ANSH9BOSS & VayuClient Team'),
    ('https://github.com/FastClient/fastclient-hud', 'https://github.com/ANSH9BOSS/VayuClient'),
    ('discord.gg/fastclient', 'discord.gg/vayuclient'),
    ('fastclient.net', 'vayuclient.com'),
    
    # Internal variables & constants
    ('fastClientSkinEnabled', 'vayuSkinEnabled'),
    ('isFastClientSkinEnabled', 'isVayuSkinEnabled'),
    ('FASTCLIENT_SKIN', 'VAYU_SKIN'),
    ('FAST_CLIENT', 'VAYU_CLIENT'),
]

modified_count = 0
for root, dirs, files in os.walk(src_dir):
    for f in files:
        if f.endswith('.java'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8', errors='ignore') as file:
                content = file.read()
            
            orig = content
            for old, new in replacements:
                content = content.replace(old, new)
                
            if content != orig:
                with open(path, 'w', encoding='utf-8') as file:
                    file.write(content)
                modified_count += 1

print(f'Rebranded {modified_count} source files.')
