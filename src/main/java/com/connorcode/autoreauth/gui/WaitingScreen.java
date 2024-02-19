package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.AuthUtils;
import com.connorcode.autoreauth.AutoReauth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import static com.connorcode.autoreauth.AutoReauth.serverJoin;

public class WaitingScreen extends Screen {
    ServerAddress address;
    ServerInfo info;
    boolean quickPlay;

    public WaitingScreen(ServerAddress address, ServerInfo info, boolean quickPlay) {
        super(Text.of("Waiting for Reauth"));
        this.address = address;
        this.info = info;
        this.quickPlay = quickPlay;
    }

    @Override
    public void tick() {
        if (!serverJoin.tryAcquire()) return;
        AuthUtils.connectToServer(address, info, quickPlay);
        serverJoin.release();
    }

    @Override
    public void close() {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var txt = AutoReauth.client.textRenderer;

        var title = Text.literal("AutoReauth").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 10, 0xFFFFFF);

        var body = Text.literal("You will automatically join the server once you are authenticated.");
        context.drawCenteredTextWithShadow(txt, body, this.width / 2, 40, 0xFFFFFF);
    }
}