package com.akpew.minecraft.qcblock;

import com.akpew.minecraft.qcblock.command.QcbCommand;
import net.fabricmc.api.ModInitializer;

public final class QcblockMod implements ModInitializer {
    public static final String MOD_ID = "qcblock";

    @Override
    public void onInitialize() {
        QcbCommand.register();
    }
}
