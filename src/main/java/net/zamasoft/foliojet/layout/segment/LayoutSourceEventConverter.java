package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FloatReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * {@link LayoutSource.Event}を{@link SegmentEvent}へ変換するアダプタ
 * です(2026-07-22新設、M6d-A3c)。
 *
 * <p>
 * イベント数は1:1を維持する(M6d-A2のordinal対応・将来のshadow比較を
 * 壊さないため、codex設計相談で確認)。{@link BoxKind#FLOW}/
 * {@link BoxKind#INLINE}はM6d-A3bのテンプレートで完全に(内容の
 * 欠落なく)変換する。{@code Replaced}は{@link ReplacedRecipe}
 * (2026-07-22、M6d-A Replaced要素対応)へ変換する——ただし
 * {@link ReplacedParamsTemplate#freeze}が不安全な{@code image}
 * ({@code ReplacedBoxImage}実装)を検出した場合、または未知の
 * {@code AbstractReplacedBox}サブクラスの場合はfail closedで
 * {@link SegmentEvent.Barrier}へ落とす。それ以外の{@code BoxKind}、
 * および{@code Opaque}は常に{@link SegmentEvent.Barrier}へ変換する
 * ——silent fallbackを避けるため、理由(常に
 * {@link BarrierReason#NOT_YET_SUPPORTED}、未知の型は
 * {@link BarrierReason#UNKNOWN_TYPE})と、判明していれば元の種別を
 * 明示する。
 * </p>
 *
 * <p>
 * {@code LayoutSource}本体・{@code SourceReplayer}には一切関与しない
 * ——読み取り専用の変換のみを行う。
 * </p>
 */
public final class LayoutSourceEventConverter {
	private LayoutSourceEventConverter() {
	}

	/**
	 * {@code slice}の全イベントを、対応する{@link SegmentEvent}へ1:1変換する。
	 * E-6増分3a(2026-07-24): {@code slice}はstreamingビュー(consume-once)
	 * のため、この変換が{@code slice}を消費する——呼び出し側は同じsliceを
	 * 再度読めない(凍結結果のListだけを使うこと)。
	 */
	public static List<SegmentEvent> convert(final LayoutSource.ReplaySlice slice) {
		// イベント数 == 範囲長(EventIdは連番、captureが検証済み)
		final List<SegmentEvent> result = new ArrayList<>((int) (slice.toId() - slice.fromId() + 1));
		slice.replay(event -> result.add(convert(event)));
		return result;
	}

	/** 単一の{@link LayoutSource.Event}を対応する{@link SegmentEvent}へ変換する。 */
	public static SegmentEvent convert(final LayoutSource.Event event) {
		return switch (event) {
		case LayoutSource.Start start -> convertStart(start);
		case LayoutSource.EndBlock endBlock -> new SegmentEvent.EndBox();
		case LayoutSource.Chars(final int charOffset, final LayoutSource.TextPayload payload, final boolean fixed) ->
			// freshChars()はInline=clone、Spilled=storeからのdecode(E-6増分3b-2)
			new SegmentEvent.Text(charOffset, new String(payload.freshChars()), fixed);
		case LayoutSource.Replaced(final AbstractReplacedBox box) -> convertReplaced(box);
		// Opaqueはそもそも種別情報を保持しない(フィールドなしの位置占有
		// マーカー)ため常にBarrier化する
		case LayoutSource.Opaque opaque ->
			new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED);
		};
	}

	/**
	 * live{@code AbstractReplacedBox}から{@link ReplacedRecipe}を
	 * 組み立てる。{@code ReplacedParamsTemplate.freeze()}が不安全な
	 * {@code image}を検出した場合、または未知のサブクラスの場合は
	 * fail closedで{@link SegmentEvent.Barrier}を返す。
	 */
	private static SegmentEvent convertReplaced(final AbstractReplacedBox box) {
		final Optional<ReplacedParamsTemplate> params = ReplacedParamsTemplate.freeze(box.getReplacedParams());
		if (params.isEmpty()) {
			return new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED);
		}
		// 4実装(InlineReplacedBox/FlowReplacedBox/FloatReplacedBox/
		// AbsoluteReplacedBox)がそれぞれInlinePos/FlowPos/FloatPos/
		// AbsolutePosを使う(既存コード確認済み、ReplacedRecipeのjavadoc参照)
		if (box instanceof InlineReplacedBox inline) {
			return new SegmentEvent.Replaced(
					new ReplacedRecipe.Inline(params.get(), InlinePosTemplate.freeze(inline.getInlinePos())));
		}
		if (box instanceof FlowReplacedBox flow) {
			return new SegmentEvent.Replaced(
					new ReplacedRecipe.Flow(params.get(), FlowPosTemplate.freeze(flow.getFlowPos())));
		}
		if (box instanceof FloatReplacedBox floatBox) {
			return new SegmentEvent.Replaced(
					new ReplacedRecipe.Float(params.get(), FloatPosTemplate.freeze(floatBox.getFloatPos())));
		}
		if (box instanceof AbsoluteReplacedBox absolute) {
			return new SegmentEvent.Replaced(
					new ReplacedRecipe.Absolute(params.get(), AbsolutePosTemplate.freeze(absolute.getAbsolutePos())));
		}
		return new SegmentEvent.Barrier(Optional.empty(), BarrierReason.UNKNOWN_TYPE);
	}

	private static SegmentEvent convertStart(final LayoutSource.Start start) {
		return switch (start.kind()) {
		case FLOW -> new SegmentEvent.BeginBox(new BoxRecipe.Flow(
				BlockParamsTemplate.freeze((BlockParams) start.params()),
				FlowPosTemplate.freeze((FlowPos) start.pos())));
		// MulticolumnBlockBoxはFlowBlockBoxを継承するためBoxKind.FLOWと
		// 同じBlockParams/FlowPosを使う(既存コード確認済み)
		case MULTICOL -> new SegmentEvent.BeginBox(new BoxRecipe.Multicol(
				BlockParamsTemplate.freeze((BlockParams) start.params()),
				FlowPosTemplate.freeze((FlowPos) start.pos())));
		case INLINE -> new SegmentEvent.BeginBox(new BoxRecipe.Inline(
				InlineParamsTemplate.freeze((InlineParams) start.params()),
				InlinePosTemplate.freeze((InlinePos) start.pos())));
		// OutsideMarkerBoxはBlockParams/InlinePosを使う(既存コード確認済み)
		case MARKER -> new SegmentEvent.BeginBox(new BoxRecipe.Marker(
				BlockParamsTemplate.freeze((BlockParams) start.params()),
				InlinePosTemplate.freeze((InlinePos) start.pos())));
		// FloatBlockBoxはBlockParams/FloatPosを使う(既存コード確認済み)
		case FLOAT_BLOCK -> new SegmentEvent.BeginBox(new BoxRecipe.FloatBlock(
				BlockParamsTemplate.freeze((BlockParams) start.params()),
				FloatPosTemplate.freeze((FloatPos) start.pos())));
		// InlineBlockBoxはBlockParams/InlinePosを使う(既存コード確認済み)
		case INLINE_BLOCK -> new SegmentEvent.BeginBox(new BoxRecipe.InlineBlock(
				BlockParamsTemplate.freeze((BlockParams) start.params()),
				InlinePosTemplate.freeze((InlinePos) start.pos())));
		// InsideMarkerBoxはBlockParams/InlinePosを使う(既存コード確認済み)
		case INSIDE_MARKER -> new SegmentEvent.BeginBox(new BoxRecipe.InsideMarker(
				BlockParamsTemplate.freeze((BlockParams) start.params()),
				InlinePosTemplate.freeze((InlinePos) start.pos())));
		// TableBoxはTableParams/FlowPosを使う(StyleBuilderがbox.getPos()
		// instanceof FlowPosの場合のみBoxKind.TABLEを記録するため、
		// このcastは常に成立する、既存コード確認済み)
		case TABLE -> new SegmentEvent.BeginBox(new BoxRecipe.Table(
				TableParamsTemplate.freeze((TableParams) start.params()),
				FlowPosTemplate.freeze((FlowPos) start.pos())));
		// TableRowGroupBoxはInnerTableParams/TableRowGroupPosを使う(既存コード確認済み)
		case TABLE_ROW_GROUP -> new SegmentEvent.BeginBox(new BoxRecipe.TableRowGroup(
				InnerTableParamsTemplate.freeze((InnerTableParams) start.params()),
				TableRowGroupPosTemplate.freeze((TableRowGroupPos) start.pos())));
		// TableRowBoxはInnerTableParams/TableRowPosを使う(既存コード確認済み)
		case TABLE_ROW -> new SegmentEvent.BeginBox(new BoxRecipe.TableRow(
				InnerTableParamsTemplate.freeze((InnerTableParams) start.params()),
				TableRowPosTemplate.freeze((TableRowPos) start.pos())));
		// TableCellBoxはBlockParams/TableCellPosを使う(既存コード確認済み)
		case TABLE_CELL -> new SegmentEvent.BeginBox(new BoxRecipe.TableCell(
				BlockParamsTemplate.freeze((BlockParams) start.params()),
				TableCellPosTemplate.freeze((TableCellPos) start.pos())));
		// TableColumnGroupBoxはInnerTableParams/TableColumnPosを使う(既存コード確認済み)
		case TABLE_COLUMN_GROUP -> new SegmentEvent.BeginBox(new BoxRecipe.TableColumnGroup(
				InnerTableParamsTemplate.freeze((InnerTableParams) start.params()),
				TableColumnPosTemplate.freeze((TableColumnPos) start.pos())));
		// TableColumnBoxはTableColumnGroupBoxと同じInnerTableParams/
		// TableColumnPosを使う(既存コード確認済み)
		case TABLE_COLUMN -> new SegmentEvent.BeginBox(new BoxRecipe.TableColumn(
				InnerTableParamsTemplate.freeze((InnerTableParams) start.params()),
				TableColumnPosTemplate.freeze((TableColumnPos) start.pos())));
		default -> new SegmentEvent.Barrier(Optional.of(mapKind(start.kind())), BarrierReason.NOT_YET_SUPPORTED);
		};
	}

	private static BoxKind mapKind(final LayoutSource.BoxKind kind) {
		return switch (kind) {
		case FLOW -> BoxKind.FLOW;
		case MULTICOL -> BoxKind.MULTICOL;
		case INLINE -> BoxKind.INLINE;
		case MARKER -> BoxKind.MARKER;
		case FLOAT_BLOCK -> BoxKind.FLOAT_BLOCK;
		case INLINE_BLOCK -> BoxKind.INLINE_BLOCK;
		case INSIDE_MARKER -> BoxKind.INSIDE_MARKER;
		case TABLE -> BoxKind.TABLE;
		case TABLE_ROW_GROUP -> BoxKind.TABLE_ROW_GROUP;
		case TABLE_ROW -> BoxKind.TABLE_ROW;
		case TABLE_CELL -> BoxKind.TABLE_CELL;
		case TABLE_COLUMN_GROUP -> BoxKind.TABLE_COLUMN_GROUP;
		case TABLE_COLUMN -> BoxKind.TABLE_COLUMN;
		};
	}
}
