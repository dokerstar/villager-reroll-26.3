package com.joplayx.client.state;

/**
 * Counts a repeated step that must not spin forever: opening trades, placing
 * the lectern, or waiting for the librarian job.
 */
public final class RetryBudget {

	private final int max;
	private int used;

	public RetryBudget(int max) {
		if (max < 1) throw new IllegalArgumentException("max");
		this.max = max;
	}

	public void reset() {
		used = 0;
	}

	public int used() {
		return used;
	}

	/** Records one try. True while this try is still inside the budget. */
	public boolean allow() {
		used++;
		return used <= max;
	}
}
