package net.zamasoft.foliojet.layout.segment;

import java.awt.geom.AffineTransform;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.CellAlign;
import net.zamasoft.foliojet.layout.box.params.EmptyCellsMode;
import net.zamasoft.foliojet.layout.box.params.FirstLineParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.RowGroupType;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.box.params.TextShadow;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * M6d-A3b Stage1(2026-07-22新設)のfreeze/materialize契約を固定する
 * 単体テストです。まだ未配線(既存の{@code LayoutSource}/
 * {@code SegmentEvent}への変換アダプタはA3c以降)——この段階では
 * テンプレート自身の「複数回materializeしても互いに影響しない」
 * (M6d-Aの最重要契約)を直接検証する。
 */
public class ParamsTemplateTest extends TestCase {
	/** 2回materializeした結果は、値は等しいが可変フィールドの参照は別物である。 */
	public void testBlockParamsMaterializeIsIndependent() {
		final BlockParams source = new BlockParams();
		source.transform = AffineTransform.getTranslateInstance(3, 4);
		source.textShadows = new TextShadow[] { new TextShadow(1, 2, RGBColor.create(0, 0, 0)) };
		source.orphans = 3;

		final BlockParamsTemplate template = BlockParamsTemplate.freeze(source);
		final BlockParams m1 = template.materialize();
		final BlockParams m2 = template.materialize();

		// 値は等しい
		assertEquals(source.transform, m1.transform);
		assertEquals(source.transform, m2.transform);
		assertEquals(3, m1.orphans);
		assertEquals(3, m2.orphans);
		assertEquals(1, m1.textShadows.length);

		// しかし可変フィールドの参照は別物(独立している)
		assertNotSame(m1.transform, m2.transform);
		assertNotSame(m1.textShadows, m2.textShadows);

		// m1のtransformをミューテートしてもm2・テンプレート自身・3回目の
		// materialize結果には一切影響しない
		m1.transform.translate(100, 100);
		final BlockParams m3 = template.materialize();
		assertEquals(AffineTransform.getTranslateInstance(3, 4), m2.transform);
		assertEquals(AffineTransform.getTranslateInstance(3, 4), m3.transform);
		assertFalse(m1.transform.equals(m2.transform));
	}

	/** freeze後に元のsourceをミューテートしても、テンプレートは既にfreeze済みの値を保持する。 */
	public void testFreezeIsUnaffectedByLaterMutationOfSource() {
		final BlockParams source = new BlockParams();
		source.transform = AffineTransform.getTranslateInstance(1, 1);
		source.orphans = 2;

		final BlockParamsTemplate template = BlockParamsTemplate.freeze(source);

		// freeze後にsourceを変更
		source.transform.translate(50, 50);
		source.orphans = 9;

		final BlockParams materialized = template.materialize();
		assertEquals(AffineTransform.getTranslateInstance(1, 1), materialized.transform);
		assertEquals(2, materialized.orphans);
	}

	/** nullなtextShadowsは正しくnullのまま往復する。 */
	public void testNullTextShadowsRoundTrip() {
		final BlockParams source = new BlockParams();
		source.textShadows = null;
		final BlockParams materialized = BlockParamsTemplate.freeze(source).materialize();
		assertNull(materialized.textShadows);
	}

	/** firstLineStyle(nullable、再帰的なFirstLineParams)が正しく往復する。 */
	public void testFirstLineStyleRoundTrips() {
		final BlockParams source = new BlockParams();
		source.firstLineStyle = new FirstLineParams();
		source.firstLineStyle.textAlign = net.zamasoft.foliojet.layout.box.params.AbstractLineParams.TEXT_ALIGN_CENTER;
		source.firstLineStyle.transform = AffineTransform.getScaleInstance(2, 2);

		final BlockParamsTemplate template = BlockParamsTemplate.freeze(source);
		final BlockParams m1 = template.materialize();
		final BlockParams m2 = template.materialize();

		assertNotNull(m1.firstLineStyle);
		assertEquals(net.zamasoft.foliojet.layout.box.params.AbstractLineParams.TEXT_ALIGN_CENTER,
				m1.firstLineStyle.textAlign);
		assertNotSame(m1.firstLineStyle, m2.firstLineStyle);
		assertNotSame(m1.firstLineStyle.transform, m2.firstLineStyle.transform);
		assertEquals(m1.firstLineStyle.transform, m2.firstLineStyle.transform);
	}

	/** firstLineStyleがnullなら、materialize後もnullのまま。 */
	public void testNullFirstLineStyleRoundTrips() {
		final BlockParams source = new BlockParams();
		source.firstLineStyle = null;
		final BlockParams materialized = BlockParamsTemplate.freeze(source).materialize();
		assertNull(materialized.firstLineStyle);
	}

	/** FlowPosも同様に複数回materializeしても値が保たれ、別インスタンスになる。 */
	public void testFlowPosMaterializeIsIndependent() {
		final FlowPos source = new FlowPos();
		source.align = net.zamasoft.foliojet.layout.box.params.Align.CENTER;
		source.columnSpan = FlowPos.COLUMN_SPAN_ALL;

		final FlowPosTemplate template = FlowPosTemplate.freeze(source);
		final FlowPos m1 = template.materialize();
		final FlowPos m2 = template.materialize();

		assertNotSame(m1, m2);
		assertEquals(net.zamasoft.foliojet.layout.box.params.Align.CENTER, m1.align);
		assertEquals(FlowPos.COLUMN_SPAN_ALL, m2.columnSpan);
	}

	/**
	 * InlineParams(AbstractTextParamsを直接継承、line固有フィールドを
	 * 持たない)も、共有TextParamsFields経由で同じ独立性契約を満たす。
	 */
	public void testInlineParamsMaterializeIsIndependent() {
		final InlineParams source = new InlineParams();
		source.transform = AffineTransform.getRotateInstance(1.0);
		source.textShadows = new TextShadow[] { new TextShadow(5, 6, RGBColor.create(255, 0, 0)) };

		final InlineParamsTemplate template = InlineParamsTemplate.freeze(source);
		final InlineParams m1 = template.materialize();
		final InlineParams m2 = template.materialize();

		assertEquals(source.transform, m1.transform);
		assertNotSame(m1.transform, m2.transform);
		assertNotSame(m1.textShadows, m2.textShadows);

		m1.transform.translate(9, 9);
		assertEquals(AffineTransform.getRotateInstance(1.0), m2.transform);
	}

	/** InlinePosも複数回materializeしても値が保たれ、別インスタンスになる。 */
	public void testInlinePosMaterializeIsIndependent() {
		final InlinePos source = new InlinePos();
		source.lineHeight = 1.5;

		final InlinePosTemplate template = InlinePosTemplate.freeze(source);
		final InlinePos m1 = template.materialize();
		final InlinePos m2 = template.materialize();

		assertNotSame(m1, m2);
		assertEquals(1.5, m1.lineHeight);
		assertEquals(source.verticalAlign, m2.verticalAlign);
	}

	/**
	 * FloatPosはFlowPosと同じAbstractNormalFlowPosを継承するため
	 * (NormalFlowPosFields経由で)同じ独立性契約を満たす。
	 */
	public void testFloatPosMaterializeIsIndependent() {
		final FloatPos source = new FloatPos();
		source.floating = FloatSide.END;
		source.clear = net.zamasoft.foliojet.layout.box.params.ClearMode.BOTH;

		final FloatPosTemplate template = FloatPosTemplate.freeze(source);
		final FloatPos m1 = template.materialize();
		final FloatPos m2 = template.materialize();

		assertNotSame(m1, m2);
		assertEquals(FloatSide.END, m1.floating);
		assertEquals(net.zamasoft.foliojet.layout.box.params.ClearMode.BOTH, m2.clear);
	}

	/** 特殊floatの実行時型と上下指定もrecipe化で失わない。 */
	public void testSpecialFloatKindsSurviveMaterialize() {
		final FloatPos footnote = FloatPosTemplate
				.freeze(new net.zamasoft.foliojet.layout.box.params.FootnotePos()).materialize();
		final FloatPos pageTop = FloatPosTemplate
				.freeze(new net.zamasoft.foliojet.layout.box.params.PageFloatPos(true)).materialize();
		final FloatPos pageBottom = FloatPosTemplate
				.freeze(new net.zamasoft.foliojet.layout.box.params.PageFloatPos(false)).materialize();

		assertTrue(footnote instanceof net.zamasoft.foliojet.layout.box.params.FootnotePos);
		assertTrue(pageTop instanceof net.zamasoft.foliojet.layout.box.params.PageFloatPos);
		assertTrue(((net.zamasoft.foliojet.layout.box.params.PageFloatPos) pageTop).top);
		assertFalse(((net.zamasoft.foliojet.layout.box.params.PageFloatPos) pageBottom).top);
	}

	/**
	 * InnerTableParams(Paramsを直接継承、AbstractTextParamsを経由しない)
	 * もParamsFields経由で同じ独立性契約を満たす。
	 */
	public void testInnerTableParamsMaterializeIsIndependent() {
		final InnerTableParams source = new InnerTableParams();
		source.transform = AffineTransform.getScaleInstance(3, 3);
		source.pageBreakInside = net.zamasoft.foliojet.layout.box.params.PageBreakMode.AVOID;

		final InnerTableParamsTemplate template = InnerTableParamsTemplate.freeze(source);
		final InnerTableParams m1 = template.materialize();
		final InnerTableParams m2 = template.materialize();

		assertNotSame(m1.transform, m2.transform);
		assertEquals(source.transform, m1.transform);
		assertEquals(net.zamasoft.foliojet.layout.box.params.PageBreakMode.AVOID, m2.pageBreakInside);

		m1.transform.translate(1, 1);
		assertEquals(AffineTransform.getScaleInstance(3, 3), m2.transform);
	}

	/** TableCellPosも複数回materializeしても値が保たれ、別インスタンスになる。 */
	public void testTableCellPosMaterializeIsIndependent() {
		final TableCellPos source = new TableCellPos();
		source.colspan = 2;
		source.rowspan = 3;
		source.emptyCells = EmptyCellsMode.SHOW;
		source.verticalAlign = CellAlign.MIDDLE;

		final TableCellPosTemplate template = TableCellPosTemplate.freeze(source);
		final TableCellPos m1 = template.materialize();
		final TableCellPos m2 = template.materialize();

		assertNotSame(m1, m2);
		assertEquals(2, m1.colspan);
		assertEquals(3, m2.rowspan);
		assertEquals(EmptyCellsMode.SHOW, m1.emptyCells);
		assertEquals(CellAlign.MIDDLE, m2.verticalAlign);
	}

	/** TableRowGroupPosも複数回materializeしても値が保たれ、別インスタンスになる。 */
	public void testTableRowGroupPosMaterializeIsIndependent() {
		final TableRowGroupPos source = new TableRowGroupPos();
		source.rowGroupType = RowGroupType.FOOTER;

		final TableRowGroupPosTemplate template = TableRowGroupPosTemplate.freeze(source);
		final TableRowGroupPos m1 = template.materialize();
		final TableRowGroupPos m2 = template.materialize();

		assertNotSame(m1, m2);
		assertEquals(RowGroupType.FOOTER, m1.rowGroupType);
		assertEquals(RowGroupType.FOOTER, m2.rowGroupType);
	}

	/** TableRowPos(固有フィールドなし)も複数回materializeしても別インスタンスになる。 */
	public void testTableRowPosMaterializeIsIndependent() {
		final TableRowPos source = new TableRowPos();
		source.pageBreakBefore = net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE;

		final TableRowPosTemplate template = TableRowPosTemplate.freeze(source);
		final TableRowPos m1 = template.materialize();
		final TableRowPos m2 = template.materialize();

		assertNotSame(m1, m2);
		assertEquals(net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE, m1.pageBreakBefore);
		assertEquals(net.zamasoft.foliojet.layout.box.params.PageBreakMode.PAGE, m2.pageBreakBefore);
	}

	/** TableColumnPos(Posを直接実装、共有祖先なし)も複数回materializeしても別インスタンスになる。 */
	public void testTableColumnPosMaterializeIsIndependent() {
		final TableColumnPos source = new TableColumnPos();
		source.span = 4;

		final TableColumnPosTemplate template = TableColumnPosTemplate.freeze(source);
		final TableColumnPos m1 = template.materialize();
		final TableColumnPos m2 = template.materialize();

		assertNotSame(m1, m2);
		assertEquals(4, m1.span);
		assertEquals(4, m2.span);
	}
}
