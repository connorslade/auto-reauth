package com.connorcode.authreauth;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

public class AutoReauth implements ClientModInitializer {
    public static Logger log = LogUtils.getLogger();
    public static MinecraftClient client = MinecraftClient.getInstance();


    @Override
    public void onInitializeClient() {
        log.info("Starting auto-reauth");
    }
}

// https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id={}&response_type=code&redirect_uri=http://localhost:%d/callback&scope=XboxLive.signin offline_access&state={}
// https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=e16699bb-2aa8-46da-b5e3-45cbcce29091&response_type=code&redirect_uri=http://localhost:9090/callback&scope=XboxLive.signin offline_access&state=1234
