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

	public MyGVTGlyphVector(TextImpl text, MyGVTFont font, FontRenderContext frc) {
		this.text = text;
		this.font = font;
		this.frc = frc;
		// 配列の最後は末尾の位置
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

	public Rectangle2D getLogicalBounds() {
		double advance = this.text.getAdvance();
		double size = this.text.getFontStyle().getSize();
		return new Rectangle2D.Double(this.x[0], this.y[0], advance, size);
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
		float hadvance = (float) this.text.getAdvance() / this.getNumGlyphs();
		float vadvance = (float) this.text.getFontStyle().getSize();
		return new GVTGlyphMetrics(hadvance, vadvance, (Rectangle2D) this.getGlyphLogicalBounds(ix),
				GlyphMetrics.STANDARD);
	}

	public Shape getGlyphVisualBounds(int ix) {
		return this.getGlyphLogicalBounds(ix);
	}

	public Shape getGlyphLogicalBounds(int ix) {
		float hadvance = (float) this.text.getAdvance() / this.getNumGlyphs();
		float vadvance = (float) this.text.getFontStyle().getSize();
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
			throw new UnsupportedOperationException();
		}
		ShapedFont font = (ShapedFont) this.font.font;
		return font.getShapeByGID(this.getGlyphCode(ix));
	}

	public Shape getOutline() {
		if (!(this.font.font instanceof ShapedFont)) {
			throw new UnsupportedOperationException();
		}
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
			// 自前でイタリックを再現する
			if (verticalFont) {
				oblique = AffineTransform.getShearInstance(0, 0.25);
			} else {
				oblique = AffineTransform.getShearInstance(-0.25, 0);
			}
		}

		GeneralPath path = new GeneralPath();
		if (verticalFont) {
			// 縦書きモード
			// 縦書き対応フォント
			at.concatenate(AffineTransform.getTranslateInstance(-fontSize / 2.0, fontSize * 0.88));
			int pgid = 0;
			for (int i = 0; i < glen; ++i) {
				AffineTransform at2 = at;
				int gid = gids[i];
				if (i > 0) {
					double dy = fm.getAdvance(pgid) + letterSpacing;
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
					if (width != 0) {
						at2 = AffineTransform.getTranslateInstance(width, 0);
						at2.concatenate(at);
					}
					if (oblique != null) {
						shape = oblique.createTransformedShape(shape);
					}
					path.append(shape.getPathIterator(at2), false);
				}
			}
		} else {
			if (direction == FontStyle.Direction.TB) {
				// 横倒し
				at.concatenate(AffineTransform.getRotateInstance(Math.PI / 2.0));
				BBox bbox = font.getFontSource().getBBox();
				double dy = ((bbox.lly() + bbox.ury()) * fontSize / FontSource.DEFAULT_UNITS_PER_EM) / 2.0;
				at.concatenate(AffineTransform.getTranslateInstance(0, dy));
			}
			// 横書き
			int pgid = 0;
			for (int i = 0; i < glen; ++i) {
				final int gid = gids[i];
				if (i > 0) {
					double dx = fm.getAdvance(pgid) + letterSpacing;
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
		return path.createTransformedShape(at);
	}

	public Rectangle2D getGeometricBounds() {
		throw new UnsupportedOperationException();
	}

	public Rectangle2D getGlyphCellBounds(int ix) {
		throw new UnsupportedOperationException();
	}

	public int[] getGlyphCodes(int begin, int num, int[] ret) {
		throw new UnsupportedOperationException();
	}

	public GlyphJustificationInfo getGlyphJustificationInfo(int ix) {
		throw new UnsupportedOperationException();
	}

	public void draw(Graphics2D g2d, AttributedCharacterIterator aci) {
		throw new UnsupportedOperationException();
	}

	public AffineTransform getGlyphTransform(int ix) {
		throw new UnsupportedOperationException();
	}

	public Shape getOutline(float x, float y) {
		throw new UnsupportedOperationException();
	}

	public boolean isGlyphVisible(int ix) {
		throw new UnsupportedOperationException();
	}

	public void setGlyphTransform(int ix, AffineTransform t) {
		throw new UnsupportedOperationException();
	}

	public void setGlyphVisible(int ix, boolean v) {
		throw new UnsupportedOperationException();
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
