package com.connorcode.autoreauth;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.realms.RealmsAvailability;
import net.minecraft.client.realms.RealmsClient;
import net.minecraft.client.realms.RealmsPeriodicCheckers;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.connorcode.autoreauth.AutoReauth.client;
import static com.connorcode.autoreauth.AutoReauth.log;

public class AuthUtils {
    private static long lastAuthStatusCheck = 0;
    private static AuthStatus authStatus = AuthStatus.Unknown;

    public static CompletableFuture<AuthStatus> getAuthStatus() {
//        if (System.currentTimeMillis() - lastAuthStatusCheck <= 1000 * 60 * 5 && authStatus != AuthStatus.Unknown)
//            return CompletableFuture.completedFuture(authStatus);

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

    public static void setSession(Session session) throws AuthenticationException {
        log.info("Overwriting session with {} ({})", session.getUsername(), session.getUuidOrNull());
        client.session = session;
        client.splashTextLoader.session = session;
        client.userApiService = client.authenticationService.createUserApiService(session.getAccessToken());
        client.socialInteractionsManager = new SocialInteractionsManager(client, client.userApiService);
        client.profileKeys = ProfileKeys.create(client.userApiService, session, client.runDirectory.toPath());
        client.abuseReportContext = AbuseReportContext.create(client.abuseReportContext.environment, client.userApiService);
        client.realmsPeriodicCheckers = new RealmsPeriodicCheckers(RealmsClient.create());
        RealmsAvailability.currentFuture = null;

        authStatus = AuthStatus.Unknown;
        lastAuthStatusCheck = 0;
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

        public boolean isInvalid() {
            return this != Unknown && this != Online;
        }
    }
}
