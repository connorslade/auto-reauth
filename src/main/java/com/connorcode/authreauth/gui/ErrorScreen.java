package com.connorcode.authreauth.gui;

import com.connorcode.authreauth.AutoReauth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class ErrorScreen extends Screen {
    Screen parent;
    String title;
    String error;

    public ErrorScreen(String title, String error) {
        super(Text.of("Error"));
        this.parent = AutoReauth.client.currentScreen;
        this.title = title;
        this.error = error;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.of("Back"), (button) -> {
                    AutoReauth.client.setScreen(parent);
                }).size(200, 20)
                .position(this.width / 2 - 100, this.height - 30)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        var txt = AutoReauth.client.textRenderer;

        var title = Text.of(this.title).getWithStyle(Style.EMPTY.withBold(true)).get(0);
        var titleWidth = txt.getWidth(title);
        context.drawText(txt, title, this.width / 2 - titleWidth / 2, 10, 0xFFFFFF, true);

        var lines = txt.wrapLines(StringVisitable.plain(this.error), 300);
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var lineWidth = txt.getWidth(line);
            context.drawText(txt, line, this.width / 2 - lineWidth / 2, 40 + i * 10, 0xFFFFFF, true);
        }
    }
}
