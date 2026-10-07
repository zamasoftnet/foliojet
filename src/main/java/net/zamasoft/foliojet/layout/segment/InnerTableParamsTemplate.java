package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectBorder;

/**
 * A template that freezes the contents of {@link InnerTableParams}
 * (directly extends {@code Params} , bypassing ``AbstractTextParams``;
 * used by {@link BoxKind#TABLE_ROW_GROUP} /{@link BoxKind#TABLE_ROW}/
 * {@link BoxKind#TABLE_COLUMN_GROUP} /{@link BoxKind#TABLE_COLUMN})
 * and materializes an independent, fresh {@code InnerTableParams} on each call
 * (introduced 2026-07-22, M6d-A3b).
 *
 * <p>
 * {@link ParamsFields} (also shared with `TextParamsFields`) handles ancestor ({@code Params}) fields.
 * Existing implementations of {@code background} /{@code border}/the {@code size} family/
 * {@code pageBreakInside} have been confirmed effectively immutable with only final fields,
 * so references are retained unchanged (no copy needed;
 * replaced with an immutable record in Stage2, 2026-07-22).
 * </p>
 */
public record InnerTableParamsTemplate(ParamsFields common, Background background, RectBorder border, Length size,
		Length minSize, Length maxSize, PageBreakMode pageBreakInside) {
	public static InnerTableParamsTemplate freeze(final InnerTableParams source) {
		return new InnerTableParamsTemplate(ParamsFields.freeze(source), source.background, source.border,
				source.size, source.minSize, source.maxSize, source.pageBreakInside);
	}

	/** Returns a fresh {@code InnerTableParams} on each call (multiple calls do not affect one another). */
	public InnerTableParams materialize() {
		final InnerTableParams p = new InnerTableParams();
		this.common.materializeInto(p);
		p.background = this.background;
		p.border = this.border;
		p.size = this.size;
		p.minSize = this.minSize;
		p.maxSize = this.maxSize;
		p.pageBreakInside = this.pageBreakInside;
		return p;
	}
}
