package net.zamasoft.foliojet.layout.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * 欠落なく)変換する。それ以外の{@code BoxKind}、および
 * {@code Replaced}/{@code Opaque}(まだ{@code ReplacedRecipe}の
 * 中身が未設計)は{@link SegmentEvent.Barrier}へ変換する——silent
 * fallbackを避けるため、理由(常に{@link BarrierReason
 * #NOT_YET_SUPPORTED})と、判明していれば元の種別を明示する。
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

	/** {@code slice}の全イベントを、対応する{@link SegmentEvent}へ1:1変換する。 */
	public static List<SegmentEvent> convert(final LayoutSource.ReplaySlice slice) {
		final List<LayoutSource.Event> events = slice.events();
		final List<SegmentEvent> result = new ArrayList<>(events.size());
		for (final LayoutSource.Event event : events) {
			result.add(convert(event));
		}
		return result;
	}

	/** 単一の{@link LayoutSource.Event}を対応する{@link SegmentEvent}へ変換する。 */
	public static SegmentEvent convert(final LayoutSource.Event event) {
		return switch (event) {
		case LayoutSource.Start start -> convertStart(start);
		case LayoutSource.EndBlock endBlock -> new SegmentEvent.EndBox();
		case LayoutSource.Chars(final int charOffset, final char[] ch, final boolean fixed) ->
			new SegmentEvent.Text(charOffset, new String(ch), fixed);
		// Replaced/Opaqueはそもそも種別情報を保持しない(旧Opaqueは
		// フィールドなしの位置占有マーカー、旧Replacedはlive boxのみ
		// 保持——ReplacedRecipeの中身はまだ未設計のためlive boxを見に
		// 行かず、そのままBarrier化する)
		case LayoutSource.Replaced replaced ->
			new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED);
		case LayoutSource.Opaque opaque ->
			new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED);
		};
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
