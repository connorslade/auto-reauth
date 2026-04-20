package com.connorcode.autoreauth.mixin;

import com.connorcode.autoreauth.gui.ConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import static com.connorcode.autoreauth.Reauth.*;

@Mixin(JoinMultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen {
    @Unique
    boolean hovered;

    protected MultiplayerScreenMixin(Component title) {
        super(title);
        throw new UnsupportedOperationException("Mixin constructor");
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(CallbackInfo ci) {
        refreshAuthStatus();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.hovered = renderAuthStatus(context, mouseX, mouseY);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void tick(CallbackInfo ci) {
        tickAuthStatus(this);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.hovered) Objects.requireNonNull(minecraft).setScreen(new ConfigScreen(this));
        return super.mouseClicked(click, doubled);
    }
}
