package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Unique;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

public class AutoReauth implements ClientModInitializer {
    public static Logger log = LogUtils.getLogger();
    public static MinecraftClient client = MinecraftClient.getInstance();
    public static final Path directory = client.runDirectory.toPath().resolve("config/auto-reauth");

    public static Config config = new Config();
    public static Semaphore serverJoin = new Semaphore(1);
    public static CompletableFuture<AuthUtils.AuthStatus> authStatus;
    public static long lastUpdate = System.currentTimeMillis();
    public static boolean sentToast = false;


    @Override
    public void onInitializeClient() {
        log.info("Starting auto-reauth");
        ClientCommandRegistrationCallback.EVENT.register(Commands::register);

        if (config.load()) log.info("Config loaded");
        else log.info("No config found");
    }
}
