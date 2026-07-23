package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractNormalFlowPos;
import net.zamasoft.foliojet.layout.box.params.ClearMode;

/**
 * {@code AbstractNormalFlowPos}が{@code AbstractBlockLevelPos}に加えて
 * 持つフィールド({@code clear})のfreeze/materialize処理です
 * (2026-07-22新設、M6d-A3b、package-private——{@link FlowPosTemplate}
 * と{@link FloatPosTemplate}が共有する)。祖先
 * ({@code AbstractStaticPos}/{@code AbstractBlockLevelPos})の
 * フィールドは{@link BlockLevelPosFields}へ委譲する(合成)。
 *
 * <p>
 * 全フィールドが既存実装(値クラス・enum)で実質不変と確認済みのため、
 * 防御的コピーは不要——単純に値をそのまま保持・書き戻すだけで済む。
 * </p>
 */
record NormalFlowPosFields(BlockLevelPosFields common, ClearMode clear) {
	static NormalFlowPosFields freeze(final AbstractNormalFlowPos source) {
		return new NormalFlowPosFields(BlockLevelPosFields.freeze(source), source.clear);
	}

	void materializeInto(final AbstractNormalFlowPos target) {
		this.common.materializeInto(target);
		target.clear = this.clear;
	}
}
