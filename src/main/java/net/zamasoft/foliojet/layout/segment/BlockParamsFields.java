package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.OverflowMode;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectFrame;

/**
 * Freeze/materialize processing for the fields {@code BlockParams} adds to {@code AbstractLineParams}
 * ({@code frame}, {@code firstLineStyle} , {@code pageBreakInside} , {@code orphans} /{@code widows},
 * the {@code size} family, {@code boxSizing} , {@code overflow} , and {@code columns} ).
 * Introduced 2026-07-22, M6d-A3b; package-private and used by {@link BlockParamsTemplate} .
 * Delegates ancestor ({@code Params}/{@code AbstractTextParams}/{@code AbstractLineParams}) fields to
 * {@link LineParamsFields} (composition, the same pattern as
 * {@code TextParamsFields} /{@code LineParamsFields}).
 * </p>
 *
 * <p>
 * Recursively freezes/materializes nullable {@code firstLineStyle} through {@link FirstLineParamsTemplate} .
 * Existing implementations of other fields have been confirmed effectively immutable with only final fields,
 * so their references are retained unchanged (no copy needed).
 * </p>
 */
record BlockParamsFields(LineParamsFields common, RectFrame frame, FirstLineParamsTemplate firstLineStyle,
		PageBreakMode pageBreakInside, byte orphans, byte widows, Dimension size, Dimension minSize,
		Dimension maxSize, BoxSizingMode boxSizing, OverflowMode overflow,
		net.zamasoft.foliojet.layout.box.params.BoxAlignment blockAlignContent, boolean paintClip, Columns columns,
		net.zamasoft.foliojet.layout.box.params.ClipPathShape clipPath, boolean flowRoot, byte textOverflow, double aspectRatio,
		int lineClamp) {
	static BlockParamsFields freeze(final BlockParams source) {
		final FirstLineParamsTemplate firstLineStyle = source.firstLineStyle == null ? null
				: FirstLineParamsTemplate.freeze(source.firstLineStyle);
		return new BlockParamsFields(LineParamsFields.freeze(source), source.frame, firstLineStyle,
				source.pageBreakInside, source.orphans, source.widows, source.size, source.minSize, source.maxSize,
				source.boxSizing, source.overflow, source.blockAlignContent, source.paintClip, source.columns,
				source.clipPath, source.flowRoot, source.textOverflow, source.aspectRatio, source.lineClamp);
	}

	/**
	 * Writes all fields back to {@code target} . {@code target} may be a {@code BlockParams} subclass
	 * (such as {@code TableParams} ). Assigns a fresh {@code firstLineStyle} on each call,
	 * so multiple materializations do not affect one another.
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
		target.blockAlignContent = this.blockAlignContent;
		target.paintClip = this.paintClip;
		target.columns = this.columns;
		target.clipPath = this.clipPath;
		target.flowRoot = this.flowRoot;
		target.textOverflow = this.textOverflow;
		// aspect-ratio (2026-08-29). Omitting it from freezing loses the ratio on replay/restyle,
		// collapsing thumbnail height to the height of its (empty) content: 0.
		target.aspectRatio = this.aspectRatio;
		// line-clamp (2026-08-29). Omitting it loses the line-count cutoff on replay.
		target.lineClamp = this.lineClamp;
	}
}
