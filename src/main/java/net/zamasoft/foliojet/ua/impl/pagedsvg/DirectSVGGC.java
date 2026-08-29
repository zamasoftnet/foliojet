package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * SVGを<b>直に書き出す</b>グラフィックスコンテキストです。
 *
 * <p>
 * 従来のPaged SVGはBatikの{@code SVGGraphics2D}を通しており、描画のたびに
 * DOMノードを作って最後に直列化していました。ここではDOMを作らず、
 * 描画が来た順に{@link SVGWriter}へ流します。溜めるのは
 * <b>{@code defs}に入るもの(クリップ経路・グラデーション・{@code @font-face})だけ</b>で、
 * それらは先頭に予約したフラグメントへページを閉じるときに書きます。
 * </p>
 *
 * <p>
 * <b>状態の持ち方。</b>{@link #begin()}で状態を積み、{@code close()}で戻します。
 * SVGでは状態そのものを積む仕組みが無いので、変換・クリップ・不透明度など
 * <b>要素へ出す必要のあるものが変わったときだけ</b>{@code <g>}を開きます。
 * 何も変わらなければ要素を作りません——1ページに数万個の空の{@code <g>}が
 * 出るのを避けるためです。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
class DirectSVGGC implements GC {
	/** 積まれた状態1つ。 */
	private static final class Frame implements State {
		private final DirectSVGGC gc;
		private final AffineTransform transform;
		private final Paint fillPaint, strokePaint;
		private final float fillAlpha, strokeAlpha;
		private final net.zamasoft.pdfg2d.gc.paint.BlendMode blendMode;
		private final double lineWidth;
		private final double[] linePattern;
		private final LineJoin lineJoin;
		private final LineCap lineCap;
		private final TextMode textMode;
		/** この状態で開いた{@code <g>}の数。閉じるときに同じ数だけ閉じる。 */
		private int openGroups;

		Frame(final DirectSVGGC gc) {
			this.gc = gc;
			this.transform = new AffineTransform(gc.transform);
			this.fillPaint = gc.fillPaint;
			this.strokePaint = gc.strokePaint;
			this.fillAlpha = gc.fillAlpha;
			this.strokeAlpha = gc.strokeAlpha;
			this.blendMode = gc.blendMode;
			this.lineWidth = gc.lineWidth;
			this.linePattern = gc.linePattern;
			this.lineJoin = gc.lineJoin;
			this.lineCap = gc.lineCap;
			this.textMode = gc.textMode;
		}

		@Override
		public void close() throws GraphicsException {
			try {
				for (int i = 0; i < this.openGroups; ++i) {
					this.gc.writer.end("g");
				}
			} catch (final IOException e) {
				throw new GraphicsException(e);
			}
			this.gc.transform.setTransform(this.transform);
			this.gc.fillPaint = this.fillPaint;
			this.gc.strokePaint = this.strokePaint;
			this.gc.fillAlpha = this.fillAlpha;
			this.gc.strokeAlpha = this.strokeAlpha;
			this.gc.blendMode = this.blendMode;
			this.gc.lineWidth = this.lineWidth;
			this.gc.linePattern = this.linePattern;
			this.gc.lineJoin = this.lineJoin;
			this.gc.lineCap = this.lineCap;
			this.gc.textMode = this.textMode;
			this.gc.frames.pop();
		}
	}

	protected final SVGWriter writer;
	private final SVGPaintWriter paints;
	private final FontManager fontManager;
	private final Deque<Frame> frames = new ArrayDeque<>();

	private final AffineTransform transform = new AffineTransform();
	private Paint fillPaint = net.zamasoft.pdfg2d.gc.paint.RGBColor.BLACK;
	private Paint strokePaint = net.zamasoft.pdfg2d.gc.paint.RGBColor.BLACK;
	private float fillAlpha = 1f, strokeAlpha = 1f;
	/** mix-blend-mode(2026-08-29)。各要素のstyle属性で出す。 */
	private net.zamasoft.pdfg2d.gc.paint.BlendMode blendMode = net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL;
	private double lineWidth = 1.0;
	private double[] linePattern = null;
	private LineJoin lineJoin = LineJoin.MITER;
	private LineCap lineCap = LineCap.BUTT;
	private TextMode textMode = TextMode.FILL;

	/**
	 * 変換が最後に{@code <g>}へ出た時点の値。これと現在値が違うときだけ
	 * 新しい{@code <g transform=...>}を開きます。
	 */
	private final AffineTransform emittedTransform = new AffineTransform();

	DirectSVGGC(final SVGWriter writer, final FontManager fontManager) {
		this.writer = writer;
		this.paints = new SVGPaintWriter(writer);
		this.fontManager = fontManager;
	}

	@Override
	public FontManager getFontManager() {
		return this.fontManager;
	}

	@Override
	public State begin() throws GraphicsException {
		final Frame frame = new Frame(this);
		this.frames.push(frame);
		return frame;
	}

	@Override
	public void resetState() throws GraphicsException {
		this.transform.setToIdentity();
		this.fillPaint = net.zamasoft.pdfg2d.gc.paint.RGBColor.BLACK;
		this.strokePaint = net.zamasoft.pdfg2d.gc.paint.RGBColor.BLACK;
		this.fillAlpha = this.strokeAlpha = 1f;
		this.blendMode = net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL;
		this.lineWidth = 1.0;
		this.linePattern = null;
		this.lineJoin = LineJoin.MITER;
		this.lineCap = LineCap.BUTT;
		this.textMode = TextMode.FILL;
	}

	public void close() throws GraphicsException {
		// ページ側が閉じるので、ここでは何もしない
	}

	// --- 状態 -------------------------------------------------------------

	@Override
	public void setStrokePaint(final Paint paint) {
		this.strokePaint = paint;
	}

	@Override
	public Paint getStrokePaint() {
		return this.strokePaint;
	}

	@Override
	public void setFillPaint(final Paint paint) {
		this.fillPaint = paint;
	}

	@Override
	public Paint getFillPaint() {
		return this.fillPaint;
	}

	@Override
	public float getStrokeAlpha() {
		return this.strokeAlpha;
	}

	@Override
	public void setStrokeAlpha(final float strokeAlpha) {
		this.strokeAlpha = strokeAlpha;
	}

	@Override
	public float getFillAlpha() {
		return this.fillAlpha;
	}

	@Override
	public void setFillAlpha(final float fillAlpha) {
		this.fillAlpha = fillAlpha;
	}

	@Override
	public void setBlendMode(final net.zamasoft.pdfg2d.gc.paint.BlendMode mode) {
		this.blendMode = mode == null ? net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL : mode;
	}

	@Override
	public net.zamasoft.pdfg2d.gc.paint.BlendMode getBlendMode() {
		return this.blendMode;
	}

	/**
	 * 現在のブレンドモードを描画要素の{@code style}属性として書きます
	 * (normalなら何も書かない。2026-08-29)。
	 */
	protected final void writeBlendMode(final SVGWriter w) throws IOException {
		if (this.blendMode != net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL) {
			w.attr("style", "mix-blend-mode:" + this.blendMode.cssName);
		}
	}

	@Override
	public void setLineWidth(final double width) {
		this.lineWidth = width;
	}

	@Override
	public double getLineWidth() {
		return this.lineWidth;
	}

	@Override
	public void setLinePattern(final double[] pattern) {
		this.linePattern = pattern;
	}

	@Override
	public double[] getLinePattern() {
		return this.linePattern;
	}

	@Override
	public void setLineJoin(final LineJoin style) {
		this.lineJoin = style;
	}

	@Override
	public LineJoin getLineJoin() {
		return this.lineJoin;
	}

	@Override
	public void setLineCap(final LineCap style) {
		this.lineCap = style;
	}

	@Override
	public LineCap getLineCap() {
		return this.lineCap;
	}

	@Override
	public void setTextMode(final TextMode textMode) {
		this.textMode = textMode;
	}

	@Override
	public TextMode getTextMode() {
		return this.textMode;
	}

	@Override
	public void transform(final AffineTransform at) {
		this.transform.concatenate(at);
	}

	@Override
	public AffineTransform getTransform() {
		return new AffineTransform(this.transform);
	}

	// --- 描画 -------------------------------------------------------------

	@Override
	public void clip(final Shape shape) throws GraphicsException {
		try {
			final String id = this.writer.nextId("cp");
			final String rule = SVGPathWriter.fillRule(shape);
			// クリップ経路は現在の変換を適用した座標で入れる。こうすると
			// clip-path を付ける <g> の変換に左右されない
			final StringBuilder def = new StringBuilder(128);
			def.append("<clipPath id=\"").append(id).append("\" clipPathUnits=\"userSpaceOnUse\"><path d=\"")
					.append(SVGPathWriter.toPathData(shape, this.transform)).append('"');
			if (rule != null) {
				def.append(" clip-rule=\"").append(rule).append('"');
			}
			def.append("/></clipPath>");
			this.writer.addDef(def.toString());

			this.writer.open("g");
			this.writer.attr("clip-path", "url(#" + id + ")");
			this.writer.closeStart();
			this.openedGroup();
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
	}

	@Override
	public void draw(final Shape shape) throws GraphicsException {
		this.path(shape, false, true, null);
	}

	@Override
	public void fill(final Shape shape) throws GraphicsException {
		this.path(shape, true, false, null);
	}

	@Override
	public void fillDraw(final Shape shape) throws GraphicsException {
		this.path(shape, true, true, null);
	}

	/**
	 * ブラウザが描くSVGなので、PDFで近似になる機能のほとんどを厳密に
	 * 書けます(2026-08-29): ぼかしと層への効果は{@code <filter>}、
	 * 繰り返しは{@code spreadMethod}、層のブレンドは{@code <g>}の
	 * {@code mix-blend-mode}。円錐グラデーションだけはSVGのpaint serverに
	 * 無いので扇形の近似のまま。
	 */
	@Override
	public boolean supports(final Capability capability) {
		return switch (capability) {
		case GAUSSIAN_BLUR, REPEATING_GRADIENT, GROUP_FILTER, DROP_SHADOW, BLEND_GROUP -> true;
		case CONIC_GRADIENT -> false;
		};
	}

	/**
	 * ガウスぼかし付きの塗り(2026-08-29)。{@code <path filter="url(#..)">}で、
	 * フィルタ領域は形の外接矩形を3σ広げた範囲(既定の10%ではぼかしが
	 * 大きいと切れる)。座標には現在の変換を畳み込んでいるので、σも同じ
	 * 倍率で換算する。
	 */
	@Override
	public void fillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		if (!(sigma > 0)) {
			this.fill(shape);
			return;
		}
		final double det = Math.abs(this.transform.getDeterminant());
		final double s = sigma * (det > 0 ? Math.sqrt(det) : 1);
		final java.awt.geom.Rectangle2D b = this.transform.createTransformedShape(shape).getBounds2D();
		final double pad = s * 3 + 1;
		final String id = this.writer.defId("fb", "filter",
				" filterUnits=\"userSpaceOnUse\" x=\"" + SVGWriter.number(b.getX() - pad) + "\" y=\""
						+ SVGWriter.number(b.getY() - pad) + "\" width=\"" + SVGWriter.number(b.getWidth() + pad * 2)
						+ "\" height=\"" + SVGWriter.number(b.getHeight() + pad * 2)
						+ "\"><feGaussianBlur stdDeviation=\"" + SVGWriter.number(s) + "\"/>");
		this.path(shape, true, false, id);
	}

	/**
	 * 層(グループ画像)に掛ける効果を{@code <filter>}にして、そのidを返します
	 * (効果が無ければnull。2026-08-29)。色行列はCSSのfilter関数と同じ
	 * sRGBで計算させる(SVGの既定はlinearRGB)。適用順は{@link GroupEffects}
	 * どおり色行列→ぼかし→落とし影。不透明度は呼び出し側が{@code opacity}
	 * 属性で出す。
	 *
	 * @param w 層の幅(層の座標系。フィルタ領域の算出用)
	 * @param h 層の高さ
	 */
	protected final String effectsFilter(final net.zamasoft.pdfg2d.gc.GroupEffects effects, final double w,
			final double h) {
		final float[] m = effects.colorMatrix();
		final double blur = effects.blurSigma() > 0 ? effects.blurSigma() : 0;
		final net.zamasoft.pdfg2d.gc.GroupEffects.DropShadow shadow = effects.dropShadow();
		if (m == null && blur <= 0 && shadow == null) {
			return null;
		}
		double pad = blur * 3 + 1;
		if (shadow != null) {
			pad += Math.abs(shadow.dx()) + Math.abs(shadow.dy()) + Math.max(0, shadow.sigma()) * 3;
		}
		final StringBuilder f = new StringBuilder(256);
		f.append(" filterUnits=\"userSpaceOnUse\" color-interpolation-filters=\"sRGB\" x=\"")
				.append(SVGWriter.number(-pad)).append("\" y=\"").append(SVGWriter.number(-pad))
				.append("\" width=\"").append(SVGWriter.number(w + pad * 2)).append("\" height=\"")
				.append(SVGWriter.number(h + pad * 2)).append("\">");
		if (m != null) {
			f.append("<feColorMatrix type=\"matrix\" values=\"");
			for (int i = 0; i < m.length; ++i) {
				if (i != 0) {
					f.append(' ');
				}
				f.append(SVGWriter.number(m[i]));
			}
			f.append("\"/>");
		}
		if (blur > 0) {
			f.append("<feGaussianBlur stdDeviation=\"").append(SVGWriter.number(blur)).append("\"/>");
		}
		if (shadow != null) {
			f.append("<feDropShadow dx=\"").append(SVGWriter.number(shadow.dx())).append("\" dy=\"")
					.append(SVGWriter.number(shadow.dy())).append("\" stdDeviation=\"")
					.append(SVGWriter.number(Math.max(0, shadow.sigma()))).append('"');
			if (shadow.color() != null) {
				f.append(" flood-color=\"").append(SVGPaintWriter.toHex(shadow.color())).append('"');
				if (shadow.color().getAlpha() < 1f) {
					f.append(" flood-opacity=\"").append(SVGWriter.number(shadow.color().getAlpha())).append('"');
				}
			}
			f.append("/>");
		}
		return this.writer.defId("fx", "filter", f.toString());
	}

	private void path(final Shape shape, final boolean doFill, final boolean doStroke, final String filterId)
			throws GraphicsException {
		try {
			this.openTransformGroup();
			this.writer.open("path");
			this.writer.attr("d", SVGPathWriter.toPathData(shape, this.transform));
			if (filterId != null) {
				this.writer.attr("filter", "url(#" + filterId + ")");
			}
			this.writeBlendMode(this.writer);
			final String rule = SVGPathWriter.fillRule(shape);
			if (doFill && rule != null) {
				this.writer.attr("fill-rule", rule);
			}
			if (doFill) {
				final String paint = this.paints.toSVGPaint(this.fillPaint);
				this.writer.attr("fill", paint == null ? "none" : paint);
				final float alpha = SVGPaintWriter.alphaOf(this.fillPaint, this.fillAlpha);
				if (alpha < 1f) {
					this.writer.attr("fill-opacity", alpha);
				}
			} else {
				this.writer.attr("fill", "none");
			}
			if (doStroke) {
				final String paint = this.paints.toSVGPaint(this.strokePaint);
				this.writer.attr("stroke", paint == null ? "none" : paint);
				final float alpha = SVGPaintWriter.alphaOf(this.strokePaint, this.strokeAlpha);
				if (alpha < 1f) {
					this.writer.attr("stroke-opacity", alpha);
				}
				this.writer.attr("stroke-width", this.lineWidth);
				if (this.lineJoin != LineJoin.MITER) {
					this.writer.attr("stroke-linejoin", this.lineJoin == LineJoin.ROUND ? "round" : "bevel");
				}
				if (this.lineCap != LineCap.BUTT) {
					this.writer.attr("stroke-linecap", this.lineCap == LineCap.ROUND ? "round" : "square");
				}
				if (this.linePattern != null && this.linePattern.length != 0) {
					final StringBuilder dash = new StringBuilder();
					for (int i = 0; i < this.linePattern.length; ++i) {
						if (i != 0) {
							dash.append(' ');
						}
						dash.append(SVGWriter.number(this.linePattern[i]));
					}
					this.writer.attr("stroke-dasharray", dash.toString());
				}
			}
			this.writer.closeEmpty();
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		throw new UnsupportedOperationException("subclass must handle images");
	}

	/**
	 * 文字をアウトラインで描きます。字形を共有できない場合の退避先です。
	 *
	 * <p>
	 * {@code Font.drawTo}は{@link GC}の基本操作(状態・変換・{@code fill})だけを
	 * 使うので、Java2DにもPDFにも依存しません。ここへ渡せばそのまま
	 * {@code <path>}として出ます。
	 * </p>
	 */
	protected void drawTextAsOutline(final Text text, final double x, final double y) throws GraphicsException {
		try (final State state = this.begin()) {
			this.transform(AffineTransform.getTranslateInstance(x, y));
			final net.zamasoft.pdfg2d.font.Font font =
					((net.zamasoft.pdfg2d.font.FontMetricsImpl) text.getFontMetrics()).getFont();
			try {
				font.drawTo(this, text);
			} catch (final IOException e) {
				throw new GraphicsException(e);
			}
		}
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		this.drawTextAsOutline(text, x, y);
	}

	/**
	 * 透明度グループなどの一時描画面です。SVGへ直接書けないので、
	 * Java2Dの画像へ描いてからラスタ画像として扱います。Batikは使いません。
	 */
	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		final int w = Math.max(1, (int) Math.ceil(width));
		final int h = Math.max(1, (int) Math.ceil(height));
		final java.awt.image.BufferedImage buffer =
				new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g2d = buffer.createGraphics();
		g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
				java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		return new BufferedGroupImageGC(g2d, this.fontManager, buffer);
	}

	/** {@link #createGroupImage}が返す一時描画面。 */
	private static final class BufferedGroupImageGC extends net.zamasoft.pdfg2d.g2d.gc.G2DGC
			implements GroupImageGC {
		private final java.awt.image.BufferedImage buffer;

		BufferedGroupImageGC(final java.awt.Graphics2D g2d, final FontManager fonts,
				final java.awt.image.BufferedImage buffer) {
			super(g2d, fonts);
			this.buffer = buffer;
		}

		@Override
		public Image finish() throws GraphicsException {
			this.getGraphics2D().dispose();
			return new net.zamasoft.pdfg2d.g2d.image.RasterImageImpl(this.buffer);
		}
	}

	/**
	 * 座標をそのまま書くので、通常は{@code transform}属性を出しません。
	 * {@link SVGPathWriter}が現在の変換を適用済みの座標を作るためです。
	 * 将来グループ単位の不透明度などを出す必要が生じたときの入口として
	 * ここに置いてあります。
	 */
	protected void openTransformGroup() throws IOException {
		// 座標へ畳み込んでいるので、いまは何も開かない
	}

	/** {@code <g>}を1つ開いたことを、いま積まれている状態へ記録します。 */
	protected void openedGroup() {
		final Frame frame = this.frames.peek();
		if (frame != null) {
			++frame.openGroups;
		}
	}

	/** 現在の変換。部分クラスが座標を書くのに使います。 */
	protected AffineTransform currentTransform() {
		return this.transform;
	}

	protected SVGPaintWriter paints() {
		return this.paints;
	}

	protected static boolean isPlainColor(final Paint paint) {
		return paint instanceof Color;
	}

	/** 直前に{@code <g>}へ出した変換。差分判定に使います。 */
	protected AffineTransform emittedTransform() {
		return this.emittedTransform;
	}
}
