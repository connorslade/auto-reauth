package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import static com.connorcode.autoreauth.Main.*;

public class ConfigScreen extends Screen {
    private final GridWidget grid = new GridWidget().setColumnSpacing(5);
    Screen parent;
    Semaphore semaphore = new Semaphore(0);

    public ConfigScreen(Screen screen) {
        super(Text.of("AutoReauth Config"));
        this.parent = screen;
    }

    @Override
    protected void init() {
        var adder = this.grid.createAdder(3);
        var positioner = adder.copyPositioner().alignHorizontalCenter();

        adder.add(ButtonWidget.builder(Text.of("Save & Back"), (button) -> {
            config.save();
            Main.client.setScreen(this.parent);
        }).build(), positioner);
        adder.add(ButtonWidget.builder(Text.of(config.tokenExists() ? "Login Again" : "Login"), (button) -> MicrosoftAuth.getCode(semaphore)
                .thenCompose(code -> new MicrosoftAuth().getAccessToken(code)).thenAccept(access -> {
                    config.accessToken = access.accessToken();
                    config.refreshToken = access.refreshToken();
                    config.save();

                    authStatus = null;
                    lastUpdate = 0;
                    sentToast = false;
                }).exceptionally(e -> {
                    if (e.getCause() instanceof MicrosoftAuth.AbortException) return null;
                    log.error("Error re-authenticating", e);
                    RenderSystem.recordRenderCall(() -> Main.client.setScreen(new ErrorScreen(this, "Error re-authenticating", e.toString())));
                    return null;
                })).build(), positioner);
        adder.add(ButtonWidget.builder(Text.of("D"), (button) -> {
            config.debug ^= true;
        }).tooltip(Tooltip.of(Text.of("Toggles debug mode"))).size(20, 20).build(), positioner);

        this.grid.forEachChild(this::addDrawableChild);
        this.grid.refreshPositions();
        SimplePositioningWidget.setPos(this.grid, 0, this.height - 64, this.width, 64);
    }

    @Override
    public void close() {
        semaphore.release();
        Main.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var txt = Main.client.textRenderer;
        var height = textRenderer.fontHeight + textRenderer.fontHeight / 3;
        var y = 40;

        var title = Text.literal("AutoReauth Config").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 20, 0xFFFFFF);

        var textLines = new ArrayList<Text>();
        textLines.add(Text.literal("Config: ")
                .append(Text.literal(Main.config.tokenExists() ? "Present" : "Not present")
                        .fillStyle(Style.EMPTY.withColor(Main.config.tokenExists() ? 0x00FF00 : 0xFF0000))));
        if (!Main.config.tokenExists())
            textLines.add(Text.literal("Because config is not present, you will need to login."));
        textLines.add(Text.literal("Warning: Tokens are stored in your config folder.")
                .fillStyle(Style.EMPTY.withColor(Formatting.GOLD)));


        if (config.debug) {
            textLines.add(Text.literal(""));
            textLines.add(Text.literal("Debug Mode Enabled").fillStyle(Style.EMPTY.withColor(0xFF0000)));
            textLines.add(Text.literal("Warning: Debug mode will send auth tokens in the log.")
                    .fillStyle(Style.EMPTY.withColor(Formatting.GOLD)));
        }

        var maxWidth = textLines.stream().mapToInt(txt::getWidth).max().orElse(0);
        for (var line : textLines) {
            context.drawText(txt, line, (this.width - maxWidth) / 2, y, 0xFFFFFF, true);
            y += height;
        }
    }
}
