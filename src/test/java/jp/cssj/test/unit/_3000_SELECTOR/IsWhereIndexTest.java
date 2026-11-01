package jp.cssj.test.unit._3000_SELECTOR;

import junit.framework.TestCase;

/**
 * A rule whose rightmost compound depends on an :is()/:where() is indexed under the keys of the arguments (2026-10-31:
 * it was tried on every element, the square of the rules). The index must not lose a match: :is(X, Y) shows the same
 * paragraphs as the selector list X, Y, whichever arguments can be indexed. Every paragraph is hidden unless a rule
 * shows it; !important because :where() has no specificity to beat the hiding rule.
 */
public class IsWhereIndexTest extends TestCase {
	private static final String BODY = "<div class=\"w\"><p class=\"a\">A1</p><p class=\"b\">B1</p><p class=\"a b\">C1</p>"
			+ "<p id=\"x\">D1</p><p data-k=\"1\">E1</p><p>F1</p><i class=\"a\">G1</i><i>H1</i></div>"
			+ "<p class=\"A\">I1</p><p class=\"a\" id=\"y\">J1</p><p class=\"d\">K1</p><p class=\"d b\">L1</p>";

	private static String shown(final String selector) throws Exception {
		return SiblingPositionAndPassCarryTest.shown(selector + " { display: block !important }", BODY, 1).toString();
	}

	private static void assertLikeList(final String list, final String... arguments) throws Exception {
		final String expected = shown(String.join(", ", arguments));
		final String wrapped = list.replace("%", String.join(", ", arguments));
		assertEquals(wrapped, expected, shown(wrapped));
	}

	public void testSameAsTheList() throws Exception {
		assertEquals("[A1, B1, C1, G1, I1, J1, L1]", shown(":is(.a, .b)"));
		assertEquals("[A1, B1, C1, G1, I1, J1, L1]", shown(":where(.a, .b)"));
		for (final String form : new String[] { ":is(%)", ":where(%)", ":is(:where(%))" }) {
			assertLikeList(form, ".a", ".b");
			assertLikeList(form, ".a", "#x");
			assertLikeList(form, ".a", "i");
			assertLikeList(form, ".a", "p.a");
			assertLikeList(form, "p", "i");
			assertLikeList(form, ".w .b", ".d");
			assertLikeList(form, ".w > i", "#y");
			assertLikeList(form, ".a + .b", ".d ~ .b");
			// Arguments without a key: the rule stays with the universal rules
			assertLikeList(form, ".a", "[data-k]");
			assertLikeList(form, ".a", "*");
			assertLikeList(form, ".a", ":not(.a)");
			assertLikeList(form, ".a", ":first-child");
		}
	}

	/** The other conditions of the compound still apply. */
	public void testWithOtherConditions() throws Exception {
		assertEquals("[A1, C1, I1, J1]", shown("p:is(.a, .q)"));
		assertEquals("[C1]", shown(".b:where(.a, .q)"));
		assertEquals("[A1, J1]", shown(":is(.a, .b):not(.b):not(i):first-child, #y:is(.a, .b)"));
		assertEquals("[C1]", shown(":where(.a):where(.b, .q)"));
	}

	/** A rule met through two of its keys applies once, also with layers and !important. */
	public void testOnceFromTwoBuckets() throws Exception {
		assertEquals("[A1, B1, C1, G1, I1, J1, L1]", SiblingPositionAndPassCarryTest
				.shown("@layer l { :is(.a, .b, p.a) { display: block !important } }", BODY, 1).toString());
	}
}
