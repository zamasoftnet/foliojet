package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.OverflowMode;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectFrame;

/**
 * {@link BlockParams}(M6d-Aの{@link BoxKind#FLOW}等が使う代表的な
 * {@code Params}実装)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code BlockParams}をmaterializeするテンプレートです(2026-07-22
 * 新設、M6d-A3b Stage1——`SegmentEvent`/`BoxRecipe`(A3a)への実際の
 * 配線はまだ行わない、`Params`のfreeze/materialize機構そのものの
 * 実装・検証が今回の対象)。
 *
 * <p>
 * {@code firstLineStyle}(nullable、{@code FirstLineParams})は
 * {@link FirstLineParamsTemplate}で再帰的にfreeze/materializeする。
 * その他のフィールド({@code frame}・{@code size}系・{@code columns}等)
 * は既存実装がfinalフィールドのみで実質不変と確認済みのため、参照を
 * そのまま保持する(コピー不要)。祖先({@code Params}/
 * {@code AbstractTextParams}/{@code AbstractLineParams})のフィールドは
 * {@link LineParamsFields}が担う。
 * </p>
 */
public final class BlockParamsTemplate {
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

	private BlockParamsTemplate(final LineParamsFields common, final RectFrame frame,
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

	public static BlockParamsTemplate freeze(final BlockParams source) {
		final FirstLineParamsTemplate firstLineStyle = source.firstLineStyle == null ? null
				: FirstLineParamsTemplate.freeze(source.firstLineStyle);
		return new BlockParamsTemplate(LineParamsFields.freeze(source), source.frame, firstLineStyle,
				source.pageBreakInside, source.orphans, source.widows, source.size, source.minSize, source.maxSize,
				source.boxSizing, source.overflow, source.columns);
	}

	/** 呼び出しごとに新品の{@code BlockParams}を返す(複数回呼んでも互いに影響しない)。 */
	public BlockParams materialize() {
		final BlockParams p = new BlockParams();
		this.common.materializeInto(p);
		p.frame = this.frame;
		p.firstLineStyle = this.firstLineStyle == null ? null : this.firstLineStyle.materialize();
		p.pageBreakInside = this.pageBreakInside;
		p.orphans = this.orphans;
		p.widows = this.widows;
		p.size = this.size;
		p.minSize = this.minSize;
		p.maxSize = this.maxSize;
		p.boxSizing = this.boxSizing;
		p.overflow = this.overflow;
		p.columns = this.columns;
		return p;
	}
}
