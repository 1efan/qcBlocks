package com.akpew.minecraft.qcblock;

import com.akpew.minecraft.qcblock.command.QcbCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class QcblockMod implements ModInitializer {
    public static final String MOD_ID = "qcblock";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            QcbCommand.register(dispatcher));
    }
}
