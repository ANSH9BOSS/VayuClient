import os
import re

# 1. Fix InGameHudMixin.java and VayuHUDClient.java
hud_mixin = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\mixin\client\InGameHudMixin.java'
with open(hud_mixin, 'r', encoding='utf-8') as f:
    hcontent = f.read()

old_render = '''        VayuHUDClient instance = VayuHUDClient.getInstance();
        if (instance != null && instance.getRenderManager() != null) {
            instance.getRenderManager().onRenderHudDirect(graphics, deltaTracker);
        }'''

new_render = '''        if (this.minecraft.player != null) {
            ModuleManager mm = ModuleManager.getInstance();
            if (mm != null) {
                mm.onTick();
            }
        }
        VayuHUDClient instance = VayuHUDClient.getInstance();
        if (instance != null && instance.getRenderManager() != null) {
            instance.getRenderManager().onRenderHudDirect(graphics, deltaTracker);
        }'''

hcontent = hcontent.replace(old_render, new_render)
with open(hud_mixin, 'w', encoding='utf-8') as f:
    f.write(hcontent)
print('Updated InGameHudMixin.java')

# VayuHUDClient.java: remove ClientTickEvents.END_CLIENT_TICK call
hud_client = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\VayuHUDClient.java'
with open(hud_client, 'r', encoding='utf-8') as f:
    hc = f.read()

old_tick_reg = '''        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                this.moduleManager.onTick();
                if (!this.joinedServer) {
                    this.joinedServer = true;
                    String localUsername = client.player.getGameProfile().getName();
                    VayuUserCache.getInstance().pingServer(localUsername);
                }
            } else {
                if (this.joinedServer) {
                    this.joinedServer = false;
                    VayuUserCache.getInstance().onDisconnect();
                }
            }
        });'''

hc = hc.replace(old_tick_reg, '')
with open(hud_client, 'w', encoding='utf-8') as f:
    f.write(hc)
print('Updated VayuHUDClient.java')

# 2. Fix StoreManager.java: JsonPrimitive for json arrays
sm_file = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\store\StoreManager.java'
with open(sm_file, 'r', encoding='utf-8') as f:
    sm = f.read()

if 'import com.google.gson.JsonPrimitive;' not in sm:
    sm = sm.replace('import com.google.gson.JsonObject;', 'import com.google.gson.JsonObject;\nimport com.google.gson.JsonPrimitive;')

sm = sm.replace('owned.add(id);', 'owned.add(new JsonPrimitive(id));')
sm = sm.replace('equipped.add(id);', 'equipped.add(new JsonPrimitive(id));')
with open(sm_file, 'w', encoding='utf-8') as f:
    f.write(sm)
print('Updated StoreManager.java')

# 3. Fix MarketplaceClient.java: Gson.fromJson instead of JsonParser.parseString
mp_file = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\store\MarketplaceClient.java'
with open(mp_file, 'r', encoding='utf-8') as f:
    mp = f.read()

mp = mp.replace('return JsonParser.parseString((String)response.body()).getAsJsonObject();', 'return this.gson.fromJson((String)response.body(), JsonObject.class);')
with open(mp_file, 'w', encoding='utf-8') as f:
    f.write(mp)
print('Updated MarketplaceClient.java')

# 4. Fix LauncherSkinPreference.java: Gson.fromJson instead of JsonParser.parseString
lsp_file = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\launcher\LauncherSkinPreference.java'
with open(lsp_file, 'r', encoding='utf-8') as f:
    lsp = f.read()

if 'import com.google.gson.Gson;' not in lsp:
    lsp = lsp.replace('import com.google.gson.JsonObject;', 'import com.google.gson.Gson;\nimport com.google.gson.JsonObject;')

lsp = lsp.replace('JsonObject root = JsonParser.parseString((String)Files.readString(CONFIG_PATH)).getAsJsonObject();', 'JsonObject root = new Gson().fromJson((String)Files.readString(CONFIG_PATH), JsonObject.class);')
with open(lsp_file, 'w', encoding='utf-8') as f:
    f.write(lsp)
print('Updated LauncherSkinPreference.java')
