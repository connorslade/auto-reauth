package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public abstract class WaitingScreen extends Screen {
    Screen parent;
    Component message;

    protected WaitingScreen(Screen parent, Component message) {
        super(Component.nullToEmpty("Waiting for Reauth"));
        this.parent = parent;
        this.message = message;
    }

    @Override
    public void onClose() {
        Main.client.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        var txt = Main.client.font;

        var title = Component.literal("AutoReauth").withStyle(Style.EMPTY.withBold(true));
        context.centeredText(txt, title, this.width / 2, 20, 0xFFFFFFFF);
        context.centeredText(txt, message, this.width / 2, this.height / 2 + txt.lineHeight / 2, 0xFFFFFFFF);
    }
}
