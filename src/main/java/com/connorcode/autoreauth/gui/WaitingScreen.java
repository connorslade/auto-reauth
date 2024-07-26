package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public abstract class WaitingScreen extends Screen {
    Screen parent;
    Text message;

    protected WaitingScreen(Screen parent, Text message) {
        super(Text.of("Waiting for Reauth"));
        this.parent = parent;
        this.message = message;
    }

    @Override
    public void close() {
        Main.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        var txt = Main.client.textRenderer;

        var title = Text.literal("AutoReauth").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(txt, message, this.width / 2, this.height / 2 + txt.fontHeight / 2, 0xFFFFFF);
    }
}
