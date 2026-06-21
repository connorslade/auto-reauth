package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.auth.MicrosoftAuth;
import com.connorcode.autoreauth.gui.ErrorScreen;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;

import static com.connorcode.autoreauth.Main.*;

public class Reauth {
    static final boolean METEOR_LOADED = FabricLoader.getInstance().isModLoaded("meteor-client");

    public static boolean renderAuthStatus(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        var status = authStatus.getNow(AuthUtils.AuthStatus.Unknown);
        var color = switch (status) {
            case Unknown -> ChatFormatting.GRAY;
            case Invalid, Offline -> ChatFormatting.RED;
            case Online -> ChatFormatting.GREEN;
        };

        var txt = client.font;
        if (METEOR_LOADED && client.gui.screen() instanceof JoinMultiplayerScreen) {
            var text = Component.literal("[ ").append(Component.literal(String.valueOf(status)).withStyle(color))
                    .append(Component.literal(" ]"));
            var x = txt.width("Logged in as  " + client.getUser().getName()) + 3;
            context.text(txt, text, x, 3, 0xFFFFFFFF, true);
            return false;
        } else {
            var text = status == AuthUtils.AuthStatus.Online ? Component.empty()
                    .append(Component.literal("Online").withStyle(color)).append(" as ")
                    .append(client.getUser().getName()) : Component.literal(String.valueOf(status)).withStyle(color);
            context.text(txt, text, 10, 10, 0xFFFFFFFF, true);

            var bounds = new Rect2i(10, 10, txt.width(text), txt.lineHeight);
            var inBounds = bounds.contains(mouseX, mouseY);
            if (inBounds) context.requestCursor(CursorTypes.POINTING_HAND);
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
        if (config.auto && status.isInvalid() && !sentToast) {
            sentToast = true;
            var account = config.getAccount(client.user.getProfileId());
            if (account.isEmpty()) {
                Misc.sendToast("AutoReauth", "Session expired but no login info found");
                return;
            }

            Misc.sendToast("AutoReauth", "Session expired, reauthenticating...");
            attemptReauth(parent, account.get());
        }
    }

    public static void refreshAuthStatus() {
        // This method will be called whenever the Multiplayer tab is opened
        // or the servers are refreshed (as refreshing just secretly reopens the list)

        var now = System.currentTimeMillis();
        // Now we check for a 5s delay and I stole this code from line 53
        if (lastUpdate + 1000 * 5 < now) {
            lastUpdate = now;
            authStatus = AuthUtils.getAuthStatus();
        }
    }

    public static CompletableFuture<Void> attemptReauth(Screen parent, Config.Account account) {
        return MicrosoftAuth.authenticate(account.accessToken()).thenAccept(session -> {
            try {
                AuthUtils.setSession(session);
            } catch (AuthenticationException e) {
                log.error("Error re-authenticating", e);
            }
            authStatus = AuthUtils.getAuthStatus();
            Misc.sendToast("AutoReauth", String.format("Authenticated as %s!", session.getName()));
        }).exceptionally(e -> {
            log.error("Error re-authenticating", e);
            client.schedule(() -> client.gui.setScreen(new ErrorScreen(parent, "Error re-authenticating", e.toString())));
            return null;
        });
    }
}
