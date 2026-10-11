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
		assertFalse(q.getCondition().evaluate(299, 12, 12));
		assertTrue(q.getCondition().evaluate(300, 12, 12));
		assertTrue(q.getCondition().evaluate(500, 12, 12));
	}

	public void testWithoutName() {
		final ContainerQuery q = ContainerQuery.parse("(max-width: 300pt)", ua());
		assertNull(q.getName());
		assertTrue(q.getCondition().evaluate(300, 12, 12));
		assertTrue(q.getCondition().evaluate(200, 12, 12));
		assertFalse(q.getCondition().evaluate(301, 12, 12));
	}

	/** Treat inline-size features as using the same axis as width features. */
	public void testInlineSizeSynonym() {
		final ContainerQuery q = ContainerQuery.parse("(min-inline-size: 100pt)", ua());
		assertFalse(q.getCondition().evaluate(99, 12, 12));
		assertTrue(q.getCondition().evaluate(100, 12, 12));
	}

	/** The range syntax of Media Queries 4, as for @media (2026-10-11): strict and inclusive bounds, both sides. */
	public void testRangeSyntax() {
		final ContainerQuery lt = ContainerQuery.parse("headerlinks (width < 300px)", ua());
		assertEquals("headerlinks", lt.getName());
		assertTrue(lt.getCondition().evaluate(224, 12, 12));
		assertFalse(lt.getCondition().evaluate(225, 12, 12));
		final ContainerQuery both = ContainerQuery.parse("(400px < width <= 700px)", ua());
		assertFalse(both.getCondition().evaluate(300, 12, 12));
		assertTrue(both.getCondition().evaluate(301, 12, 12));
		assertTrue(both.getCondition().evaluate(525, 12, 12));
		assertFalse(both.getCondition().evaluate(526, 12, 12));
		final ContainerQuery inline = ContainerQuery.parse("(INLINE-SIZE >= 100pt)", ua());
		assertFalse(inline.getCondition().evaluate(99, 12, 12));
		assertTrue(inline.getCondition().evaluate(100, 12, 12));
		// not negates the range as a whole
		final ContainerQuery not = ContainerQuery.parse("not (150px < width <= 250px)", ua());
		assertTrue(not.getCondition().evaluate(75, 12, 12));
		assertFalse(not.getCondition().evaluate(150, 12, 12));
		assertTrue(not.getCondition().evaluate(188, 12, 12));
	}

	/**
	 * em and rem stand for the container's and the root's font size, as in Chrome (2026-10-11): a 300px container in
	 * 20px with a 10px root holds (min-width: 25rem) and (width > 14em), not (min-width: 17em) or (width < 26rem).
	 */
	public void testFontRelative() {
		assertTrue(ContainerQuery.parse("(min-width: 25rem)", ua()).getCondition().evaluate(225, 15, 7.5));
		assertFalse(ContainerQuery.parse("(min-width: 17em)", ua()).getCondition().evaluate(225, 15, 7.5));
		assertFalse(ContainerQuery.parse("(width < 26rem)", ua()).getCondition().evaluate(225, 15, 7.5));
		assertTrue(ContainerQuery.parse("(width > 14em)", ua()).getCondition().evaluate(225, 15, 7.5));
		assertTrue(ContainerQuery.parse("(14em < width <= 30rem)", ua()).getCondition().evaluate(225, 15, 7.5));
	}

	public void testAndChain() {
		final ContainerQuery q = ContainerQuery.parse("sidebar (min-width: 100pt) and (max-width: 300pt)", ua());
		assertEquals("sidebar", q.getName());
		assertFalse(q.getCondition().evaluate(50, 12, 12));
		assertTrue(q.getCondition().evaluate(200, 12, 12));
		assertFalse(q.getCondition().evaluate(400, 12, 12));
	}

	public void testNot() {
		final ContainerQuery q = ContainerQuery.parse("not (min-width: 200pt)", ua());
		assertTrue(q.getCondition().isValid());
		assertTrue(q.getCondition().evaluate(100, 12, 12));
		assertFalse(q.getCondition().evaluate(300, 12, 12));
	}

	public void testExactWidth() {
		final ContainerQuery q = ContainerQuery.parse("(width: 300pt)", ua());
		assertTrue(q.getCondition().evaluate(300, 12, 12));
		assertFalse(q.getCondition().evaluate(300.5, 12, 12));
	}

	/** Features outside phase 1 (or, unsupported features, cq units, ex) never match. */
	public void testUnsupportedIsAlwaysFalse() {
		assertFalse(ContainerQuery.parse("(min-width: 100pt) or (max-width: 200pt)", ua()).getCondition()
				.evaluate(150, 12, 12));
		assertFalse(ContainerQuery.parse("(aspect-ratio: 1/1)", ua()).getCondition().evaluate(100, 12, 12));
		assertFalse(ContainerQuery.parse("style(--theme: dark)", ua()).getCondition().evaluate(100, 12, 12));
		assertFalse(ContainerQuery.parse("(min-width: 10cqw)", ua()).getCondition().evaluate(100, 12, 12));
		assertFalse(ContainerQuery.parse("(min-width: 10ex)", ua()).getCondition().evaluate(100, 12, 12));
	}

	public void testNotRequiresSingleGroup() {
		// not applies only to a single parenthesized term (the specification does not permit it together with and).
		final ContainerQuery q = ContainerQuery.parse("not (min-width: 100pt) and (max-width: 300pt)", ua());
		assertFalse(q.getCondition().isValid());
		assertFalse(q.getCondition().evaluate(150, 12, 12));
	}

	public void testNullParams() {
		final ContainerQuery q = ContainerQuery.parse(null, ua());
		assertNull(q.getName());
		assertFalse(q.getCondition().isValid());
	}
}
