package net.zamasoft.foliojet.objects.mathml;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.content.BaselineImage;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.sourceforge.jeuclid.layout.JEuclidView;

/**
 * Laid-out formula. Has a depth below the baseline ({@link #getDescent()});
 * within a line, aligns its baseline, rather than its bottom, with the body text baseline
 * (2026-10-04; previously aligning the bottom made formulas with subscripts, brackets, or y float upward).
 *
 * <p>
 * In vertical writing lines, rotates <b>sideways</b> (90° clockwise) like Latin text
 * ({@code sideways}, 2026-10-05). Swapping width and height makes the formula advance by its width
 * along the line, while the line width becomes the formula's height. Previously, it was placed
 * as an upright horizontal box, advancing only about one character along the line while its width
 * overflowed into the neighboring line.
 * </p>
 */
public class MathMLImage implements Image, BaselineImage {
	protected final JEuclidView view;

	/** Scale factor (text scaling; 1 when a CSS size is received). */
	protected final double scale;

	/** Whether to rotate sideways in vertical writing lines. */
	protected final boolean sideways;

	public MathMLImage(JEuclidView view) {
		this(view, 1.0, false);
	}

	public MathMLImage(JEuclidView view, double scale, boolean sideways) {
		this.view = view;
		this.scale = scale;
		this.sideways = sideways;
	}

	/** Width of the laid-out formula (before sideways rotation). */
	private double mathWidth() {
		return this.view.getWidth() * this.scale;
	}

	/** Height of the laid-out formula (before sideways rotation). */
	private double mathHeight() {
		return (this.view.getAscentHeight() + this.view.getDescentHeight()) * this.scale;
	}

	public double getWidth() {
		return this.sideways ? this.mathHeight() : this.mathWidth();
	}

	public double getHeight() {
		return this.sideways ? this.mathWidth() : this.mathHeight();
	}

	public double getDescent() {
		return this.view.getDescentHeight() * this.scale;
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			if (this.sideways) {
				// 90° clockwise: the formula's top faces right (the line's upper side), and reading proceeds downward
				gc.transform(new AffineTransform(0, 1, -1, 0, this.mathHeight(), 0));
			}
			if (this.scale != 1.0) {
				gc.transform(AffineTransform.getScaleInstance(this.scale, this.scale));
			}
			// BridgeGraphics2D setTransform resets the GC to the latest begin() before reapplying, so save state after
			// rotation and scaling. Without this, JEuclid resetting the transform discarded the rotation and scaling
			try (final var transformed = gc.begin()) {
				Graphics2D g2d = new BridgeGraphics2D(gc);
				this.view.draw(g2d, 0, this.view.getAscentHeight());
				g2d.dispose();
			}
		}
	}

	public String getAltString() {
		return null;
	}
}
