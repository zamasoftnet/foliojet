package net.zamasoft.foliojet.ua.impl.svg;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedCharacterIterator;

import org.apache.batik.gvt.font.GVTFont;
import org.apache.batik.gvt.font.GVTGlyphMetrics;
import org.apache.batik.gvt.font.GVTGlyphVector;

import net.zamasoft.pdfg2d.font.BBox;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

public class MyGVTGlyphVector implements GVTGlyphVector {
	protected final TextImpl text;
	protected final MyGVTFont font;
	protected final FontRenderContext frc;
	protected float[] x, y;
	private AffineTransform[] glyphTransforms;
	private boolean[] visible;
	/** Per-glyph advances (fallback font advances for missing characters). Null uses FontMetrics advances. */
	private final double[] advances;

	public MyGVTGlyphVector(TextImpl text, MyGVTFont font, FontRenderContext frc) {
		this(text, font, frc, null);
	}

	public MyGVTGlyphVector(TextImpl text, MyGVTFont font, FontRenderContext frc, double[] advances) {
		this.text = text;
		this.font = font;
		this.frc = frc;
		this.advances = advances;
		// The final array entry is the end position.
		this.x = new float[text.getGlyphCount() + 1];
		this.y = new float[text.getGlyphCount() + 1];
	}

	public GVTFont getFont() {
		return this.font;
	}

	public void performDefaultLayout() {
		// ignore
	}

	public float[] getGlyphPositions(int begin, int num, float[] ret) {
		if (ret == null) {
			ret = new float[num * 2];
		}
		for (int i = 0; i < num; ++i) {
			ret[i * 2] = this.x[i + begin];
			ret[i * 2 + 1] = this.y[i + begin];
		}
		return ret;
	}

	public Rectangle2D getBounds2D(AttributedCharacterIterator aci) {
		return this.getLogicalBounds();
	}

	/** Advance of glyph ix. */
	private double advance(int ix) {
		if (this.advances != null) {
			return this.advances[ix];
		}
		return this.text.getFontMetrics().getAdvance(this.text.getGlyphIds()[ix]);
	}

	public Rectangle2D getLogicalBounds() {
		return this.textBox();
	}

	/**
	 * The box the text takes where it is: from the ascent above the baseline to the descent below it, or the em box
	 * centred on the line in vertical text (2026-11-26). It was the box below the baseline (y0 to y0 + size): Batik
	 * takes a filter's region and the image it filters from the bounds, and the text under a filter was cut away.
	 */
	private Rectangle2D textBox() {
		double advance = 0;
		for (int i = 0; i < this.getNumGlyphs(); ++i) {
			advance += this.advance(i);
		}
		advance += this.text.getLetterSpacing() * this.getNumGlyphs();
		final double size = this.text.getFontStyle().getSize();
		if (this.text.getFontStyle().getDirection() == FontStyle.Direction.TB) {
			final Point2D start = this.verticalStart();
			return new Rectangle2D.Double(start.getX() - size / 2, start.getY(), size, advance);
		}
		final FontMetrics fm = this.text.getFontMetrics();
		final double ascent = Math.abs(fm.getAscent()), descent = Math.abs(fm.getDescent());
		return new Rectangle2D.Double(this.x[0], this.y[0] - ascent, advance, ascent + descent);
	}

	/**
	 * Where vertical text starts as MyTextPainter draws it: the column's centre and the top of the first glyph
	 * (2026-11-26). Batik puts an upright first glyph half its advance left of x and its first offset, the ascent
	 * (half the em in vertical text), below y (GlyphLayout.doExplicitGlyphLayout); a Latin letter first on its side is
	 * placed otherwise, and the box is then off by up to half an em.
	 */
	private Point2D verticalStart() {
		final double size = this.text.getFontStyle().getSize();
		return new Point2D.Double(this.x[0] + (this.getNumGlyphs() > 0 ? this.advance(0) / 2 : 0), this.y[0] - size / 2);
	}

	public int getGlyphCode(int ix) {
		return this.text.getGlyphIds()[ix];
	}

	public int getNumGlyphs() {
		return this.text.getGlyphCount();
	}

	public int getCharacterCount(int start, int end) {
		int numChars = 0;
		if (start < 0) {
			start = 0;
		}
		if (end > this.text.getGlyphCount() - 1) {
			end = this.text.getGlyphCount() - 1;
		}
		byte[] clens = this.text.getClusterLengths();
		for (int i = start; i <= end; i++) {
			numChars += clens[i];
		}
		return numChars;
	}

	public GVTGlyphMetrics getGlyphMetrics(int ix) {
		float hadvance = (float) this.advance(ix);
		float vadvance = (float) this.text.getFontStyle().getSize();
		return new GVTGlyphMetrics(hadvance, vadvance, (Rectangle2D) this.getGlyphLogicalBounds(ix),
				GlyphMetrics.STANDARD);
	}

	public Shape getGlyphVisualBounds(int ix) {
		return this.getGlyphLogicalBounds(ix);
	}

	public Shape getGlyphLogicalBounds(int ix) {
		float hadvance = (float) this.advance(ix);
		float vadvance = (float) this.text.getFontStyle().getSize();
		if (this.text.getFontStyle().getDirection() == FontStyle.Direction.TB) {
			// Above the baseline by the ascent, as AWT's bounds are (2026-11-26): Batik adds the last glyph's bounds
			// below the baseline and the ascent to a vertical chunk's length for text-anchor, and with the box below the
			// baseline middle and end stood a quarter and half an em too high. (Horizontal text uses the width only.)
			return new Rectangle2D.Float(0, -vadvance / 2, hadvance, vadvance);
		}
		return new Rectangle2D.Float(0, 0, hadvance, vadvance);
	}

	public void setGlyphPosition(int ix, Point2D pos) {
		this.x[ix] = (float) pos.getX();
		this.y[ix] = (float) pos.getY();
	}

	public Point2D getGlyphPosition(int ix) {
		return new Point2D.Float(this.x[ix], this.y[ix]);
	}

	public FontRenderContext getFontRenderContext() {
		return this.frc;
	}

	public Shape getGlyphOutline(int ix) {
		if (!(this.font.font instanceof ShapedFont)) {
			// a font without glyph shapes (base 14, a CID font not embedded): the glyph's box
			final FontMetrics fm = this.text.getFontMetrics();
			final double ascent = Math.abs(fm.getAscent()), descent = Math.abs(fm.getDescent());
			return new Rectangle2D.Double(0, -ascent, this.advance(ix), ascent + descent);
		}
		ShapedFont font = (ShapedFont) this.font.font;
		return font.getShapeByGID(this.getGlyphCode(ix));
	}

	/**
	 * The outline of the text where it is (2026-11-26). Batik takes a filter's region, an objectBoundingBox and a
	 * link's area from it. It threw for a font without glyph shapes, and every shields.io badge (text under a blur
	 * filter, in Verdana that falls back to Helvetica) failed as a whole; with shapes it was at the origin, not at the
	 * text, and the text under a filter was cut away.
	 */
	public Shape getOutline() {
		if (!(this.font.font instanceof ShapedFont)) {
			return this.textBox();
		}
		final GeneralPath path = this.shapedOutline();
		// vertical shapes run down from the origin, centred on it
		final Point2D start = this.text.getFontStyle().getDirection() == FontStyle.Direction.TB ? this.verticalStart()
				: new Point2D.Float(this.x[0], this.y[0]);
		path.transform(AffineTransform.getTranslateInstance(start.getX(), start.getY()));
		return path;
	}

	/** The glyph shapes from the pen's start at the origin. */
	private GeneralPath shapedOutline() {
		ShapedFont font = (ShapedFont) this.font.font;
		FontStyle fontStyle = text.getFontStyle();
		FontStyle.Direction direction = fontStyle.getDirection();
		double fontSize = fontStyle.getSize();
		int glen = text.getGlyphCount();
		int[] gids = text.getGlyphIds();
		double letterSpacing = text.getLetterSpacing();
		final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xadvances = text.xAdvances();
		FontMetrics fm = text.getFontMetrics();
		AffineTransform at;
		{
			double s = fontSize / FontSource.DEFAULT_UNITS_PER_EM;
			at = AffineTransform.getScaleInstance(s, s);
		}

		boolean verticalFont = direction == FontStyle.Direction.TB && font.getFontSource().getDirection() == direction;
		AffineTransform oblique = null;
		FontStyle.Style style = fontStyle.getStyle();
		if (style != FontStyle.Style.NORMAL && !font.getFontSource().isItalic()) {
			// Synthesize italics.
			if (verticalFont) {
				oblique = AffineTransform.getShearInstance(0, 0.25);
			} else {
				oblique = AffineTransform.getShearInstance(-0.25, 0);
			}
		}

		GeneralPath path = new GeneralPath();
		if (verticalFont) {
			// Vertical writing mode
			// Font supporting vertical writing
			// Same composition as FontUtils.drawText: T(centering, pen + per-glyph vertical origin) × S.
			// Vertical origin is the font's VORG / yMax+tsb (pdfg2d Font.getVerticalOrigin, 2026-09-12).
			// Previously, 0.88em was concatenated in glyph coordinates, giving only 1/1000 of the intended effect.
			at.preConcatenate(AffineTransform.getTranslateInstance(-fontSize / 2.0, 0));
			int pgid = 0;
			for (int i = 0; i < glen; ++i) {
				AffineTransform at2;
				int gid = gids[i];
				if (i == 0 && xadvances != null && xadvances.get(0) != 0) {
					at.preConcatenate(AffineTransform.getTranslateInstance(0, xadvances.get(0)));
				} else if (i > 0) {
					double dy = this.advance(i - 1) + letterSpacing;
					dy -= fm.getKerning(pgid, gid);
					if (xadvances != null) {
						dy += xadvances.get(i);
					}
					at.preConcatenate(AffineTransform.getTranslateInstance(0, dy));
				}
				pgid = gid;
				Shape shape = font.getShapeByGID(gid);
				if (shape != null) {
					double width = (fontSize - fm.getWidth(gid)) / 2.0;
					at2 = AffineTransform.getTranslateInstance(width,
							font.getVerticalOrigin(gid) * fontSize / FontSource.DEFAULT_UNITS_PER_EM);
					at2.concatenate(at);
					if (oblique != null) {
						shape = oblique.createTransformedShape(shape);
					}
					path.append(shape.getPathIterator(at2), false);
				}
			}
		} else {
			if (direction == FontStyle.Direction.TB) {
				// Sideways
				at.concatenate(AffineTransform.getRotateInstance(Math.PI / 2.0));
				BBox bbox = font.getFontSource().getBBox();
				double dy = ((bbox.lly() + bbox.ury()) * fontSize / FontSource.DEFAULT_UNITS_PER_EM) / 2.0;
				at.concatenate(AffineTransform.getTranslateInstance(0, dy));
			}
			// Horizontal writing
			int pgid = 0;
			for (int i = 0; i < glen; ++i) {
				final int gid = gids[i];
				if (i == 0 && xadvances != null && xadvances.get(0) != 0) {
					at.preConcatenate(AffineTransform.getTranslateInstance(xadvances.get(0), 0));
				} else if (i > 0) {
					double dx = this.advance(i - 1) + letterSpacing;
					if (i > 0) {
						dx -= fm.getKerning(pgid, gid);
					}
					if (xadvances != null) {
						dx += xadvances.get(i);
					}
					at.preConcatenate(AffineTransform.getTranslateInstance(dx, 0));
				}
				Shape shape = font.getShapeByGID(gid);
				if (shape != null) {
					if (oblique != null) {
						shape = oblique.createTransformedShape(shape);
					}
					path.append(shape.getPathIterator(at), false);
				}
				pgid = gid;
			}
		}
		// Each glyph is already transformed by at/at2. Applying at again doubles scaling and pen advance (2026-09-14).
		return path;
	}

	public Rectangle2D getGeometricBounds() {
		return this.getOutline().getBounds2D();
	}

	public Rectangle2D getGlyphCellBounds(int ix) {
		// Batik's StrokingTextPainter uses this for selection and decoration calculations. Logical character boxes suffice.
		return (Rectangle2D) this.getGlyphLogicalBounds(ix);
	}

	public int[] getGlyphCodes(int begin, int num, int[] ret) {
		if (ret == null) {
			ret = new int[num];
		}
		final int[] gids = this.text.getGlyphIds();
		System.arraycopy(gids, begin, ret, 0, num);
		return ret;
	}

	public GlyphJustificationInfo getGlyphJustificationInfo(int ix) {
		// foliojet already handles justification, so do not let Batik stretch or shrink.
		return null;
	}

	public void draw(Graphics2D g2d, AttributedCharacterIterator aci) {
		g2d.fill(this.getOutline());
	}

	public AffineTransform getGlyphTransform(int ix) {
		// No per-glyph transformations (null = identity). Previously threw an exception, but Batik's
		// GlyphLayout.doExplicitGlyphLayout calls this, causing all SVG document <text>
		// conversions to fail (2026-09-14).
		return this.glyphTransforms == null ? null : this.glyphTransforms[ix];
	}

	public Shape getOutline(float x, float y) {
		return AffineTransform.getTranslateInstance(x, y).createTransformedShape(this.getOutline());
	}

	public boolean isGlyphVisible(int ix) {
		return this.visible == null || this.visible[ix];
	}

	public void setGlyphTransform(int ix, AffineTransform t) {
		if (this.glyphTransforms == null) {
			this.glyphTransforms = new AffineTransform[this.text.getGlyphCount()];
		}
		this.glyphTransforms[ix] = t;
	}

	public void setGlyphVisible(int ix, boolean v) {
		if (this.visible == null) {
			this.visible = new boolean[this.text.getGlyphCount()];
			java.util.Arrays.fill(this.visible, true);
		}
		this.visible[ix] = v;
	}

	@Override
	public boolean isReversed() {
		return false;
	}

	@Override
	public void maybeReverse(boolean mirror) {
		// ignore
	}
}
