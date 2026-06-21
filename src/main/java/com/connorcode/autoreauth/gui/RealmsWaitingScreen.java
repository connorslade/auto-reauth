package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.auth.AuthUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static com.connorcode.autoreauth.Main.authStatus;
import static com.connorcode.autoreauth.Reauth.tickAuthStatus;

public class RealmsWaitingScreen extends WaitingScreen {
    public RealmsWaitingScreen(Screen parent) {
        super(parent, Component.literal("You will automatically continue to Realms once you are authenticated."));
        this.parent = parent;
    }

    @Override
    public void tick() {
        if (authStatus.getNow(AuthUtils.AuthStatus.Invalid).isOnline())
            Main.client.gui.setScreen(new RealmsMainScreen(parent));
        else tickAuthStatus(parent);
    }
}
