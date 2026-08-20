import os
import re

src_dir = r'C:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java'

# 1. Fix Mixin `(TargetClass)this` -> `(TargetClass)(Object)this`
mixin_casts = [
    (r'\(AbstractClientPlayer\)this', r'(AbstractClientPlayer)(Object)this'),
    (r'\(JoinMultiplayerScreen\)this', r'(JoinMultiplayerScreen)(Object)this'),
    (r'\(ParticleEngine\)this', r'(ParticleEngine)(Object)this'),
    (r'\(PauseScreen\)this', r'(PauseScreen)(Object)this'),
    (r'\(Screen\)this', r'(Screen)(Object)this'),
    (r'\(TitleScreen\)this', r'(TitleScreen)(Object)this'),
    (r'this instanceof JoinMultiplayerScreen', r'(Object)this instanceof JoinMultiplayerScreen'),
]

# 2. Fix GameProfile .name() -> .getName()
profile_fixes = [
    (r'\.getGameProfile\(\)\.name\(\)', r'.getGameProfile().getName()'),
    (r'\.getProfile\(\)\.name\(\)', r'.getProfile().getName()'),
]

# Apply to all files
for root, dirs, files in os.walk(src_dir):
    for f in files:
        if f.endswith('.java'):
            p = os.path.join(root, f)
            with open(p, 'r', encoding='utf-8') as file:
                content = file.read()
            
            orig = content
            for pat, repl in mixin_casts:
                content = re.sub(pat, repl, content)
            for pat, repl in profile_fixes:
                content = re.sub(pat, repl, content)
            
            # Remove (Object) casting in cir.setReturnValue((Object)...)
            content = re.sub(r'cir\.setReturnValue\(\(Object\)(.*?)\);', r'cir.setReturnValue(\1);', content)
            content = re.sub(r'callback\.setReturnValue\(\(Object\)(.*?)\);', r'callback.setReturnValue(\1);', content)
            
            if content != orig:
                with open(p, 'w', encoding='utf-8') as file:
                    file.write(content)

print("Applied automated Mixin and GameProfile fixes.")
