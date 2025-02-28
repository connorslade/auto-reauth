package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.net.URI;
import java.util.concurrent.Semaphore;

public class WaitingForLogin extends Screen {
    private static final Text message = Text.literal("Complete the oauth flow opened in your browser. If it failed to open, you can manually copy the link with the button below.");
    private final GridWidget grid = new GridWidget().setColumnSpacing(5);

    Screen parent;
    Semaphore semaphore;
    URI redirect;

    public WaitingForLogin(Screen parent, Semaphore semaphore, URI redirect) {
        super(Text.of("Waiting for Login"));
        this.parent = parent;
        this.semaphore = semaphore;
        this.redirect = redirect;
    }

    @Override
    public void close() {
        semaphore.release();
        Main.client.setScreen(parent);
    }

    @Override
    protected void init() {
        var adder = this.grid.createAdder(2);
        var positioner = adder.copyPositioner().alignHorizontalCenter();

        adder.add(ButtonWidget.builder(Text.of("Copy Auth Link"), (button) -> Main.client.keyboard.setClipboard(redirect.toString())).build(), positioner);
        adder.add(ButtonWidget.builder(Text.of("Abort"), (button) -> this.close()).build(), positioner);

        this.grid.forEachChild(this::addDrawableChild);
        this.grid.refreshPositions();
        SimplePositioningWidget.setPos(this.grid, 0, this.height - 64, this.width, 64);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var txt = Main.client.textRenderer;

        var title = Text.literal("AutoReauth").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 20, 0xFFFFFF);
        context.drawWrappedText(txt, message, this.width / 4, 40, this.width / 2, 0xFFFFFF, false);
    }
}
