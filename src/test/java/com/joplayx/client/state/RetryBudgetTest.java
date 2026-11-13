package com.joplayx.client.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryBudgetTest {

	@Test
	void allowsOnlyTheBudgetedTries() {
		RetryBudget budget = new RetryBudget(3);
		assertTrue(budget.allow());
		assertTrue(budget.allow());
		assertTrue(budget.allow());
		assertFalse(budget.allow());
		assertEquals(4, budget.used());
	}

	@Test
	void resetStartsOver() {
		RetryBudget budget = new RetryBudget(1);
		assertTrue(budget.allow());
		assertFalse(budget.allow());
		budget.reset();
		assertTrue(budget.allow());
	}
}
