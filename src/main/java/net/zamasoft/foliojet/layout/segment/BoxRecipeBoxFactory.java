package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
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
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * {@link BoxRecipe}から実際の{@code IBox}を再構築するファクトリです
 * (2026-07-22新設、M6d-A3d)。
 *
 * <p>
 * E-6増分3b-1(2026-07-24): 旧{@code SourceReplayer.newBox}
 * ({@code LayoutSource.Start}から直接読む対の実装)をkind別の
 * 構築カーネル({@link #create(LayoutSource.BoxKind, Params, Pos)})
 * としてここへ一元化した。TableBoxの「外箱と内側FlowBlockBoxが
 * TableParamsを共有する」alias構造もカーネルの一箇所だけが持つ。
 * recipe版({@link #create(BoxRecipe)})はテンプレートを
 * materializeしてからカーネルへ委譲する——旧{@code LayoutSource}
 * オブジェクトには一切触れない。
 * </p>
 */
public final class BoxRecipeBoxFactory {
	private BoxRecipeBoxFactory() {
	}

	/** {@code recipe}のテンプレートをmaterializeし、対応する新品の{@code IBox}を返す。 */
	public static INonReplacedBox create(final BoxRecipe recipe) {
		return switch (recipe) {
		case BoxRecipe.Flow r -> create(LayoutSource.BoxKind.FLOW, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Multicol r ->
			create(LayoutSource.BoxKind.MULTICOL, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Inline r -> create(LayoutSource.BoxKind.INLINE, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Marker r -> create(LayoutSource.BoxKind.MARKER, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.FloatBlock r ->
			create(LayoutSource.BoxKind.FLOAT_BLOCK, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.InlineBlock r ->
			create(LayoutSource.BoxKind.INLINE_BLOCK, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.InsideMarker r ->
			create(LayoutSource.BoxKind.INSIDE_MARKER, r.params().materialize(), r.pos().materialize());
		// Tableのparams共有(alias)はカーネル側で行うため、ここは
		// materializeを1回ずつ呼ぶだけでよい
		case BoxRecipe.Table r -> create(LayoutSource.BoxKind.TABLE, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableRowGroup r ->
			create(LayoutSource.BoxKind.TABLE_ROW_GROUP, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableRow r ->
			create(LayoutSource.BoxKind.TABLE_ROW, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableCell r ->
			create(LayoutSource.BoxKind.TABLE_CELL, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableColumnGroup r ->
			create(LayoutSource.BoxKind.TABLE_COLUMN_GROUP, r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableColumn r ->
			create(LayoutSource.BoxKind.TABLE_COLUMN, r.params().materialize(), r.pos().materialize());
		};
	}

	/**
	 * kindとparams/posから同型の新品ボックスを作る構築カーネルです
	 * (E-6増分3b-1で旧{@code SourceReplayer.newBox}を移設——
	 * {@code StyleBuilder.boxKind}と対のファクトリ)。E-6増分3b-4で
	 * {@code LayoutSource.Start}も記録時freezeのrecipe保持になったため、
	 * live params/posを直接渡す呼び出し元は残っていない——recipe駆動
	 * ({@link #create(BoxRecipe)})のmaterialize結果がここを通り、
	 * kindごとの構築ロジック(Tableのparams共有aliasを含む)は
	 * この一箇所だけが持つ。
	 */
	public static INonReplacedBox create(final LayoutSource.BoxKind kind, final Params params, final Pos pos) {
		return switch (kind) {
		case FLOW -> new FlowBlockBox((BlockParams) params, (FlowPos) pos);
		case MULTICOL -> new MulticolumnBlockBox((BlockParams) params, (FlowPos) pos);
		case INLINE -> new InlineBox((InlineParams) params, (InlinePos) pos);
		case MARKER -> new OutsideMarkerBox((BlockParams) params, (InlinePos) pos);
		case FLOAT_BLOCK -> new FloatBlockBox((BlockParams) params, (FloatPos) pos);
		case INLINE_BLOCK -> new InlineBlockBox((BlockParams) params, (InlinePos) pos);
		case INSIDE_MARKER -> new InsideMarkerBox((BlockParams) params, (InlinePos) pos);
		case TABLE -> {
			// 外側のTableBoxと内側のFlowBlockBoxはTableParamsを共有する
			// (alias構造の一元点——live/recipeの両駆動で同じ)
			final TableParams tableParams = (TableParams) params;
			yield new TableBox(tableParams, new FlowBlockBox(tableParams, (FlowPos) pos));
		}
		case TABLE_ROW_GROUP -> new TableRowGroupBox((InnerTableParams) params, (TableRowGroupPos) pos);
		case TABLE_ROW -> new TableRowBox((InnerTableParams) params, (TableRowPos) pos);
		case TABLE_CELL -> new TableCellBox((BlockParams) params, (TableCellPos) pos, new FlowContainer());
		case TABLE_COLUMN_GROUP -> new TableColumnGroupBox((InnerTableParams) params, (TableColumnPos) pos);
		case TABLE_COLUMN -> new TableColumnBox((InnerTableParams) params, (TableColumnPos) pos);
		};
	}

	/**
	 * {@link ReplacedRecipe}のテンプレートをmaterializeし、対応する
	 * 新品の{@link AbstractReplacedBox}を返します(2026-07-22新設、
	 * M6d-A——{@link #create}と対だが戻り値型が{@code INonReplacedBox}
	 * ではなく{@code AbstractReplacedBox}のため別メソッドとした
	 * (`docs/history/2026-07-22-m6d-a-replaced-element-support.md`
	 * 「未着手のまま残るもの」参照)。
	 */
	public static AbstractReplacedBox createReplaced(final ReplacedRecipe recipe) {
		return switch (recipe) {
		case ReplacedRecipe.Inline r -> new InlineReplacedBox(r.params().materialize(), r.pos().materialize());
		case ReplacedRecipe.Flow r -> new FlowReplacedBox(r.params().materialize(), r.pos().materialize());
		case ReplacedRecipe.Float r -> new FloatReplacedBox(r.params().materialize(), r.pos().materialize());
		case ReplacedRecipe.Absolute r -> new AbsoluteReplacedBox(r.params().materialize(), r.pos().materialize());
		};
	}
}
