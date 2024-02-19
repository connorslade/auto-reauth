package com.connorcode.autoreauth.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.connorcode.autoreauth.Common.refreshAuthStatus;
import static com.connorcode.autoreauth.Common.renderAuthStatus;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin extends Screen {
    protected DisconnectedScreenMixin(Text title) {
        super(title);
        throw new UnsupportedOperationException("Mixin constructor");
    }

    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;Lnet/minecraft/text/Text;)V")
    void onInit(CallbackInfo ci) {
        refreshAuthStatus();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        renderAuthStatus(context);
    }

    // Note: for compatibility with meteor client, the tick method is in MinecraftClientMixin
}
