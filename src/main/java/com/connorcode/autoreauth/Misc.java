package com.connorcode.autoreauth;

import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.math.BigInteger;
import java.util.UUID;

import static com.connorcode.autoreauth.Main.client;

public class Misc {
    // Modified from https://stackoverflow.com/a/30760478/12471934
    public static UUID parseUUID(String uuid) {
        uuid = uuid.replace("-", "");
        var a = new BigInteger(uuid.substring(0, 16), 16);
        var b = new BigInteger(uuid.substring(16, 32), 16);
        return new UUID(a.longValue(), b.longValue());
    }

    public static void sendToast(String title, String message) {
        client.getToastManager().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT, Text.of(title), Text.of(message)));
    }

    public static String randomString(int length) {
        var sb = new StringBuilder();
        for (int i = 0; i < length; i++)
            sb.append((char) (Math.random() * 26 + 'a'));
        return sb.toString();
    }
}
