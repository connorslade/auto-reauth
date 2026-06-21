package com.connorcode.autoreauth.mixin;

import com.connorcode.autoreauth.auth.AuthUtils;
import com.connorcode.autoreauth.gui.ServerWaitingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.connorcode.autoreauth.Main.authStatus;
import static com.connorcode.autoreauth.Main.config;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Inject(method = "startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V", at = @At("HEAD"), cancellable = true)
    private static void onConnect(Screen screen, Minecraft client, ServerAddress address, ServerData info, boolean quickPlay, @Nullable TransferState cookieStorage, CallbackInfo ci) {
        if (!config.auto || authStatus.getNow(AuthUtils.AuthStatus.Invalid).isOnline()) return;
        client.gui.setScreen(new ServerWaitingScreen(client.gui.screen(), address, info, quickPlay));
        ci.cancel();
    }
}
