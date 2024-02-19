package com.connorcode.autoreauth.mixin;

import com.connorcode.autoreauth.Misc;
import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import com.connorcode.autoreauth.gui.ErrorScreen;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

import static com.connorcode.autoreauth.AutoReauth.*;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
    @Unique
    CompletableFuture<AuthUtils.AuthStatus> authStatus;
    @Unique
    long lastUpdate = System.currentTimeMillis();
    @Unique
    boolean sentToast = false;

    @Inject(at = @At("TAIL"), method = "init")
    private void init(CallbackInfo ci) {
        authStatus = AuthUtils.getAuthStatus();
    }

    @Inject(at = @At("TAIL"), method = "render")
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        var color = switch (status) {
            case Unknown -> Formatting.GRAY;
            case Invalid, Offline -> Formatting.RED;
            case Online -> Formatting.GREEN;
        };

        var text = Text.literal(String.valueOf(status)).fillStyle(Style.EMPTY.withColor(color));
        context.drawText(client.textRenderer, text, 10, 10, 0xFFFFFF, true);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void tick(CallbackInfo ci) {
        var now = System.currentTimeMillis();
        if (authStatus.isDone() && this.lastUpdate + 1000 * 60 * 5 < now) {
            this.lastUpdate = now;
            this.authStatus = AuthUtils.getAuthStatus();
        }

        var status = this.authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        if (status.isInvalid() && !sentToast) {
            sentToast = true;
            if (!config.tokenExists()) {
                Misc.sendToast("AuthReauth", "Session expired but no login info found");
                return;
            }

            Misc.sendToast("AuthReauth", "Session expired, reauthenticating...");
            serverJoin.acquireUninterruptibly();
            new MicrosoftAuth(s -> log.info(s)).authenticate(config.asAccessToken()).thenAccept(session -> {
                try {
                    AuthUtils.setSession(session);
                } catch (AuthenticationException e) {
                    log.error("Error re-authenticating", e);
                }
                serverJoin.release();
                this.authStatus = AuthUtils.getAuthStatus();
                Misc.sendToast("AuthReauth", String.format("Authenticated as %s!", session.getUsername()));
            }).exceptionally(e -> {
                serverJoin.release();
                log.error("Error re-authenticating", e);
                RenderSystem.recordRenderCall(() -> client.setScreen(new ErrorScreen((Screen) (Object) this, "Error re-authenticating", e.toString())));
                return null;
            });
        }
    }
}
