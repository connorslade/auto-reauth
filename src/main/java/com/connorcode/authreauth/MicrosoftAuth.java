package com.connorcode.authreauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.session.Session;
import net.minecraft.util.JsonHelper;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.connorcode.authreauth.AutoReauth.log;

public class MicrosoftAuth {
    // My client ID: de4f1d47-957d-49bf-a282-0da6cdaf8c54
    public static final String CLIENT_ID = "e16699bb-2aa8-46da-b5e3-45cbcce29091";
    public static final String REDIRECT_URI = "http://localhost:9090/callback";
    public static final String code = "M.C106_BL2.2.0a9cfa0b-39f8-0bd5-4fba-07f8296815f4";

    public static final URI ACCESS_TOKEN_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    public static final URI XBOX_AUTH_URI = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    public static final URI XSTS_AUTH_URI = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    public static final URI MINECRAFT_AUTH_URI = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    public static final URI PROFILE_URI = URI.create("https://api.minecraftservices.com/minecraft/profile");

    AuthProgressCallback callback;

    public MicrosoftAuth(AuthProgressCallback callback) {
        this.callback = callback;
    }

    static JsonElement getIfPresent(JsonObject json, String key, String context) throws AuthException {
        if (!json.has(key)) throw new AuthException(String.format("Missing key '%s' in %s", key, context), null);
        return json.get(key);
    }

    public CompletableFuture<Session> authenticate() {
        return getAccessToken(code)
                .thenCompose(this::authenticateXbox)
                .thenCompose(this::obtainXstsToken)
                .thenCompose(this::authenticateMinecraft)
                .thenCompose(this::createSession);
    }

    CompletableFuture<AccessToken> getAccessToken(String code) {
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

                var ctx = "access token response";
                var access_token = getIfPresent(json, "access_token", ctx).getAsString();
                var refresh_token = getIfPresent(json, "refresh_token", ctx).getAsString();
                return new AccessToken(access_token, refresh_token);
            } catch (IOException e) {
                throw new AuthException("Failed to get access token", e);
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

    record AccessToken(String accessToken, String refreshToken) {
    }

    record XboxAuth(String xblToken, String userHash) {
    }

    record MinecraftAuth(String accessToken) {
    }
}
