import os

# 1. Fix StoreManager.java
store_mgr_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\store\StoreManager.java'
with open(store_mgr_path, 'r', encoding='utf-8') as f:
    content = f.read()

if 'private void runOnClientThread' not in content:
    helper = """
    private void runOnClientThread(Runnable runnable) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(runnable);
        } else {
            runnable.run();
        }
    }
"""
    content = content.replace('public class StoreManager {', 'public class StoreManager {\n' + helper)
    with open(store_mgr_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Fixed StoreManager.java')

# 2. Fix RemoteCosmeticResolver.java
rcr_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\store\RemoteCosmeticResolver.java'
with open(rcr_path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('(Object)ex.getMessage()', '(Object)(ex != null ? ex.getMessage() : "unknown")')
content = content.replace('.exceptionally(ex -> {', '.exceptionally((Throwable ex) -> {')
with open(rcr_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed RemoteCosmeticResolver.java')

# 3. Fix WaypointsModule.java
wp_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\modules\impl\render\WaypointsModule.java'
with open(wp_path, 'r', encoding='utf-8') as f:
    content = f.read()
old_sp = '''        if (mc.getSingleplayerServer() != null) {
            return "singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName().toLowerCase(Locale.ROOT);
        }'''
new_sp = '''        if (mc.getSingleplayerServer() != null) {
            try {
                Object server = mc.getSingleplayerServer();
                java.lang.reflect.Method m = server.getClass().getMethod("getWorldData");
                Object wd = m.invoke(server);
                java.lang.reflect.Method getLevelName = wd.getClass().getMethod("getLevelName");
                return "singleplayer:" + getLevelName.invoke(wd).toString().toLowerCase(Locale.ROOT);
            } catch (Exception e) {
                return "singleplayer:world";
            }
        }'''
content = content.replace(old_sp, new_sp)
content = content.replace('List loaded = (List)this.gson.fromJson(Files.readString(this.configPath), WAYPOINT_LIST);', 'List<Waypoint> loaded = this.gson.fromJson(Files.readString(this.configPath), WAYPOINT_LIST);')
content = content.replace('loaded.removeIf(waypoint -> waypoint == null || waypoint.id == null || waypoint.name == null);', 'loaded.removeIf((Waypoint waypoint) -> waypoint == null || waypoint.id == null || waypoint.name == null);')
with open(wp_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed WaypointsModule.java')

# 4. Fix ArrayListModule.java
al_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\modules\impl\hud\ArrayListModule.java'
with open(al_path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('List enabledModules = ModuleManager.getInstance().getEnabledModules().stream().filter(m -> m != this).collect(Collectors.toList());', 'List<Module> enabledModules = ModuleManager.getInstance().getEnabledModules().stream().filter(m -> m != this).collect(Collectors.toList());')
content = content.replace('enabledModules.sort((a, b) ->', 'enabledModules.sort((Module a, Module b) ->')
with open(al_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed ArrayListModule.java')

# 5. Fix CoordinatesModule.java
coords_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\modules\impl\hud\CoordinatesModule.java'
with open(coords_path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('String biomePath = biomeHolder.unwrapKey().map(key -> key.registry().getPath()).orElse("unknown");', 'String biomePath = biomeHolder.unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");')
with open(coords_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed CoordinatesModule.java')

# 6. Fix PackDisplay.java
pd_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\modules\impl\hud\PackDisplay.java'
with open(pd_path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('Collection packs = mc.getResourcePackRepository().getSelectedPacks();', 'Collection<Pack> packs = mc.getResourcePackRepository().getSelectedPacks();')
with open(pd_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed PackDisplay.java')

# 7. Fix PotionHUD.java
pot_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\modules\impl\hud\PotionHUD.java'
with open(pot_path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('Collection effects = PotionHUD.mc.player.getActiveEffects();', 'Collection<MobEffectInstance> effects = PotionHUD.mc.player.getActiveEffects();')
with open(pot_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed PotionHUD.java')

# 8. Fix BlockOverlayModule.java
bo_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\modules\impl\render\BlockOverlayModule.java'
with open(bo_path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('Identifier blockId = BuiltInRegistries.BLOCK.getKey((Object)block);', 'Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);')
with open(bo_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed BlockOverlayModule.java')

# 9. Fix MotionBlurShaderManager.java
mb_path = r'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\vayuclient-hud-mod\src\main\java\com\vayuclient\hud\render\MotionBlurShaderManager.java'
with open(mb_path, 'r', encoding='utf-8') as f:
    content = f.read()
old_create = '''    private static GpuBuffer createBufferCompat() {
        GpuDevice device = RenderSystem.getDevice();
        Supplier<String> name = () -> "vayuclient-hud:MotionBlurUniforms";
        try {
            if (createBufferMethod == null) {
                createBufferMethod = device.getClass().getMethod("createBuffer", Supplier.class, Integer.TYPE, Long.TYPE);
            }
            return (GpuBuffer)createBufferMethod.invoke((Object)device, name, 130, 304L);
        }
        catch (NoSuchMethodException noSuchMethodException) {
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException("[VayuHUD] createBuffer failed", e);
        }
        throw new RuntimeException("[VayuHUD] No compatible createBuffer found on " + String.valueOf(device.getClass()));
    }'''
new_create = '''    private static GpuBuffer createBufferCompat() {
        try {
            Class<?> rs = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
            Object device = rs.getMethod("getDevice").invoke(null);
            Supplier<String> name = () -> "vayuclient-hud:MotionBlurUniforms";
            if (createBufferMethod == null) {
                createBufferMethod = device.getClass().getMethod("createBuffer", Supplier.class, Integer.TYPE, Long.TYPE);
            }
            return (GpuBuffer)createBufferMethod.invoke(device, name, 130, 304L);
        }
        catch (NoSuchMethodException noSuchMethodException) {
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException("[VayuHUD] createBuffer failed", e);
        }
        throw new RuntimeException("[VayuHUD] No compatible createBuffer found");
    }'''
content = content.replace(old_create, new_create)
with open(mb_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed MotionBlurShaderManager.java')
