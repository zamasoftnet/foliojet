package net.zamasoft.foliojet.layout.util;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

/**
 * Maps horizontal local coordinates of sideways lines to physical coordinates.
 *
 * <p>
 * In local coordinates, the baseline starts at {@code (0, 0)}, inline advance is {@code +x},
 * and the descent side is {@code +y}. CW performs a pure {@code +90}-degree rotation with
 * physical origin {@code (x + descent, y)}; CCW performs a pure {@code -90}-degree rotation
 * with origin {@code (x + ascent, y + advance)}.
 * Thus CW has descent/under on the left and ascent/over on the right; CCW has them on the right
 * and left, respectively. Local dimensions such as line width, underline distance,
 * and shadows are not scaled.
 * </p>
 *
 * <p>
 * Combining direction yields four logical inline progressions: CW×LTR = top to bottom,
 * CW×RTL = bottom to top, CCW×LTR = bottom to top, CCW×RTL = top to bottom.
 * The transform itself does not change logical/visual glyph order;
 * the caller draws post-bidi runs directly in horizontal local coordinates.
 * </p>
 *
 * <p>
 * In Stage 2, both CW/CCW use this shared contract; the caller performs physical inline reversal
 * within the line box.
 * </p>
 */
public final class SidewaysGeometry {
	private SidewaysGeometry() {
	}

	/**
	 * Returns a transform mapping horizontal run baseline coordinates to sideways line physical coordinates.
	 *
	 * @param variant SIDEWAYS_CW or SIDEWAYS_CCW
	 * @param x       left edge of the physical run box
	 * @param y       top edge of the physical run box
	 * @param ascent  ascent in horizontal layout
	 * @param descent descent in horizontal layout
	 * @param advance advance of the horizontal run
	 * @return transform consisting of translation and a pure quarter turn
	 */
	public static AffineTransform runTransform(final WritingModeVariant variant, final double x, final double y,
			final double ascent, final double descent, final double advance) {
		switch (variant) {
		case SIDEWAYS_CW:
			// Explicit matrix (avoids cos/sin rounding and -0.0; PDF Tm also becomes 0 1 -1 0)
			return new AffineTransform(0, 1, -1, 0, x + descent, y);
		case SIDEWAYS_CCW:
			return new AffineTransform(0, -1, 1, 0, x + ascent, y + advance);
		case NORMAL:
		default:
			throw new IllegalArgumentException("A normal writing mode has no sideways run transform");
		}
	}

	/**
	 * Returns the physical bounding rectangle of the rotated horizontal run box
	 * {@code [0, advance] x [-ascent, descent]}.
	 */
	public static Rectangle2D bounds(final WritingModeVariant variant, final double x, final double y,
			final double ascent, final double descent, final double advance) {
		switch (variant) {
		case SIDEWAYS_CW:
		case SIDEWAYS_CCW:
			return new Rectangle2D.Double(x, y, ascent + descent, advance);
		case NORMAL:
		default:
			throw new IllegalArgumentException("A normal writing mode has no sideways run bounds");
		}
	}
}
