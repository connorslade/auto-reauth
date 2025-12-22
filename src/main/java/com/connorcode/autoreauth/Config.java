package com.connorcode.autoreauth;

import com.connorcode.autoreauth.auth.MicrosoftAuth;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.session.Session;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static com.connorcode.autoreauth.Main.directory;

public class Config {
    private static final Path CONFIG_PATH = directory.resolve("config.nbt");

    public boolean debug = false;
    public ArrayList<Account> accounts = new ArrayList<>();

    public Config() {
    }

    public void addAccount(Account account) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).uuid.equals(account.uuid)) {
                accounts.set(i, account);
                return;
            }
        }

        accounts.add(account);
    }

    public Optional<Account> getAccount(UUID uuid) {
        for (var account : accounts)
            if (account.uuid.equals(uuid)) return Optional.of(account);
        if (!accounts.isEmpty()) return Optional.of(accounts.getFirst());
        return Optional.empty();
    }

    public boolean load() {
        if (Files.notExists(CONFIG_PATH)) return false;

        try {
            var tag = NbtIo.read(CONFIG_PATH);
            assert tag != null;

            this.debug = tag.getBoolean("debug").orElse(false);
            this.accounts = tag.getList("accounts").orElse(new NbtList()).stream()
                    .map(account -> new Account(account.asCompound().orElseThrow()))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        var tag = new NbtCompound();
        tag.putBoolean("debug", debug);

        var accounts = new NbtList();
        for (var account : this.accounts)
            accounts.add(account.seralize());

        tag.put("accounts", accounts);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            NbtIo.write(tag, CONFIG_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record Account(MicrosoftAuth.AccessToken accessToken, UUID uuid, String username) {
        public Account(MicrosoftAuth.AccessToken access, Session session) {
            this(access, session.getUuidOrNull(), session.getUsername());
        }

        public Account(NbtCompound nbt) {
            this(new MicrosoftAuth.AccessToken(nbt.getString("accessToken").orElseThrow(), nbt.getString("refreshToken")
                    .orElseThrow()), Misc.parseUUID(nbt.getString("uuid").orElseThrow()), nbt.getString("username")
                    .orElseThrow());
        }

        public NbtCompound seralize() {
            var tag = new NbtCompound();
            tag.putString("accessToken", accessToken.accessToken());
            tag.putString("refreshToken", accessToken.refreshToken());
            tag.putString("uuid", uuid.toString());
            tag.putString("username", username);
            return tag;
        }

        public GameProfile gameProfile() {
            return new GameProfile(uuid, username);
        }
    }
}
