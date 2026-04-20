package com.connorcode.autoreauth.mixin;

import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.gui.ConfigScreen;
import com.connorcode.autoreauth.gui.RealmsWaitingScreen;
import com.mojang.realmsclient.RealmsAvailability;
import com.mojang.realmsclient.RealmsMainScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import static com.connorcode.autoreauth.Main.*;
import static com.connorcode.autoreauth.Reauth.*;

@Mixin(RealmsMainScreen.class)
public class RealmsMainScreenMixin extends Screen {
    @Unique
    boolean hovered;

    protected RealmsMainScreenMixin(Component title) {
        super(title);
        throw new UnsupportedOperationException("Mixin constructor");
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(CallbackInfo ci) {
        refreshAuthStatus();
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState")
    private void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.hovered = renderAuthStatus(context, mouseX, mouseY);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void tick(CallbackInfo ci) {
        tickAuthStatus(this);
    }

    @Inject(at = @At("HEAD"), method = "lambda$init$9(Lcom/mojang/realmsclient/RealmsAvailability$Result;)V", cancellable = true)
    void onRealmsAvailabilityInfo(RealmsAvailability.Result info, CallbackInfo ci) {
        if (!config.auto || info.type() != RealmsAvailability.Type.AUTHENTICATION_ERROR) return;

        log.info("Invalid Realms auth, re-authenticating...");
        authStatus = CompletableFuture.completedFuture(AuthUtils.AuthStatus.Invalid);
        Main.client.setScreen(new RealmsWaitingScreen(new TitleScreen()));
        ci.cancel();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.hovered) Objects.requireNonNull(minecraft).setScreen(new ConfigScreen(this));
        return super.mouseClicked(click, doubled);
    }
}
