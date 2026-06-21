package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Main;
import java.net.URI;
import java.util.concurrent.Semaphore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaitingForLogin extends Screen {
    private static final Component message = Component.literal("Complete the oauth flow opened in your browser. If it failed to open, you can manually copy the link with the button below.");
    private final GridLayout grid = new GridLayout().columnSpacing(5);

    Screen parent;
    Semaphore semaphore;
    URI redirect;

    public WaitingForLogin(Screen parent, Semaphore semaphore, URI redirect) {
        super(Component.nullToEmpty("Waiting for Login"));
        this.parent = parent;
        this.semaphore = semaphore;
        this.redirect = redirect;
    }

    @Override
    public void onClose() {
        semaphore.release();
        Main.client.gui.setScreen(parent);
    }

    @Override
    protected void init() {
        var adder = this.grid.createRowHelper(2);
        var positioner = adder.newCellSettings().alignHorizontallyCenter();

        adder.addChild(Button.builder(Component.nullToEmpty("Copy Auth Link"), (button) -> Main.client.keyboardHandler.setClipboard(redirect.toString()))
                .build(), positioner);
        adder.addChild(Button.builder(Component.nullToEmpty("Abort"), (button) -> this.onClose()).build(), positioner);

        this.grid.visitWidgets(this::addRenderableWidget);
        this.grid.arrangeElements();
        FrameLayout.centerInRectangle(this.grid, 0, this.height - 64, this.width, 64);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!semaphore.hasQueuedThreads()) Main.client.gui.setScreen(parent);

        super.extractRenderState(context, mouseX, mouseY, delta);
        var txt = Main.client.font;

        var title = Component.literal("AutoReauth").withStyle(ChatFormatting.BOLD);
        context.centeredText(txt, title, this.width / 2, 20, 0xFFFFFFFF);
        context.textWithWordWrap(txt, message, this.width / 2 - 256 / 2, 40, 256, 0xFFFFFFFF, true);
    }
}
