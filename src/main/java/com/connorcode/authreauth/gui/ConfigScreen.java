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
import net.minecraft.util.Formatting;

import java.util.ArrayList;
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
            } catch (InterruptedException | ExecutionException e) {
                AutoReauth.client.setScreen(new ErrorScreen("Error re-authenticating", e.toString()));
            }
        }).build(), positioner);

        this.grid.forEachChild(this::addDrawableChild);
        this.grid.refreshPositions();
        SimplePositioningWidget.setPos(this.grid, 0, 0, this.width, this.height, 0.5f, 0.9f);
    }

    @Override
    public void close() {
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
