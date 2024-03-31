package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.auth.AuthUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import static com.connorcode.autoreauth.Main.authStatus;
import static com.connorcode.autoreauth.Reauth.tickAuthStatus;

public class ServerWaitingScreen extends WaitingScreen {
    ServerAddress address;
    ServerInfo info;
    boolean quickPlay;

    public ServerWaitingScreen(Screen parent, ServerAddress address, ServerInfo info, boolean quickPlay) {
        super(parent, Text.literal("You will automatically join the server once you are authenticated."));
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
