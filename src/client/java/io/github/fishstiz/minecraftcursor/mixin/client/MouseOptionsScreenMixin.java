package io.github.fishstiz.minecraftcursor.mixin.client;

import io.github.fishstiz.minecraftcursor.MinecraftCursorClient;
import io.github.fishstiz.minecraftcursor.gui.screen.CursorOptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.MouseOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseOptionsScreen.class)
public abstract class MouseOptionsScreenMixin extends GameOptionsScreen {
    @Unique
    private final ButtonWidget cursorSettingsButton = ButtonWidget.builder(Text.of("Cursor Settings..."), button -> {
        if (this.client != null) {
            this.client.setScreen(new CursorOptionsScreen(this, MinecraftCursorClient.CURSOR_MANAGER));
        }
    }).build();

    public MouseOptionsScreenMixin(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    protected void addOptions(CallbackInfo ci) {
        if (this.body == null) {
            return;
        }

        ButtonWidget buttonWidget = ButtonWidget.builder(Text.empty(), button -> {
        }).build();
        buttonWidget.visible = false;
        buttonWidget.active = false;
        this.body.addWidgetEntry(cursorSettingsButton, buttonWidget);
    }
}
