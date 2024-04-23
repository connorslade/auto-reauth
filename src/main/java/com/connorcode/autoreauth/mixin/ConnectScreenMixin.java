package com.connorcode.autoreauth.mixin;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.gui.ServerWaitingScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.connorcode.autoreauth.Main.authStatus;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Inject(method = "Lnet/minecraft/client/gui/screen/multiplayer/ConnectScreen;connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V", at = @At("HEAD"), cancellable = true)
    private static void onConnect(Screen screen, MinecraftClient client, ServerAddress address, ServerInfo info, boolean quickPlay, @Nullable CookieStorage cookieStorage, CallbackInfo ci) {
        if (authStatus.getNow(AuthUtils.AuthStatus.Invalid).isOnline()) return;
        client.setScreen(new ServerWaitingScreen(client.currentScreen, address, info, quickPlay));
        ci.cancel();
    }
}
