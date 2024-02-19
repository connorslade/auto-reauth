package com.connorcode.authreauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.session.Session;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.connorcode.authreauth.AutoReauth.log;

public class MicrosoftAuth {
    // Note: Im using the client ID from ReAuth it would take like a week to get my own approved for minecraft services
    // My client ID: de4f1d47-957d-49bf-a282-0da6cdaf8c54
    public static final String CLIENT_ID = "e16699bb-2aa8-46da-b5e3-45cbcce29091";
    public static final int PORT = 9090;
    public static final String REDIRECT_URI = "http://localhost:" + PORT + "/callback";

    public static final URI ACCESS_TOKEN_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    public static final URI XBOX_AUTH_URI = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    public static final URI XSTS_AUTH_URI = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    public static final URI MINECRAFT_AUTH_URI = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    public static final URI PROFILE_URI = URI.create("https://api.minecraftservices.com/minecraft/profile");

    AuthProgressCallback callback;

    public MicrosoftAuth(AuthProgressCallback callback) {
        this.callback = callback;
    }

    public MicrosoftAuth() {
        this.callback = (e) -> {
        };
    }

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
                    var params = URLEncodedUtils.parse(ctx.getRequestURI(), StandardCharsets.UTF_8);
                    var map = params.stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));

                    if (!map.containsKey("code") || !map.containsKey("state")) {
                        ctx.sendResponseHeaders(400, 0);
                        ctx.getResponseBody().write("Invalid request!\nYou need the code and state parameters.".getBytes());
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
                    ctx.sendResponseHeaders(200, 0);
                    ctx.getResponseBody().write("You can close this tab now.".getBytes());
                    ctx.close();
                    semaphore.release();
                });

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            URI uri;
            try {
                var builder = new URIBuilder("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize");
                builder.addParameter("client_id", CLIENT_ID);
                builder.addParameter("response_type", "code");
                builder.addParameter("redirect_uri", REDIRECT_URI);
                builder.addParameter("scope", "XboxLive.signin offline_access");
                builder.addParameter("state", state);
                uri = builder.build();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            server.start();
            Util.getOperatingSystem().open(uri);

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            server.stop(0);
            return finalCode.get();
        });
    }

    public CompletableFuture<Session> authenticate(String code) {
        return getAccessToken(code)
                .thenCompose(this::authenticateXbox)
                .thenCompose(this::obtainXstsToken)
                .thenCompose(this::authenticateMinecraft)
                .thenCompose(this::createSession);
    }

    public CompletableFuture<Session> authenticate(AccessToken token) {
        return authenticateXbox(token)
                .thenCompose(this::obtainXstsToken)
                .thenCompose(this::authenticateMinecraft)
                .thenCompose(this::createSession);
    }

    public CompletableFuture<AccessToken> getAccessToken(String code) {
        this.callback.onProgress("Getting access token");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var client = HttpClients.createMinimal();
                var req = new HttpPost(ACCESS_TOKEN_URI);
                req.setHeader("Content-Type", "application/x-www-form-urlencoded");
                req.setEntity(new UrlEncodedFormEntity(
                        List.of(new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("code", code),
                                new BasicNameValuePair("redirect_uri", REDIRECT_URI),
                                new BasicNameValuePair("grant_type", "authorization_code"))
                ));

                var result = client.execute(req);
                var str = EntityUtils.toString(result.getEntity());
                var json = JsonHelper.deserialize(str);

                var ctx = "access token response from code";
                var access_token = getIfPresent(json, "access_token", ctx).getAsString();
                var refresh_token = getIfPresent(json, "refresh_token", ctx).getAsString();
                return new AccessToken(access_token, refresh_token);
            } catch (IOException e) {
                throw new AuthException("Failed to get access token from code", e);
            }
        });
    }

    CompletableFuture<AccessToken> refreshAccessToken(String refreshToken) {
        this.callback.onProgress("Getting access token");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var client = HttpClients.createMinimal();
                var req = new HttpPost(ACCESS_TOKEN_URI);
                req.setHeader("Content-Type", "application/x-www-form-urlencoded");
                req.setEntity(new UrlEncodedFormEntity(
                        List.of(new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("refresh_token", refreshToken),
                                new BasicNameValuePair("grant_type", "refresh_token"))
                ));

                var result = client.execute(req);
                var str = EntityUtils.toString(result.getEntity());
                var json = JsonHelper.deserialize(str);

                var ctx = "access token response from refresh token";
                var access_token = getIfPresent(json, "access_token", ctx).getAsString();
                var refresh_token = getIfPresent(json, "refresh_token", ctx).getAsString();
                return new AccessToken(access_token, refresh_token);
            } catch (IOException e) {
                throw new AuthException("Failed to get access token from refresh token", e);
            }
        });
    }

    CompletableFuture<XboxAuth> authenticateXbox(AccessToken token) {
        this.callback.onProgress("Authenticating Xbox");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var client = HttpClients.createMinimal();
                var req = new HttpPost(XBOX_AUTH_URI);
                req.setHeader("Content-Type", "application/json");

                var jsonBuilder = new JsonObject();
                var properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", "d=" + token.accessToken);
                jsonBuilder.add("Properties", properties);
                jsonBuilder.addProperty("RelyingParty", "http://auth.xboxlive.com");
                jsonBuilder.addProperty("TokenType", "JWT");
                req.setEntity(new StringEntity(jsonBuilder.toString()));

                var result = client.execute(req);
                var str = EntityUtils.toString(result.getEntity());
                log.info("Xbox auth response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "xbox auth response";
                var xbl_token = getIfPresent(json, "Token", ctx).getAsString();
                var user_hash = getIfPresent(json, "DisplayClaims", ctx).getAsJsonObject().get("xui").getAsJsonArray().get(0).getAsJsonObject().get("uhs").getAsString();
                return new XboxAuth(xbl_token, user_hash);
            } catch (IOException e) {
                throw new AuthException("Failed to authenticate Xbox", e);
            }
        });
    }

    CompletableFuture<XboxAuth> obtainXstsToken(XboxAuth xboxAuth) {
        this.callback.onProgress("Obtaining XSTS token");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var client = HttpClients.createMinimal();
                var req = new HttpPost(XSTS_AUTH_URI);
                req.setHeader("Content-Type", "application/json");

                var jsonBuilder = new JsonObject();
                var properties = new JsonObject();
                properties.addProperty("SandboxId", "RETAIL");
                var userTokens = new JsonArray();
                userTokens.add(xboxAuth.xblToken);
                properties.add("UserTokens", userTokens);
                jsonBuilder.add("Properties", properties);
                jsonBuilder.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                jsonBuilder.addProperty("TokenType", "JWT");
                req.setEntity(new StringEntity(jsonBuilder.toString()));

                var result = client.execute(req);
                var str = EntityUtils.toString(result.getEntity());
                log.info("XSTS auth response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "xsts auth response";
                var xsts_token = getIfPresent(json, "Token", ctx).getAsString();
                return new XboxAuth(xsts_token, xboxAuth.userHash);
            } catch (IOException e) {
                throw new AuthException("Failed to obtain XSTS token", e);
            }
        });
    }

    CompletableFuture<MinecraftAuth> authenticateMinecraft(XboxAuth xstsAuth) {
        this.callback.onProgress("Authenticating Minecraft");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var client = HttpClients.createMinimal();
                var req = new HttpPost(MINECRAFT_AUTH_URI);
                req.setHeader("Content-Type", "application/json");

                var jsonBuilder = new JsonObject();
                jsonBuilder.addProperty("identityToken", "XBL3.0 x=" + xstsAuth.userHash + ";" + xstsAuth.xblToken);
                req.setEntity(new StringEntity(jsonBuilder.toString()));

                var result = client.execute(req);
                var str = EntityUtils.toString(result.getEntity());
                log.info("Minecraft auth response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "minecraft auth response";
                var access_token = getIfPresent(json, "access_token", ctx).getAsString();
                return new MinecraftAuth(access_token);
            } catch (IOException e) {
                throw new AuthException("Failed to authenticate Minecraft", e);
            }
        });
    }

    CompletableFuture<Session> createSession(MinecraftAuth minecraftAuth) {
        this.callback.onProgress("Creating session");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var client = HttpClients.createMinimal();
                var req = new HttpGet(PROFILE_URI);
                req.setHeader("Authorization", "Bearer " + minecraftAuth.accessToken);

                var result = client.execute(req);
                var str = EntityUtils.toString(result.getEntity());
                log.info("Profile response: {}", str);
                var json = JsonHelper.deserialize(str);

                var ctx = "profile response";
                var id = getIfPresent(json, "id", ctx).getAsString();
                var name = getIfPresent(json, "name", ctx).getAsString();

                return new Session(
                        name,
                        Misc.parseUUID(id),
                        minecraftAuth.accessToken,
                        Optional.empty(),
                        Optional.empty(),
                        Session.AccountType.MSA
                );
            } catch (IOException e) {
                throw new AuthException("Failed to create session", e);
            }
        });
    }

    public interface AuthProgressCallback {
        void onProgress(String message);
    }

    static class AuthException extends CancellationException {
        @Nullable
        Throwable cause;

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
}
