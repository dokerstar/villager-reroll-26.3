package com.joplayx.client.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockStatePredictionHandler.class)
public interface PredictionLedgerAccess {

	@Accessor("serverVerifiedStates")
	Long2ObjectOpenHashMap<Object> villagerReroll$states();
}
