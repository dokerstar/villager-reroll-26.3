package com.joplayx.client.util;

import com.joplayx.client.mixin.ClientLevelAccess;
import com.joplayx.client.mixin.PredictionLedgerAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;

/**
 * Whether a position is still inside the 26.3 client prediction ledger.
 * While it is, {@code ClientLevel.getBlockState} is the local guess.
 */
public final class PredictionLedger {

	private PredictionLedger() {}

	public static boolean isPending(ClientLevel level, BlockPos pos) {
		if (level == null || pos == null) return false;
		BlockStatePredictionHandler handler = ((ClientLevelAccess) (Object) level).villagerReroll$prediction();
		if (handler == null) return false;
		Long2ObjectOpenHashMap<Object> states = ((PredictionLedgerAccess) (Object) handler).villagerReroll$states();
		return states != null && states.containsKey(pos.asLong());
	}
}
