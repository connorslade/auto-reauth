package com.connorcode.authreauth.gui;

import com.connorcode.authreauth.AutoReauth;
import com.connorcode.authreauth.Config;
import com.connorcode.authreauth.MicrosoftAuth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.connorcode.authreauth.AutoReauth.config;

public class ConfigScreen extends Screen {
    private final GridWidget grid = new GridWidget().setColumnSpacing(10);
    Screen parent;

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
        adder.add(ButtonWidget.builder(Text.of(config.isPresent() ? "Login Again" : "Login"), (button) -> {
            try {
                var code = MicrosoftAuth.getCode().get();
                var access = new MicrosoftAuth().getAccessToken(code).get();
                var newConfig = Config.of(access);
                newConfig.save();
                config = Optional.of(newConfig);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).build(), positioner);

        this.grid.forEachChild(this::addDrawableChild);
        this.grid.refreshPositions();
        SimplePositioningWidget.setPos(this.grid, 0, 0, this.width, this.height, 0.5f, 0.9f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var txt = AutoReauth.client.textRenderer;
        var height = textRenderer.fontHeight + textRenderer.fontHeight / 3;

        var title = Text.literal("AutoReauth Config").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 20, 0xFFFFFF);

        var config = Text.literal("Config: ").append(Text.literal(AutoReauth.config.isPresent() ? "Present" : "Not present").fillStyle(Style.EMPTY.withColor(AutoReauth.config.isPresent() ? 0x00FF00 : 0xFF0000)));
        context.drawText(txt, config, 10, 40, 0xFFFFFF, true);

        if (AutoReauth.config.isEmpty()) {
            var username = Text.literal("Because config is not present, you will need to login.");
            context.drawText(txt, username, 10, 40 + height, 0xFFFFFF, true);
        }
    }
}
