package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import com.connorcode.autoreauth.gui.ErrorScreen;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.connorcode.autoreauth.AutoReauth.*;

public class Common {
    static final boolean METEOR_LOADED = FabricLoader.getInstance().isModLoaded("meteor-client");

    public static void renderAuthStatus(DrawContext context) {
        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        var color = switch (status) {
            case Unknown -> Formatting.GRAY;
            case Invalid, Offline -> Formatting.RED;
            case Online -> Formatting.GREEN;
        };

        if (METEOR_LOADED && client.currentScreen instanceof MultiplayerScreen) {
            var text = Text.literal("[ ")
                    .append(Text.literal(String.valueOf(status)).fillStyle(Style.EMPTY.withColor(color)))
                    .append(Text.literal(" ]"));
            var x = client.textRenderer.getWidth("Logged in as  " + client.getSession().getUsername()) + 3;
            context.drawText(client.textRenderer, text, x, 3, 0xFFFFFF, true);
            return;
        }

        var text = Text.literal(String.valueOf(status)).fillStyle(Style.EMPTY.withColor(color));
        context.drawText(client.textRenderer, text, 10, 10, 0xFFFFFF, true);
    }

    public static void tickAuthStatus(Screen parent) {
        var now = System.currentTimeMillis();
        if (authStatus.isDone() && lastUpdate + 1000 * 60 * 5 < now) {
            lastUpdate = now;
            authStatus = AuthUtils.getAuthStatus();
        }

        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);

        if (status.isInvalid() && !sentToast) {
            sentToast = true;
            if (!config.tokenExists()) {
                Misc.sendToast("AutoReauth", "Session expired but no login info found");
                return;
            }

            Misc.sendToast("AutoReauth", "Session expired, reauthenticating...");
            new MicrosoftAuth(s -> log.info(s)).authenticate(config.asAccessToken()).thenAccept(session -> {
                try {
                    AuthUtils.setSession(session);
                } catch (AuthenticationException e) {
                    log.error("Error re-authenticating", e);
                }
                authStatus = AuthUtils.getAuthStatus();
                Misc.sendToast("AutoReauth", String.format("Authenticated as %s!", session.getUsername()));
            }).exceptionally(e -> {
                log.error("Error re-authenticating", e);
                RenderSystem.recordRenderCall(() -> client.setScreen(new ErrorScreen(parent, "Error re-authenticating", e.toString())));
                return null;
            });
        }
    }

    public static void refreshAuthStatus() {
        authStatus = AuthUtils.getAuthStatus();
    }
}
