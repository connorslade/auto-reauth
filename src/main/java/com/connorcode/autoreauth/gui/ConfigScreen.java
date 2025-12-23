package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Config;
import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.Reauth;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;

import java.util.concurrent.Semaphore;

import static com.connorcode.autoreauth.Main.*;

public class ConfigScreen extends Screen {
    final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, 33, 60);
    AccountListWidget accountList;

    Screen parent;
    Semaphore semaphore = new Semaphore(0);

    public ConfigScreen(Screen screen) {
        super(Text.of("AutoReauth Config"));
        this.parent = screen;
    }

    @Override
    protected void init() {
        this.layout.addHeader(Text.of("AutoReauth Config"), this.textRenderer);

        var footer = this.layout.addFooter(DirectionalLayoutWidget.vertical().spacing(4));
        footer.getMainPositioner().alignHorizontalCenter();
        var footerTop = footer.add(DirectionalLayoutWidget.horizontal().spacing(4));
        var footerBottom = footer.add(DirectionalLayoutWidget.horizontal().spacing(4));

        footerTop.add(ButtonWidget.builder(Text.of("Switch"), (button) -> {
        }).width(74).build());
        footerTop.add(ButtonWidget.builder(Text.of("Delete"), (button) -> {
        }).width(74).build());
        footerTop.add(ButtonWidget.builder(Text.of("Make Default"), (button) -> {
        }).width(74).build());
        footerTop.add(ButtonWidget.builder(Text.of("Add Account"), (button) -> MicrosoftAuth.getCode(semaphore)
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
                })).width(74).build());

        footerBottom.add(ButtonWidget.builder(Text.of("Debug Mode"), (button) -> {
            config.debug ^= true;
        }).width(152).build());
        footerBottom.add(ButtonWidget.builder(Text.of("Back"), (button) -> {
            config.save();
            Main.client.setScreen(this.parent);
        }).width(152).build());

        this.accountList = this.layout.addBody(new AccountListWidget(this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight(), 32));
        this.layout.forEachChild(this::addDrawableChild);
        this.refreshWidgetPositions();
    }

    @Override
    protected void refreshWidgetPositions() {
        this.layout.refreshPositions();
        this.accountList.position(this.width, this.layout);
    }

    @Override
    public void close() {
        semaphore.release();
        Main.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
//        var height = textRenderer.fontHeight + textRenderer.fontHeight / 3;
//        var y = 40;
//
//        var textLines = new ArrayList<Text>();
//        if (config.accounts.isEmpty()) textLines.add(Text.literal("No accounts added yet."));
//          textLines.add(Text.literal("Warning: Tokens are stored in your home folder.")
//                  .fillStyle(Style.EMPTY.withColor(Formatting.GOLD)));
//
//        if (config.debug) {
//            textLines.add(Text.literal(""));
//            textLines.add(Text.literal("Debug Mode Enabled").fillStyle(Style.EMPTY.withColor(0xFF0000)));
//            textLines.add(Text.literal("Warning: Debug mode will send auth tokens in the log.")
//                    .fillStyle(Style.EMPTY.withColor(Formatting.GOLD)));
//        }
//
//        var maxWidth = textLines.stream().mapToInt(this.textRenderer::getWidth).max().orElse(0);
//        for (var line : textLines) {
//            context.drawText(this.textRenderer, line.asOrderedText(), (this.width - maxWidth) / 2, y, 0xFFFFFFFF, true);
//            y += height;
//        }
    }

    class AccountListWidget extends AlwaysSelectedEntryListWidget<AccountListEntry> {
        public AccountListWidget(int width, int height, int y, int itemHeight) {
            super(Main.client, width, height, y, itemHeight);

            for (var account : config.accounts)
                this.addEntry(new AccountListEntry(account));
        }

        @Override
        public int getRowWidth() {
            return 260;
        }
    }

    class AccountListEntry extends AccountListWidget.Entry<AccountListEntry> {
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
            var skin = Main.client.getPlayerSkinCache().get(ProfileComponent.ofDynamic(this.account.uuid()))
                    .getTextures();
            PlayerSkinDrawer.draw(context, skin, this.getContentX(), this.getContentY(), this.getHeight() - 4);

            var txt = Main.client.textRenderer;
            var contentX = this.getContentX() + this.getHeight();
            context.drawText(txt, this.account.username(), contentX, this.getContentY() + 2, 0xFFFFFFFF, true);
            context.drawText(txt, this.account.uuid()
                    .toString(), contentX, this.getContentY() + 2 + txt.fontHeight + txt.fontHeight / 3, 0xFFAAAAAA, true);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            if (doubled) Reauth.attemptReauth(ConfigScreen.this, this.account);
            return super.mouseClicked(click, doubled);
        }
    }
}
