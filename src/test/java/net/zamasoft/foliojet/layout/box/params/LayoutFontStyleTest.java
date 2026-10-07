package net.zamasoft.foliojet.layout.box.params;

import java.util.Locale;

import junit.framework.TestCase;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * Locks down preservation of language and paint-order in font specifications for ruby and warichu
 * (copies with only the size changed) (2026-10-04, overall review: ruby with lang=zh or ko was laid out
 * using the font sequence for Japanese text).
 */
public class LayoutFontStyleTest extends TestCase {
	private static FontStyleImpl base() {
		return new FontStyleImpl(FontFamilyList.SANS_SERIF, 20, FontStyle.Style.ITALIC, FontStyle.Weight.W_700,
				FontStyle.Direction.TB, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE, FontFeatureSet.EMPTY, false, true,
				FontStyle.TextOrientation.UPRIGHT, 7, Locale.KOREAN);
	}

	public void testWithSizeKeepsEverythingButTheSize() {
		final FontStyle half = LayoutFontStyle.withSize(base(), 10);
		assertEquals(10.0, half.getSize());
		assertEquals(Locale.KOREAN, half.getLang());
		assertEquals(FontStyle.Style.ITALIC, half.getStyle());
		assertEquals(FontStyle.Weight.W_700, half.getWeight());
		assertEquals(FontStyle.Direction.TB, half.getDirection());
		assertFalse(half.getSynthesisWeight());
		assertTrue(half.getSynthesisStyle());
		assertEquals(FontStyle.TextOrientation.UPRIGHT, half.getTextOrientation());
		assertEquals(7, half.getWidthClass());
	}

	public void testWithSizeKeepsThePaintOrder() {
		final FontStyle painted = LayoutFontStyle.withPaintOrder(base(), "stroke fill");
		final FontStyle half = LayoutFontStyle.withSize(painted, 10);
		assertTrue(half instanceof LayoutFontStyle);
		assertEquals("stroke fill", ((LayoutFontStyle) half).paintOrder());
		assertEquals(Locale.KOREAN, half.getLang());
	}
}
