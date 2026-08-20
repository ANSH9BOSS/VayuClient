/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ModInitializer
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.fastclient.hud;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastClientHUD
implements ModInitializer {
    public static final String MOD_ID = "fast-client-hud";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"fast-client-hud");

    public void onInitialize() {
        LOGGER.info("Hello Fabric world!");
    }
}

