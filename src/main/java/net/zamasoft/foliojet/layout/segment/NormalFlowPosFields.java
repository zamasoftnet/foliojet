package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractNormalFlowPos;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

/**
 * {@code AbstractStaticPos}/{@code AbstractBlockLevelPos}/
 * {@code AbstractNormalFlowPos}が共通して持つフィールド
 * ({@code offset}・{@code pageBreakBefore}/{@code pageBreakAfter}・
 * {@code clear})のfreeze/materialize処理です(2026-07-22新設、
 * M6d-A3b、package-private——{@link FlowPosTemplate}と
 * {@link FloatPosTemplate}が共有する。{@code FlowPos}/{@code FloatPos}
 * はどちらも{@code AbstractNormalFlowPos}を継承するため、
 * {@link TextParamsFields}/{@link LineParamsFields}と同じ合成パターン
 * を踏襲する)。
 *
 * <p>
 * 全フィールドが既存実装(値クラス・enum)で実質不変と確認済みのため、
 * 防御的コピーは不要——単純に値をそのまま保持・書き戻すだけで済む。
 * </p>
 */
final class NormalFlowPosFields {
	final Offset offset;
	final PageBreakMode pageBreakBefore;
	final PageBreakMode pageBreakAfter;
	final ClearMode clear;

	private NormalFlowPosFields(final Offset offset, final PageBreakMode pageBreakBefore,
			final PageBreakMode pageBreakAfter, final ClearMode clear) {
		this.offset = offset;
		this.pageBreakBefore = pageBreakBefore;
		this.pageBreakAfter = pageBreakAfter;
		this.clear = clear;
	}

	static NormalFlowPosFields freeze(final AbstractNormalFlowPos source) {
		return new NormalFlowPosFields(source.offset, source.pageBreakBefore, source.pageBreakAfter, source.clear);
	}

	void materializeInto(final AbstractNormalFlowPos target) {
		target.offset = this.offset;
		target.pageBreakBefore = this.pageBreakBefore;
		target.pageBreakAfter = this.pageBreakAfter;
		target.clear = this.clear;
	}
}
