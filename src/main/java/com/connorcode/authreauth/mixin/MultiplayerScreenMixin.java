package com.connorcode.authreauth.mixin;

import com.connorcode.authreauth.AuthUtils;
import com.connorcode.authreauth.MicrosoftAuth;
import com.connorcode.authreauth.Misc;
import com.connorcode.authreauth.gui.ErrorScreen;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.connorcode.authreauth.AutoReauth.*;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
    @Unique
    CompletableFuture<AuthUtils.AuthStatus> authStatus;
    @Unique
    boolean sentToast = false;

    @Inject(at = @At("TAIL"), method = "init")
    private void init(CallbackInfo ci) {
        authStatus = AuthUtils.getAuthStatus();
    }

    @Inject(at = @At("TAIL"), method = "render")
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        context.drawText(client.textRenderer, String.valueOf(status), 10, 10, 0xFFFFFF, true);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void tick(CallbackInfo ci) throws InterruptedException {
        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        if (status.isInvalid() && !sentToast) {
            if (config.isEmpty()) {
                Misc.sendToast("AuthReauth", "Session expired but no login info found");
                return;
            }

            Misc.sendToast("AuthReauth", "Session expired, reauthenticating...");
            sentToast = true;

            try {
                var session = new MicrosoftAuth(s -> log.info(s)).authenticate(config.get().asAccessToken()).get();
                AuthUtils.setSession(session);
                authStatus = AuthUtils.getAuthStatus();
                Misc.sendToast("AuthReauth", String.format("Authenticated as %s!", session.getUsername()));
            } catch (ExecutionException e) {
                client.setScreen(new ErrorScreen("Error re-authenticating", e.toString()));
            } catch (AuthenticationException e) {
                client.setScreen(new ErrorScreen("Error re-authenticating", "Error creating user api service: " + e));
            }
        }
    }
}
