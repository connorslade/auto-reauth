package com.connorcode.autoreauth.auth;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import net.minecraft.client.QuickPlay;
import net.minecraft.client.QuickPlayLogger;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.realms.RealmsClient;
import net.minecraft.client.realms.RealmsPeriodicCheckers;
import net.minecraft.client.report.AbuseReportContext;
import net.minecraft.client.report.ReporterEnvironment;
import net.minecraft.client.util.ProfileKeys;
import net.minecraft.client.util.Session;
import net.minecraft.screen.ScreenTexts;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.connorcode.autoreauth.Main.*;

public class AuthUtils {

    public static CompletableFuture<AuthStatus> getAuthStatus() {
        log.info("Checking auth status");
        return CompletableFuture.supplyAsync(() -> {
            var session = client.getSession();
            var token = session.getAccessToken();
            var id = UUID.randomUUID().toString();

            // Thank you https://github.com/axieum/authme
            var sessionService = (YggdrasilMinecraftSessionService) client.getSessionService();
            try {
                sessionService.joinServer(client.getSession().getProfile(), token, id);
                var authStatus = sessionService.hasJoinedServer(session.getProfile(), id, null) != null ? AuthStatus.Online : AuthStatus.Offline;
                if (authStatus.isOnline()) sentToast = false;
                log.info("Auth status: " + authStatus.getText());
                return authStatus;
            } catch (AuthenticationException e) {
                log.info("Invalid auth status");
                return AuthStatus.Invalid;
            }
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
    }

    public static void connectToServer(ServerAddress address, ServerInfo info, boolean quickPlay) {
        var connectScreen = new ConnectScreen(new TitleScreen(), quickPlay ? QuickPlay.ERROR_TITLE : ScreenTexts.CONNECT_FAILED);
        client.disconnect();
        client.loadBlockList();
        client.ensureAbuseReportContext(ReporterEnvironment.ofThirdPartyServer(info != null ? info.address : address.getAddress()));
        client.getQuickPlayLogger().setWorld(QuickPlayLogger.WorldType.MULTIPLAYER, info.address, info.name);
        client.setScreen(connectScreen);
        connectScreen.connect(client, address, info);
    }

    public enum AuthStatus {
        Unknown, Invalid, Online, Offline;

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

        public boolean isOnline() {
            return this == Online;
        }
    }
}
