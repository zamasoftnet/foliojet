package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.Length;

/**
 * Freeze/materialize processing for the line-specific fields
 * (textAlign/textAlignLast/textIndent/lineHeight) that {@code AbstractLineParams} adds to
 * {@code AbstractTextParams} .
 * Introduced 2026-07-22, M6d-A3b Stage1; package-private and shared as the internal implementation of
 * {@link BlockParamsTemplate} and {@link FirstLineParamsTemplate} .
 *
 * <p>
 * Delegates shared ancestor fields from {@code Params} /{@code AbstractTextParams} to
 * {@link TextParamsFields} (composition).
 * They were extracted there to avoid duplication because the template for {@code InlineParams}
 * (directly extends `AbstractTextParams`, with no line-specific fields) needs the same ancestor portion.
 * </p>
 *
 * <p>
 * Stage2 replaced the templates themselves with immutable records (completed 2026-07-22).
 * See the {@link TextParamsFields} Javadoc for the basis of field classification.
 * </p>
 */
record LineParamsFields(TextParamsFields text, byte textAlign, byte textAlignLast, Length textIndent,
		double lineHeight) {
	static LineParamsFields freeze(final AbstractLineParams source) {
		return new LineParamsFields(TextParamsFields.freeze(source), source.textAlign, source.textAlignLast,
				source.textIndent, source.lineHeight);
	}

	/**
	 * Writes all fields back to {@code target} .
	 * Allocates fresh {@code AffineTransform} /{@code TextShadow[]} instances on each call,
	 * so multiple materializations do not affect one another (M6d-A's most important contract).
	 */
	void materializeInto(final AbstractLineParams target) {
		this.text.materializeInto(target);
		target.textAlign = this.textAlign;
		target.textAlignLast = this.textAlignLast;
		target.textIndent = this.textIndent;
		target.lineHeight = this.lineHeight;
	}
}
