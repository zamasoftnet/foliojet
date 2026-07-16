package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.ClearMode;


/**
 * @author MIYABE Tatsuhiko
 */
public enum ClearValue implements Value {
	NONE_VALUE(ClearMode.NONE),

	LEFT_VALUE(ClearMode.START),

	RIGHT_VALUE(ClearMode.END),

	START_VALUE(ClearMode.START),

	END_VALUE(ClearMode.END),

	BOTH_VALUE(ClearMode.BOTH);

	private final ClearMode clear;

	private ClearValue(ClearMode clear) {
		this.clear = clear;
	}

	public ClearMode getClear() {
		return this.clear;
	}

	public String toString() {
		switch (this.clear) {
		case ClearMode.NONE:
			return "none";

		case ClearMode.START:
			return "left";

		case ClearMode.END:
			return "right";

		case ClearMode.BOTH:
			return "both";

		default:
			throw new IllegalStateException();
		}
	}
}