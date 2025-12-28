package com.connorcode.autoreauth.auth;

import com.connorcode.autoreauth.Misc;
import com.connorcode.autoreauth.gui.WaitingForLogin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.session.Session;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static com.connorcode.autoreauth.Main.*;
import static com.connorcode.autoreauth.auth.NetworkUtils.ofFormUrlEncodedData;
import static com.connorcode.autoreauth.auth.NetworkUtils.parseQuery;

public class MicrosoftAuth {
    public static final String CLIENT_ID = "de4f1d47-957d-49bf-a282-0da6cdaf8c54";

    public static final int PORT = 9090;
    public static final String REDIRECT_URI = "http://localhost:" + PORT + "/callback";

    public static final URI ACCESS_TOKEN_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    public static final URI XBOX_AUTH_URI = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    public static final URI XSTS_AUTH_URI = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    public static final URI MINECRAFT_AUTH_URI = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    public static final URI PROFILE_URI = URI.create("https://api.minecraftservices.com/minecraft/profile");

    static JsonElement getIfPresent(JsonObject json, String key, String context) throws AuthException {
        if (!json.has(key)) throw new AuthException(String.format("Missing key '%s' in %s", key, context), null);
        return json.get(key);
    }

    public static CompletableFuture<String> getCode(Semaphore semaphore) {
        return CompletableFuture.supplyAsync(() -> {
            var state = Misc.randomString(10);

            HttpServer server;
            AtomicReference<String> finalCode = new AtomicReference<>("");

            try {
                server = HttpServer.create(new InetSocketAddress(PORT), 0);
                server.createContext("/callback", ctx -> {
                    var map = parseQuery(ctx.getRequestURI().getRawQuery());

                    if (!map.containsKey("code") || !map.containsKey("state")) {
                        ctx.sendResponseHeaders(400, 0);
                        ctx.getResponseBody()
                                .write("Invalid request!\nYou need the code and state parameters.".getBytes());
                        ctx.close();
                        return;
                    }

                    var code = map.get("code");
                    var gotState = map.get("state");

                    if (!gotState.equals(state)) {
                        ctx.sendResponseHeaders(400, 0);
                        ctx.getResponseBody().write("Invalid state!".getBytes());
                        ctx.close();
                        return;
                    }

                    finalCode.set(code);
                    log.info("Got code: {}", finalCode.get());
                    ctx.sendResponseHeaders(200, 0);
                    ctx.getResponseBody().write("You can close this tab now.".getBytes());
                    ctx.close();
                    semaphore.release();
                });

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            var builder = new NetworkUtils.URIBuilder("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize");
            builder.addParameter("client_id", CLIENT_ID);
            builder.addParameter("response_type", "code");
            builder.addParameter("redirect_uri", REDIRECT_URI);
            builder.addParameter("scope", "XboxLive.signin offline_access");
            builder.addParameter("state", state);
            var uri = builder.build();

            server.start();
            Util.getOperatingSystem().open(uri);

            client.send(() -> client.setScreen(new WaitingForLogin(client.currentScreen, semaphore, uri)));

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            server.stop(0);

            if (finalCode.get().isEmpty()) throw new CompletionException(new AbortException());

            return finalCode.get();
        });
    }

    public static void debugLog(String fmt, Object... args) {
        if (config.debug) log.info(fmt, args);
    }

    public static CompletableFuture<Session> authenticate(String code) {
        return getAccessToken(code).thenCompose(MicrosoftAuth::authenticateXbox)
                .thenCompose(MicrosoftAuth::obtainXstsToken).thenCompose(MicrosoftAuth::authenticateMinecraft)
                .thenCompose(MicrosoftAuth::createSession);
    }

    public static CompletableFuture<Session> authenticate(AccessToken token) {
        // TODO: Use access token if its still valid
        return refreshAccessToken(token.refreshToken).thenCompose(MicrosoftAuth::authenticateXbox)
                .thenCompose(MicrosoftAuth::obtainXstsToken).thenCompose(MicrosoftAuth::authenticateMinecraft)
                .thenCompose(MicrosoftAuth::createSession);
    }

    public static CompletableFuture<AccessToken> getAccessToken(String code) {
        log.info("Getting access token");
        return CompletableFuture.supplyAsync(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var req = HttpRequest.newBuilder(ACCESS_TOKEN_URI)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(ofFormUrlEncodedData(Map.of("client_id", CLIENT_ID, "code", code, "redirect_uri", REDIRECT_URI, "grant_type", "authorization_code")))
                        .build();
                var result = client.send(req, HttpResponse.BodyHandlers.ofString());
                var str = result.body();

                debugLog("Access token response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "access token response from code";
                var access_token = getIfPresent(json, "access_token", ctx).getAsString();
                var refresh_token = getIfPresent(json, "refresh_token", ctx).getAsString();
                return new AccessToken(access_token, refresh_token);
            } catch (IOException | InterruptedException e) {
                throw new AuthException("Failed to get access token from code", e);
            }
        });
    }

    static CompletableFuture<AccessToken> refreshAccessToken(String refreshToken) {
        log.info("Refreshing access token");
        return CompletableFuture.supplyAsync(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var req = HttpRequest.newBuilder(ACCESS_TOKEN_URI)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(ofFormUrlEncodedData(Map.of("client_id", CLIENT_ID, "refresh_token", refreshToken, "grant_type", "refresh_token")))
                        .build();
                var str = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

                debugLog("Refresh token response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "access token response from refresh token";
                var access_token = getIfPresent(json, "access_token", ctx).getAsString();
                var refresh_token = getIfPresent(json, "refresh_token", ctx).getAsString();
                return new AccessToken(access_token, refresh_token);
            } catch (IOException | InterruptedException e) {
                throw new AuthException("Failed to get access token from refresh token", e);
            }
        });
    }

    static CompletableFuture<XboxAuth> authenticateXbox(AccessToken token) {
        log.info("Authenticating Xbox");
        return CompletableFuture.supplyAsync(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var jsonBuilder = new JsonObject();
                var properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", "d=" + token.accessToken);
                jsonBuilder.add("Properties", properties);
                jsonBuilder.addProperty("RelyingParty", "http://auth.xboxlive.com");
                jsonBuilder.addProperty("TokenType", "JWT");

                var req = HttpRequest.newBuilder(XBOX_AUTH_URI).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString())).build();
                var str = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

                debugLog("Xbox auth response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "xbox auth response";
                var xbl_token = getIfPresent(json, "Token", ctx).getAsString();
                var user_hash = getIfPresent(json, "DisplayClaims", ctx).getAsJsonObject().get("xui").getAsJsonArray()
                        .get(0).getAsJsonObject().get("uhs").getAsString();
                return new XboxAuth(xbl_token, user_hash);
            } catch (IOException | InterruptedException e) {
                throw new AuthException("Failed to authenticate Xbox", e);
            }
        });
    }

    static CompletableFuture<XboxAuth> obtainXstsToken(XboxAuth xboxAuth) {
        log.info("Obtaining XSTS token");
        return CompletableFuture.supplyAsync(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var jsonBuilder = new JsonObject();
                var properties = new JsonObject();
                properties.addProperty("SandboxId", "RETAIL");
                var userTokens = new JsonArray();
                userTokens.add(xboxAuth.xblToken);
                properties.add("UserTokens", userTokens);
                jsonBuilder.add("Properties", properties);
                jsonBuilder.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                jsonBuilder.addProperty("TokenType", "JWT");

                var req = HttpRequest.newBuilder(XSTS_AUTH_URI).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString())).build();
                var str = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

                debugLog("XSTS auth response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "xsts auth response";
                var xsts_token = getIfPresent(json, "Token", ctx).getAsString();
                return new XboxAuth(xsts_token, xboxAuth.userHash);
            } catch (IOException | InterruptedException e) {
                throw new AuthException("Failed to obtain XSTS token", e);
            }
        });
    }

    static CompletableFuture<MinecraftAuth> authenticateMinecraft(XboxAuth xstsAuth) {
        log.info("Authenticating Minecraft");
        return CompletableFuture.supplyAsync(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var jsonBuilder = new JsonObject();
                jsonBuilder.addProperty("identityToken", "XBL3.0 x=" + xstsAuth.userHash + ";" + xstsAuth.xblToken);

                var req = HttpRequest.newBuilder(MINECRAFT_AUTH_URI).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString())).build();
                var str = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

                debugLog("Minecraft auth response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "minecraft auth response";
                var access_token = getIfPresent(json, "access_token", ctx).getAsString();
                return new MinecraftAuth(access_token);
            } catch (IOException | InterruptedException e) {
                throw new AuthException("Failed to authenticate Minecraft", e);
            }
        });
    }

    static CompletableFuture<Session> createSession(MinecraftAuth minecraftAuth) {
        log.info("Creating session");
        return CompletableFuture.supplyAsync(() -> {
            try (var client = HttpClient.newHttpClient()) {
                var req = HttpRequest.newBuilder(PROFILE_URI)
                        .header("Authorization", "Bearer " + minecraftAuth.accessToken).GET().build();
                var str = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

                debugLog("Profile response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "profile response";
                var id = getIfPresent(json, "id", ctx).getAsString();
                var name = getIfPresent(json, "name", ctx).getAsString();

                return new Session(name, Misc.parseUUID(id), minecraftAuth.accessToken, Optional.empty(), Optional.empty());
            } catch (IOException | InterruptedException e) {
                throw new AuthException("Failed to create session", e);
            }
        });
    }

    static class AuthException extends CancellationException {
        @Nullable Throwable cause;

        public AuthException(String message, @Nullable Throwable cause) {
            super(message);
            this.cause = cause;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", super.toString(), cause == null ? "" : cause.toString());
        }
    }

    public record AccessToken(String accessToken, String refreshToken) {
    }

    public record XboxAuth(String xblToken, String userHash) {
    }

    public record MinecraftAuth(String accessToken) {
    }

    public static class AbortException extends Exception {
    }
}
