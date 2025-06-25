package com.connorcode.autoreauth.mixin;

import com.connorcode.autoreauth.Main;
import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.gui.RealmsWaitingScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.realms.RealmsAvailability;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

import static com.connorcode.autoreauth.Main.authStatus;
import static com.connorcode.autoreauth.Main.log;
import static com.connorcode.autoreauth.Reauth.*;

@Mixin(RealmsMainScreen.class)
public class RealmsMainScreenMixin extends Screen {
    protected RealmsMainScreenMixin(Text title) {
        super(title);
        throw new UnsupportedOperationException("Mixin constructor");
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(CallbackInfo ci) {
        refreshAuthStatus();
    }

    @Inject(at = @At("TAIL"), method = "render")
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        renderAuthStatus(context);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void tick(CallbackInfo ci) {
        tickAuthStatus(this);
    }

    @Inject(at = @At("HEAD"), method = "method_52634(Lnet/minecraft/client/realms/RealmsAvailability$Info;)V", cancellable = true)
    void onRealmsAvailabilityInfo(RealmsAvailability.Info info, CallbackInfo ci) {
        if (info.type() != RealmsAvailability.Type.AUTHENTICATION_ERROR) return;

        log.info("Invalid Realms auth, re-authenticating...");
        authStatus = CompletableFuture.completedFuture(AuthUtils.AuthStatus.Invalid);
        Main.client.setScreen(new RealmsWaitingScreen(new TitleScreen()));
        ci.cancel();
    }
}
