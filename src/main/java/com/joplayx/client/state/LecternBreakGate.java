package com.joplayx.client.state;

/**
 * Decides when a lectern break is real enough to place another one or read trades.
 *
 * <p>Minecraft 26.3 predicts the removal inside {@code MultiPlayerGameMode.destroyBlock}
 * ({@code ClientLevel.setBlock} while {@code BlockStatePredictionHandler} is predicting).
 * The position stays in that ledger until {@code ClientLevel.handleBlockChangedAck}
 * runs {@code endPredictionsUpTo} and {@code syncBlockState}. Until then the block you
 * can see is the guess, not the server. {@code ClientboundBlockChangedAckPacket} carries
 * no verdict — a rollback is the pre-break state being written back because no
 * {@code setServerVerifiedBlockState} replaced it. A swing packet and a timer are not
 * confirmation.
 *
 * <p>The gate is pure so those cases can be tested without a client.
 */
public final class LecternBreakGate {

	/** Survival hand-break is well under this; it only caps a swing that never finishes. */
	public static final int MAX_SWING_TICKS = 300;
	/** How long to wait for the ack after the client has predicted the removal. */
	public static final int CONFIRM_GRACE_TICKS = 100;
	/** Fresh START_DESTROY_BLOCK attempts after the server settles the lectern back. */
	public static final int MAX_ATTEMPTS = 3;
	/** Pause between those attempts so a rejected break is not resent every tick. */
	public static final int RETRY_PAUSE_TICKS = 10;

	public enum BlockKind {
		LECTERN,
		AIR,
		OTHER,
		/** Chunk is not actually loaded. Client getBlockState then reports air. */
		UNLOADED
	}

	public enum Phase {
		IDLE,
		SWINGING,
		AWAIT_SETTLE,
		RETRY_PAUSE,
		CONFIRMED,
		OCCUPIED,
		UNCONFIRMED,
		CANCELLED,
		FAILED_BREAK
	}

	/**
	 * @param worldId     stable id of the client level; 0 means the world is gone
	 * @param block       what a settled read would show, or {@link BlockKind#UNLOADED}
	 * @param pendingPrediction true while this position is still in the prediction ledger
	 * @param swingSent   true if start/continue destroy ran this tick; never a success signal
	 */
	public record Observation(int worldId, BlockKind block, boolean pendingPrediction, boolean swingSent) {
	}

	private Phase phase = Phase.IDLE;
	private int boundWorld;
	private int attempt = 1;
	private int swingTicks;
	private int settleWait;
	private int pauseTicks;
	private int blindTicks;
	private boolean sawPending;

	public Phase phase() {
		return phase;
	}

	public int attempt() {
		return attempt;
	}

	public void reset() {
		phase = Phase.IDLE;
		boundWorld = 0;
		attempt = 1;
		swingTicks = 0;
		settleWait = 0;
		pauseTicks = 0;
		blindTicks = 0;
		sawPending = false;
	}

	public void begin(int worldId) {
		reset();
		if (worldId == 0) {
			phase = Phase.CANCELLED;
			return;
		}
		boundWorld = worldId;
		phase = Phase.SWINGING;
	}

	/** Player abort, or the caller is dropping the attempt. Terminal phases stay as they are. */
	public Phase cancel() {
		if (!isTerminal(phase)) phase = Phase.CANCELLED;
		return phase;
	}

	public Phase observe(Observation observation) {
		// observation.swingSent() records that a destroy packet was sent. It is not read:
		// a swing is never confirmation. Tests pass true to lock that in.
		if (isTerminal(phase) || phase == Phase.IDLE) return phase;

		if (observation.worldId() == 0 || observation.worldId() != boundWorld) {
			phase = Phase.CANCELLED;
			return phase;
		}

		if (phase == Phase.RETRY_PAUSE
				&& !observation.pendingPrediction()
				&& observation.block() == BlockKind.LECTERN) {
			if (++pauseTicks >= RETRY_PAUSE_TICKS) phase = Phase.SWINGING;
			return phase;
		}

		if (observation.pendingPrediction()) {
			sawPending = true;
			phase = Phase.AWAIT_SETTLE;
			if (++settleWait >= CONFIRM_GRACE_TICKS) phase = Phase.UNCONFIRMED;
			return phase;
		}

		if (sawPending) {
			// The ledger entry is gone, so handleBlockChangedAck has settled this position.
			sawPending = false;
			settleWait = 0;
			return switch (observation.block()) {
				case AIR -> settle(Phase.CONFIRMED);
				case LECTERN -> onRollback();
				case OTHER -> settle(Phase.OCCUPIED);
				case UNLOADED -> settle(Phase.UNCONFIRMED);
			};
		}

		return switch (observation.block()) {
			case AIR -> settle(Phase.CONFIRMED);
			case OTHER -> settle(Phase.OCCUPIED);
			case UNLOADED -> {
				phase = Phase.AWAIT_SETTLE;
				if (++blindTicks >= CONFIRM_GRACE_TICKS) phase = Phase.UNCONFIRMED;
				yield phase;
			}
			case LECTERN -> {
				blindTicks = 0;
				phase = Phase.SWINGING;
				if (++swingTicks >= MAX_SWING_TICKS) phase = Phase.FAILED_BREAK;
				yield phase;
			}
		};
	}

	private Phase onRollback() {
		if (attempt >= MAX_ATTEMPTS) return settle(Phase.UNCONFIRMED);
		attempt++;
		pauseTicks = 0;
		swingTicks = 0;
		settleWait = 0;
		blindTicks = 0;
		sawPending = false;
		phase = Phase.RETRY_PAUSE;
		return phase;
	}

	private Phase settle(Phase terminal) {
		phase = terminal;
		return phase;
	}

	public static boolean isTerminal(Phase phase) {
		return phase == Phase.CONFIRMED
				|| phase == Phase.OCCUPIED
				|| phase == Phase.UNCONFIRMED
				|| phase == Phase.CANCELLED
				|| phase == Phase.FAILED_BREAK;
	}
}
