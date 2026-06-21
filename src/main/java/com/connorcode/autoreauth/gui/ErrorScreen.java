package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class ErrorScreen extends Screen {
    Screen parent;
    String title;
    String error;

    public ErrorScreen(Screen parent, String title, String error) {
        super(Component.nullToEmpty("Error"));
        this.parent = parent;
        this.title = title;
        this.error = error;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.nullToEmpty("Back"), (button) -> {
            Main.client.gui.setScreen(parent);
        }).size(200, 20).pos(this.width / 2 - 100, this.height - 30).build());
    }

    @Override
    public void onClose() {
        Main.client.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        var txt = Main.client.font;

        var title = Component.nullToEmpty(this.title).toFlatList(Style.EMPTY.withBold(true)).get(0);
        var titleWidth = txt.width(title);
        context.text(txt, title, this.width / 2 - titleWidth / 2, 20, 0xFFFFFFFF, true);

        var lines = txt.split(FormattedText.of(this.error), 300);
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var lineWidth = txt.width(line);
            context.text(txt, line, this.width / 2 - lineWidth / 2, 40 + i * 10, 0xFFFFFFFF, true);
        }
    }
}
