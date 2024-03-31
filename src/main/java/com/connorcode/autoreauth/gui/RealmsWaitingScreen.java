package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.auth.AuthUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;

import static com.connorcode.autoreauth.Main.authStatus;
import static com.connorcode.autoreauth.Reauth.tickAuthStatus;

public class RealmsWaitingScreen extends WaitingScreen {
    public RealmsWaitingScreen(Screen parent) {
        super(parent, Text.literal("You will automatically continue to Realms once you are authenticated."));
        this.parent = parent;
    }

    @Override
    public void tick() {
        if (authStatus.getNow(AuthUtils.AuthStatus.Invalid).isOnline())
            Main.client.setScreen(new RealmsMainScreen(parent));
        else tickAuthStatus(parent);
    }
}
