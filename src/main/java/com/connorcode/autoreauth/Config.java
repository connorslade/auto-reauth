package com.connorcode.autoreauth;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.connorcode.autoreauth.AutoReauth.directory;

public class Config {
    public static final File CONFIG_PATH = new File(directory.toFile(), "config.nbt");
    public String accessToken;
    public String refreshToken;

    public Config(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static Config of(MicrosoftAuth.AccessToken accessToken) {
        return new Config(accessToken.accessToken(), accessToken.refreshToken());
    }

    public static Optional<Config> load() {
        if (!CONFIG_PATH.exists()) return Optional.empty();

        try {
            var tag = NbtIo.read(CONFIG_PATH);
            assert tag != null;

            var accessToken = tag.getString("accessToken");
            var refreshToken = tag.getString("refreshToken");
            return Optional.of(new Config(accessToken, refreshToken));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MicrosoftAuth.AccessToken asAccessToken() {
        return new MicrosoftAuth.AccessToken(accessToken, refreshToken);
    }

    public void save() {
        var tag = new NbtCompound();
        tag.putString("accessToken", accessToken);
        tag.putString("refreshToken", refreshToken);

        try {
            var _ignored = CONFIG_PATH.getParentFile().mkdirs();
            NbtIo.write(tag, CONFIG_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
