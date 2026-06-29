package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.User;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import java.util.Objects;

import static com.connorcode.autoreauth.Main.*;

public class Commands {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommands.literal("auto-reauth").requires(requirement -> config.debug)
                .then(ClientCommands.literal("invalidate").executes(context -> {
                    var session = client.getUser();
                    var newSession = new User(session.getName(), session.getProfileId(), "", session.getXuid(), session.getClientId());
                    try {
                        AuthUtils.setSession(newSession);
                    } catch (AuthenticationException e) {
                        // ignored
                    }
                    sentToast = false;
                    authStatus = AuthUtils.getAuthStatus();
                    context.getSource().sendFeedback(Component.nullToEmpty("Session invalidated"));
                    return 1;
                })).then(ClientCommands.literal("kick").executes(context -> {
                    Objects.requireNonNull(client.player).connection.handleDisconnect(new ClientboundDisconnectPacket(Component.translatable("disconnect.kicked")));
                    return 1;
                })));
    }
}
