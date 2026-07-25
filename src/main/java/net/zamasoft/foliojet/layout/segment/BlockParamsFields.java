package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.OverflowMode;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectFrame;

/**
 * {@code BlockParams}が{@code AbstractLineParams}に加えて持つ
 * フィールド({@code frame}・{@code firstLineStyle}・
 * {@code pageBreakInside}・{@code orphans}/{@code widows}・
 * {@code size}系・{@code boxSizing}・{@code overflow}・{@code columns})
 * のfreeze/materialize処理です(2026-07-22新設、M6d-A3b、
 * package-private——{@link BlockParamsTemplate}が使う。
 * 祖先({@code Params}/{@code AbstractTextParams}/
 * {@code AbstractLineParams})のフィールドは{@link LineParamsFields}へ
 * 委譲する(合成、{@code TextParamsFields}/{@code LineParamsFields}と
 * 同じパターン)。
 * </p>
 *
 * <p>
 * {@code firstLineStyle}(nullable)は{@link FirstLineParamsTemplate}で
 * 再帰的にfreeze/materializeする。その他のフィールドは既存実装が
 * finalフィールドのみで実質不変と確認済みのため、参照をそのまま
 * 保持する(コピー不要)。
 * </p>
 */
record BlockParamsFields(LineParamsFields common, RectFrame frame, FirstLineParamsTemplate firstLineStyle,
		PageBreakMode pageBreakInside, byte orphans, byte widows, Dimension size, Dimension minSize,
		Dimension maxSize, BoxSizingMode boxSizing, OverflowMode overflow, Columns columns) {
	static BlockParamsFields freeze(final BlockParams source) {
		final FirstLineParamsTemplate firstLineStyle = source.firstLineStyle == null ? null
				: FirstLineParamsTemplate.freeze(source.firstLineStyle);
		return new BlockParamsFields(LineParamsFields.freeze(source), source.frame, firstLineStyle,
				source.pageBreakInside, source.orphans, source.widows, source.size, source.minSize, source.maxSize,
				source.boxSizing, source.overflow, source.columns);
	}

	/**
	 * {@code target}へ全フィールドを書き戻す。{@code target}は
	 * {@code BlockParams}のサブクラス({@code TableParams}等)でもよい
	 * ——呼び出しごとに新品の{@code firstLineStyle}を割り当てるため、
	 * 複数回materializeしても互いに影響しない。
	 */
	void materializeInto(final BlockParams target) {
		this.common.materializeInto(target);
		target.frame = this.frame;
		target.firstLineStyle = this.firstLineStyle == null ? null : this.firstLineStyle.materialize();
		target.pageBreakInside = this.pageBreakInside;
		target.orphans = this.orphans;
		target.widows = this.widows;
		target.size = this.size;
		target.minSize = this.minSize;
		target.maxSize = this.maxSize;
		target.boxSizing = this.boxSizing;
		target.overflow = this.overflow;
		target.columns = this.columns;
	}
}
