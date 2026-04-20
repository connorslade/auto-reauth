package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.auth.AuthUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import static com.connorcode.autoreauth.Main.authStatus;
import static com.connorcode.autoreauth.Reauth.tickAuthStatus;

public class ServerWaitingScreen extends WaitingScreen {
    ServerAddress address;
    ServerData info;
    boolean quickPlay;

    public ServerWaitingScreen(Screen parent, ServerAddress address, ServerData info, boolean quickPlay) {
        super(parent, Component.literal("You will automatically join the server once you are authenticated."));
        this.parent = parent;
        this.address = address;
        this.info = info;
        this.quickPlay = quickPlay;
    }

    @Override
    public void tick() {
        if (authStatus.getNow(AuthUtils.AuthStatus.Invalid).isOnline())
            AuthUtils.connectToServer(address, info, quickPlay);
        else tickAuthStatus(parent);
    }
}
