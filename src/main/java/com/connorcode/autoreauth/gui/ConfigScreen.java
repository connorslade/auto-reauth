package com.connorcode.autoreauth.gui;

import com.connorcode.autoreauth.Config;
import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.Reauth;
import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;

import static com.connorcode.autoreauth.Main.*;

public class ConfigScreen extends Screen {
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 60);
    AccountListWidget accountList;

    Button switchButton;
    Button deleteButton;
    Button makeDefaultButton;

    Screen parent;
    Semaphore semaphore = new Semaphore(0);
    CompletableFuture<Void> reauth;

    public ConfigScreen(Screen screen) {
        super(Component.nullToEmpty("AutoReauth Config"));
        this.parent = screen;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(Component.nullToEmpty("AutoReauth Config"), this.font);

        var footer = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
        footer.defaultCellSetting().alignHorizontallyCenter();
        var footerTop = footer.addChild(LinearLayout.horizontal().spacing(4));
        var footerBottom = footer.addChild(LinearLayout.horizontal().spacing(4));

        this.switchButton = footerTop.addChild(Button.builder(Component.nullToEmpty("Switch"), (button) -> {
            var selected = this.accountList.getSelected();
            if (selected != null) this.reauth = Reauth.attemptReauth(this, selected.account);
        }).width(74).build());
        this.deleteButton = footerTop.addChild(Button.builder(Component.nullToEmpty("Delete"), (button) -> {
            var selected = this.accountList.getSelected();
            if (selected != null) config.removeAccount(selected.account);
        }).width(74).build());
        this.makeDefaultButton = footerTop.addChild(Button.builder(Component.nullToEmpty("Make Default"), (button) -> {
            var selected = this.accountList.getSelected();
            if (selected != null) config.defaultAccount = selected.account;
        }).width(74).build());
        footerTop.addChild(Button.builder(Component.nullToEmpty("Add Account"), (button) -> MicrosoftAuth.getCode(semaphore)
                .thenCompose(MicrosoftAuth::getAccessToken).thenCompose(access -> MicrosoftAuth.authenticate(access)
                        .thenApply(session -> new java.util.AbstractMap.SimpleEntry<>(access, session))).thenAccept(pair -> {
                    config.addAccount(new Config.Account(pair.getKey(), pair.getValue()));
                    config.save();

                    authStatus = AuthUtils.getAuthStatus();
                    lastUpdate = 0;
                    sentToast = false;
                }).exceptionally(e -> {
                    if (e.getCause() instanceof MicrosoftAuth.AbortException) return null;
                    log.error("Error re-authenticating", e);
                    Main.client.execute(() -> Main.client.gui.setScreen(new ErrorScreen(this, "Error re-authenticating", e.toString())));
                    return null;
                })).width(74).tooltip(Tooltip.create(Component.nullToEmpty("Warning: Tokens are stored in your home folder."))).build());


        footerBottom.addChild(callbackButton(clicked -> {
            config.debug ^= clicked;
            return "Debug: " + (config.debug ? "On" : "Off");
        }).width(100).tooltip(Tooltip.create(Component.nullToEmpty("Warning: Debug mode will send authentication tokens in the log.")))
                .build());
        footerBottom.addChild(callbackButton(clicked -> {
            config.auto ^= clicked;
            return "Reauth: " + (config.auto ? "Auto" : "Manual");
        }).width(100)
                .tooltip(Tooltip.create(Component.nullToEmpty("Whether your session should be automatically re-authenticated on expiration.")))
                .build());
        footerBottom.addChild(Button.builder(Component.nullToEmpty("Back"), (button) -> {
            config.save();
            Main.client.gui.setScreen(this.parent);
        }).width(100).build());

        this.accountList = this.layout.addToContents(new AccountListWidget(this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight(), 32));
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.accountList.updateSize(this.width, this.layout);
    }

    @Override
    public void onClose() {
        semaphore.release();
        Main.client.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        var selected = this.accountList.getSelected();
        var uuid = Optional.ofNullable(selected).map(x -> x.account.uuid());

        this.switchButton.active = (reauth == null || reauth.isDone()) && uuid.isPresent() && !Main.client.user.getProfileId()
                .equals(uuid.get());
        this.makeDefaultButton.active = uuid.isPresent() && !config.isDefault(selected.account);
        this.deleteButton.active = uuid.isPresent();

        // ↓ eh prob shouldn't call this every frame but like whatever...
        this.accountList.refreshEntries();
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private Button.Builder callbackButton(Function<Boolean, String> callback) {
        return Button.builder(Component.nullToEmpty(callback.apply(false)), (button) -> {
            button.setMessage(Component.nullToEmpty(callback.apply(true)));
        });
    }

    class AccountListWidget extends ObjectSelectionList<AccountListEntry> {
        public AccountListWidget(int width, int height, int y, int itemHeight) {
            super(Main.client, width, height, y, itemHeight);
            this.refreshEntries();
        }

        void refreshEntries() {
            var selected = this.getSelected();
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
            ConfigScreen.this.repositionElements();
        }
    }

    class AccountListEntry extends AccountListWidget.Entry<AccountListEntry> {
        private final Config.Account account;

        AccountListEntry(Config.Account account) {
            this.account = account;
        }

        @Override
        public Component getNarration() {
            return Component.literal(String.format("Account for %s", this.account.username()));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            assert minecraft != null;
            var skin = minecraft.playerSkinRenderCache().getOrDefault(ResolvableProfile.createUnresolved(this.account.uuid())).playerSkin();
            PlayerFaceExtractor.extractRenderState(context, skin, this.getContentX(), this.getContentY(), this.getHeight() - 4);

            var txt = minecraft.font;
            var contentX = this.getContentX() + this.getHeight();
            context.text(txt, this.account.username(), contentX, this.getContentY() + 2, 0xFFFFFFFF, true);
            context.text(txt, Component.literal(this.account.uuid().toString())
                    .withStyle(ChatFormatting.GRAY), contentX, this.getContentY() + 2 + txt.lineHeight + txt.lineHeight / 3, 0xFFFFFFFF, true);

            var text = Component.empty();
            if (config.isDefault(account)) text.append(Component.literal("[DEFAULT]").withStyle(ChatFormatting.GOLD));
            if (minecraft.user.getProfileId().equals(account.uuid()))
                text.append(Component.literal(" [ACTIVE]").withStyle(ChatFormatting.GREEN));
            context.text(txt, text, this.getContentX() + this.getContentWidth() - txt.width(text), this.getContentY() + 2, 0xFFFFFFFF, true);
        }
    }
}
