package com.connorcode.authreauth;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.connorcode.authreauth.AutoReauth.client;
import static com.connorcode.authreauth.AutoReauth.log;

public class AuthUtils {
    private static long lastAuthStatusCheck = 0;
    private static AuthStatus authStatus = AuthStatus.Unknown;

    public static CompletableFuture<AuthStatus> getAuthStatus() {
        if (System.currentTimeMillis() - lastAuthStatusCheck <= 1000 * 60 * 5) return CompletableFuture.completedFuture(authStatus);

        log.info("Checking auth status");
        lastAuthStatusCheck = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            var session = client.getSession();
            var token = session.getAccessToken();
            var id = UUID.randomUUID().toString();

            // Thank you https://github.com/axieum/authme
            var sessionService = (YggdrasilMinecraftSessionService) client.getSessionService();
            try {
                sessionService.joinServer(client.getSession().getUuidOrNull(), token, id);
                authStatus = sessionService.hasJoinedServer(session.getUsername(), id, null) != null ? AuthStatus.Online : AuthStatus.Offline;
                log.info("Auth status: " + authStatus.getText());
                return authStatus;
            } catch (AuthenticationException e) {
                log.error("Invalid auth status", e);
                authStatus = AuthStatus.Invalid;
            }

            return authStatus;
        });
    }

    public enum AuthStatus {
        Unknown,
        Invalid,
        Online,
        Offline;

        public String getText() {
            return switch (this) {
                case Unknown -> "Waiting...";
                case Invalid -> "Invalid";
                case Online -> "Online";
                case Offline -> "Offline";
            };
        }
    }
}
