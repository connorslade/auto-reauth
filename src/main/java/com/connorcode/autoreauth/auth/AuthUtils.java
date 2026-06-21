package com.connorcode.autoreauth.auth;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.realmsclient.RealmsAvailability;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.quickplay.QuickPlay;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.network.chat.CommonComponents;

import static com.connorcode.autoreauth.Main.*;

public class AuthUtils {

    public static CompletableFuture<AuthStatus> getAuthStatus() {
        log.info("Checking auth status");
        return CompletableFuture.supplyAsync(() -> {
            var session = client.getUser();
            var token = session.getAccessToken();
            var id = UUID.randomUUID().toString();

            // Thank you https://github.com/axieum/authme
            var sessionService = (YggdrasilMinecraftSessionService) client.services().sessionService();
            try {
                sessionService.joinServer(client.getUser().getProfileId(), token, id);
                var authStatus = sessionService.hasJoinedServer(session.getName(), id, null) != null ? AuthStatus.Online : AuthStatus.Offline;
                if (authStatus.isOnline()) sentToast = false;
                log.info("Auth status: " + authStatus.getText());
                return authStatus;
            } catch (AuthenticationException e) {
                log.info("Invalid auth status");
                return AuthStatus.Invalid;
            }
        });
    }

    public static void setSession(User session) throws AuthenticationException {
        log.info("Overwriting session with {} ({})", session.getName(), session.getProfileId());
        client.user = session;
        client.gui.splashManager().user = session;
        YggdrasilAuthenticationService yggdrasilAuthenticationService = client.isOfflineDeveloperMode() ? YggdrasilAuthenticationService.createOffline(client.getProxy()) : new YggdrasilAuthenticationService(client.getProxy());
        client.userApiService = yggdrasilAuthenticationService.createUserApiService(session.getAccessToken());
        FriendsService friendsService = yggdrasilAuthenticationService.createFriendsService(client.user.getAccessToken());
        RemoteFriendListUpdateHandler remoteFriendListUpdateHandler = new RemoteFriendListUpdateHandler(friendsService, client);
        client.playerSocialManager = new PlayerSocialManager(client, client.userApiService, friendsService, remoteFriendListUpdateHandler);
        client.profileKeyPairManager = ProfileKeyPairManager.create(client.userApiService, session, client.gameDirectory.toPath());
        client.reportingContext = ReportingContext.create(client.reportingContext.environment, client.userApiService);
        RealmsAvailability.future = null;

        var realmsClient = new RealmsClient(session.getSessionId(), session.getName(), client);
        RealmsClient.realmsClientInstance = realmsClient;
        client.realmsDataFetcher = new RealmsDataFetcher(realmsClient);
    }

    public static void connectToServer(ServerAddress address, ServerData info, boolean quickPlay) {
        var connectScreen = new ConnectScreen(new TitleScreen(), quickPlay ? QuickPlay.ERROR_TITLE : CommonComponents.CONNECT_FAILED);
        client.disconnect(connectScreen, false);
        client.prepareForMultiplayer();
        client.updateReportEnvironment(ReportEnvironment.thirdParty(info != null ? info.ip : address.getHost()));
        client.quickPlayLog().setWorldData(QuickPlayLog.Type.MULTIPLAYER, info.ip, info.name);
        client.gui.setScreen(connectScreen);
        connectScreen.connect(client, address, info, null);
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
