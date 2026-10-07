package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * Used value of outline (2026-08-29). A frame drawn at a distance of {@link #offset} outside the
 * border edge, using the style, width, and color of {@link #border}. Does not affect layout
 * (CSS UI 3 §4).
 *
 * @author MIYABE Tatsuhiko
 */
public final class Outline {
	/** Line shared by all four sides. Invisible if style=NONE. */
	public final Border border;

	/** Distance from the border edge to the outline's inner edge. Negative values move inside the border. */
	public final double offset;

	/**
	 * Creates a visible outline. Returns null for outlines that would be invisible when drawn
	 * (none, zero width, or transparent).
	 */
	public static Outline create(short style, double width, Color color, double offset) {
		final Border border = Border.create(style, width, color);
		if (!border.isVisible()) {
			return null;
		}
		return new Outline(border, offset);
	}

	private Outline(Border border, double offset) {
		this.border = border;
		this.offset = offset;
	}

	public String toString() {
		return "[border=" + this.border + ",offset=" + this.offset + "]";
	}
}
