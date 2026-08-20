import os
import re

# 1. Fix build_vayuclient_hud.py classpath filtering
build_script = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\build_vayuclient_hud.py'
with open(build_script, 'r', encoding='utf-8') as f:
    bcontent = f.read()

# Make sure ancient realms libraries are excluded from classpath
old_filter = 'if "vayuclient-hud" not in jar and "fastclient" not in jar:'
new_filter = 'if "vayuclient-hud" not in jar and "fastclient" not in jar and "mojang\\\\realms" not in jar and "mojang/realms" not in jar:'
bcontent = bcontent.replace(old_filter, new_filter)
with open(build_script, 'w', encoding='utf-8') as f:
    f.write(bcontent)
print('Updated build_vayuclient_hud.py classpath filter')

# 2. Fix VayuHUDClient.java
hud_client = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\VayuHUDClient.java'
with open(hud_client, 'r', encoding='utf-8') as f:
    hcontent = f.read()

# Replace ClientPlayConnectionEvents with tick-based connection tracker
old_reg = '''        VayuUserCache.getInstance();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null) {
                return;
            }
            String localUsername = client.player.getGameProfile().getName();
            VayuUserCache cache = VayuUserCache.getInstance();
            cache.pingServer(localUsername);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((_handler, _client) -> VayuUserCache.getInstance().onDisconnect());'''

new_reg = '''        VayuUserCache.getInstance();'''

hcontent = hcontent.replace(old_reg, new_reg)

old_tick = '''        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                this.moduleManager.onTick();
            }
        });'''

new_tick = '''        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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

hcontent = hcontent.replace(old_tick, new_tick)
if 'private boolean joinedServer = false;' not in hcontent:
    hcontent = hcontent.replace('private RenderManager renderManager;', 'private RenderManager renderManager;\n    private boolean joinedServer = false;')

with open(hud_client, 'w', encoding='utf-8') as f:
    f.write(hcontent)
print('Updated VayuHUDClient.java')

# 3. Fix StoreManager.java futures
sm_file = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\store\StoreManager.java'
with open(sm_file, 'r', encoding='utf-8') as f:
    scontent = f.read()

# Remove raw ((CompletableFuture) ...) casts
scontent = scontent.replace('((CompletableFuture)this.marketplaceClient.bootstrap(username)', 'this.marketplaceClient.bootstrap(username)')
scontent = scontent.replace('((CompletableFuture)this.marketplaceClient.purchase(username, id)', 'this.marketplaceClient.purchase(username, id)')
scontent = scontent.replace('((CompletableFuture)this.marketplaceClient.equip(username, id)', 'this.marketplaceClient.equip(username, id)')
scontent = scontent.replace('((CompletableFuture)this.marketplaceClient.unequip(username, id)', 'this.marketplaceClient.unequip(username, id)')
scontent = scontent.replace('((CompletableFuture)this.marketplaceClient.removeAll(username)', 'this.marketplaceClient.removeAll(username)')
scontent = scontent.replace('((CompletableFuture)this.marketplaceClient.claimDaily(username)', 'this.marketplaceClient.claimDaily(username)')

# Fix exceptionally lambdas
scontent = re.sub(r'\.exceptionally\(\(Throwable ex\) -> \{', '.exceptionally(ex -> {', scontent)
scontent = re.sub(r'\.exceptionally\(\(ex\) -> \{', '.exceptionally(ex -> {', scontent)

with open(sm_file, 'w', encoding='utf-8') as f:
    f.write(scontent)
print('Updated StoreManager.java')

# 4. Fix RemoteCosmeticResolver.java
rcr_file = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\store\RemoteCosmeticResolver.java'
with open(rcr_file, 'r', encoding='utf-8') as f:
    rcontent = f.read()

rcontent = rcontent.replace('((CompletableFuture)this.marketplaceClient.publicEquipped(staleUsernames)', 'this.marketplaceClient.publicEquipped(staleUsernames)')
rcontent = re.sub(r'\.exceptionally\(\(Throwable ex\) -> \{', '.exceptionally(ex -> {', rcontent)
with open(rcr_file, 'w', encoding='utf-8') as f:
    f.write(rcontent)
print('Updated RemoteCosmeticResolver.java')

# 5. Fix CoordinatesModule.java
cm_file = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\modules\impl\hud\CoordinatesModule.java'
with open(cm_file, 'r', encoding='utf-8') as f:
    ccontent = f.read()

ccontent = ccontent.replace('Holder biomeHolder = CoordinatesModule.mc.level.getBiome(pos);', 'Holder<net.minecraft.world.level.biome.Biome> biomeHolder = CoordinatesModule.mc.level.getBiome(pos);')
with open(cm_file, 'w', encoding='utf-8') as f:
    f.write(ccontent)
print('Updated CoordinatesModule.java')
