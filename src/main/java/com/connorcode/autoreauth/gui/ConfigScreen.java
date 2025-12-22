package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Config;
import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import static com.connorcode.autoreauth.Main.*;

public class ConfigScreen extends Screen {
    private final GridWidget grid = new GridWidget().setColumnSpacing(5);
    private AccountListWidget accountList;

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
        adder.add(ButtonWidget.builder(Text.of("Login"), (button) -> MicrosoftAuth.getCode(semaphore)
                .thenCompose(MicrosoftAuth::getAccessToken).thenCompose(access -> MicrosoftAuth.authenticate(access)
                        .thenApply(session -> new Pair<>(access, session))).thenAccept(pair -> {
                    config.addAccount(new Config.Account(pair.getLeft(), pair.getRight()));
                    config.save();

                    authStatus = null;
                    lastUpdate = 0;
                    sentToast = false;
                }).exceptionally(e -> {
                    if (e.getCause() instanceof MicrosoftAuth.AbortException) return null;
                    log.error("Error re-authenticating", e);
                    Main.client.setScreen(new ErrorScreen(this, "Error re-authenticating", e.toString()));
                    return null;
                })).build(), positioner);
        adder.add(ButtonWidget.builder(Text.of("D"), (button) -> {
            config.debug ^= true;
        }).tooltip(Tooltip.of(Text.of("Toggles debug mode"))).size(20, 20).build(), positioner);

        this.grid.forEachChild(this::addDrawableChild);
        this.grid.refreshPositions();
        SimplePositioningWidget.setPos(this.grid, 0, this.height - 64, this.width, 64);

        accountList = new AccountListWidget(380, 0, 40, 32);
        this.addDrawableChild(accountList);
    }

    @Override
    public void close() {
        semaphore.release();
        Main.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.accountList.setX(this.width / 2 - this.accountList.getWidth() / 2);
        this.accountList.setHeight(this.height - 40 - 66);

        super.render(context, mouseX, mouseY, delta);
        var txt = Main.client.textRenderer;
        var height = textRenderer.fontHeight + textRenderer.fontHeight / 3;
        var y = 40;

        var title = Text.literal("AutoReauth Config").fillStyle(Style.EMPTY.withBold(true));
        context.drawCenteredTextWithShadow(txt, title, this.width / 2, 20 - txt.fontHeight/2, 0xFFFFFFFF);

        var textLines = new ArrayList<Text>();
        if (config.accounts.isEmpty()) textLines.add(Text.literal("No accounts added yet."));
//        textLines.add(Text.literal("Warning: Tokens are stored in your home folder.")
//                .fillStyle(Style.EMPTY.withColor(Formatting.GOLD)));

        if (config.debug) {
            textLines.add(Text.literal(""));
            textLines.add(Text.literal("Debug Mode Enabled").fillStyle(Style.EMPTY.withColor(0xFF0000)));
            textLines.add(Text.literal("Warning: Debug mode will send auth tokens in the log.")
                    .fillStyle(Style.EMPTY.withColor(Formatting.GOLD)));
        }

        var maxWidth = textLines.stream().mapToInt(txt::getWidth).max().orElse(0);
        for (var line : textLines) {
            context.drawText(txt, line.asOrderedText(), (this.width - maxWidth) / 2, y, 0xFFFFFFFF, true);
            y += height;
        }
    }

    static class AccountListWidget extends AlwaysSelectedEntryListWidget<AccountListEntry> {
        public AccountListWidget(int width, int height, int y, int itemHeight) {
            super(Main.client, width, height, y, itemHeight);

            for (var entry : config.accounts)
                this.addEntry(new AccountListEntry(entry));
        }
    }

    static class AccountListEntry extends AlwaysSelectedEntryListWidget.Entry<AccountListEntry> {
        private final Config.Account account;

        AccountListEntry(Config.Account account) {
            this.account = account;
        }

        @Override
        public Text getNarration() {
            return Text.literal(String.format("Account for %s", this.account.username()));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            var skin = Main.client.getPlayerSkinCache().get(ProfileComponent.ofDynamic(this.account.uuid())).getTextures();
            PlayerSkinDrawer.draw(context, skin, this.getContentX(), this.getContentY(), this.getHeight() - 4);

            var txt = Main.client.textRenderer;
            var contentX = this.getContentX() + this.getHeight();
            context.drawText(txt, this.account.username(), contentX, this.getContentY() + 2, 0xFFFFFFFF, true);
            context.drawText(txt, this.account.uuid().toString(), contentX, this.getContentY() + 2 + txt.fontHeight + txt.fontHeight/3, 0xFF7F7F7F, true);
        }
    }
}
