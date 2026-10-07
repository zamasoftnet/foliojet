package net.zamasoft.foliojet.css.value;

/**
 * Keyword values for Box Alignment properties (Grid G5a, 2026-07-31;
 * consult-codex-2026-07-31-grid-g5.txt Q2). Correspond by name to layout-side
 * {@code BoxAlignment} (mapped in BoxStyleMapper).
 * baseline and safe/unsafe prefixes are outside the subset (invalid declaration,
 * not silently discarded). Flex F3a added flex-start/flex-end and space-* (the property-side
 * list determines acceptance; the Grid mapper falls back to NORMAL for unsupported values).
 *
 * @author MIYABE Tatsuhiko
 */
public enum BoxAlignmentValue implements Value {
	AUTO("auto"), NORMAL("normal"), START("start"), CENTER("center"), END("end"), STRETCH("stretch"),
	/** flex-start/flex-end (Flex F3a: synonymous with start/end until reverse is introduced in F5b). */
	FLEX_START("flex-start"), FLEX_END("flex-end"),
	/** Content distribution (Flex F3a: not accepted by self-alignment properties). */
	SPACE_BETWEEN("space-between"), SPACE_AROUND("space-around"), SPACE_EVENLY("space-evenly");

	private final String text;

	private BoxAlignmentValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
