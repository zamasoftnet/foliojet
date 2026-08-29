package net.zamasoft.foliojet.layout.util;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * 全ての操作を{@link #delegate()}へ素通しする{@link GC}の土台です
 * (2026-08-29)。包み紙は必要な操作だけを上書きする。
 *
 * <p>
 * 出力先の能力({@link #supports})・ぼかし塗り・効果付き画像描画・
 * ブレンドモード・artifactスコープも必ず委譲する——どれか1つでも既定
 * (何もしない)へ落ちると、包んだ途端に厳密経路が近似へ化ける。
 * </p>
 */
public abstract class AbstractDelegatingGC implements GC, DelegatingGC {
	protected final GC gc;

	protected AbstractDelegatingGC(final GC gc) {
		this.gc = gc;
	}

	@Override
	public GC delegate() {
		return this.gc;
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
		this.gc.setStrokePaint(paint);
	}

	@Override
	public Paint getStrokePaint() {
		return this.gc.getStrokePaint();
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		this.gc.setFillPaint(paint);
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
	public boolean supports(final Capability capability) {
		return this.gc.supports(capability);
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
	public void fillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		this.gc.fillBlurred(shape, sigma);
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		this.gc.drawImage(image);
	}

	@Override
	public void drawImage(final Image image, final GroupEffects effects) throws GraphicsException {
		this.gc.drawImage(image, effects);
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		this.gc.drawText(text, x, y);
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return this.gc.createGroupImage(width, height);
	}
}
