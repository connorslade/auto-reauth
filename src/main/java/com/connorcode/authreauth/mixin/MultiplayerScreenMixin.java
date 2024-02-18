package com.connorcode.authreauth.mixin;

import com.connorcode.authreauth.AuthUtils;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.session.Session;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.connorcode.authreauth.AutoReauth.client;

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
    private void tick(CallbackInfo ci) {
        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        if (status.isInvalid() && !sentToast) {
            client.getToastManager().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT, Text.of("AuthReauth"), Text.of("Session expired, reauthenticating...")));

            var session = new Session(
                    "Sigma76",
                    UUID.fromString("3c358264-b456-4bde-ab1e-fe1023db6679"),
                    "",
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.MSA
            );
            ;
            try {
                AuthUtils.setSession(session);
                authStatus = AuthUtils.getAuthStatus();
            } catch (AuthenticationException e) {
                throw new RuntimeException(e);
            }

            client.getToastManager().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT, Text.of("AuthReauth"), Text.of("Authenticated as Sigma76!")));

            sentToast = true;
        }
    }
}
