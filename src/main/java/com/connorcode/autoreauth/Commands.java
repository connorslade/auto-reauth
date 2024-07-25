package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.util.Session;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.text.Text;

import java.util.Objects;

import static com.connorcode.autoreauth.Main.client;
import static com.connorcode.autoreauth.Main.config;

public class Commands {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("auto-reauth").requires(requirement -> config.debug)
                .then(ClientCommandManager.literal("invalidate").executes(context -> {
                    var session = client.getSession();
                    var newSession = new Session(session.getUsername(), Objects.requireNonNull(session.getUuidOrNull()).toString(), "", session.getXuid(), session.getClientId(), session.getAccountType());
                    try {
                        AuthUtils.setSession(newSession);
                    } catch (AuthenticationException e) {
                        // ignored
                    }
                    context.getSource().sendFeedback(Text.of("Session invalidated"));
                    return 1;
                })).then(ClientCommandManager.literal("kick").executes(context -> {
                    Objects.requireNonNull(client.player).networkHandler.onDisconnect(new DisconnectS2CPacket(Text.translatable("disconnect.kicked")));
                    return 1;
                })));
    }
}
