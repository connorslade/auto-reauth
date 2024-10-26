package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.MicrosoftAuth;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.connorcode.autoreauth.Main.directory;

public class Config {
    public static final File CONFIG_PATH = new File(directory.toFile(), "config.nbt");
    public String accessToken = null;
    public String refreshToken = null;
    public boolean debug = false;

    public Config() {

    }

    public boolean load() {
        if (!CONFIG_PATH.exists()) return false;

        try {
            var tag = NbtIo.read(CONFIG_PATH.toPath());
            assert tag != null;

            this.debug = tag.getBoolean("debug");
            if (tag.contains("accessToken"))
                this.accessToken = tag.getString("accessToken");
            if (tag.contains("refreshToken"))
                this.refreshToken = tag.getString("refreshToken");
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
            var _ignored = CONFIG_PATH.getParentFile().mkdirs();
            NbtIo.write(tag, CONFIG_PATH.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
