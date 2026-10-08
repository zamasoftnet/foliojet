package net.zamasoft.foliojet.css.impl.part;

import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * The image of a {@code disc}, {@code circle} or {@code square} list marker: a bullet 0.35em across whose center
 * sits 0.3em above the image's bottom edge, which the marker places on the baseline.
 *
 * <p>
 * The image is 1em wide and 0.7em tall (2026-10-08). It was 1em square with the bullet drawn in its lower part: on
 * the baseline it reached 1em above it, past the ascent of the text, so a disc list's first line came out taller
 * than the others (14.4pt instead of 12pt for 10pt text with a 12pt line height; Chrome's bullet is a glyph and
 * leaves the line alone). 0.7em stays below the ascent of the usual text fonts. The bullet keeps its distance from
 * the baseline and, in vertical writing, from the start of the line.
 * </p>
 */
abstract class ListBulletImage implements Image {
	/** Height of the image in em. */
	private static final double HEIGHT = 0.7;
	/** Distance from the bottom edge to the bullet's center, in em. */
	private static final double CENTER = 0.3;
	/** Diameter (side) of the bullet, in em. */
	private static final double BULLET = 0.35;

	protected final double size;

	protected final Color color;

	protected ListBulletImage(final FontStyle fontStyle, final Color color) {
		this.size = fontStyle.getSize();
		this.color = color;
	}

	public double getWidth() {
		return this.size;
	}

	public double getHeight() {
		return this.size * HEIGHT;
	}

	/** The bullet's bounding square in the image's coordinates. */
	protected final Rectangle2D bullet() {
		final double d = this.size * BULLET;
		return new Rectangle2D.Double(this.size / 2.0 - d / 2.0, this.getHeight() - this.size * CENTER - d / 2.0, d, d);
	}
}
