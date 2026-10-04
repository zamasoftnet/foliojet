package net.zamasoft.foliojet.css.value;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;

/**
 * list-style-type の名前の表が一つで、名前と値が往復することを固定します(2026-10-04、全体レビュー。
 * {@code toString} の switch に upper-latin が無く、例外になっていた)。
 */
public class ListStyleTypeValueTest extends TestCase {
	public void testEveryValueRoundTripsThroughItsName() {
		for (final ListStyleTypeValue value : ListStyleTypeValue.values()) {
			assertSame(value.toString(), value, GeneratedValueUtils.toListStyleType(value.toString()));
		}
		assertEquals("upper-latin", ListStyleTypeValue.UPPER_LATIN_VALUE.toString());
	}

	public void testAliasesAndCase() {
		assertSame(ListStyleTypeValue.UPPER_ROMAN_VALUE, GeneratedValueUtils.toListStyleType("Upper-Roman"));
		assertSame(ListStyleTypeValue._CSSJ_FULL_WIDTH_DECIMAL_VALUE,
				GeneratedValueUtils.toListStyleType("-cssj-decimal-full-width"));
		assertSame(ListStyleTypeValue._CSSJ_CJK_DECIMAL_VALUE, GeneratedValueUtils.toListStyleType("-cssj-cjk-decimal"));
		assertEquals("cjk-decimal", ListStyleTypeValue._CSSJ_CJK_DECIMAL_VALUE.toString());
		assertNull(GeneratedValueUtils.toListStyleType("no-such-style"));
	}
}
