package net.zamasoft.foliojet.css.value;

import net.zamasoft.pdfg2d.gc.font.FontStyle;

/** CSS Writing Modesのtext-orientation値。 */
public enum TextOrientationValue implements Value {
	MIXED("mixed", FontStyle.TextOrientation.MIXED),
	UPRIGHT("upright", FontStyle.TextOrientation.UPRIGHT),
	SIDEWAYS("sideways", FontStyle.TextOrientation.SIDEWAYS);

	private final String text;
	private final FontStyle.TextOrientation orientation;

	private TextOrientationValue(final String text, final FontStyle.TextOrientation orientation) {
		this.text = text;
		this.orientation = orientation;
	}

	public FontStyle.TextOrientation orientation() {
		return this.orientation;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
