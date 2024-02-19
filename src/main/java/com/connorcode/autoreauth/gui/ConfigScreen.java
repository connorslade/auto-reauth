package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.AutoReauth;
import com.connorcode.autoreauth.Config;
import com.connorcode.autoreauth.MicrosoftAuth;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import static com.connorcode.autoreauth.AutoReauth.config;
import static com.connorcode.autoreauth.AutoReauth.log;

public class ConfigScreen extends Screen {
    private final GridWidget grid = new GridWidget().setColumnSpacing(10);
    Screen parent;
    Semaphore semaphore = new Semaphore(0);

    public ConfigScreen(Screen screen) {
        super(Text.of("AutoReauth Config"));
        this.parent = screen;
    }

    @Override
    protected void init() {
        var adder = this.grid.createAdder(2);
        var positioner = adder.copyPositioner().alignHorizontalCenter();

        adder.add(ButtonWidget.builder(Text.of("Save & Back"), (button) -> {
            config.ifPresent(Config::save);
            AutoReauth.client.setScreen(this.parent);
        }).build(), positioner);
        adder.add(ButtonWidget.builder(Text.of(config.isPresent() ? "Login Again" : "Login"), (button) -> MicrosoftAuth.getCode(semaphore).thenCompose(code -> new MicrosoftAuth().getAccessToken(code)).thenAccept(access -> {
            var newConfig = Config.of(access);
            newConfig.save();
            config = Optional.of(newConfig);
        }).exceptionally(e -> {
            log.error("Error re-authenticating", e);
            RenderSystem.recordRenderCall(() -> AutoReauth.client.setScreen(new ErrorScreen("Error re-authenticating", e.toString())));
            return null;
        })).build(), positioner);

        this.grid.forEachChild(this::addDrawableChild);
        this.grid.refreshPositions();
        SimplePositioningWidget.setPos(this.grid, 0, 0, this.width, this.height, 0.5f, 0.9f);
    }

    @Override
    public void close() {
        semaphore.release();
        AutoReauth.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var txt = AutoReauth.client.textRenderer;
        var height = textRenderer.fontHeight + textRenderer.fontHeight / 3;
        var y = 40;

        var title = Text.literal("AutoReauth Config").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 20, 0xFFFFFF);

        var textLines = new ArrayList<Text>();
        textLines.add(Text.literal("Config: ").append(Text.literal(AutoReauth.config.isPresent() ? "Present" : "Not present").fillStyle(Style.EMPTY.withColor(AutoReauth.config.isPresent() ? 0x00FF00 : 0xFF0000))));
        if (AutoReauth.config.isEmpty())
            textLines.add(Text.literal("Because config is not present, you will need to login."));
        textLines.add(Text.literal("Warning: Tokens are stored in your config folder.").fillStyle(Style.EMPTY.withColor(Formatting.GOLD)));

        var maxWidth = textLines.stream().mapToInt(txt::getWidth).max().orElse(0);
        for (var line : textLines) {
            context.drawText(txt, line, (this.width - maxWidth) / 2, y, 0xFFFFFF, true);
            y += height;
        }
    }
}
