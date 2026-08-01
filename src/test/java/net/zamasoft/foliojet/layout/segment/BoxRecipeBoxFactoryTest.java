package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.CellAlign;
import net.zamasoft.foliojet.layout.box.params.EmptyCellsMode;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.box.params.RowGroupType;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * {@link BoxRecipeBoxFactory}(2026-07-22新設、M6d-A3d)の単体テストです。
 * {@code BoxRecipe}の最初の意味のある使い道——テンプレートから実際の
 * {@code IBox}を再構築できることを、13種類すべてについて固定する
 * (codex設計相談で「A3dの本命はこれ、kindだけの比較はtautological」と
 * 確認済み)。
 */
public class BoxRecipeBoxFactoryTest extends TestCase {
	/**
	 * {@code AbstractBlockBox}のコンストラクタは{@code assert params
	 * .fontStyle != null}を要求する(通常はCSSスタイル解決の産物)。
	 * このテストはファクトリの組み立てロジックのみを見るため、値そのものは
	 * どうでもよくnon-nullでさえあればよい({@code ContinuationCapabilityTest}
	 * と同じダミー実装パターン)。
	 */
	private static final FontStyle DUMMY_FONT_STYLE = new FontStyle() {
		public Direction getDirection() {
			return Direction.LTR;
		}

		public Weight getWeight() {
			return Weight.W_400;
		}

		public Style getStyle() {
			return Style.NORMAL;
		}

		public net.zamasoft.pdfg2d.gc.font.FontFamilyList getFamily() {
			return null;
		}

		public double getSize() {
			return 10;
		}

		public net.zamasoft.pdfg2d.gc.font.FontPolicyList getPolicy() {
			return null;
		}
	};

	/** {@code InlineBox}のコンストラクタはさらに{@code lineBreakRules}/{@code fontManager}もnon-null要求する。 */
	private static final TextBreakingRules DUMMY_LINE_BREAK_RULES = new TextBreakingRules() {
		public boolean atomic(final char c1, final char c2) {
			return false;
		}

		public boolean canSeparate(final char c1, final char c2) {
			return false;
		}
	};

	private static final FontManager DUMMY_FONT_MANAGER = new FontManager() {
		public void addFontFace(final net.zamasoft.pdfg2d.gc.font.FontFace face) {
		}

		public net.zamasoft.pdfg2d.gc.font.FontListMetrics getFontListMetrics(final FontStyle fontStyle) {
			return null;
		}

		public net.zamasoft.pdfg2d.gc.text.TextShaper getTextShaper() {
			return null;
		}
	};

	private static BlockParams blockParams() {
		final BlockParams params = new BlockParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		return params;
	}

	private static InlineParams inlineParams() {
		final InlineParams params = new InlineParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		params.lineBreakRules = DUMMY_LINE_BREAK_RULES;
		params.fontManager = DUMMY_FONT_MANAGER;
		return params;
	}

	private static ReplacedParams replacedParams() {
		final ReplacedParams params = new ReplacedParams();
		params.fontStyle = DUMMY_FONT_STYLE;
		return params;
	}

	/** BoxKind.FLOWはFlowBlockBoxへ、非デフォルト値も保持したまま再構築される。 */
	public void testFlowRecipeCreatesFlowBlockBox() {
		final BlockParams params = blockParams();
		params.orphans = 5;
		final FlowPos pos = new FlowPos();
		pos.align = Align.CENTER;

		final BoxRecipe recipe = new BoxRecipe.Flow(BlockParamsTemplate.freeze(params), FlowPosTemplate.freeze(pos));
		final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);

		assertTrue(box instanceof FlowBlockBox);
		assertEquals(5, ((BlockParams) box.getParams()).orphans);
		assertEquals(Align.CENTER, ((FlowPos) box.getPos()).align);
	}

	/**
	 * BoxKind.CAPTIONはTableCaptionPos付きFlowBlockBoxへ、captionSide・
	 * align等の非デフォルト値も保持したまま再構築される(caption recipe化
	 * C1——consult-codex-2026-08-01-caption-recipe.txt)。
	 */
	public void testCaptionRecipeCreatesCaptionBox() {
		final BlockParams params = blockParams();
		params.orphans = 7;
		final net.zamasoft.foliojet.layout.box.params.TableCaptionPos pos = new net.zamasoft.foliojet.layout.box.params.TableCaptionPos();
		pos.align = Align.CENTER;
		pos.captionSide = net.zamasoft.foliojet.layout.box.params.CaptionSideMode.AFTER;

		final BoxRecipe recipe = new BoxRecipe.Caption(BlockParamsTemplate.freeze(params),
				TableCaptionPosTemplate.freeze(pos));
		assertEquals(BoxKind.CAPTION, recipe.kind());
		final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);

		assertTrue(box instanceof FlowBlockBox);
		final net.zamasoft.foliojet.layout.box.params.TableCaptionPos out = (net.zamasoft.foliojet.layout.box.params.TableCaptionPos) box
				.getPos();
		assertEquals(net.zamasoft.foliojet.layout.box.params.PosType.TABLE_CAPTION, out.getType());
		assertEquals(net.zamasoft.foliojet.layout.box.params.CaptionSideMode.AFTER, out.captionSide);
		assertEquals(Align.CENTER, out.align);
		assertEquals(7, ((BlockParams) box.getParams()).orphans);

		// materializeは呼び出しごとに独立した新品を返す
		final INonReplacedBox box2 = BoxRecipeBoxFactory.create(recipe);
		assertTrue(box.getPos() != box2.getPos());
	}

	/** BoxKind.GRIDはGridBoxへ再構築され、トラック定義とgapを保つ(Grid G0c)。 */
	public void testGridRecipeCreatesGridBox() {
		final net.zamasoft.foliojet.layout.box.params.GridParams params = new net.zamasoft.foliojet.layout.box.params.GridParams();
		copyBlockParams(blockParams(), params);
		params.templateColumns = java.util.List.of(new net.zamasoft.foliojet.css.value.GridTrackListValue.Fixed(100),
				net.zamasoft.foliojet.css.value.GridTrackListValue.Auto.INSTANCE,
				new net.zamasoft.foliojet.css.value.GridTrackListValue.Fr(2));
		params.rowGap = 7;
		params.columnGap = 3;
		final BoxRecipe recipe = new BoxRecipe.Grid(GridParamsTemplate.freeze(params),
				FlowPosTemplate.freeze(new FlowPos()));
		final long before = BoxRecipeBoxFactory.GRID_REPLAYS.get();
		final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
		assertTrue(box instanceof net.zamasoft.foliojet.layout.box.impl.GridBox);
		final net.zamasoft.foliojet.layout.box.params.GridParams out = ((net.zamasoft.foliojet.layout.box.impl.GridBox) box)
				.getGridParams();
		assertEquals(3, out.templateColumns.size());
		assertEquals(7.0, out.rowGap, 0);
		assertEquals(3.0, out.columnGap, 0);
		assertEquals(before + 1, BoxRecipeBoxFactory.GRID_REPLAYS.get());
		// PageAtomicBoxの印も再構築で保たれる(クラス固有なので自明だが
		// 契約として固定)
		assertTrue(box instanceof net.zamasoft.foliojet.layout.box.PageAtomicBox);
	}

	/** BoxKind.FLEXはFlexBoxへ再構築され、direction/wrap/itemSpecを保つ(Flex F0c/F1a)。 */
	public void testFlexRecipeCreatesFlexBox() {
		final net.zamasoft.foliojet.layout.box.params.FlexParams params = new net.zamasoft.foliojet.layout.box.params.FlexParams();
		copyBlockParams(blockParams(), params);
		params.orphans = 7;
		params.flexDirection = net.zamasoft.foliojet.layout.box.params.FlexDirection.COLUMN;
		params.flexWrap = net.zamasoft.foliojet.layout.box.params.FlexWrap.WRAP;
		final FlowPos sourcePos = new FlowPos();
		final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec = net.zamasoft.foliojet.layout.box.params.FlexItemSpec
				.of(2, 3, net.zamasoft.foliojet.css.value.FlexBasisValue.CONTENT_VALUE,
						net.zamasoft.foliojet.layout.box.params.BoxAlignment.AUTO, 0, true, false);
		sourcePos.flexItem = spec;
		final BoxRecipe recipe = new BoxRecipe.Flex(FlexParamsTemplate.freeze(params),
				FlowPosTemplate.freeze(sourcePos));
		final long before = BoxRecipeBoxFactory.FLEX_REPLAYS.get();
		final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
		assertTrue(box instanceof net.zamasoft.foliojet.layout.box.impl.FlexBox);
		final net.zamasoft.foliojet.layout.box.params.FlexParams out = ((net.zamasoft.foliojet.layout.box.impl.FlexBox) box)
				.getFlexParams();
		assertEquals(7, out.orphans);
		assertSame(net.zamasoft.foliojet.layout.box.params.FlexDirection.COLUMN, out.flexDirection);
		assertSame(net.zamasoft.foliojet.layout.box.params.FlexWrap.WRAP, out.flexWrap);
		assertSame(spec, ((FlowPos) box.getPos()).flexItem);
		assertEquals(before + 1, BoxRecipeBoxFactory.FLEX_REPLAYS.get());
		// PageAtomicBoxの印(F0bのatomic移動)も再構築で保たれる
		assertTrue(box instanceof net.zamasoft.foliojet.layout.box.PageAtomicBox);
		// materializeは呼び出しごとに独立した新品を返す
		final INonReplacedBox box2 = BoxRecipeBoxFactory.create(recipe);
		assertTrue(box.getParams() != box2.getParams());
		assertTrue(box.getPos() != box2.getPos());
		// 全既定specはsingleton共有のままround tripする
		final FlowPos defaultPos = FlowPosTemplate.freeze(new FlowPos()).materialize();
		assertSame(net.zamasoft.foliojet.layout.box.params.FlexItemSpec.DEFAULT, defaultPos.flexItem);
	}

	private static void copyBlockParams(final BlockParams source, final BlockParams target) {
		final BlockParamsTemplate t = BlockParamsTemplate.freeze(source);
		final BlockParams m = t.materialize();
		// materializeの内容をtargetへ移す最短経路が無いため、テストでは
		// fontStyle等の必須フィールドだけを写す
		target.fontStyle = source.fontStyle;
		target.fontManager = source.fontManager;
		target.lineBreakRules = source.lineBreakRules;
		target.flow = source.flow;
		target.element = source.element;
	}

	/** MulticolumnBlockBoxもFLOWと同じテンプレート組で再構築される。 */
	public void testMulticolRecipeCreatesMulticolumnBlockBox() {
		final BoxRecipe recipe = new BoxRecipe.Multicol(BlockParamsTemplate.freeze(blockParams()),
				FlowPosTemplate.freeze(new FlowPos()));
		assertTrue(BoxRecipeBoxFactory.create(recipe) instanceof MulticolumnBlockBox);
	}

	/** BoxKind.INLINEはInlineBoxへ再構築される。 */
	public void testInlineRecipeCreatesInlineBox() {
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 2.5;
		final BoxRecipe recipe = new BoxRecipe.Inline(InlineParamsTemplate.freeze(inlineParams()),
				InlinePosTemplate.freeze(pos));
		final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
		assertTrue(box instanceof InlineBox);
		assertEquals(2.5, ((InlinePos) box.getPos()).lineHeight);
	}

	/** BoxKind.MARKERはOutsideMarkerBoxへ再構築される。 */
	public void testMarkerRecipeCreatesOutsideMarkerBox() {
		final BoxRecipe recipe = new BoxRecipe.Marker(BlockParamsTemplate.freeze(blockParams()),
				InlinePosTemplate.freeze(new InlinePos()));
		assertTrue(BoxRecipeBoxFactory.create(recipe) instanceof OutsideMarkerBox);
	}

	/** BoxKind.FLOAT_BLOCKはFloatBlockBoxへ、floatingの値も保持したまま再構築される。 */
	public void testFloatBlockRecipeCreatesFloatBlockBox() {
		final FloatPos pos = new FloatPos();
		pos.floating = FloatSide.END;
		final BoxRecipe recipe = new BoxRecipe.FloatBlock(BlockParamsTemplate.freeze(blockParams()),
				FloatPosTemplate.freeze(pos));
		final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
		assertTrue(box instanceof FloatBlockBox);
		assertEquals(FloatSide.END, ((FloatPos) box.getPos()).floating);
	}

	/** BoxKind.INLINE_BLOCKはInlineBlockBoxへ再構築される。 */
	public void testInlineBlockRecipeCreatesInlineBlockBox() {
		final BoxRecipe recipe = new BoxRecipe.InlineBlock(BlockParamsTemplate.freeze(blockParams()),
				InlinePosTemplate.freeze(new InlinePos()));
		// InsideMarkerBox/OutsideMarkerBoxはいずれもInlineBlockBoxのサブ
		// クラスのため、instanceofではなく厳密なクラス一致で確認する
		assertEquals(InlineBlockBox.class, BoxRecipeBoxFactory.create(recipe).getClass());
	}

	/** BoxKind.INSIDE_MARKERはInsideMarkerBoxへ再構築される。 */
	public void testInsideMarkerRecipeCreatesInsideMarkerBox() {
		final BoxRecipe recipe = new BoxRecipe.InsideMarker(BlockParamsTemplate.freeze(blockParams()),
				InlinePosTemplate.freeze(new InlinePos()));
		assertTrue(BoxRecipeBoxFactory.create(recipe) instanceof InsideMarkerBox);
	}

	/** BoxKind.TABLE_ROW_GROUP/TABLE_ROW/TABLE_COLUMN_GROUP/TABLE_COLUMNはInnerTableParamsで再構築される。 */
	public void testInnerTableParamsRecipes() {
		final InnerTableParams rowGroupParams = new InnerTableParams();
		final TableRowGroupPos rowGroupPos = new TableRowGroupPos();
		rowGroupPos.rowGroupType = RowGroupType.HEADER;
		final BoxRecipe rowGroupRecipe = new BoxRecipe.TableRowGroup(InnerTableParamsTemplate.freeze(rowGroupParams),
				TableRowGroupPosTemplate.freeze(rowGroupPos));
		final INonReplacedBox rowGroup = BoxRecipeBoxFactory.create(rowGroupRecipe);
		assertTrue(rowGroup instanceof TableRowGroupBox);
		assertEquals(RowGroupType.HEADER, ((TableRowGroupPos) rowGroup.getPos()).rowGroupType);

		final BoxRecipe rowRecipe = new BoxRecipe.TableRow(InnerTableParamsTemplate.freeze(new InnerTableParams()),
				TableRowPosTemplate.freeze(new TableRowPos()));
		assertTrue(BoxRecipeBoxFactory.create(rowRecipe) instanceof TableRowBox);

		final TableColumnPos columnPos = new TableColumnPos();
		columnPos.span = 3;
		final BoxRecipe columnGroupRecipe = new BoxRecipe.TableColumnGroup(
				InnerTableParamsTemplate.freeze(new InnerTableParams()), TableColumnPosTemplate.freeze(columnPos));
		final INonReplacedBox columnGroup = BoxRecipeBoxFactory.create(columnGroupRecipe);
		assertTrue(columnGroup instanceof TableColumnGroupBox);
		assertEquals(3, ((TableColumnPos) columnGroup.getPos()).span);

		final BoxRecipe columnRecipe = new BoxRecipe.TableColumn(InnerTableParamsTemplate.freeze(new InnerTableParams()),
				TableColumnPosTemplate.freeze(new TableColumnPos()));
		// TableColumnGroupBox extends TableColumnBoxのため、厳密なクラス一致で確認する
		assertEquals(TableColumnBox.class, BoxRecipeBoxFactory.create(columnRecipe).getClass());
	}

	/**
	 * BoxKind.TABLE_CELLは既存のBlockParamsを再利用し、呼び出しごとに
	 * 新品(独立)のFlowContainerを持つ。
	 */
	public void testTableCellRecipeGetsFreshContainer() {
		final BlockParams params = blockParams();
		final TableCellPos pos = new TableCellPos();
		pos.colspan = 2;
		pos.emptyCells = EmptyCellsMode.SHOW;
		pos.verticalAlign = CellAlign.MIDDLE;
		final BoxRecipe recipe = new BoxRecipe.TableCell(BlockParamsTemplate.freeze(params),
				TableCellPosTemplate.freeze(pos));

		final TableCellBox cell1 = (TableCellBox) BoxRecipeBoxFactory.create(recipe);
		final TableCellBox cell2 = (TableCellBox) BoxRecipeBoxFactory.create(recipe);

		assertNotSame(cell1, cell2);
		assertNotSame(cell1.getContainer(), cell2.getContainer());
		assertNotSame(cell1.getParams(), cell2.getParams());
		assertEquals(2, cell1.getTableCellPos().colspan);
		assertEquals(EmptyCellsMode.SHOW, cell2.getTableCellPos().emptyCells);
	}

	/**
	 * BoxKind.ABSOLUTEはAbsoluteBlockBoxへ、AbsolutePosの非デフォルト値も
	 * 保持したまま再構築される(E-6増分4e、2026-07-24)。
	 */
	public void testAbsoluteRecipeCreatesAbsoluteBlockBox() {
		final AbsolutePos pos = new AbsolutePos();
		pos.autoPosition = net.zamasoft.foliojet.layout.box.params.AutoPosition.INLINE;
		pos.fiducial = net.zamasoft.foliojet.layout.box.params.Fiducial.ALL_PAGE;
		final BoxRecipe recipe = new BoxRecipe.Absolute(BlockParamsTemplate.freeze(blockParams()),
				AbsolutePosTemplate.freeze(pos));

		final INonReplacedBox box1 = BoxRecipeBoxFactory.create(recipe);
		final INonReplacedBox box2 = BoxRecipeBoxFactory.create(recipe);
		assertEquals(net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox.class, box1.getClass());
		assertNotSame(box1, box2);
		assertNotSame(box1.getParams(), box2.getParams());
		final AbsolutePos materialized = (AbsolutePos) box1.getPos();
		assertEquals(net.zamasoft.foliojet.layout.box.params.AutoPosition.INLINE, materialized.autoPosition);
		assertEquals(net.zamasoft.foliojet.layout.box.params.Fiducial.ALL_PAGE, materialized.fiducial);
	}

	/**
	 * {@link ReplacedRecipe}の4variant(2026-07-22新設、M6d-A)——
	 * {@link BoxRecipeBoxFactory#createReplaced}が対応する
	 * {@code AbstractReplacedBox}実装へ正しく再構築することを、
	 * 非デフォルト値の保持込みで固定する。
	 */
	public void testInlineReplacedRecipeCreatesInlineReplacedBox() {
		final ReplacedParams params = replacedParams();
		params.lineHeight = 2.5;
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 3.5;
		final ReplacedRecipe recipe = new ReplacedRecipe.Inline(ReplacedParamsTemplate.freeze(params),
				InlinePosTemplate.freeze(pos));

		final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
		assertTrue(box instanceof InlineReplacedBox);
		assertEquals(2.5, ((ReplacedParams) box.getParams()).lineHeight);
		assertEquals(3.5, ((InlinePos) box.getPos()).lineHeight);
	}

	public void testFlowReplacedRecipeCreatesFlowReplacedBox() {
		final FlowPos pos = new FlowPos();
		pos.align = Align.CENTER;
		final ReplacedRecipe recipe = new ReplacedRecipe.Flow(
				ReplacedParamsTemplate.freeze(replacedParams()), FlowPosTemplate.freeze(pos));

		final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
		assertTrue(box instanceof FlowReplacedBox);
		assertEquals(Align.CENTER, ((FlowPos) box.getPos()).align);
	}

	public void testFloatReplacedRecipeCreatesFloatReplacedBox() {
		final FloatPos pos = new FloatPos();
		pos.floating = FloatSide.END;
		final ReplacedRecipe recipe = new ReplacedRecipe.Float(
				ReplacedParamsTemplate.freeze(replacedParams()), FloatPosTemplate.freeze(pos));

		final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
		assertTrue(box instanceof FloatReplacedBox);
		assertEquals(FloatSide.END, ((FloatPos) box.getPos()).floating);
	}

	public void testAbsoluteReplacedRecipeCreatesAbsoluteReplacedBox() {
		final AbsolutePos pos = new AbsolutePos();
		pos.autoPosition = net.zamasoft.foliojet.layout.box.params.AutoPosition.INLINE;
		final ReplacedRecipe recipe = new ReplacedRecipe.Absolute(
				ReplacedParamsTemplate.freeze(replacedParams()), AbsolutePosTemplate.freeze(pos));

		final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
		assertTrue(box instanceof AbsoluteReplacedBox);
		assertEquals(net.zamasoft.foliojet.layout.box.params.AutoPosition.INLINE,
				((AbsolutePos) box.getPos()).autoPosition);
	}

	/**
	 * 2回createReplacedすれば、独立した別インスタンスになる
	 * (非Replaced版の{@link #testTableCellRecipeGetsFreshContainer}と
	 * 同じ契約——M6d-Aの最重要契約: frozen templateからの複数回
	 * materialize()は互いに独立していること)。
	 */
	public void testCreateReplacedProducesIndependentInstances() {
		final ReplacedRecipe recipe = new ReplacedRecipe.Flow(
				ReplacedParamsTemplate.freeze(replacedParams()), FlowPosTemplate.freeze(new FlowPos()));
		final AbstractReplacedBox box1 = BoxRecipeBoxFactory.createReplaced(recipe);
		final AbstractReplacedBox box2 = BoxRecipeBoxFactory.createReplaced(recipe);
		assertNotSame(box1, box2);
		assertNotSame(box1.getParams(), box2.getParams());
		assertNotSame(box1.getPos(), box2.getPos());
	}
}
