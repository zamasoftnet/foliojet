package net.zamasoft.foliojet.css.container;

import junit.framework.TestCase;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Condition-parser tests for stage 3 of the {@code @container} implementation
 * (development record §5/§6).
 * Fix acceptance of names, {@code and} conjunctions, {@code not}, and unit resolution, and require
 * unsupported syntax (`or`, unsupported features, unitless numbers, etc.) to always fail to match.
 */
public class ContainerQueryTest extends TestCase {

	private static UserAgent ua() {
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(ContainerQueryTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "ContainerQueryTest.UserAgent";
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}

	/** Point conversion (96 dpi, 1 in=72 pt): 400 px = 300 pt. */
	public void testSingleMinWidthWithName() {
		final ContainerQuery q = ContainerQuery.parse("card (min-width: 400px)", ua());
		assertEquals("card", q.getName());
		assertTrue(q.getCondition().isValid());
		assertFalse(q.getCondition().evaluate(299));
		assertTrue(q.getCondition().evaluate(300));
		assertTrue(q.getCondition().evaluate(500));
	}

	public void testWithoutName() {
		final ContainerQuery q = ContainerQuery.parse("(max-width: 300pt)", ua());
		assertNull(q.getName());
		assertTrue(q.getCondition().evaluate(300));
		assertTrue(q.getCondition().evaluate(200));
		assertFalse(q.getCondition().evaluate(301));
	}

	/** Treat inline-size features as using the same axis as width features. */
	public void testInlineSizeSynonym() {
		final ContainerQuery q = ContainerQuery.parse("(min-inline-size: 100pt)", ua());
		assertFalse(q.getCondition().evaluate(99));
		assertTrue(q.getCondition().evaluate(100));
	}

	public void testAndChain() {
		final ContainerQuery q = ContainerQuery.parse("sidebar (min-width: 100pt) and (max-width: 300pt)", ua());
		assertEquals("sidebar", q.getName());
		assertFalse(q.getCondition().evaluate(50));
		assertTrue(q.getCondition().evaluate(200));
		assertFalse(q.getCondition().evaluate(400));
	}

	public void testNot() {
		final ContainerQuery q = ContainerQuery.parse("not (min-width: 200pt)", ua());
		assertTrue(q.getCondition().isValid());
		assertTrue(q.getCondition().evaluate(100));
		assertFalse(q.getCondition().evaluate(300));
	}

	public void testExactWidth() {
		final ContainerQuery q = ContainerQuery.parse("(width: 300pt)", ua());
		assertTrue(q.getCondition().evaluate(300));
		assertFalse(q.getCondition().evaluate(300.5));
	}

	/** Features outside phase 1 (or, unsupported features, range syntax, cq units) never match. */
	public void testUnsupportedIsAlwaysFalse() {
		assertFalse(ContainerQuery.parse("(min-width: 100pt) or (max-width: 200pt)", ua()).getCondition()
				.evaluate(150));
		assertFalse(ContainerQuery.parse("(aspect-ratio: 1/1)", ua()).getCondition().evaluate(100));
		assertFalse(ContainerQuery.parse("style(--theme: dark)", ua()).getCondition().evaluate(100));
		assertFalse(ContainerQuery.parse("(min-width: 10cqw)", ua()).getCondition().evaluate(100));
		assertFalse(ContainerQuery.parse("(width >= 400px)", ua()).getCondition().evaluate(500));
	}

	public void testNotRequiresSingleGroup() {
		// not applies only to a single parenthesized term (the specification does not permit it together with and).
		final ContainerQuery q = ContainerQuery.parse("not (min-width: 100pt) and (max-width: 300pt)", ua());
		assertFalse(q.getCondition().isValid());
		assertFalse(q.getCondition().evaluate(150));
	}

	public void testNullParams() {
		final ContainerQuery q = ContainerQuery.parse(null, ua());
		assertNull(q.getName());
		assertFalse(q.getCondition().isValid());
	}
}
