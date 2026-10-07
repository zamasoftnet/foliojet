package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * One used value of box-shadow (2026-08-29). All lengths are absolute.
 * Drawn by {@link net.zamasoft.foliojet.layout.util.BoxDecorationRenderer}.
 *
 * @author MIYABE Tatsuhiko
 */
public final class BoxShadow {
	public final double x, y;

	/** Blur radius (nonnegative) and spread (may be negative). */
	public final double blur, spread;

	/** Shadow color (including alpha). */
	public final Color color;

	/** true for an inset shadow (drawn inside the padding box). */
	public final boolean inset;

	public BoxShadow(double x, double y, double blur, double spread, Color color, boolean inset) {
		this.x = x;
		this.y = y;
		this.blur = blur;
		this.spread = spread;
		this.color = color;
		this.inset = inset;
	}

	public String toString() {
		return "[x=" + this.x + ",y=" + this.y + ",blur=" + this.blur + ",spread=" + this.spread + ",inset="
				+ this.inset + "]";
	}
}
