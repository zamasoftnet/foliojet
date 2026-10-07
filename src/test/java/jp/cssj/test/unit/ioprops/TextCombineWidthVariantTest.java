package jp.cssj.test.unit.ioprops;

import java.lang.reflect.Method;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.builder.impl.StyledTextUnitizer;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/** Fix tate-chu-yoko width-variant requests and the compression ratio when the feature is absent. */
public class TextCombineWidthVariantTest extends TestCase {
	private static FontStyle style() {
		return new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL, FontStyle.Weight.W_400,
				FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
	}

	public void testWidthVariantMatchesCharacterCount() throws Exception {
		final FontStyle base = style();
		assertEquals(1, textCombineFontStyle(base, 2).getFeatures()
				.value(FontFeatureSet.packTag("hwid")));
		assertEquals(1, textCombineFontStyle(base, 3).getFeatures()
				.value(FontFeatureSet.packTag("twid")));
		assertEquals(1, textCombineFontStyle(base, 4).getFeatures()
				.value(FontFeatureSet.packTag("qwid")));
		assertSame(base, textCombineFontStyle(base, 1));
		assertSame(base, textCombineFontStyle(base, 5));
	}

	private static FontStyle textCombineFontStyle(final FontStyle base, final int count) throws Exception {
		final Method method = StyledTextUnitizer.class.getDeclaredMethod("textCombineFontStyle", FontStyle.class,
				int.class);
		method.setAccessible(true);
		return (FontStyle) method.invoke(null, base, count);
	}

	public void testFallbackScaleIsCellExtentDividedByNaturalWidth() {
		final TestBox box = new TestBox();
		box.setNaturalWidth(18);
		box.compressTextCombine(12, null);
		assertEquals(12.0 / 18.0, box.scaleX(), 0);
		assertEquals(12, box.getWidth(), 0);
	}

	/**
	 * Center a single character narrower than 1em in its cell, and do not center it again on the
	 * second call (when column balancing adds the same box to a line again)
	 * (2026-10-06, jigensha report). Previously, the second call recentered already-shifted glyph bounds,
	 * resetting the shift to 0 and placing the character's right edge at the cell center.
	 */
	public void testSingleCharacterStaysCenteredWhenFittedTwice() {
		final TestBox box = new TestBox();
		box.setNaturalWidth(6);
		box.compressTextCombine(12, new java.awt.geom.Rectangle2D.Double(0.5, -8, 5, 8));
		assertEquals(1, box.scaleX(), 0);
		assertEquals(3, box.offsetX(), 1e-9);
		assertEquals(12, box.getWidth(), 0);
		// Second call: glyph bounds are measured at the shifted position.
		box.compressTextCombine(12, new java.awt.geom.Rectangle2D.Double(3.5, -8, 5, 8));
		assertEquals(3, box.offsetX(), 1e-9);
		assertEquals(12, box.getWidth(), 0);
	}

	private static final class TestBox extends InlineBlockBox {
		TestBox() {
			super(params(), new InlinePos());
		}

		private static BlockParams params() {
			final BlockParams params = new BlockParams();
			params.fontStyle = new FontStyleImpl(FontFamilyList.SERIF, 12, FontStyle.Style.NORMAL,
					FontStyle.Weight.W_400, FontStyle.Direction.LTR, FontPolicyList.FONT_POLICY_CORE_CID_KEYED_VALUE);
			return params;
		}

		void setNaturalWidth(final double width) {
			this.width = width;
		}

		double scaleX() {
			return this.internalScaleX();
		}

		double offsetX() {
			return this.internalOffsetX();
		}
	}
}
