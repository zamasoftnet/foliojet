package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
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
import net.zamasoft.foliojet.layout.box.params.RowGroupType;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.text.breaking.LineBreakRules;
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
	private static final LineBreakRules DUMMY_LINE_BREAK_RULES = new LineBreakRules() {
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

	private static TableParams tableParams() {
		final TableParams params = new TableParams();
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

	/**
	 * BoxKind.TABLEはTableBoxへ再構築され、外側のTableBoxと内側の
	 * FlowBlockBoxが同一のmaterialize済みTableParamsインスタンスを
	 * 共有する(SourceReplayer.newBoxと同じ組み立て契約)。
	 */
	public void testTableRecipeSharesSingleMaterializedTableParams() {
		final TableParams params = tableParams();
		params.borderCollapse = TableParams.BORDER_COLLAPSE;
		final BoxRecipe recipe = new BoxRecipe.Table(TableParamsTemplate.freeze(params),
				FlowPosTemplate.freeze(new FlowPos()));

		final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
		assertTrue(box instanceof TableBox);
		final TableBox table = (TableBox) box;
		assertSame(table.getTableParams(), table.getBlockBox().getParams());
		assertEquals(TableParams.BORDER_COLLAPSE, table.getTableParams().borderCollapse);

		// 2回createすれば、共有はそれぞれ独立して保たれたまま別インスタンスになる
		final TableBox table2 = (TableBox) BoxRecipeBoxFactory.create(recipe);
		assertNotSame(table.getTableParams(), table2.getTableParams());
		assertSame(table2.getTableParams(), table2.getBlockBox().getParams());
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
}
