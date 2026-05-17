package jp.cssj.homare.style.util;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.cssj.homare.style.box.params.Border;
import jp.cssj.homare.style.box.params.RectBorder;
import jp.cssj.homare.style.box.params.RectBorder.Radius;
import jp.cssj.homare.style.part.TableCollapsedBorders;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * ボックスおよびテーブルの境界を描画します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderRenderer.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class BorderRenderer {
	public static BorderRenderer INSTANCE = new BorderRenderer();

	/** 実線 */
	public static final double[] STROKE_SOLID = new double[0];

	/** 点線 */
	public static final double[] STROKE_DOTTED = new double[] { 1, 2 };

	/** 破線 */
	public static final double[] STROKE_DASHED = new double[] { 3, 3 };

	private BorderRenderer() {
		// private
	}

	protected static void setStroke(GC gc, double w, double[] pattern) {
		gc.setLineWidth(w);
		if (pattern.length == 0 || w == 1) {
			gc.setLinePattern(pattern);
			return;
		}
		double[] p = new double[pattern.length];
		for (int i = 0; i < p.length; ++i) {
			p[i] = pattern[i] * w;
		}
		gc.setLinePattern(p);
	}

	protected Color brighter(Color color) {
		float r = color.getRed();
		float g = color.getGreen();
		float b = color.getBlue();
		return RGBColor.create(r + (1f - r) / 2, g + (1f - g) / 2, b + (1f - b) / 2);
	}

	protected Color darker(Color color) {
		float r = color.getRed();
		float g = color.getGreen();
		float b = color.getBlue();
		return RGBColor.create(r / 2, g / 2, b / 2);
	}

	protected void drawBottomDoubleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		double w = border.getBottom().width / 3.0;
		double y = height - border.getBottom().width / 2.0;
		gc.begin();
		gc.setStrokePaint(border.getBottom().color);
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.draw(new Line2D.Double(border.getLeft().width / 6.0 + tx, y + w + ty,
				width - border.getRight().width / 6.0 + tx, y + w + ty));
		gc.draw(new Line2D.Double(border.getLeft().width / 6.0 + tx, y - w + ty,
				width - border.getRight().width / 6.0 + tx, y - w + ty));
		gc.end();
	}

	protected void drawRightDoubleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		double w = border.getRight().width / 3f;
		double x = width - border.getRight().width / 2.0;
		gc.begin();
		gc.setStrokePaint(border.getRight().color);
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.draw(new Line2D.Double(x + w + tx, border.getTop().width / 6.0 + ty, x + w + tx,
				height - border.getBottom().width / 6.0 + ty));
		gc.draw(new Line2D.Double(x - w + tx, border.getTop().width / 6.0 + ty, x - w + tx,
				height - border.getBottom().width / 6.0 + ty));
		gc.end();
	}

	protected void drawTopDoubleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		double w = border.getTop().width / 3f;
		double y = border.getTop().width / 2.0;
		gc.begin();
		gc.setStrokePaint(border.getTop().color);
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.draw(new Line2D.Double(border.getLeft().width / 6.0 + tx, y - w + ty,
				width - border.getRight().width / 6.0 + tx, y - w + ty));
		gc.draw(new Line2D.Double(border.getLeft().width / 6.0 + tx, y + w + ty,
				width - border.getRight().width / 6.0 + tx, y + w + ty));
		gc.end();
	}

	protected void drawLeftDoubleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		double w = border.getLeft().width / 3f;
		double x = border.getLeft().width / 2.0;
		gc.begin();
		gc.setStrokePaint(border.getLeft().color);
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.draw(new Line2D.Double(x - w + tx, border.getTop().width / 6.0 + ty, x - w + tx,
				height - border.getBottom().width / 6.0 + ty));
		gc.draw(new Line2D.Double(x + w + tx, border.getTop().width / 6.0 + ty, x + w + tx,
				height - border.getBottom().width / 6.0 + ty));
		gc.end();
	}

	protected void drawBottomSimpleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		gc.begin();
		gc.setStrokePaint(border.getBottom().color);
		double y = height - border.getBottom().width / 2.0;
		gc.draw(new Line2D.Double(tx, y + ty, width + tx, y + ty));
		gc.end();
	}

	protected void drawRightSimpleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		gc.begin();
		gc.setStrokePaint(border.getRight().color);
		double x = width - border.getRight().width / 2.0;
		gc.draw(new Line2D.Double(x + tx, ty, x + tx, height + ty));
		gc.end();
	}

	protected void drawTopSimpleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		gc.begin();
		gc.setStrokePaint(border.getTop().color);
		double y = border.getTop().width / 2.0;
		gc.draw(new Line2D.Double(tx, y + ty, width + tx, y + ty));
		gc.end();
	}

	protected void drawLeftSimpleLine(GC gc, RectBorder border, double tx, double ty, double width, double height)
			throws GraphicsException {
		gc.begin();
		gc.setStrokePaint(border.getLeft().color);
		double x = border.getLeft().width / 2.0;
		gc.draw(new Line2D.Double(x + tx, ty, x + tx, height + ty));
		gc.end();
	}

	protected void drawRightGrooveLine(GC gc, RectBorder border, boolean ridge, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getRight().color;
		double w = border.getRight().width / 2.0;
		double x = width - border.getRight().width / 2.0;
		gc.begin();
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(ridge ? this.darker(color) : this.brighter(color));
		gc.draw(new Line2D.Double(x + w / 2.0 + tx, border.getTop().width / 4.0 + ty, x + w / 2.0 + tx,
				height - border.getBottom().width / 4.0 + ty));
		gc.setStrokePaint(ridge ? this.brighter(color) : this.darker(color));
		gc.draw(new Line2D.Double(x - w / 2.0 + tx, border.getTop().width / 4.0 + ty, x - w / 2.0 + tx,
				height - border.getBottom().width / 4.0 + ty));
		gc.end();
	}

	protected void drawLeftInsetLine(GC gc, RectBorder border, boolean outset, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getLeft().color;
		gc.begin();
		setStroke(gc, border.getLeft().width, BorderRenderer.STROKE_SOLID);
		double x = border.getLeft().width / 2.0;
		gc.setStrokePaint(outset ? this.brighter(color) : this.darker(color));
		gc.draw(new Line2D.Double(x + tx, border.getTop().width / 2.0 + ty, x + tx,
				height - border.getBottom().width / 2.0 + ty));
		gc.end();
	}

	protected void drawBottomGrooveLine(GC gc, RectBorder border, boolean ridge, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getBottom().color;
		double w = border.getBottom().width / 2.0;
		double y = height - border.getBottom().width / 2.0;
		gc.begin();
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(ridge ? this.darker(color) : this.brighter(color));
		gc.draw(new Line2D.Double(border.getLeft().width / 4.0 + tx, y + w / 2.0 + ty,
				width - border.getRight().width / 4.0 + tx, y + w / 2.0 + ty));
		gc.setStrokePaint(ridge ? this.brighter(color) : this.darker(color));
		gc.draw(new Line2D.Double(border.getLeft().width / 4.0 + tx, y - w / 2.0 + ty,
				width - border.getRight().width / 4.0 + tx, y - w / 2.0 + ty));
		gc.end();
	}

	protected void drawLeftGrooveLine(GC gc, RectBorder border, boolean ridge, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getLeft().color;
		double w = border.getLeft().width / 2.0;
		double x = border.getLeft().width / 2.0;
		gc.begin();
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(ridge ? this.brighter(color) : this.darker(color));
		gc.draw(new Line2D.Double(x - w / 2.0 + tx, border.getTop().width / 4.0 + ty, x - w / 2.0 + tx,
				height - border.getBottom().width / 4.0 + ty));
		gc.setStrokePaint(ridge ? this.darker(color) : this.brighter(color));
		gc.draw(new Line2D.Double(x + w / 2.0 + tx, border.getTop().width / 4.0 + ty, x + w / 2.0 + tx,
				height - border.getBottom().width / 4.0 + ty));
		gc.end();
	}

	protected void drawTopInsetLine(GC gc, RectBorder border, boolean outset, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getTop().color;
		gc.begin();
		setStroke(gc, border.getTop().width, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(outset ? this.brighter(color) : this.darker(color));
		double y = border.getTop().width / 2.0;
		gc.draw(new Line2D.Double(border.getLeft().width / 2.0 + tx, y + ty, width - border.getRight().width / 2.0 + tx,
				y + ty));
		gc.end();
	}

	protected void drawRightInsetLine(GC gc, RectBorder border, boolean outset, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getRight().color;
		gc.begin();
		setStroke(gc, border.getRight().width, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(outset ? this.darker(color) : this.brighter(color));
		double x = width - border.getRight().width / 2.0;
		gc.draw(new Line2D.Double(x + tx, border.getTop().width / 2.0 + ty, x + tx,
				height - border.getBottom().width / 2.0 + ty));
		gc.end();
	}

	protected void drawTopGrooveLine(GC gc, RectBorder border, boolean ridge, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getTop().color;
		double w = border.getTop().width / 2.0;
		double y = border.getTop().width / 2.0;
		gc.begin();
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(ridge ? this.brighter(color) : this.darker(color));
		gc.draw(new Line2D.Double(border.getLeft().width / 4.0 + tx, y - w / 2.0 + ty,
				width - border.getRight().width / 4.0 + tx, y - w / 2.0 + ty));
		gc.setStrokePaint(ridge ? this.darker(color) : this.brighter(color));
		gc.draw(new Line2D.Double(border.getLeft().width / 4.0 + tx, y + w / 2.0 + ty,
				width - border.getRight().width / 4.0 + tx, y + w / 2.0 + ty));
		gc.end();
	}

	protected void drawBottomInsetLine(GC gc, RectBorder border, boolean outset, double tx, double ty, double width,
			double height) throws GraphicsException {
		Color color = border.getBottom().color;
		gc.begin();
		setStroke(gc, border.getBottom().width, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(outset ? this.darker(color) : this.brighter(color));
		double y = height - border.getBottom().width / 2.0;
		gc.draw(new Line2D.Double(border.getLeft().width / 2.0 + tx, y + ty, width - border.getRight().width / 2.0 + tx,
				y + ty));
		gc.end();
	}

	protected void drawRect(GC gc, Border border, Radius radius, double x, double y, double width, double height)
			throws GraphicsException {
		final short style = border.style;
		final double w = border.width;
		final Color color = border.color;
		gc.begin();
		switch (style) {
		case Border.SOLID:
			setStroke(gc, w, BorderRenderer.STROKE_SOLID);
			break;

		case Border.DASHED:
			setStroke(gc, w, BorderRenderer.STROKE_DASHED);
			break;

		case Border.DOTTED:
			setStroke(gc, w, BorderRenderer.STROKE_DOTTED);
			break;

		case Border.DOUBLE:
			setStroke(gc, w / 3.0, BorderRenderer.STROKE_SOLID);
			break;
		default:
			throw new IllegalStateException();
		}
		gc.setStrokePaint(color);
		if (style == Border.DOUBLE) {
			{
				final Shape shape;
				if (radius.hr == 0 && radius.vr == 0) {
					shape = new Rectangle2D.Double(w / 6.0 + x, w / 6.0 + y, width - w / 3.0, height - w / 3.0);
				} else {
					shape = new RoundRectangle2D.Double(w / 6.0 + x, w / 6.0 + y, width - w / 3.0, height - w / 3.0,
							radius.hr * 2 - w / 6.0, radius.vr * 2 - w / 6.0);
				}
				gc.draw(shape);
			}
			{
				final Shape shape;
				if (radius.hr == 0 && radius.vr == 0) {
					shape = new Rectangle2D.Double(w * 5.0 / 6.0 + x, w * 5.0 / 6.0 + y, width - w * 5.0 / 3.0,
							height - w * 5.0 / 3.0);
				} else {
					shape = new RoundRectangle2D.Double(w * 5.0 / 6.0 + x, w * 5.0 / 6.0 + y, width - w * 5.0 / 3.0,
							height - w * 5.0 / 3.0, radius.hr * 2 - w * 5.0 / 6.0, radius.vr * 2 - w * 5.0 / 6.0);
				}
				gc.draw(shape);
			}
		} else {
			final Shape shape;
			if (radius.hr == 0 && radius.vr == 0) {
				shape = new Rectangle2D.Double(w / 2.0 + x, w / 2.0 + y, width - w, height - w);
			} else {
				shape = new RoundRectangle2D.Double(w / 2.0 + x, w / 2.0 + y, width - w, height - w,
						radius.hr * 2 - w / 2.0, radius.vr * 2 - w / 2.0);
			}
			gc.draw(shape);
		}
		gc.end();
	}

	protected void clipTopTrapezium(GC gc, Border border, double w1, double w2, double x, double y, double length)
			throws GraphicsException {
		length -= w1 + w2;
		y += border.width;
		x += w1;
		GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
		path.moveTo((float) x, (float) y);
		path.lineTo((float) (x + length), (float) y);
		path.lineTo((float) (x + length + w2), (float) (y - border.width));
		path.lineTo((float) (x - w1), (float) (y - border.width));
		gc.clip(path);
	}

	protected void clipBottomTrapezium(GC gc, Border border, double w1, double w2, double x, double y, double length)
			throws GraphicsException {
		length -= w1 + w2;
		y -= border.width;
		x += w1;
		GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
		path.moveTo((float) x, (float) y);
		path.lineTo((float) (x + length), (float) y);
		path.lineTo((float) (x + length + w2), (float) (y + border.width));
		path.lineTo((float) (x - w1), (float) (y + border.width));
		gc.clip(path);
	}

	protected void clipLeftTrapezium(GC gc, Border border, double w1, double w2, double x, double y, double length)
			throws GraphicsException {
		length -= w1 + w2;
		x += border.width;
		y += w1;
		GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
		path.moveTo((float) x, (float) y);
		path.lineTo((float) x, (float) (y + length));
		path.lineTo((float) (x - border.width), (float) (y + length + w2));
		path.lineTo((float) (x - border.width), (float) (y - w1));
		gc.clip(path);
	}

	protected void clipRightTrapezium(GC gc, Border border, double w1, double w2, double x, double y, double length)
			throws GraphicsException {
		length -= w1 + w2;
		x -= border.width;
		y += w1;
		GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
		path.moveTo((float) x, (float) y);
		path.lineTo((float) x, (float) (y + length));
		path.lineTo((float) (x + border.width), (float) (y + length + w2));
		path.lineTo((float) (x + border.width), (float) (y - w1));
		gc.clip(path);
	}

	protected void drawHorizontalSimpleLine(GC gc, Border border, double x, double y, double length)
			throws GraphicsException {
		gc.begin();
		gc.setStrokePaint(border.color);
		gc.draw(new Line2D.Double(x, y, x + length, y));
		gc.end();
	}

	protected void drawVerticalSimpleLine(GC gc, Border border, double x, double y, double length)
			throws GraphicsException {
		gc.begin();
		gc.setStrokePaint(border.color);
		gc.draw(new Line2D.Double(x, y, x, y + length));
		gc.end();
	}

	protected void drawHorizontalGrooveLine(GC gc, Border border, double x, double y, double length, boolean ridge)
			throws GraphicsException {
		Color color = border.color;
		double w = border.width / 2.0;
		gc.begin();
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(ridge ? this.darker(color) : this.brighter(color));
		gc.draw(new Line2D.Double(x, y + w / 2.0, x + length, y + w / 2.0));
		gc.setStrokePaint(ridge ? this.brighter(color) : this.darker(color));
		gc.draw(new Line2D.Double(x, y - w / 2.0, x + length, y - w / 2.0));
		gc.end();
	}

	protected void drawVerticalGrooveLine(GC gc, Border border, double x, double y, double length, boolean ridge)
			throws GraphicsException {
		Color color = border.color;
		double w = border.width / 2.0;
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(ridge ? this.darker(color) : this.brighter(color));
		gc.draw(new Line2D.Double(x + w / 2.0, y, x + w / 2.0, y + length));
		gc.setStrokePaint(ridge ? this.brighter(color) : this.darker(color));
		gc.draw(new Line2D.Double(x - w / 2.0, y, x - w / 2.0, y + length));
	}

	protected void drawHorizontalDoubleLine(GC gc, Border border, double x, double y, double length)
			throws GraphicsException {
		Color color = border.color;
		double w = border.width / 3.0;
		gc.begin();
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(color);
		gc.draw(new Line2D.Double(x, y + w, x + length, y + w));
		gc.draw(new Line2D.Double(x, y - w, x + length, y - w));
		gc.end();
	}

	protected void drawVerticalDoubleLine(GC gc, Border border, double x, double y, double length)
			throws GraphicsException {
		Color color = border.color;
		double w = border.width / 3.0;
		gc.begin();
		setStroke(gc, w, BorderRenderer.STROKE_SOLID);
		gc.setStrokePaint(color);
		gc.draw(new Line2D.Double(x + w, y, x + w, y + length));
		gc.draw(new Line2D.Double(x - w, y, x - w, y + length));
		gc.end();
	}

	/**
	 * テーブルのつぶし境界を描画します。
	 * 
	 * @param gc
	 * @param borders
	 * @param x
	 * @param y
	 */
	public void drawTableCollapseBorders(final GC gc, final TableCollapsedBorders borders, final double x,
			final double y, final boolean vertical) throws GraphicsException {
		int cols = borders.getColumnCount();
		int rows = borders.getRowCount();
		class TBorder implements Comparable<Object> {
			final Border border;
			final double x, y, l;
			final boolean v;

			TBorder(Border border, double x, double y, double l, boolean v) {
				this.border = border;
				this.x = x;
				this.y = y;
				this.l = l;
				this.v = v;
			}

			void draw() throws GraphicsException {
				if (this.v) {
					BorderRenderer.this.drawVerticalBorder(gc, this.border, this.x, this.y, this.l);
				} else {
					BorderRenderer.this.drawHorizontalBorder(gc, this.border, this.x, this.y, this.l);
				}
			}

			public int compareTo(Object o) {
				TBorder tb = (TBorder) o;
				return -this.border.compareTo(tb.border);
			}
		}
		List<TBorder> list = new ArrayList<TBorder>();

		// 垂直の線を描画する
		if (vertical) {
			double cy = y;
			for (int index = 0; index <= cols; ++index) {
				double cx = x;
				for (int row = rows - 1; row >= 0; --row) {
					double ch = borders.getRowSize(row);
					Border border = borders.getVBorder(row, index);
					if (border != null && border.isVisible()) {
						double xx = cx;
						double yy = cy;
						double hh = ch;

						{
							Border ul = index == 0 ? null : borders.getHBorder(index - 1, row + 1);
							Border ur = index == cols ? null : borders.getHBorder(index, row + 1);
							double bw = Math.max(ul == null ? 0 : ul.width, ur == null ? 0 : ur.width) / 2.0;
							if (bw != 0) {
								xx -= bw;
								hh += bw;
							}
						}
						{
							Border bl = index == 0 ? null : borders.getHBorder(index - 1, row);
							Border br = index == cols ? null : borders.getHBorder(index, row);
							double bw = Math.max(bl == null ? 0 : bl.width, br == null ? 0 : br.width) / 2.0;
							if (bw != 0) {
								hh += bw;
							}
						}

						switch (border.style) {
						case Border.NONE:
						case Border.HIDDEN:
							break;
						default:
							list.add(new TBorder(border, xx, yy, hh, false));
							break;
						}
					}
					cx += ch;
				}
				if (index != cols) {
					cy += borders.getColumnSize(index);
				}
			}
		} else {
			double cx = x;
			for (int index = 0; index <= cols; ++index) {
				double cy = y;
				for (int row = 0; row < rows; ++row) {
					double ch = borders.getRowSize(row);
					Border border = borders.getVBorder(row, index);
					if (border != null && border.isVisible()) {
						double xx = cx;
						double yy = cy;
						double hh = ch;

						{
							Border ul = index == 0 ? null : borders.getHBorder(index - 1, row);
							Border ur = index == cols ? null : borders.getHBorder(index, row);
							double bw = Math.max(ul == null ? 0 : ul.width, ur == null ? 0 : ur.width) / 2.0;
							if (bw != 0) {
								yy -= bw;
								hh += bw;
							}
						}
						{
							Border bl = index == 0 ? null : borders.getHBorder(index - 1, row + 1);
							Border br = index == cols ? null : borders.getHBorder(index, row + 1);
							double bw = Math.max(bl == null ? 0 : bl.width, br == null ? 0 : br.width) / 2.0;
							if (bw != 0) {
								hh += bw;
							}
						}

						switch (border.style) {
						case Border.NONE:
						case Border.HIDDEN:
							break;
						default:
							list.add(new TBorder(border, xx, yy, hh, true));
							break;
						}
					}
					cy += ch;
				}
				if (index != cols) {
					cx += borders.getColumnSize(index);
				}
			}
		}

		// 水平の線を描画する
		if (vertical) {
			double cx = x;
			for (int index = rows; index >= 0; --index) {
				double cy = y;
				for (int col = 0; col < cols; ++col) {
					double cw = borders.getColumnSize(col);
					Border border = borders.getHBorder(col, index);
					if (border != null && border.isVisible()) {
						double xx = cx;
						double yy = cy;
						double ww = cw;

						{
							Border lu = index == 0 ? null : borders.getVBorder(index - 1, col);
							Border lb = index == rows ? null : borders.getVBorder(index, col);
							double bw = Math.max(lu == null ? 0 : lu.width, lb == null ? 0 : lb.width) / 2.0;
							if (bw != 0) {
								yy -= bw;
								ww += bw;
							}
						}
						{
							Border ru = index == 0 ? null : borders.getVBorder(index - 1, col + 1);
							Border rb = index == rows ? null : borders.getVBorder(index, col + 1);
							double bw = Math.max(ru == null ? 0 : ru.width, rb == null ? 0 : rb.width) / 2.0;
							if (bw != 0) {
								ww += bw;
							}
						}

						switch (border.style) {
						case Border.NONE:
						case Border.HIDDEN:
							break;
						default:
							list.add(new TBorder(border, xx, yy, ww, true));
							break;
						}
					}
					cy += cw;
				}
				if (index > 0) {
					cx += borders.getRowSize(index - 1);
				}
			}
		} else {
			double cy = y;
			for (int index = 0; index <= rows; ++index) {
				double cx = x;
				for (int col = 0; col < cols; ++col) {
					double cw = borders.getColumnSize(col);
					Border border = borders.getHBorder(col, index);
					if (border != null && border.isVisible()) {
						double xx = cx;
						double yy = cy;
						double ww = cw;

						{
							Border lu = index == 0 ? null : borders.getVBorder(index - 1, col);
							Border lb = index == rows ? null : borders.getVBorder(index, col);
							double bw = Math.max(lu == null ? 0 : lu.width, lb == null ? 0 : lb.width) / 2.0;
							if (bw != 0) {
								xx -= bw;
								ww += bw;
							}
						}
						{
							Border ru = index == 0 ? null : borders.getVBorder(index - 1, col + 1);
							Border rb = index == rows ? null : borders.getVBorder(index, col + 1);
							double bw = Math.max(ru == null ? 0 : ru.width, rb == null ? 0 : rb.width) / 2.0;
							if (bw != 0) {
								ww += bw;
							}
						}

						switch (border.style) {
						case Border.NONE:
						case Border.HIDDEN:
							break;
						default:
							list.add(new TBorder(border, xx, yy, ww, false));
							break;
						}
					}
					cx += cw;
				}
				if (index != rows) {
					cy += borders.getRowSize(index);
				}
			}
		}

		// 細い順に描画
		Collections.sort(list);
		for (int i = 0; i < list.size(); ++i) {
			final TBorder tb = (TBorder) list.get(i);
			tb.draw();
		}
	}

	private void drawArc(GC gc, Border border, double x, double y, double w, double h, double start, double extent) {
		switch (border.style) {
		case Border.SOLID:
			gc.setStrokePaint(border.color);
			setStroke(gc, border.width, BorderRenderer.STROKE_SOLID);
			break;

		case Border.DASHED:
			gc.setStrokePaint(border.color);
			setStroke(gc, border.width, BorderRenderer.STROKE_DASHED);
			break;

		case Border.DOTTED:
			gc.setStrokePaint(border.color);
			setStroke(gc, border.width, BorderRenderer.STROKE_DOTTED);
			break;

		case Border.DOUBLE:
			gc.setStrokePaint(border.color);
			gc.setLineWidth(border.width / 3.0); {
			final Arc2D arc = new Arc2D.Double(x - border.width / 3.0, y - border.width / 3.0,
					w + border.width * 2 / 3.0, h + border.width * 2 / 3.0, start - 1, extent + 2, Arc2D.OPEN);
			gc.draw(arc);
		} {
			final Arc2D arc = new Arc2D.Double(x + border.width / 3.0, y + border.width / 3.0,
					w - border.width * 2 / 3.0, h - border.width * 2 / 3.0, start - 1, extent + 2, Arc2D.OPEN);
			gc.draw(arc);
		}
			return;

		case Border.RIDGE:
			gc.setLineWidth(border.width / 2.0); {
			if (start >= 45 && start <= 180) {
				gc.setStrokePaint(this.brighter(border.color));
			} else {
				gc.setStrokePaint(this.darker(border.color));
			}
			final Arc2D arc = new Arc2D.Double(x - border.width / 4.0, y - border.width / 4.0, w + border.width / 2.0,
					h + border.width / 2.0, start - 1, extent + 2, Arc2D.OPEN);
			gc.draw(arc);
		} {
			if (start >= 45 && start <= 180) {
				gc.setStrokePaint(this.darker(border.color));
			} else {
				gc.setStrokePaint(this.brighter(border.color));
			}
			final Arc2D arc = new Arc2D.Double(x + border.width / 4.0, y + border.width / 4.0, w - border.width / 2.0,
					h - border.width / 2.0, start - 1, extent + 2, Arc2D.OPEN);
			gc.draw(arc);
		}
			return;

		case Border.GROOVE:
			gc.setLineWidth(border.width / 2.0); {
			if (start >= 45 && start <= 180) {
				gc.setStrokePaint(this.darker(border.color));
			} else {
				gc.setStrokePaint(this.brighter(border.color));
			}
			final Arc2D arc = new Arc2D.Double(x - border.width / 4.0, y - border.width / 4.0, w + border.width / 2.0,
					h + border.width / 2.0, start - 1, extent + 2, Arc2D.OPEN);
			gc.draw(arc);
		} {
			if (start >= 45 && start <= 180) {
				gc.setStrokePaint(this.brighter(border.color));
			} else {
				gc.setStrokePaint(this.darker(border.color));
			}
			final Arc2D arc = new Arc2D.Double(x + border.width / 4.0, y + border.width / 4.0, w - border.width / 2.0,
					h - border.width / 2.0, start - 1, extent + 2, Arc2D.OPEN);
			gc.draw(arc);
		}
			return;

		case Border.OUTSET:
			if (start >= 45 && start <= 180) {
				gc.setStrokePaint(this.brighter(border.color));
			} else {
				gc.setStrokePaint(this.darker(border.color));
			}
			gc.setLineWidth(border.width);
			break;

		case Border.INSET:
			if (start >= 45 && start <= 180) {
				gc.setStrokePaint(this.darker(border.color));
			} else {
				gc.setStrokePaint(this.brighter(border.color));
			}
			gc.setLineWidth(border.width);
			break;

		default:
			gc.setLineWidth(border.width);
			break;
		}
		final Arc2D arc = new Arc2D.Double(x, y, w, h, start - 1, extent + 2, Arc2D.OPEN);
		gc.draw(arc);
	}

	/**
	 * 水平線を描画します。
	 * 
	 * @param gc
	 * @param border
	 * @param x
	 * @param y
	 * @param l
	 */
	public void drawHorizontalBorder(GC gc, Border border, double x, double y, double l) {
		switch (border.style) {
		case Border.DOUBLE:
			BorderRenderer.this.drawHorizontalDoubleLine(gc, border, x, y, l);
			break;

		case Border.SOLID:
			gc.begin();
			setStroke(gc, border.width, BorderRenderer.STROKE_SOLID);
			BorderRenderer.this.drawHorizontalSimpleLine(gc, border, x, y, l);
			gc.end();
			break;

		case Border.DASHED:
			gc.begin();
			setStroke(gc, border.width, BorderRenderer.STROKE_DASHED);
			BorderRenderer.this.drawHorizontalSimpleLine(gc, border, x, y, l);
			gc.end();
			break;

		case Border.DOTTED:
			gc.begin();
			setStroke(gc, border.width, BorderRenderer.STROKE_DOTTED);
			BorderRenderer.this.drawHorizontalSimpleLine(gc, border, x, y, l);
			gc.end();
			break;

		case Border.RIDGE:
		case Border.OUTSET:
			BorderRenderer.this.drawHorizontalGrooveLine(gc, border, x, y, l, true);
			break;

		case Border.GROOVE:
		case Border.INSET:
			BorderRenderer.this.drawHorizontalGrooveLine(gc, border, x, y, l, false);
			break;

		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * 垂直線を描画します。
	 * 
	 * @param gc
	 * @param border
	 * @param x
	 * @param y
	 * @param l
	 */
	public void drawVerticalBorder(GC gc, Border border, double x, double y, double l) {
		switch (border.style) {
		case Border.DOUBLE:
			BorderRenderer.this.drawVerticalDoubleLine(gc, border, x, y, l);
			break;

		case Border.SOLID:
			gc.begin();
			setStroke(gc, border.width, BorderRenderer.STROKE_SOLID);
			BorderRenderer.this.drawVerticalSimpleLine(gc, border, x, y, l);
			gc.end();
			break;

		case Border.DASHED:
			gc.begin();
			setStroke(gc, border.width, BorderRenderer.STROKE_DASHED);
			BorderRenderer.this.drawVerticalSimpleLine(gc, border, x, y, l);
			gc.end();
			break;

		case Border.DOTTED:
			gc.begin();
			setStroke(gc, border.width, BorderRenderer.STROKE_DOTTED);
			BorderRenderer.this.drawVerticalSimpleLine(gc, border, x, y, l);
			gc.end();
			break;

		case Border.RIDGE:
		case Border.OUTSET:
			BorderRenderer.this.drawVerticalGrooveLine(gc, border, x, y, l, true);
			break;

		case Border.GROOVE:
		case Border.INSET:
			BorderRenderer.this.drawVerticalGrooveLine(gc, border, x, y, l, false);
			break;

		default:
			throw new IllegalStateException();
		}
	}

	public Shape getBorderShape(RectBorder border, double x, double y, double w, double h) {
		final Radius topLeft = border.getTopLeft();
		final Radius topRight = border.getTopRight();
		final Radius bottomLeft = border.getBottomLeft();
		final Radius bottomRight = border.getBottomRight();

		/* NoAndroid begin */
		if (topLeft == Radius.ZERO_RADIUS && topRight == Radius.ZERO_RADIUS && bottomLeft == Radius.ZERO_RADIUS
				&& bottomRight == Radius.ZERO_RADIUS) {
			// ただの矩形
			return new Rectangle2D.Double(x, y, w, h);
		}
		if (topLeft.equals(topRight) && topLeft.equals(bottomLeft) && topLeft.equals(bottomRight)) {
			// ただの角丸矩形
			Radius radius = topLeft;
			return new RoundRectangle2D.Double(x, y, w, h, radius.hr * 2, radius.vr * 2);
		}
		/* NoAndroid end */

		final double topLeftHr = Math.min(w / 2, topLeft.hr);
		final double topLeftVr = Math.min(h / 2, topLeft.vr);
		final double topRightHr = Math.min(w / 2, topRight.hr);
		final double topRightVr = Math.min(h / 2, topRight.vr);
		final double bottomLeftHr = Math.min(w / 2, bottomLeft.hr);
		final double bottomLeftVr = Math.min(h / 2, bottomLeft.vr);
		final double bottomRightHr = Math.min(w / 2, bottomRight.hr);
		final double bottomRightVr = Math.min(h / 2, bottomRight.vr);

		GeneralPath path = new GeneralPath();

		// 左境界
		path.moveTo((float) x, (float) (y + topLeftVr));
		path.lineTo((float) x, (float) (y + h - bottomLeftVr));
		if (bottomLeftHr != 0 || bottomLeftVr != 0) {
			Arc2D arc = new Arc2D.Double(x, y + h - bottomLeftVr * 2, bottomLeftHr * 2, bottomLeftVr * 2, 180, 45,
					Arc2D.OPEN);
			path.append(arc, true);
		}

		// 下境界
		if (bottomLeftHr != 0 || bottomLeftVr != 0) {
			Arc2D arc = new Arc2D.Double(x, y + h - bottomLeftVr * 2, bottomLeftHr * 2, bottomLeftVr * 2, 225, 45,
					Arc2D.OPEN);
			path.append(arc, true);
		}
		path.lineTo((float) (x + w - bottomRightHr), (float) (y + h));
		if (bottomRightHr != 0 || bottomRightVr != 0) {
			Arc2D arc = new Arc2D.Double(x + w - bottomRightHr * 2, y + h - bottomRightVr * 2, bottomRightHr * 2,
					bottomRightVr * 2, 270, 45, Arc2D.OPEN);
			path.append(arc, true);
		}

		// 右境界
		if (bottomRightHr != 0 || bottomRightVr != 0) {
			Arc2D arc = new Arc2D.Double(x + w - bottomRightHr * 2, y + h - bottomRightVr * 2, bottomRightHr * 2,
					bottomRightVr * 2, 315, 45, Arc2D.OPEN);
			path.append(arc, true);
		}
		path.lineTo((float) (x + w), (float) (y + topRightVr));
		if (topRightHr != 0 || topRightVr != 0) {
			Arc2D arc = new Arc2D.Double(x + w - topRightHr * 2, y, topRightHr * 2, topRightVr * 2, 0, 45, Arc2D.OPEN);
			path.append(arc, true);
		}

		// 上境界
		if (topRightHr != 0 || topRightVr != 0) {
			Arc2D arc = new Arc2D.Double(x + w - topRightHr * 2, y, topRightHr * 2, topRightVr * 2, 45, 45, Arc2D.OPEN);
			path.append(arc, true);
		}
		path.lineTo((float) (x + topLeftHr), (float) y);
		if (topLeftHr != 0 || topLeftVr != 0) {
			Arc2D arc = new Arc2D.Double(x, y, topLeftHr * 2, topLeftVr * 2, 90, 45, Arc2D.OPEN);
			path.append(arc, true);
		}

		if (topLeftHr != 0 || topLeftVr != 0) {
			Arc2D arc = new Arc2D.Double(x, y, topLeftHr * 2, topLeftVr * 2, 135, 45, Arc2D.OPEN);
			path.append(arc, true);
		}
		path.closePath();
		return path;
	}

	/**
	 * ボックスの境界を描画します。
	 * 
	 * @param gc
	 * @param border
	 * @param x
	 * @param y
	 */
	public void drawRectBorder(GC gc, RectBorder border, double x, double y, double w, double h)
			throws GraphicsException {
		final Border left = border.getLeft();
		final Border top = border.getTop();
		final Border right = border.getRight();
		final Border bottom = border.getBottom();

		final Radius topLeft = border.getTopLeft();
		final Radius topRight = border.getTopRight();
		final Radius bottomLeft = border.getBottomLeft();
		final Radius bottomRight = border.getBottomRight();

		final double topLeftHr = Math.min(w / 2, topLeft.hr);
		final double topLeftVr = Math.min(h / 2, topLeft.vr);
		final double topRightHr = Math.min(w / 2, topRight.hr);
		final double topRightVr = Math.min(h / 2, topRight.vr);
		final double bottomLeftHr = Math.min(w / 2, bottomLeft.hr);
		final double bottomLeftVr = Math.min(h / 2, bottomLeft.vr);
		final double bottomRightHr = Math.min(w / 2, bottomRight.hr);
		final double bottomRightVr = Math.min(h / 2, bottomRight.vr);

		if (left.isVisible()) {
			// 全ての境界のスタイルが同じかどうかの判断
			if (left.style == Border.SOLID || left.style == Border.DOTTED || left.style == Border.DASHED
					|| left.style == Border.DOUBLE) {
				if (left.equals(top) && left.equals(right) && left.equals(bottom) && topLeft.equals(topRight)
						&& topLeft.equals(bottomLeft) && topLeft.equals(bottomRight)) {
					// 全て同じであれば単純に四角を描く
					this.drawRect(gc, left, topLeft, x, y, w, h);
					return;
				}
			}

			if (left.style >= Border.DOUBLE) {
				// 左境界
				gc.begin();
				final boolean tr = (topLeftHr != 0 || topLeftVr != 0);
				final boolean br = (bottomLeftHr != 0 || bottomLeftVr != 0);
				if (tr) {
					drawArc(gc, left, x + left.width / 2, y + top.width / 2, topLeftHr * 2, topLeftVr * 2, 135, 45);
				}
				if (br) {
					drawArc(gc, left, x + left.width / 2, y + h - bottomLeftVr * 2 - bottom.width / 2, bottomLeftHr * 2,
							bottomLeftVr * 2, 180, 45);
				}

				if (left.width > 1) {
					this.clipLeftTrapezium(gc, left, tr ? 0 : top.width, br ? 0 : bottom.width, x,
							y + (tr ? top.width / 2 : 0) + topLeftVr,
							h - (tr ? top.width / 2 : 0) - (br ? bottom.width / 2 : 0) - topLeftVr - bottomLeftVr);
				}
				this.drawLeftBorder(gc, this, border, x, y + topLeftVr, w, h - topLeftVr - bottomLeftVr);
				gc.end();
			}
		}
		if (top.isVisible() && top.style >= Border.DOUBLE) {
			// 上境界
			gc.begin();
			final boolean lr = (topLeftHr != 0 || topLeftVr != 0);
			final boolean rr = (topRightHr != 0 || topRightVr != 0);
			if (lr) {
				drawArc(gc, top, x + left.width / 2, y + top.width / 2, topLeftHr * 2, topLeftVr * 2, 90, 45);
			}
			if (rr) {
				drawArc(gc, top, x + w - topRightHr * 2 - right.width / 2, y + top.width / 2, topRightHr * 2,
						topRightVr * 2, 45, 45);
			}

			if (top.width > 1) {
				this.clipTopTrapezium(gc, top, lr ? 0 : left.width, rr ? 0 : right.width,
						x + (lr ? left.width / 2 : 0) + topLeftHr, y,
						w - (lr ? left.width / 2 : 0) - (rr ? right.width / 2 : 0) - topLeftHr - topRightHr);
			}
			this.drawTopBorder(gc, this, border, x + topLeftHr, y, w - topLeftHr - topRightHr, h);
			gc.end();
		}
		if (right.isVisible() && right.style >= Border.DOUBLE) {
			// 右境界
			gc.begin();
			final boolean tr = (topRightHr != 0 || topRightVr != 0);
			final boolean br = (bottomRightHr != 0 || bottomRightVr != 0);
			if (tr) {
				drawArc(gc, right, x + w - topRightHr * 2 - right.width / 2, y + top.width / 2, topRightHr * 2,
						topRightVr * 2, 0, 45);
			}
			if (br) {
				drawArc(gc, right, x + w - bottomRightHr * 2 - right.width / 2,
						y + h - bottomRightVr * 2 - bottom.width / 2, bottomRightHr * 2, bottomRightVr * 2, 315, 45);
			}

			if (right.width > 1) {
				this.clipRightTrapezium(gc, right, tr ? 0 : top.width, br ? 0 : bottom.width, x + w,
						y + (tr ? top.width / 2 : 0) + topRightVr,
						h - (tr ? top.width / 2 : 0) - (br ? bottom.width / 2 : 0) - topRightVr - bottomRightVr);
			}
			this.drawRightBorder(gc, this, border, x, y + topRightVr, w, h - topRightVr - bottomRightVr);
			gc.end();
		}
		if (bottom.isVisible() && bottom.style >= Border.DOUBLE) {
			// 下境界
			gc.begin();
			final boolean lr = (bottomLeftHr != 0 || bottomLeftVr != 0);
			final boolean rr = (bottomRightHr != 0 || bottomRightVr != 0);
			if (lr) {
				drawArc(gc, bottom, x + left.width / 2, y + h - bottomLeftVr * 2 - bottom.width / 2, bottomLeftHr * 2,
						bottomLeftVr * 2, 225, 45);
			}
			if (rr) {
				drawArc(gc, bottom, x + w - bottomRightHr * 2 - right.width / 2,
						y + h - bottomRightVr * 2 - bottom.width / 2, bottomRightHr * 2, bottomRightVr * 2, 270, 45);
			}

			if (bottom.width > 1) {
				this.clipBottomTrapezium(gc, bottom, lr ? 0 : left.width, rr ? 0 : right.width,
						x + (lr ? left.width / 2 : 0) + bottomLeftHr, y + h,
						w - (lr ? left.width / 2 : 0) - (rr ? right.width / 2 : 0) - bottomLeftHr - bottomRightHr);
			}
			this.drawBottomBorder(gc, this, border, x + bottomLeftHr, y, w - bottomLeftHr - bottomRightHr, h);
			gc.end();
		}
	}

	private void drawLeftBorder(GC gc, BorderRenderer renderer, RectBorder border, double x, double y, double w,
			double h) {
		switch (border.getLeft().style) {
		case Border.DOUBLE:
			renderer.drawLeftDoubleLine(gc, border, x, y, w, h);
			break;

		case Border.SOLID:
			Border left = border.getLeft();
			gc.begin();
			setStroke(gc, left.width, BorderRenderer.STROKE_SOLID);
			renderer.drawLeftSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DASHED:
			gc.begin();
			setStroke(gc, border.getLeft().width, BorderRenderer.STROKE_DASHED);
			renderer.drawLeftSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DOTTED:
			gc.begin();
			setStroke(gc, border.getLeft().width, BorderRenderer.STROKE_DOTTED);
			renderer.drawLeftSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.RIDGE:
			renderer.drawLeftGrooveLine(gc, border, true, x, y, w, h);
			break;

		case Border.OUTSET:
			renderer.drawLeftInsetLine(gc, border, true, x, y, w, h);
			break;

		case Border.GROOVE:
			renderer.drawLeftGrooveLine(gc, border, false, x, y, w, h);
			break;

		case Border.INSET:
			renderer.drawLeftInsetLine(gc, border, false, x, y, w, h);
			break;

		default:
			throw new IllegalStateException();
		}
	}

	private void drawTopBorder(GC gc, BorderRenderer renderer, RectBorder border, double x, double y, double w,
			double h) {
		switch (border.getTop().style) {
		case Border.DOUBLE:
			renderer.drawTopDoubleLine(gc, border, x, y, w, h);
			break;

		case Border.SOLID:
			Border top = border.getTop();
			gc.begin();
			setStroke(gc, top.width, BorderRenderer.STROKE_SOLID);
			renderer.drawTopSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DASHED:
			gc.begin();
			setStroke(gc, border.getTop().width, BorderRenderer.STROKE_DASHED);
			renderer.drawTopSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DOTTED:
			gc.begin();
			setStroke(gc, border.getTop().width, BorderRenderer.STROKE_DOTTED);
			renderer.drawTopSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.RIDGE:
			renderer.drawTopGrooveLine(gc, border, true, x, y, w, h);
			break;

		case Border.OUTSET:
			renderer.drawTopInsetLine(gc, border, true, x, y, w, h);
			break;

		case Border.GROOVE:
			renderer.drawTopGrooveLine(gc, border, false, x, y, w, h);
			break;

		case Border.INSET:
			renderer.drawTopInsetLine(gc, border, false, x, y, w, h);
			break;

		default:
			throw new IllegalStateException();
		}
	}

	private void drawRightBorder(GC gc, BorderRenderer renderer, RectBorder border, double x, double y, double w,
			double h) {
		switch (border.getRight().style) {
		case Border.DOUBLE:
			renderer.drawRightDoubleLine(gc, border, x, y, w, h);
			break;

		case Border.SOLID:
			Border right = border.getRight();
			gc.begin();
			setStroke(gc, right.width, BorderRenderer.STROKE_SOLID);
			renderer.drawRightSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DASHED:
			gc.begin();
			setStroke(gc, border.getRight().width, BorderRenderer.STROKE_DASHED);
			renderer.drawRightSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DOTTED:
			gc.begin();
			setStroke(gc, border.getRight().width, BorderRenderer.STROKE_DOTTED);
			renderer.drawRightSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.RIDGE:
			renderer.drawRightGrooveLine(gc, border, true, x, y, w, h);
			break;

		case Border.OUTSET:
			renderer.drawRightInsetLine(gc, border, true, x, y, w, h);
			break;

		case Border.GROOVE:
			renderer.drawRightGrooveLine(gc, border, false, x, y, w, h);
			break;

		case Border.INSET:
			renderer.drawRightInsetLine(gc, border, false, x, y, w, h);
			break;

		default:
			throw new IllegalStateException();
		}
	}

	private void drawBottomBorder(GC gc, BorderRenderer renderer, RectBorder border, double x, double y, double w,
			double h) {
		switch (border.getBottom().style) {
		case Border.DOUBLE:
			renderer.drawBottomDoubleLine(gc, border, x, y, w, h);
			break;

		case Border.SOLID:
			Border bottom = border.getBottom();
			gc.begin();
			setStroke(gc, bottom.width, BorderRenderer.STROKE_SOLID);
			renderer.drawBottomSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DASHED:
			gc.begin();
			setStroke(gc, border.getBottom().width, BorderRenderer.STROKE_DASHED);
			renderer.drawBottomSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.DOTTED:
			gc.begin();
			setStroke(gc, border.getBottom().width, BorderRenderer.STROKE_DOTTED);
			renderer.drawBottomSimpleLine(gc, border, x, y, w, h);
			gc.end();
			break;

		case Border.RIDGE:
			renderer.drawBottomGrooveLine(gc, border, true, x, y, w, h);
			break;

		case Border.OUTSET:
			renderer.drawBottomInsetLine(gc, border, true, x, y, w, h);
			break;

		case Border.GROOVE:
			renderer.drawBottomGrooveLine(gc, border, false, x, y, w, h);
			break;

		case Border.INSET:
			renderer.drawBottomInsetLine(gc, border, false, x, y, w, h);
			break;

		default:
			throw new IllegalStateException();
		}
	}
}
