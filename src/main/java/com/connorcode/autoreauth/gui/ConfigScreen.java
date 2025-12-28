package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Config;
import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.Reauth;
import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import static com.connorcode.autoreauth.Main.*;

public class ConfigScreen extends Screen {
    final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, 33, 60);
    AccountListWidget accountList;

    ButtonWidget switchButton;
    ButtonWidget deleteButton;
    ButtonWidget makeDefaultButton;

    Screen parent;
    Semaphore semaphore = new Semaphore(0);
    CompletableFuture<Void> reauth;

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

        this.switchButton = footerTop.add(ButtonWidget.builder(Text.of("Switch"), (button) -> {
            var selected = this.accountList.getSelectedOrNull();
            if (selected != null) this.reauth = Reauth.attemptReauth(this, selected.account);
        }).width(74).build());
        this.deleteButton = footerTop.add(ButtonWidget.builder(Text.of("Delete"), (button) -> {
            var selected = this.accountList.getSelectedOrNull();
            if (selected != null) config.removeAccount(selected.account);
        }).width(74).build());
        this.makeDefaultButton = footerTop.add(ButtonWidget.builder(Text.of("Make Default"), (button) -> {
            var selected = this.accountList.getSelectedOrNull();
            if (selected != null) config.defaultAccount = selected.account;
        }).width(74).build());
        footerTop.add(ButtonWidget.builder(Text.of("Add Account"), (button) -> MicrosoftAuth.getCode(semaphore)
                .thenCompose(MicrosoftAuth::getAccessToken).thenCompose(access -> MicrosoftAuth.authenticate(access)
                        .thenApply(session -> new Pair<>(access, session))).thenAccept(pair -> {
                    config.addAccount(new Config.Account(pair.getLeft(), pair.getRight()));
                    config.save();

                    authStatus = AuthUtils.getAuthStatus();
                    lastUpdate = 0;
                    sentToast = false;
                }).exceptionally(e -> {
                    if (e.getCause() instanceof MicrosoftAuth.AbortException) return null;
                    log.error("Error re-authenticating", e);
                    Main.client.setScreen(new ErrorScreen(this, "Error re-authenticating", e.toString()));
                    return null;
                })).width(74).tooltip(Tooltip.of(Text.of("Warning: Tokens are stored in your home folder."))).build());

        footerBottom.add(ButtonWidget.builder(Text.of("Debug Mode: " + (config.debug ? "On" : "Off")), (button) -> {
                    config.debug ^= true;
                    button.setMessage(Text.of("Debug Mode: " + (config.debug ? "On" : "Off")));
                }).width(152).tooltip(Tooltip.of(Text.of("Warning: Debug mode will send authentication tokens in the log.")))
                .build());
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
        var selected = this.accountList.getSelectedOrNull();
        var uuid = Optional.ofNullable(selected).map(x -> x.account.uuid());

        this.switchButton.active = (reauth == null || reauth.isDone()) && uuid.isPresent() && !Main.client.session.getUuidOrNull()
                .equals(uuid.get());
        this.makeDefaultButton.active = uuid.isPresent() && !config.isDefault(selected.account);
        this.deleteButton.active = uuid.isPresent();

        // ↓ eh prob shouldn't call this every frame but like whatever...
        this.accountList.refreshEntries();
        super.render(context, mouseX, mouseY, delta);
    }

    class AccountListWidget extends AlwaysSelectedEntryListWidget<AccountListEntry> {
        public AccountListWidget(int width, int height, int y, int itemHeight) {
            super(Main.client, width, height, y, itemHeight);
            this.refreshEntries();
        }

        void refreshEntries() {
            var selected = this.getSelectedOrNull();
            this.clearEntries();
            for (var account : config.accounts)
                this.addEntry(new AccountListEntry(account));

            if (selected != null) {
                var entry = this.children().stream().filter(a -> a.account.equals(selected.account)).findFirst();
                entry.ifPresent(this::setSelected);
            }
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        @Override
        public void setSelected(@Nullable ConfigScreen.AccountListEntry entry) {
            super.setSelected(entry);
            ConfigScreen.this.refreshWidgetPositions();
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
            assert client != null;
            var skin = client.getPlayerSkinCache().get(ProfileComponent.ofDynamic(this.account.uuid())).getTextures();
            PlayerSkinDrawer.draw(context, skin, this.getContentX(), this.getContentY(), this.getHeight() - 4);

            var txt = client.textRenderer;
            var contentX = this.getContentX() + this.getHeight();
            context.drawText(txt, this.account.username(), contentX, this.getContentY() + 2, 0xFFFFFFFF, true);
            context.drawText(txt, Text.literal(this.account.uuid().toString())
                    .formatted(Formatting.GRAY), contentX, this.getContentY() + 2 + txt.fontHeight + txt.fontHeight / 3, 0xFFFFFFFF, true);

            var text = Text.empty();
            if (config.isDefault(account)) text.append(Text.literal("[DEFAULT]").formatted(Formatting.GOLD));
            if (client.session.getUuidOrNull().equals(account.uuid()))
                text.append(Text.literal(" [ACTIVE]").formatted(Formatting.GREEN));
            context.drawText(txt, text, this.getContentX() + this.getContentWidth() - txt.getWidth(text), this.getContentY() + 2, 0xFFFFFFFF, true);
        }
    }
}
