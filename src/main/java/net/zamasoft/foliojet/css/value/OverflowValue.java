package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.OverflowMode;


/**
 * @author MIYABE Tatsuhiko
 */
public enum OverflowValue implements Value {
	VISIBLE_VALUE(OverflowMode.VISIBLE),

	HIDDEN_VALUE(OverflowMode.HIDDEN),

	AUTO_VALUE(OverflowMode.SCROLL),

	SCROLL_VALUE(OverflowMode.AUTO);

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