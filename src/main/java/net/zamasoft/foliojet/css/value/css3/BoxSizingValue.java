package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum BoxSizingValue implements Value {
	CONTENT_BOX_VALUE(BoxSizingMode.CONTENT_BOX),

	BORDER_BOX_VALUE(BoxSizingMode.BORDER_BOX);

	private final BoxSizingMode boxSizing;

	private BoxSizingValue(BoxSizingMode boxSizing) {
		this.boxSizing = boxSizing;
	}

	public BoxSizingMode getBoxSizing() {
		return this.boxSizing;
	}

	public String toString() {
		switch (this.boxSizing) {
		case CONTENT_BOX:
			return "content-box";

		case BORDER_BOX:
			return "border-box";

		default:
			throw new IllegalStateException();
		}
	}
}