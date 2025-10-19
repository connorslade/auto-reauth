package com.connorcode.autoreauth.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.connorcode.autoreauth.Reauth.*;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen {
    protected MultiplayerScreenMixin(Text title) {
        super(title);
        throw new UnsupportedOperationException("Mixin constructor");
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(CallbackInfo ci) {
        refreshAuthStatus();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta){
        super.render(context, mouseX, mouseY, delta);
        renderAuthStatus(context);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void tick(CallbackInfo ci) {
        tickAuthStatus(this);
    }
}
