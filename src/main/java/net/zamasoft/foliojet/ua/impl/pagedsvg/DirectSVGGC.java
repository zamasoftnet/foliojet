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
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * A graphics context that <b>writes SVG directly</b>.
 *
 * <p>
 * Previously, Paged SVG used Batik's {@code SVGGraphics2D}, creating DOM nodes for each
 * drawing operation and serializing them at the end. Here, operations flow to {@link SVGWriter}
 * in drawing order without creating a DOM. Only <b>items for {@code defs}
 * (clip paths, gradients, and {@code @font-face})</b> accumulate;
 * they are written to a fragment reserved at the start when the page closes.
 * </p>
 *
 * <p>
 * <b>State management.</b> {@link #begin()} pushes state and {@code close()} restores it.
 * SVG has no state stack, so open {@code <g>} <b>only when something that must be emitted
 * on an element changes</b>, such as a transformation, clip, or opacity.
 * If nothing changes, create no element, avoiding tens of thousands of empty
 * {@code <g>} elements per page.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
class DirectSVGGC implements GC {
	/** One stacked state. */
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
		/** Number of {@code <g>} elements opened in this state. Close the same number when closing the state. */
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
			this.restore();
			this.gc.frames.pop();
		}

		/** Restores the state to when it was pushed (closes clipping {@code <g>} elements). */
		void restore() throws GraphicsException {
			try {
				for (int i = 0; i < this.openGroups; ++i) {
					this.gc.writer.end("g");
				}
			} catch (final IOException e) {
				throw new GraphicsException(e);
			}
			this.openGroups = 0;
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
	/** mix-blend-mode (2026-08-29). Emitted in each element's style attribute. */
	private net.zamasoft.pdfg2d.gc.paint.BlendMode blendMode = net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL;
	private double lineWidth = 1.0;
	private double[] linePattern = null;
	private LineJoin lineJoin = LineJoin.MITER;
	private LineCap lineCap = LineCap.BUTT;
	private TextMode textMode = TextMode.FILL;

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

	/**
	 * Restores the state from the latest {@link #begin()} (like PDF's Q q and Java2D's GC).
	 * Also removes clips applied since then.
	 *
	 * <p>
	 * Previously, this restored the initial state (identity matrix). The Graphics2D bridge
	 * ({@code BridgeGraphics2D}, MathML, inline SVG) reapplies transformations relative to the latest begin,
	 * so the offset to the position on the page was lost. The second and subsequent characters
	 * in formulas and nested shapes were drawn near the page origin
	 * (2026-10-04, item ⑳ of TECH-20261003-004).
	 * </p>
	 */
	@Override
	public void resetState() throws GraphicsException {
		final Frame frame = this.frames.peek();
		if (frame != null) {
			frame.restore();
			return;
		}
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
		// The page handles closing, so do nothing here.
	}

	// --- State -------------------------------------------------------------

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
	 * Writes the current blend mode in the drawing element's {@code style} attribute
	 * (writes nothing for normal; 2026-08-29).
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

	// --- Drawing -------------------------------------------------------------

	@Override
	public void clip(final Shape shape) throws GraphicsException {
		try {
			final String id = this.writer.nextId("cp");
			final String rule = SVGPathWriter.fillRule(shape);
			// Store clip path coordinates with the current transformation applied, so they are
			// unaffected by the transformation of the <g> carrying clip-path.
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
	 * Because browsers render this SVG, most features that PDF approximates can be written exactly
	 * (2026-08-29): {@code <filter>} for blur and layer effects, {@code spreadMethod} for repetition,
	 * and {@code mix-blend-mode} on {@code <g>} for layer blending. Only conic gradients retain
	 * the sector approximation because SVG has no paint server for them.
	 */
	@Override
	public boolean supports(final Capability capability) {
		return supportsCapability(capability);
	}

	/** The answer from {@link #supports}. Page recorders return the same answer (2026-10-04). */
	static boolean supportsCapability(final Capability capability) {
		return switch (capability) {
		case GAUSSIAN_BLUR, REPEATING_GRADIENT, GROUP_FILTER, DROP_SHADOW, BLEND_GROUP -> true;
		case CONIC_GRADIENT -> false;
		};
	}

	/**
	 * Fill with Gaussian blur (2026-08-29). Uses {@code <path filter="url(#..)">},
	 * with a filter region extending the shape's bounding box by 3σ (the default 10% clips large blurs).
	 * Coordinates already incorporate the current transformation, so scale σ by the same factor.
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
	 * Creates a {@code <filter>} for effects on a layer (group image) and returns its ID
	 * (null if there are no effects; 2026-08-29). Compute color matrices in sRGB, like CSS filter
	 * functions (SVG defaults to linearRGB). Apply in {@link GroupEffects} order:
	 * color matrix → blur → drop shadow. The caller emits opacity via the {@code opacity} attribute.
	 *
	 * @param w layer width (in layer coordinates, used to calculate the filter region)
	 * @param h layer height
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
				final String paint = this.paints.toSVGPaint(this.fillPaint, this.transform);
				this.writer.attr("fill", paint == null ? "none" : paint);
				final float alpha = SVGPaintWriter.alphaOf(this.fillPaint, this.fillAlpha);
				if (alpha < 1f) {
					this.writer.attr("fill-opacity", alpha);
				}
			} else {
				this.writer.attr("fill", "none");
			}
			if (doStroke) {
				final String paint = this.paints.toSVGPaint(this.strokePaint, this.transform);
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
	 * Draws text as outlines. Used as a fallback when glyphs cannot be shared.
	 *
	 * <p>
	 * {@code Font.drawTo} uses only basic {@link GC} operations (state, transformations, and {@code fill}),
	 * so it depends on neither Java2D nor PDF. Passing it here produces {@code <path>} directly.
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
	 * A temporary drawing surface for transparency groups and similar uses. It cannot be written
	 * directly to SVG, so draw into a Java2D image and treat it as a raster image. Does not use Batik.
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

	/** Temporary drawing surface returned by {@link #createGroupImage}. */
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

	/** Records one opened {@code <g>} in the current stacked state. */
	protected void openedGroup() {
		final Frame frame = this.frames.peek();
		if (frame != null) {
			++frame.openGroups;
		}
	}

	/** The current transformation. Subclasses use it to write coordinates. */
	protected AffineTransform currentTransform() {
		return this.transform;
	}

	protected SVGPaintWriter paints() {
		return this.paints;
	}

}
