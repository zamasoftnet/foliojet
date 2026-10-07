package net.zamasoft.foliojet.css.util;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;

/** Tests for resolution of standard counter-style names and legacy compatibility names. */
public class GeneratedValueUtilsTest extends TestCase {
	public void testCjkDecimalStandardName() {
		assertSame(ListStyleTypeValue._CSSJ_CJK_DECIMAL_VALUE,
				GeneratedValueUtils.toListStyleType("cjk-decimal"));
		assertSame(ListStyleTypeValue._CSSJ_CJK_DECIMAL_VALUE,
				GeneratedValueUtils.toListStyleType("-cssj-cjk-decimal"));
		assertEquals("cjk-decimal", ListStyleTypeValue._CSSJ_CJK_DECIMAL_VALUE.toString());
		assertEquals("二〇二六", GeneratedValueUtils.format(2026, ListStyleTypeValue._CSSJ_CJK_DECIMAL));
	}
}
