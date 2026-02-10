package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import com.connorcode.autoreauth.gui.ErrorScreen;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

import static com.connorcode.autoreauth.Main.*;

public class Reauth {
    static final boolean METEOR_LOADED = FabricLoader.getInstance().isModLoaded("meteor-client");

    public static boolean renderAuthStatus(DrawContext context, int mouseX, int mouseY) {
        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        var color = switch (status) {
            case Unknown -> Formatting.GRAY;
            case Invalid, Offline -> Formatting.RED;
            case Online -> Formatting.GREEN;
        };

        var txt = client.textRenderer;
        if (METEOR_LOADED && client.currentScreen instanceof MultiplayerScreen) {
            var text = Text.literal("[ ").append(Text.literal(String.valueOf(status)).formatted(color))
                    .append(Text.literal(" ]"));
            var x = txt.getWidth("Logged in as  " + client.getSession().getUsername()) + 3;
            context.drawText(txt, text, x, 3, 0xFFFFFFFF, true);
            return false;
        } else {
            var text = status == AuthUtils.AuthStatus.Online ? Text.empty()
                    .append(Text.literal("Online").formatted(color)).append(" as ")
                    .append(client.getSession().getUsername()) : Text.literal(String.valueOf(status)).formatted(color);
            context.drawText(txt, text, 10, 10, 0xFFFFFFFF, true);

            var bounds = new Rect2i(10, 10, txt.getWidth(text), txt.fontHeight);
            var inBounds = bounds.contains(mouseX, mouseY);
            if (inBounds) context.setCursor(StandardCursors.POINTING_HAND);
            return inBounds;
        }
    }

    public static void tickAuthStatus(Screen parent) {
        var now = System.currentTimeMillis();
        if (authStatus.isDone() && lastUpdate + 1000 * 60 * 5 < now) {
            lastUpdate = now;
            authStatus = AuthUtils.getAuthStatus();
        }

        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        if (config.enabled && status.isInvalid() && !sentToast) {
            sentToast = true;
            var account = config.getAccount(client.session.getUuidOrNull());
            if (account.isEmpty()) {
                Misc.sendToast("AutoReauth", "Session expired but no login info found");
                return;
            }

            Misc.sendToast("AutoReauth", "Session expired, reauthenticating...");
            attemptReauth(parent, account.get());
        }
    }

    public static void refreshAuthStatus() {
        authStatus = AuthUtils.getAuthStatus();
    }

    public static CompletableFuture<Void> attemptReauth(Screen parent, Config.Account account) {
        return MicrosoftAuth.authenticate(account.accessToken()).thenAccept(session -> {
            try {
                AuthUtils.setSession(session);
            } catch (AuthenticationException e) {
                log.error("Error re-authenticating", e);
            }
            authStatus = AuthUtils.getAuthStatus();
            Misc.sendToast("AutoReauth", String.format("Authenticated as %s!", session.getUsername()));
        }).exceptionally(e -> {
            log.error("Error re-authenticating", e);
            client.send(() -> client.setScreen(new ErrorScreen(parent, "Error re-authenticating", e.toString())));
            return null;
        });
    }
}
