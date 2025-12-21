package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.MicrosoftAuth;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.connorcode.autoreauth.Main.directory;

public class Config {
    private static final Path CONFIG_PATH = directory.resolve("config.nbt");

    public boolean debug = false;
    public String accessToken;
    public String refreshToken;

    public Config() {
    }

    public boolean load() {
        if (Files.notExists(CONFIG_PATH)) return false;

        try {
            var tag = NbtIo.read(CONFIG_PATH);
            assert tag != null;

            this.debug = tag.getBoolean("debug").orElse(false);
            this.accessToken = tag.getString("accessToken").orElse(null);
            this.refreshToken = tag.getString("refreshToken").orElse(null);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MicrosoftAuth.AccessToken asAccessToken() {
        return new MicrosoftAuth.AccessToken(accessToken, refreshToken);
    }

    public boolean tokenExists() {
        return accessToken != null && refreshToken != null;
    }

    public void save() {
        var tag = new NbtCompound();
        tag.putBoolean("debug", debug);
        if (accessToken != null && refreshToken != null) {
            tag.putString("accessToken", accessToken);
            tag.putString("refreshToken", refreshToken);
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            NbtIo.write(tag, CONFIG_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
