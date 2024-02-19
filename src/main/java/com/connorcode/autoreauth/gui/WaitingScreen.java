package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.AutoReauth;
import com.connorcode.autoreauth.auth.AuthUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import static com.connorcode.autoreauth.AutoReauth.authStatus;
import static com.connorcode.autoreauth.AutoReauth.serverJoin;

public class WaitingScreen extends Screen {
    Screen parent;

    ServerAddress address;
    ServerInfo info;
    boolean quickPlay;

    public WaitingScreen(Screen parent, ServerAddress address, ServerInfo info, boolean quickPlay) {
        super(Text.of("Waiting for Reauth"));
        this.parent = parent;
        this.address = address;
        this.info = info;
        this.quickPlay = quickPlay;
    }

    @Override
    public void tick() {
        if (!authStatus.isDone()) return;
        if (!serverJoin.tryAcquire()) return;
        serverJoin.release();
        AuthUtils.connectToServer(address, info, quickPlay);
    }

    @Override
    public void close() {
        AutoReauth.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var txt = AutoReauth.client.textRenderer;

        var title = Text.literal("AutoReauth").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 10, 0xFFFFFF);

        var body = Text.literal("You will automatically join the server once you are authenticated.");
        context.drawCenteredTextWithShadow(txt, body, this.width / 2, this.height / 2 + txt.fontHeight / 2, 0xFFFFFF);
    }
}
