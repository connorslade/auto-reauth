package com.connorcode.autoreauth.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.connorcode.autoreauth.Reauth.tickAuthStatus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @Inject(at = @At("HEAD"), method = "tick")
    void onTick(CallbackInfo ci) {
        // So janky sob but it's needed for meteor client compat
        if (this.screen instanceof DisconnectedScreen) {
            tickAuthStatus(this.screen);
        }
    }
}
