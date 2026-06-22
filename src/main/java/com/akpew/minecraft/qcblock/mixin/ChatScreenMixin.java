package com.akpew.minecraft.qcblock.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    /** Nuke the EditBox max-length so you can paste huge scripts. */
    @Inject(method = "init", at = @At("TAIL"))
    private void increaseChatLimit(CallbackInfo ci) {
        this.input.setMaxLength(Integer.MAX_VALUE);
    }

    /** normalizeChatMessage calls StringUtil.trimChatMessage which hard-truncates at 256 chars.
     *  Redirect it to a no-op so commands survive intact. */
    @Redirect(method = "normalizeChatMessage",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;trimChatMessage(Ljava/lang/String;)Ljava/lang/String;"))
    private String skipTruncation(String message) {
        return message; // already trimmed + normalized by the caller, just skip the 256-char hack
    }
}
