package jp.cssj.test.unit._3000_SELECTOR;

import junit.framework.TestCase;

/**
 * Combinators retry farther ancestors and siblings when the rest of the selector fails from the nearest one
 * (2026-10-26, found by the design-pattern review: the matcher took the nearest match and never retried, unlike
 * browsers). Every paragraph is hidden unless a rule shows it.
 */
public class CombinatorBacktrackTest extends TestCase {
	/** A child combinator to the left of a descendant one: the nearer .b's parent is not .a, the farther one's is. */
	public void testChildLeftOfDescendant() throws Exception {
		assertEquals("[C1]", SiblingPositionAndPassCarryTest.shown(".a > .b .c { display: block }",
				"<div class=\"a\"><div class=\"b\"><div class=\"b\"><p class=\"c\">C1</p></div></div></div>"
						+ "<div class=\"a\"><div class=\"x\"><div class=\"b\"><p class=\"c\">N1</p></div></div></div>",
				1).toString());
	}

	/** An adjacent sibling combinator to the left of a descendant one. */
	public void testAdjacentLeftOfDescendant() throws Exception {
		assertEquals("[D1]", SiblingPositionAndPassCarryTest.shown(".p + .q .d { display: block }",
				"<div class=\"p\"></div><div class=\"q\"><div class=\"q\"><p class=\"d\">D1</p></div></div>"
						+ "<div class=\"z\"></div><div class=\"q\"><div class=\"q\"><p class=\"d\">N2</p></div></div>",
				1).toString());
	}

	/** An adjacent sibling combinator to the left of a general one: the nearer .b follows .z, the farther one .a. */
	public void testAdjacentLeftOfGeneralSibling() throws Exception {
		assertEquals("[E1]", SiblingPositionAndPassCarryTest.shown(".a + .b ~ .e { display: block }",
				"<div><p class=\"a\">a</p><p class=\"b\">b</p><p class=\"z\">z</p><p class=\"b\">b</p>"
						+ "<p class=\"e\">E1</p></div><div><p class=\"z\">z</p><p class=\"b\">b</p><p class=\"e\">N3</p></div>",
				1).toString());
	}

	/** The same inside :is() and :not(). */
	public void testInsideIsAndNot() throws Exception {
		assertEquals("[F1, G2]", SiblingPositionAndPassCarryTest.shown(
				".f:is(.a > .b .f) { display: block } .g:not(.a > .b .g) { display: block }",
				"<div class=\"a\"><div class=\"b\"><div class=\"b\"><p class=\"f\">F1</p><p class=\"g\">N4</p></div></div></div>"
						+ "<div class=\"x\"><div class=\"b\"><p class=\"g\">G2</p></div></div>",
				1).toString());
	}

	/** A long chain of descendant combinators over deep nesting without the leftmost compound stays cheap. */
	public void testDeepNestingWithoutLeftmost() throws Exception {
		final StringBuilder body = new StringBuilder();
		for (int i = 0; i < 400; ++i) {
			body.append("<div class=\"b c d\">");
		}
		body.append("<p class=\"e\">N5</p><p class=\"k\">K1</p>");
		for (int i = 0; i < 400; ++i) {
			body.append("</div>");
		}
		final long start = System.nanoTime();
		assertEquals("[K1]", SiblingPositionAndPassCarryTest.shown(
				".a .b .c .d .e { display: block } .b .c .d .k { display: block }", body.toString(), 1).toString());
		assertTrue("遅い: " + (System.nanoTime() - start) / 1_000_000 + "ms", System.nanoTime() - start < 20_000_000_000L);
	}
}
