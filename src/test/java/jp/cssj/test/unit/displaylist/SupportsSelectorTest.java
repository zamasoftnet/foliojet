package jp.cssj.test.unit.displaylist;

import java.util.Map;
import java.util.function.Predicate;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.parser.AtRulePreludeRewriter;

/**
 * {@code @supports selector()} inside {@code @media} (2026-10-09).
 *
 * <p>
 * ph-css cannot read {@code selector()}, {@code font-tech()} or {@code font-format()} in a {@code @supports}
 * condition, and inside an {@code @media} block its error recovery dropped every rule of the block. ja.wikipedia's
 * Vector skin starts an {@code @media screen} block with {@code @supports not selector(:focus-visible)}, so the rule
 * that hides the dropdown menus was lost and every menu printed open. The expected positions are Chrome 151's for
 * {@code files/unittest/3070-AT-RULE/supports-selector-in-media.html} at 794px (px x 0.75).
 * </p>
 */
public class SupportsSelectorTest extends TestCase {
	public void testSameAsChrome() throws Exception {
		final Map<String, double[]> texts = EndTagReopenTest
				.texts("files/unittest/3070-AT-RULE/supports-selector-in-media.html", "supports-selector");
		// selector(:focus-visible) is true, so "not selector(...)" does not hide N ...
		assertAt(texts, "N", 0, 0);
		// ... and the rule after it in the same @media block applies.
		assertFalse("X is hidden by the rule after @supports", texts.containsKey("X"));
		assertFalse("Y is hidden by @supports selector(:focus-visible)", texts.containsKey("Y"));
		// An unknown pseudo-class: false.
		assertAt(texts, "Z", 0, 20);
		// font-tech() is unknown (false), and the rules around it stay.
		assertAt(texts, "V0", 10, 40);
		assertFalse("V is hidden by the rule after @supports font-tech()", texts.containsKey("V"));
		// A selector list is not a <complex-selector>: false.
		assertAt(texts, "L", 0, 60);
		assertAt(texts, "W", 30, 80);
	}

	public void testRewriteKeepsTheRest() {
		final Predicate<String> known = s -> s.equals(":focus-visible");
		assertEquals("@media x{@supports not (-foliojet-supports:1){a{b:c}} .d{e:f}}", AtRulePreludeRewriter
				.rewrite("@media x{@supports not selector(:focus-visible){a{b:c}} .d{e:f}}", known));
		assertEquals("@supports (-foliojet-supports:0) or ((display:grid) and (-foliojet-supports:0)){}",
				AtRulePreludeRewriter.rewrite(
						"@supports selector(:is(a,b)) or ((display:grid) and font-format(woff2)){}", known));
		// Strings, comments and other at-rules are left alone.
		final String untouched = "a::after{content:\"@supports selector(x)\"} /* @supports selector(y) */ @media (x){}";
		assertSame(untouched, AtRulePreludeRewriter.rewrite(untouched, known));
	}

	private static void assertAt(final Map<String, double[]> texts, final String text, final double xPx,
			final double yPx) {
		final double[] at = texts.get(text);
		assertNotNull(text + " is not drawn: " + texts.keySet(), at);
		assertEquals(text + " x", xPx * 0.75, at[0], 0.5);
		assertEquals(text + " y", yPx * 0.75, at[1], 0.5);
	}
}
