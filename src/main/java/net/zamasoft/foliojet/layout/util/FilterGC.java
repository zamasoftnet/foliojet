package net.zamasoft.foliojet.layout.util;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * {@code filter}の色行列・ぼかしを、描画命令の途中で塗りと画像に
 * 掛ける{@link GC}の包み紙です(2026-08-29新設)。
 *
 * <p>
 * 描画要素(背景・境界・文字・画像)は塗りを{@link #setFillPaint}/
 * {@link #setStrokePaint}で設定し、画像を{@link #drawImage}で描く。
 * この2箇所で{@link FilterOps}を通せば、描画要素の実装を一切触らずに
 * 効果が掛かる。文字の色も塗りなので同じ経路で変わる。
 * </p>
 *
 * <p>
 * {@link #createGroupImage}で作った子のGC(入れ子の不透明度)も包み、
 * 子の描画にも効果が届くようにする。それ以外は素通し。
 * </p>
 */
public final class FilterGC implements GC {
	private final GC gc;
	private final FilterValue filter;

	public FilterGC(final GC gc, final FilterValue filter) {
		this.gc = gc;
		this.filter = filter;
	}

	/** 現在の変換での1画素あたりのpt(ぼかしの換算用)。 */
	private double pixelScale() {
		final AffineTransform at = this.gc.getTransform();
		if (at == null) {
			return 1;
		}
		final double det = Math.abs(at.getDeterminant());
		return det > 0 ? Math.sqrt(det) : 1;
	}

	@Override
	public FontManager getFontManager() {
		return this.gc.getFontManager();
	}

	@Override
	public State begin() throws GraphicsException {
		return this.gc.begin();
	}

	@Override
	public State beginArtifactScope() throws GraphicsException {
		return this.gc.beginArtifactScope();
	}

	@Override
	public void resetState() throws GraphicsException {
		this.gc.resetState();
	}

	@Override
	public void setStrokePaint(final Paint paint) throws GraphicsException {
		this.gc.setStrokePaint(FilterOps.apply(this.filter, paint, this.pixelScale()));
	}

	@Override
	public Paint getStrokePaint() {
		return this.gc.getStrokePaint();
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		this.gc.setFillPaint(FilterOps.apply(this.filter, paint, this.pixelScale()));
	}

	@Override
	public Paint getFillPaint() {
		return this.gc.getFillPaint();
	}

	@Override
	public float getStrokeAlpha() {
		return this.gc.getStrokeAlpha();
	}

	@Override
	public void setStrokeAlpha(final float strokeAlpha) throws GraphicsException {
		this.gc.setStrokeAlpha(strokeAlpha);
	}

	@Override
	public float getFillAlpha() {
		return this.gc.getFillAlpha();
	}

	@Override
	public void setFillAlpha(final float fillAlpha) throws GraphicsException {
		this.gc.setFillAlpha(fillAlpha);
	}

	@Override
	public void setBlendMode(final BlendMode mode) throws GraphicsException {
		this.gc.setBlendMode(mode);
	}

	@Override
	public BlendMode getBlendMode() {
		return this.gc.getBlendMode();
	}

	@Override
	public void setLineWidth(final double width) throws GraphicsException {
		this.gc.setLineWidth(width);
	}

	@Override
	public double getLineWidth() {
		return this.gc.getLineWidth();
	}

	@Override
	public void setLinePattern(final double[] pattern) throws GraphicsException {
		this.gc.setLinePattern(pattern);
	}

	@Override
	public double[] getLinePattern() {
		return this.gc.getLinePattern();
	}

	@Override
	public void setLineJoin(final LineJoin style) throws GraphicsException {
		this.gc.setLineJoin(style);
	}

	@Override
	public LineJoin getLineJoin() {
		return this.gc.getLineJoin();
	}

	@Override
	public void setLineCap(final LineCap style) throws GraphicsException {
		this.gc.setLineCap(style);
	}

	@Override
	public LineCap getLineCap() {
		return this.gc.getLineCap();
	}

	@Override
	public void setTextMode(final TextMode textMode) throws GraphicsException {
		this.gc.setTextMode(textMode);
	}

	@Override
	public TextMode getTextMode() {
		return this.gc.getTextMode();
	}

	@Override
	public void transform(final AffineTransform at) throws GraphicsException {
		this.gc.transform(at);
	}

	@Override
	public AffineTransform getTransform() {
		return this.gc.getTransform();
	}

	@Override
	public void clip(final Shape shape) throws GraphicsException {
		this.gc.clip(shape);
	}

	@Override
	public void draw(final Shape shape) throws GraphicsException {
		this.gc.draw(shape);
	}

	@Override
	public void fill(final Shape shape) throws GraphicsException {
		this.gc.fill(shape);
	}

	@Override
	public void fillDraw(final Shape shape) throws GraphicsException {
		this.gc.fillDraw(shape);
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		final Image f = FilterOps.apply(this.filter, image, this.pixelScale());
		this.gc.drawImage(f);
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		this.gc.drawText(text, x, y);
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createGroupImage(width, height), this.filter);
	}

	/** 入れ子のグループにも効果を届ける包み紙。 */
	private static final class Group extends WrapperGroup implements GroupImageGC {
		private final GroupImageGC group;

		Group(final GroupImageGC group, final FilterValue filter) {
			super(new FilterGC(group, filter));
			this.group = group;
		}

		@Override
		public Image finish() throws GraphicsException {
			return this.group.finish();
		}
	}

	/** {@link FilterGC}へ全て委譲する土台(GroupImageGCの実装用)。 */
	private static abstract class WrapperGroup implements GC {
		private final FilterGC inner;

		WrapperGroup(final FilterGC inner) {
			this.inner = inner;
		}

		public FontManager getFontManager() {
			return this.inner.getFontManager();
		}

		public State begin() throws GraphicsException {
			return this.inner.begin();
		}

		public State beginArtifactScope() throws GraphicsException {
			return this.inner.beginArtifactScope();
		}

		public void resetState() throws GraphicsException {
			this.inner.resetState();
		}

		public void setStrokePaint(final Paint paint) throws GraphicsException {
			this.inner.setStrokePaint(paint);
		}

		public Paint getStrokePaint() {
			return this.inner.getStrokePaint();
		}

		public void setFillPaint(final Paint paint) throws GraphicsException {
			this.inner.setFillPaint(paint);
		}

		public Paint getFillPaint() {
			return this.inner.getFillPaint();
		}

		public float getStrokeAlpha() {
			return this.inner.getStrokeAlpha();
		}

		public void setStrokeAlpha(final float strokeAlpha) throws GraphicsException {
			this.inner.setStrokeAlpha(strokeAlpha);
		}

		public float getFillAlpha() {
			return this.inner.getFillAlpha();
		}

		public void setFillAlpha(final float fillAlpha) throws GraphicsException {
			this.inner.setFillAlpha(fillAlpha);
		}

		public void setBlendMode(final BlendMode mode) throws GraphicsException {
			this.inner.setBlendMode(mode);
		}

		public BlendMode getBlendMode() {
			return this.inner.getBlendMode();
		}

		public void setLineWidth(final double width) throws GraphicsException {
			this.inner.setLineWidth(width);
		}

		public double getLineWidth() {
			return this.inner.getLineWidth();
		}

		public void setLinePattern(final double[] pattern) throws GraphicsException {
			this.inner.setLinePattern(pattern);
		}

		public double[] getLinePattern() {
			return this.inner.getLinePattern();
		}

		public void setLineJoin(final LineJoin style) throws GraphicsException {
			this.inner.setLineJoin(style);
		}

		public LineJoin getLineJoin() {
			return this.inner.getLineJoin();
		}

		public void setLineCap(final LineCap style) throws GraphicsException {
			this.inner.setLineCap(style);
		}

		public LineCap getLineCap() {
			return this.inner.getLineCap();
		}

		public void setTextMode(final TextMode textMode) throws GraphicsException {
			this.inner.setTextMode(textMode);
		}

		public TextMode getTextMode() {
			return this.inner.getTextMode();
		}

		public void transform(final AffineTransform at) throws GraphicsException {
			this.inner.transform(at);
		}

		public AffineTransform getTransform() {
			return this.inner.getTransform();
		}

		public void clip(final Shape shape) throws GraphicsException {
			this.inner.clip(shape);
		}

		public void draw(final Shape shape) throws GraphicsException {
			this.inner.draw(shape);
		}

		public void fill(final Shape shape) throws GraphicsException {
			this.inner.fill(shape);
		}

		public void fillDraw(final Shape shape) throws GraphicsException {
			this.inner.fillDraw(shape);
		}

		public void drawImage(final Image image) throws GraphicsException {
			this.inner.drawImage(image);
		}

		public void drawText(final Text text, final double x, final double y) throws GraphicsException {
			this.inner.drawText(text, x, y);
		}

		public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
			return this.inner.createGroupImage(width, height);
		}
	}
}
