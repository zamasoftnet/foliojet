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
 * package-private——{@link BlockParamsTemplate}と
 * {@link TableParamsTemplate}(`TableParams`は`BlockParams`を直接
 * 継承)が共有する。祖先({@code Params}/{@code AbstractTextParams}/
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
final class BlockParamsFields {
	private final LineParamsFields common;
	private final RectFrame frame;
	private final FirstLineParamsTemplate firstLineStyle;
	private final PageBreakMode pageBreakInside;
	private final byte orphans;
	private final byte widows;
	private final Dimension size;
	private final Dimension minSize;
	private final Dimension maxSize;
	private final BoxSizingMode boxSizing;
	private final OverflowMode overflow;
	private final Columns columns;

	private BlockParamsFields(final LineParamsFields common, final RectFrame frame,
			final FirstLineParamsTemplate firstLineStyle, final PageBreakMode pageBreakInside, final byte orphans,
			final byte widows, final Dimension size, final Dimension minSize, final Dimension maxSize,
			final BoxSizingMode boxSizing, final OverflowMode overflow, final Columns columns) {
		this.common = common;
		this.frame = frame;
		this.firstLineStyle = firstLineStyle;
		this.pageBreakInside = pageBreakInside;
		this.orphans = orphans;
		this.widows = widows;
		this.size = size;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.boxSizing = boxSizing;
		this.overflow = overflow;
		this.columns = columns;
	}

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
