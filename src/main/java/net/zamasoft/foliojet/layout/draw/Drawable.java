package net.zamasoft.foliojet.layout.draw;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * An object that can be drawn.
 *
 * @author MIYABE Tatsuhiko
 */
public interface Drawable {
	/**
	 * Draws the object.
	 */
	public void draw(GC gc, double x, double y) throws GraphicsException;

	/**
	 * Returns a one-line representation for display-list dumps. Return a deterministic string identifying the content,
	 * since it is used for regression validation (golden comparison).
	 */
	public default String describe() {
		String name = this.getClass().getSimpleName();
		return name.isEmpty() ? this.getClass().getName() : name;
	}

	/**
	 * Detailed geometry, including drawing position. Drawables that do not alter the normal display list return an
	 * empty string.
	 */
	public default String describeGeometry(final double x, final double y) {
		return "";
	}

	/**
	 * Returns a one-line representation of the drawing clip for display-list dumps (2026-08-09). Empty string if no
	 * clip. Dump coordinates are before clipping, so clipping regressions otherwise leave no trace in goldens (the
	 * same gap as {@code tf=}).
	 */
	public default String describeClip() {
		return "";
	}
}
