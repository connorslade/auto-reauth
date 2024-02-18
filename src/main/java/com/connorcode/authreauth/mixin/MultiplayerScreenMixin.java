package com.connorcode.authreauth.mixin;

import com.connorcode.authreauth.AuthUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.connorcode.authreauth.AutoReauth.client;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
    @Inject(at = @At("TAIL"), method = "render")
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var authStatus = AuthUtils.getAuthStatus();
        context.drawText(client.textRenderer, String.valueOf(authStatus), 10, 10, 0xFFFFFF, true);
    }
}
