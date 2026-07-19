package net.zamasoft.foliojet.layout.box.params;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * calc()が絶対長さと割合を混在させた結果(LengthType.MIXED)が、Length/Dimension/
 * Insets/Offset、および{@link net.zamasoft.foliojet.layout.util.LayoutUtils}の
 * 解決関数を通して正しく基準値と合成されることを検証します。
 * {@code 実長さ = absolute + ratio * ref}(calc(50% + 10pt)なら ref=100 のとき
 * 10 + 0.5*100 = 60)。
 */
public class MixedLengthResolutionTest extends TestCase {
	private static final double DELTA = 1e-9;

	public void testLengthCreateMixedCollapsesWhenRatioIsZero() {
		Length length = Length.createMixed(10, 0);
		assertEquals(LengthType.ABSOLUTE, length.getType());
		assertEquals(10.0, length.getLength(), DELTA);
	}

	public void testLengthCreateMixedCollapsesWhenAbsoluteIsZero() {
		Length length = Length.createMixed(0, 0.5);
		assertEquals(LengthType.RELATIVE, length.getType());
		assertEquals(0.5, length.getLength(), DELTA);
	}

	public void testLengthCreateMixedKeepsBothComponents() {
		Length length = Length.createMixed(10, 0.5);
		assertEquals(LengthType.MIXED, length.getType());
		assertEquals(10.0, length.getLength(), DELTA);
		assertEquals(0.5, length.getRatio(), DELTA);
	}

	public void testComputeLengthResolvesMixed() {
		Length length = Length.createMixed(10, 0.5);
		// calc(10pt + 50%) を ref=100 で解決 -> 10 + 0.5*100 = 60
		assertEquals(60.0, LayoutUtils.computeLength(length, 100), DELTA);
	}

	public void testComputeDimensionWidthResolvesMixed() {
		Dimension dim = Dimension.create(10, 0.5, 20, 0, LengthType.MIXED, LengthType.ABSOLUTE);
		assertEquals(LengthType.MIXED, dim.getWidthType());
		assertEquals(0.5, dim.getWidthRatio(), DELTA);
		assertEquals(60.0, LayoutUtils.computeDimensionWidth(dim, 100), DELTA);
		// heightはABSOLUTEなのでrefに依存せずそのまま
		assertEquals(20.0, LayoutUtils.computeDimensionHeight(dim, 100), DELTA);
	}

	public void testComputeDimensionWidthMixedWithNoneRefStaysNone() {
		Dimension dim = Dimension.create(10, 0.5, 0, 0, LengthType.MIXED, LengthType.AUTO);
		assertEquals(LayoutUtils.NONE, LayoutUtils.computeDimensionWidth(dim, LayoutUtils.NONE));
	}

	public void testInsetsMixedRoundTripsThroughCut() {
		Insets insets = Insets.create(10, 0.5, 1, 0, 2, 0, 3, 0, LengthType.MIXED, LengthType.ABSOLUTE,
				LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		Insets cut = insets.cut(true, false, false, false);
		assertEquals(LengthType.MIXED, cut.getTopType());
		assertEquals(10.0, cut.getTop(), DELTA);
		assertEquals(0.5, cut.getTopRatio(), DELTA);
		// 落とした辺はABSOLUTE/0になる
		assertEquals(LengthType.ABSOLUTE, cut.getRightType());
		assertEquals(0.0, cut.getRight(), DELTA);
	}

	public void testInsetsIsNullTreatsMixedAsNonNull() {
		// MIXEDはcreate()がratio/absoluteいずれか0なら単純型へ縮退させるため、
		// 実際にMIXED型として保持される値は常に両成分非0=非ゼロ。
		Insets insets = Insets.create(0, 0.5, 0, 0, 0, 0, 0, 0, LengthType.MIXED, LengthType.ABSOLUTE,
				LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		assertFalse(insets.isNull());
	}

	public void testOffsetMixed() {
		Offset offset = Offset.create(5, 0.25, 0, 0, LengthType.MIXED, LengthType.ABSOLUTE);
		assertEquals(LengthType.MIXED, offset.getXType());
		assertEquals(5.0, offset.getX(), DELTA);
		assertEquals(0.25, offset.getXRatio(), DELTA);
	}

	public void testLengthTypeNeedsReference() {
		assertTrue(LengthType.RELATIVE.needsReference());
		assertTrue(LengthType.MIXED.needsReference());
		assertFalse(LengthType.ABSOLUTE.needsReference());
		assertFalse(LengthType.AUTO.needsReference());
	}
}
