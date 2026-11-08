package com.joplayx.client.state;

import com.joplayx.client.state.LecternBreakGate.BlockKind;
import com.joplayx.client.state.LecternBreakGate.Observation;
import com.joplayx.client.state.LecternBreakGate.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LecternBreakGateTest {

	private static final int WORLD = 1;

	private static Observation see(BlockKind block, boolean pending) {
		return new Observation(WORLD, block, pending, false);
	}

	private static Observation swung(BlockKind block, boolean pending) {
		return new Observation(WORLD, block, pending, true);
	}

	@Test
	void confirmsWhenAckSettlesToAir() {
		LecternBreakGate gate = started();
		assertEquals(Phase.SWINGING, gate.observe(see(BlockKind.LECTERN, false)));
		assertEquals(Phase.AWAIT_SETTLE, gate.observe(see(BlockKind.AIR, true)));
		assertEquals(Phase.CONFIRMED, gate.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void alreadySettledAirConfirmsWithoutASwing() {
		LecternBreakGate gate = started();
		assertEquals(Phase.CONFIRMED, gate.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void predictedAirIsNotConfirmation() {
		LecternBreakGate gate = started();
		assertEquals(Phase.AWAIT_SETTLE, gate.observe(swung(BlockKind.AIR, true)));
		for (int i = 1; i < LecternBreakGate.CONFIRM_GRACE_TICKS - 1; i++) {
			assertEquals(Phase.AWAIT_SETTLE, gate.observe(swung(BlockKind.AIR, true)));
		}
		assertNotEquals(Phase.CONFIRMED, gate.phase());
	}

	@Test
	void sentSwingDoesNotConfirm() {
		LecternBreakGate gate = started();
		assertEquals(Phase.SWINGING, gate.observe(swung(BlockKind.LECTERN, false)));
		assertEquals(Phase.SWINGING, gate.phase());
	}

	@Test
	void fixedDelayDoesNotConfirmWhileLecternRemains() {
		LecternBreakGate gate = started();
		for (int i = 0; i < 40; i++) {
			assertEquals(Phase.SWINGING, gate.observe(swung(BlockKind.LECTERN, false)));
		}
		assertEquals(Phase.SWINGING, gate.phase());
	}

	@Test
	void slowBreakUsesTheSwingBudgetBeforeFailing() {
		LecternBreakGate gate = started();
		Phase phase = Phase.SWINGING;
		for (int i = 0; i < LecternBreakGate.MAX_SWING_TICKS - 1; i++) {
			phase = gate.observe(see(BlockKind.LECTERN, false));
		}
		assertEquals(Phase.SWINGING, phase);
		assertEquals(Phase.FAILED_BREAK, gate.observe(see(BlockKind.LECTERN, false)));
		assertEquals(Phase.FAILED_BREAK, gate.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void serverRollbackRetriesThenStops() {
		LecternBreakGate gate = started();
		assertEquals(Phase.RETRY_PAUSE, reject(gate));
		assertEquals(2, gate.attempt());

		finishPause(gate);
		assertEquals(Phase.SWINGING, gate.phase());
		assertEquals(Phase.RETRY_PAUSE, reject(gate));
		assertEquals(3, gate.attempt());

		finishPause(gate);
		assertEquals(Phase.UNCONFIRMED, reject(gate));
		assertEquals(3, gate.attempt());
		assertEquals(Phase.UNCONFIRMED, gate.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void rollbackThenSettledAirConfirmsOnTheNextAttempt() {
		LecternBreakGate gate = started();
		assertEquals(Phase.RETRY_PAUSE, reject(gate));
		finishPause(gate);
		assertEquals(Phase.AWAIT_SETTLE, gate.observe(see(BlockKind.AIR, true)));
		assertEquals(Phase.CONFIRMED, gate.observe(see(BlockKind.AIR, false)));
		assertEquals(2, gate.attempt());
	}

	@Test
	void otherBlockAtTheSpotIsNotASuccessfulBreak() {
		LecternBreakGate gate = started();
		assertEquals(Phase.OCCUPIED, gate.observe(see(BlockKind.OTHER, false)));

		LecternBreakGate afterGuess = started();
		afterGuess.observe(see(BlockKind.AIR, true));
		assertEquals(Phase.OCCUPIED, afterGuess.observe(see(BlockKind.OTHER, false)));
		assertEquals(Phase.OCCUPIED, afterGuess.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void ackTimeoutDoesNotBecomeSuccessLater() {
		LecternBreakGate gate = started();
		Phase phase = Phase.AWAIT_SETTLE;
		for (int i = 0; i < LecternBreakGate.CONFIRM_GRACE_TICKS; i++) {
			phase = gate.observe(see(BlockKind.AIR, true));
		}
		assertEquals(Phase.UNCONFIRMED, phase);
		assertEquals(Phase.UNCONFIRMED, gate.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void cancelStopsTheAttempt() {
		LecternBreakGate gate = started();
		gate.observe(see(BlockKind.AIR, true));
		assertEquals(Phase.CANCELLED, gate.cancel());
		assertEquals(Phase.CANCELLED, gate.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void worldChangeOrUnloadCancels() {
		LecternBreakGate gate = started();
		gate.observe(see(BlockKind.AIR, true));
		assertEquals(Phase.CANCELLED, gate.observe(new Observation(2, BlockKind.AIR, false, false)));

		LecternBreakGate unloaded = started();
		unloaded.observe(see(BlockKind.LECTERN, false));
		assertEquals(Phase.CANCELLED, unloaded.observe(new Observation(0, BlockKind.AIR, false, false)));
		assertEquals(Phase.CANCELLED, unloaded.observe(see(BlockKind.AIR, false)));
	}

	@Test
	void unloadedChunkIsNotTreatedAsAir() {
		LecternBreakGate gate = started();
		assertEquals(Phase.AWAIT_SETTLE, gate.observe(see(BlockKind.UNLOADED, false)));
		assertNotEquals(Phase.CONFIRMED, gate.phase());

		LecternBreakGate settledBlind = started();
		settledBlind.observe(see(BlockKind.AIR, true));
		assertEquals(Phase.UNCONFIRMED, settledBlind.observe(see(BlockKind.UNLOADED, false)));
	}

	private static LecternBreakGate started() {
		LecternBreakGate gate = new LecternBreakGate();
		gate.begin(WORLD);
		return gate;
	}

	private static Phase reject(LecternBreakGate gate) {
		gate.observe(see(BlockKind.AIR, true));
		return gate.observe(see(BlockKind.LECTERN, false));
	}

	private static void finishPause(LecternBreakGate gate) {
		for (int i = 0; i < LecternBreakGate.RETRY_PAUSE_TICKS; i++) {
			gate.observe(see(BlockKind.LECTERN, false));
		}
	}
}
