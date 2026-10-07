package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.FlexDirection;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * A template that freezes the contents of {@link FlexParams}
 * (directly extends {@code BlockParams} , used by {@code BoxKind#FLEX} ) and materializes an independent,
 * fresh {@code FlexParams} on each call (Flex F0c, 2026-08-02;
 * consult-codex-2026-08-02-flexbox.txt F0c; analogous to {@code GridParamsTemplate} ).
 *
 * <p>
 * direction/wrap are enums and can be retained unchanged.
 * Update this template when adding F2c (gap) and F3a (alignment) fields.
 * </p>
 */
public record FlexParamsTemplate(BlockParamsFields common, FlexDirection flexDirection, FlexWrap flexWrap,
		double rowGap, double columnGap, net.zamasoft.foliojet.layout.box.params.FlexContentAlignment justifyContent,
		net.zamasoft.foliojet.layout.box.params.BoxAlignment alignItems,
		net.zamasoft.foliojet.layout.box.params.FlexContentAlignment alignContent) {
	public static FlexParamsTemplate freeze(final FlexParams source) {
		return new FlexParamsTemplate(BlockParamsFields.freeze(source), source.flexDirection, source.flexWrap,
				source.rowGap, source.columnGap, source.justifyContent, source.alignItems, source.alignContent);
	}

	/** Returns the frozen writing direction (for {@code containsMixedFlow}). */
	public WritingMode flow() {
		return this.common.common().text().flow();
	}

	/** Returns a fresh {@code FlexParams} on each call. */
	public FlexParams materialize() {
		final FlexParams p = new FlexParams();
		this.common.materializeInto(p);
		p.flexDirection = this.flexDirection;
		p.flexWrap = this.flexWrap;
		p.rowGap = this.rowGap;
		p.columnGap = this.columnGap;
		p.justifyContent = this.justifyContent;
		p.alignItems = this.alignItems;
		p.alignContent = this.alignContent;
		return p;
	}
}
