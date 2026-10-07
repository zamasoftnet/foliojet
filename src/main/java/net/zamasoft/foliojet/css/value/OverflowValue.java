package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;


/**
 * @author MIYABE Tatsuhiko
 */
public enum OverflowValue implements Value {
	VISIBLE_VALUE(OverflowMode.VISIBLE),

	HIDDEN_VALUE(OverflowMode.HIDDEN),

	// The two OverflowMode values were swapped until 2026-08-09 (no actual impact, since no code
	// distinguished values other than HIDDEN). Corrected when clipping was introduced.
	AUTO_VALUE(OverflowMode.AUTO),

	SCROLL_VALUE(OverflowMode.SCROLL);

	private final OverflowMode overflow;

	private OverflowValue(OverflowMode overflow) {
		this.overflow = overflow;
	}

	public OverflowMode getOverflow() {
		return this.overflow;
	}

	public String toString() {
		switch (this.overflow) {
		case OverflowMode.VISIBLE:
			return "visible";

		case OverflowMode.HIDDEN:
			return "hidden";

		case OverflowMode.SCROLL:
			return "scroll";

		case OverflowMode.AUTO:
			return "auto";

		default:
			throw new IllegalStateException();
		}
	}
}