package net.zamasoft.foliojet.layout.box.content;

/**
 * An image with its own baseline (2026-10-04).
 *
 * <p>
 * A replaced element in a line normally places its <b>bottom edge (margin bottom) on the baseline</b>.
 * Like text, formulas (MathML) have parts below the baseline (subscripts, parentheses, the descender
 * of y), so placing the bottom there raises the whole formula. An image with this marker is lowered
 * by {@link #getDescent()} (in horizontal writing; vertical lines center it as before).
 * </p>
 */
public interface BaselineImage {
	/**
	 * The distance from the image bottom to its baseline (in image units, the same as {@code getHeight()}).
	 * 0 means the bottom edge is the baseline.
	 */
	public double getDescent();
}
