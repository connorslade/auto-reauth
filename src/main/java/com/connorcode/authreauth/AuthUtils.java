package com.connorcode.authreauth;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;

import java.util.UUID;

import static com.connorcode.authreauth.AutoReauth.client;
import static com.connorcode.authreauth.AutoReauth.log;

public class AuthUtils {
    private static AuthStatus authStatus = AuthStatus.Unknown;
    private static long lastAuthStatusCheck = 0;

    public static synchronized AuthStatus getAuthStatus() {
        if (authStatus == AuthStatus.Waiting) return authStatus;
        if (System.currentTimeMillis() - lastAuthStatusCheck <= 1000 * 60 * 5) return authStatus;

        log.info("Checking auth status");
        authStatus = AuthStatus.Waiting;
        lastAuthStatusCheck = System.currentTimeMillis();

        new Thread(() -> {
            var session = client.getSession();
            var token = session.getAccessToken();
            var id = UUID.randomUUID().toString();

            // Thank you https://github.com/axieum/authme
            var sessionService = (YggdrasilMinecraftSessionService) client.getSessionService();
            try {
                sessionService.joinServer(client.getSession().getUuidOrNull(), token, id);
                authStatus = sessionService.hasJoinedServer(session.getUsername(), id, null) != null ? AuthStatus.Online : AuthStatus.Offline;
                log.info("Auth status: " + authStatus.getText());
            } catch (AuthenticationException e) {
                authStatus = AuthStatus.Invalid;
                log.error("Invalid auth status", e);
            }
        }).start();

        return authStatus;
    }

    public enum AuthStatus {
        Unknown,
        Waiting,
        Invalid,
        Online,
        Offline;

        public String getText() {
            return switch (this) {
                case Unknown, Waiting -> "Waiting";
                case Invalid -> "Invalid";
                case Online -> "Online";
                case Offline -> "Offline";
            };
        }
    }
}
