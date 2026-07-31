package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * Grid構築ライフサイクルの入口です(Grid G1b——TableBuilderLifecycleと
 * 同じ薄い形。実行計画の選択ではなく、G1適格判定と開始/終了の対称性
 * だけを持つ)。
 */
public final class GridBuilderLifecycle {
	private GridBuilderLifecycle() {
		// 静的ユーティリティ
	}

	/**
	 * トラック配置を適用できるGridかを判定します
	 * (consult-codex-2026-07-31-grid-g1.txt §1.1)。不適格(fr列=G3c・
	 * 明示行トラック・縦書き・TwoPass親)はG0の単一列フローへ落とす。
	 * auto列はG3b(consult-codex-2026-07-31-grid-g3.txt Q2)で適格化。
	 */
	public static boolean eligible(final GridBox gridBox, final Builder builder) {
		final GridParams params = gridBox.getGridParams();
		if (params.templateColumns.isEmpty() || !params.templateRows.isEmpty()) {
			return false;
		}
		for (final GridTrackListValue.TrackSize track : params.templateColumns) {
			if (!(track instanceof GridTrackListValue.Fixed || track instanceof GridTrackListValue.Auto)) {
				return false;
			}
		}
		if (params.flow.isVertical()) {
			return false;
		}
		return builder instanceof BlockBuilder && !builder.isTwoPass();
	}

	/** GridBuilderを開始します(適格判定済みであること)。 */
	public static GridBuilder start(final Builder builder, final GridBox gridBox) {
		return new GridBuilder((BlockBuilder) builder, gridBox);
	}
}
