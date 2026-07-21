package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

/**
 * {@link FlowPos}(通常フローの配置パラメータ、{@link BoxKind#FLOW}が
 * 使う)の内容をfreezeし、呼び出しごとに独立した新品の{@code FlowPos}を
 * materializeするテンプレートです(2026-07-22新設、M6d-A3b Stage1)。
 *
 * <p>
 * {@code FlowPos}とその祖先({@code AbstractNormalFlowPos}/
 * {@code AbstractBlockLevelPos}/{@code AbstractStaticPos})の全フィールド
 * ({@code offset}・{@code pageBreakBefore}/{@code pageBreakAfter}・
 * {@code clear}・{@code align}・{@code columnSpan})は、既存実装が
 * finalフィールドのみ(値クラス)または列挙型で実質不変と確認済みの
 * ため、{@link BlockParamsTemplate}のような防御的コピーは不要——単純に
 * 値をそのまま保持・書き戻すだけで済む({@code Params}系の
 * {@code AffineTransform}/配列のような真にmutableなフィールドが
 * {@code Pos}階層には存在しない)。
 * </p>
 */
public final class FlowPosTemplate {
	private final Offset offset;
	private final PageBreakMode pageBreakBefore;
	private final PageBreakMode pageBreakAfter;
	private final ClearMode clear;
	private final Align align;
	private final byte columnSpan;

	private FlowPosTemplate(final Offset offset, final PageBreakMode pageBreakBefore,
			final PageBreakMode pageBreakAfter, final ClearMode clear, final Align align, final byte columnSpan) {
		this.offset = offset;
		this.pageBreakBefore = pageBreakBefore;
		this.pageBreakAfter = pageBreakAfter;
		this.clear = clear;
		this.align = align;
		this.columnSpan = columnSpan;
	}

	public static FlowPosTemplate freeze(final FlowPos source) {
		return new FlowPosTemplate(source.offset, source.pageBreakBefore, source.pageBreakAfter, source.clear,
				source.align, source.columnSpan);
	}

	/** 呼び出しごとに新品の{@code FlowPos}を返す(複数回呼んでも互いに影響しない)。 */
	public FlowPos materialize() {
		final FlowPos pos = new FlowPos();
		pos.offset = this.offset;
		pos.pageBreakBefore = this.pageBreakBefore;
		pos.pageBreakAfter = this.pageBreakAfter;
		pos.clear = this.clear;
		pos.align = this.align;
		pos.columnSpan = this.columnSpan;
		return pos;
	}
}
