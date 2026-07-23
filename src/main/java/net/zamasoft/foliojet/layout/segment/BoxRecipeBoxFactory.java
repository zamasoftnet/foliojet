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
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;

/**
 * {@link BoxRecipe}から実際の{@code IBox}を再構築するファクトリです
 * (2026-07-22新設、M6d-A3d)。{@code SourceReplayer.newBox}
 * (`LayoutSource.Start`から直接読む旧実装)と対だが、こちらは
 * {@code BoxRecipe}のテンプレート(`materialize()`)だけを読む——
 * 旧{@code LayoutSource}オブジェクトには一切触れない。
 *
 * <p>
 * これが{@code BoxRecipe}の最初の意味のある使い道である
 * (それまでのテスト群は合成`Params`/`Pos`でのfreeze/materialize
 * 契約のみを検証していた)。まだ{@code SegmentEvent}列全体を駆動する
 * executorではない——{@code Replaced}/{@code Opaque}相当は
 * まだ{@link BarrierReason#NOT_YET_SUPPORTED}のままであり、
 * 完全なexecutorを名乗るには時期尚早(codex設計相談で確認)。
 * </p>
 */
public final class BoxRecipeBoxFactory {
	private BoxRecipeBoxFactory() {
	}

	/** {@code recipe}のテンプレートをmaterializeし、対応する新品の{@code IBox}を返す。 */
	public static INonReplacedBox create(final BoxRecipe recipe) {
		return switch (recipe) {
		case BoxRecipe.Flow r -> new FlowBlockBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Multicol r -> new MulticolumnBlockBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Inline r -> new InlineBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Marker r -> new OutsideMarkerBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.FloatBlock r -> new FloatBlockBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.InlineBlock r -> new InlineBlockBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.InsideMarker r -> new InsideMarkerBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.Table r -> {
			// SourceReplayer.newBoxと同じく、外側のTableBoxと内側の
			// FlowBlockBoxはTableParamsを共有する(1回だけmaterializeする)
			final TableParams params = r.params().materialize();
			final FlowPos pos = r.pos().materialize();
			yield new TableBox(params, new FlowBlockBox(params, pos));
		}
		case BoxRecipe.TableRowGroup r -> new TableRowGroupBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableRow r -> new TableRowBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableCell r ->
			new TableCellBox(r.params().materialize(), r.pos().materialize(), new FlowContainer());
		case BoxRecipe.TableColumnGroup r -> new TableColumnGroupBox(r.params().materialize(), r.pos().materialize());
		case BoxRecipe.TableColumn r -> new TableColumnBox(r.params().materialize(), r.pos().materialize());
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
