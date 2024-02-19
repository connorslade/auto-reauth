package com.connorcode.autoreauth;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

public class AutoReauth implements ClientModInitializer {
    public static Logger log = LogUtils.getLogger();
    public static MinecraftClient client = MinecraftClient.getInstance();
    public static final Path directory = client.runDirectory.toPath().resolve("config/auto-reauth");

    public static Optional<Config> config = Optional.empty();


    @Override
    public void onInitializeClient() {
        log.info("Starting auto-reauth");

        config = Config.load();
        if (config.isEmpty()) log.info("No config found");
        else log.info("Config loaded");
    }
}
