package net.zamasoft.foliojet.layout.segment;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * {@link GridParams}({@code BlockParams}を直接継承、{@code BoxKind#GRID}が
 * 使う)の内容をfreezeし、呼び出しごとに独立した新品の{@code GridParams}を
 * materializeするテンプレートです(Grid G0c、2026-07-31——
 * consult-codex-2026-07-31-grid.txt §3.7)。
 *
 * <p>
 * トラック({@link GridTrackListValue.TrackSize})は不変recordの
 * 不変リスト、gapはプリミティブのためそのまま保持できる
 * ({@code TableParamsTemplate}と同型)。
 * </p>
 */
public record GridParamsTemplate(BlockParamsFields common, List<GridTrackListValue.TrackSize> templateColumns,
		List<GridTrackListValue.TrackSize> templateRows, double rowGap, double columnGap) {
	public static GridParamsTemplate freeze(final GridParams source) {
		return new GridParamsTemplate(BlockParamsFields.freeze(source), source.templateColumns,
				source.templateRows, source.rowGap, source.columnGap);
	}

	/** 凍結済みの書字方向を返します({@code containsMixedFlow}用)。 */
	public WritingMode flow() {
		return this.common.common().text().flow();
	}

	/** 呼び出しごとに新品の{@code GridParams}を返す。 */
	public GridParams materialize() {
		final GridParams p = new GridParams();
		this.common.materializeInto(p);
		p.templateColumns = this.templateColumns;
		p.templateRows = this.templateRows;
		p.rowGap = this.rowGap;
		p.columnGap = this.columnGap;
		return p;
	}
}
