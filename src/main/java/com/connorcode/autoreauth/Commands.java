package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.session.Session;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

import static com.connorcode.autoreauth.AutoReauth.client;
import static com.connorcode.autoreauth.AutoReauth.config;

public class Commands {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("auto-reauth").requires(requirement -> config.debug)
                .then(ClientCommandManager.literal("invalidate").executes(context -> {
                    var session = client.getSession();
                    var newSession = new Session(session.getUsername(), session.getUuidOrNull(), "", session.getXuid(), session.getClientId(), session.getAccountType());
                    try {
                        AuthUtils.setSession(newSession);
                    } catch (AuthenticationException e) {
                        throw new RuntimeException(e);
                    }
                    context.getSource().sendFeedback(Text.of("Session invalidated"));
                    return 1;
                })));
    }
}
