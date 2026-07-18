package net.zamasoft.foliojet.css.selector;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.selector.Condition.ConditionType;

public class NthConditionTest extends TestCase {
	public void testOddMatchesOnlyOddPositions() {
		NthCondition c = new NthCondition(ConditionType.NTH_CHILD_CONDITION, 2, 1, "odd");
		assertTrue(c.matches(1));
		assertFalse(c.matches(2));
		assertTrue(c.matches(3));
		assertFalse(c.matches(4));
		assertTrue(c.matches(101));
	}

	public void testEvenMatchesOnlyEvenPositions() {
		NthCondition c = new NthCondition(ConditionType.NTH_CHILD_CONDITION, 2, 0, "even");
		assertFalse(c.matches(1));
		assertTrue(c.matches(2));
		assertFalse(c.matches(3));
		assertTrue(c.matches(100));
	}

	public void testFixedIntegerMatchesExactlyOnePosition() {
		// :nth-child(5) は a=0, b=5
		NthCondition c = new NthCondition(ConditionType.NTH_CHILD_CONDITION, 0, 5, "5");
		assertFalse(c.matches(4));
		assertTrue(c.matches(5));
		assertFalse(c.matches(6));
	}

	public void testNegativeCoefficientBoundsFromAbove() {
		// -n+3: 3,2,1 のみマッチ(kが非負である間だけ)
		NthCondition c = new NthCondition(ConditionType.NTH_CHILD_CONDITION, -1, 3, "-n+3");
		assertTrue(c.matches(1));
		assertTrue(c.matches(2));
		assertTrue(c.matches(3));
		assertFalse(c.matches(4));
	}

	public void test3nPlus1() {
		NthCondition c = new NthCondition(ConditionType.NTH_CHILD_CONDITION, 3, 1, "3n+1");
		assertTrue(c.matches(1));
		assertFalse(c.matches(2));
		assertFalse(c.matches(3));
		assertTrue(c.matches(4));
		assertTrue(c.matches(7));
	}

	public void testFirstOfTypeIsNthOfTypeOne() {
		NthCondition c = new NthCondition(ConditionType.NTH_OF_TYPE_CONDITION, 0, 1, "1");
		assertTrue(c.matches(1));
		assertFalse(c.matches(2));
	}
}
