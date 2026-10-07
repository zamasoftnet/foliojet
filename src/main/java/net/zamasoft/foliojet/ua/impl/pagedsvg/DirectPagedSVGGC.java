package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;

import net.zamasoft.foliojet.ua.impl.image.EncodedRasterImage;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.font.ColorGlyphFont;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.GlyphAdvances;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * A graphics context for Paged SVG that bypasses Batik.
 *
 * <p>
 * Writes text using code points that map the GIDs chosen by layout to the BMP private-use area,
 * and displays it with shared WOFF2 fonts. If glyphs are unavailable (embedding prohibited,
 * color glyphs, non-solid paint, or exhausted PUA), <b>falls back to outlines</b> and preserves
 * the original text in the page JSON. This follows the same decisions as the Batik version.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class DirectPagedSVGGC extends DirectSVGGC {
	private final PagedSVGResources resources;

	private final PagedSVGResources.PageData page;

	/**
	 * Logical line scope for paragraph bidi ({@link #beginTextReplacement}). Non-null only while drawing
	 * leaves in visual order. Only the first leaf gets the logical text as aria-label/data-copper-text;
	 * subsequent leaves get aria-hidden. Emit one TextRun for the union of the entire line when closing
	 * the scope (bidi-logical-output-spike.md §4).
	 */
	private LineReplacement replacement;

	private static final class LineReplacement {
		final String logicalText;
		boolean labelled;
		String font;
		double fontSize;
		AffineTransform transform;
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

		LineReplacement(final String logicalText) {
			this.logicalText = logicalText;
		}
	}

	@Override
	public State beginTextReplacement(final String logicalText) throws GraphicsException {
		if (this.replacement != null || logicalText == null) {
			return NO_OP_STATE;
		}
		final LineReplacement scope = new LineReplacement(logicalText);
		this.replacement = scope;
		return () -> {
			this.replacement = null;
			if (scope.font != null) {
				this.page.textRuns.add(new PagedSVGResources.TextRun(scope.logicalText, scope.font, scope.fontSize,
						scope.transform, scope.minX, scope.minY, scope.maxX, scope.maxY));
			}
		};
	}

	/**
	 * Semantic attributes for text elements. Outside a scope, behaves as before (role/aria-label/
	 * data-copper-text only when {@code defaultLabel} is set). Within a scope, the first leaf gets
	 * the logical text and subsequent leaves get aria-hidden.
	 */
	private void writeSemanticAttributes(final SVGWriter w, final String sourceText, final boolean defaultLabel)
			throws IOException {
		final LineReplacement scope = this.replacement;
		if (scope == null) {
			if (defaultLabel) {
				w.attr("role", "img");
				w.attr("aria-label", sourceText);
				w.attr("data-copper-text", sourceText);
			}
			return;
		}
		if (!scope.labelled) {
			scope.labelled = true;
			w.attr("role", "img");
			w.attr("aria-label", scope.logicalText);
			w.attr("data-copper-text", scope.logicalText);
		} else {
			w.attr("aria-hidden", "true");
		}
	}

	/** Records a TextRun. Within a scope, only expands the union and emits one run when the scope closes. */
	private void recordTextRun(final String text, final String font, final double size, final double minX,
			final double minY, final double maxX, final double maxY) {
		final LineReplacement scope = this.replacement;
		if (scope == null) {
			this.page.textRuns.add(new PagedSVGResources.TextRun(text, font, size,
					new AffineTransform(this.currentTransform()), minX, minY, maxX, maxY));
			return;
		}
		if (scope.font == null) {
			scope.font = font;
			scope.fontSize = size;
			scope.transform = new AffineTransform(this.currentTransform());
		}
		scope.minX = Math.min(scope.minX, minX);
		scope.minY = Math.min(scope.minY, minY);
		scope.maxX = Math.max(scope.maxX, maxX);
		scope.maxY = Math.max(scope.maxY, maxY);
	}

	DirectPagedSVGGC(final SVGWriter writer, final FontManager fonts, final PagedSVGResources resources,
			final PagedSVGResources.PageData page) {
		super(writer, fonts);
		this.resources = resources;
		this.page = page;
		// Use the same shared resources for tiled images as for ordinary images.
		this.paints().setImageHrefs(this::assetHref);
	}

	/** Makes an image a shared resource and returns a URI reachable from the page SVG. Returns null if unwritable. */
	private String assetHref(final Image image) throws IOException {
		final BufferedImage raster = this.toRaster(image);
		if (raster == null) {
			return null;
		}
		final byte[] png = this.resources.hasOriginal(raster) ? null : encodePng(raster);
		return this.imageAsset(image, raster, png).href();
	}

	/**
	 * Makes a rasterized image a shared resource (or a source reference). With {@code resources=source},
	 * if the source is a web URL, reference that URL without copying (2026-09-02).
	 */
	private PagedSVGResources.ImageAsset imageAsset(final Image image, final BufferedImage raster, final byte[] png)
			throws IOException {
		if (this.resources.referencesSources()) {
			final java.net.URI source = webSourceOf(image);
			if (source != null) {
				return this.resources.sourceImage(source, raster, png, raster.getWidth(), raster.getHeight());
			}
		}
		return this.resources.image(raster, png, raster.getWidth(), raster.getHeight());
	}

	/** Returns the image's source URL if its scheme is {@code http:}/{@code https:}/{@code file:}. */
	private static java.net.URI webSourceOf(final Image image) {
		Image i = image;
		while (i != null) {
			if (i instanceof final SourcedImage sourced) {
				final String scheme = sourced.uri == null ? null : sourced.uri.getScheme();
				if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")
						|| scheme.equalsIgnoreCase("file"))) {
					return sourced.uri;
				}
				return null;
			}
			i = i instanceof final WrappedImage wrapped ? wrapped.getImage() : null;
		}
		return null;
	}

	/** Scale for rasterizing vector images. At 1:1, they look coarse when enlarged. */
	private static final double RASTERIZE_SCALE = 4.0;

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		if (image instanceof final SVGFragmentImage fragment) {
			// Keep layers as vectors in <g> (opacity is the state's alpha).
			this.writeFragment(fragment, null, this.getFillAlpha());
			return;
		}
		Image original = image;
		while (original instanceof final WrappedImage wrapped) {
			original = wrapped.getImage();
		}
		if (original instanceof final KnownAssetImage known) {
			// Reference resources from the previous output directly (2026-08-28). Do not read their bytes.
			this.writeImageRef(image, this.resources.knownImage(known.asset).href());
			return;
		}
		if (!(original instanceof RasterImageImpl)) {
			// **First let the image draw itself.** Many images, such as list bullets,
			// can draw with basic GC operations alone. Skipping this and rasterizing
			// turns otherwise vector content into PNGs and adds shared resources.
			// Only images that require Java2D directly throw an exception; catch only those.
			try {
				image.drawTo(this);
				return;
			} catch (final ClassCastException e) {
				// An image requiring G2DGC. Proceed to rasterization below.
				// The implementation casts the GC at the start, so nothing has been drawn yet.
			}
		}
		final BufferedImage raster = this.toRaster(image);
		try {
			final byte[] png = this.resources.hasOriginal(raster) ? null : encodePng(raster);
			final PagedSVGResources.ImageAsset asset = this.imageAsset(image, raster, png);
			// Record resource identity in the dimension table so the next reconversion need not
			// open the image (2026-08-28). The UA attaches the URI to the image.
			this.resources.rememberAssetOf(image, asset);
			this.writeImageRef(image, asset.href());
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * Places a layer with effects applied (2026-08-29). Represent effects with {@code <filter>}
	 * and wrap in {@code <g filter=..>}. Insert SVG fragments as vectors;
	 * for raster images, wrap {@code <image>} in the same {@code <g>}.
	 */
	@Override
	public void drawImage(final Image image, final GroupEffects effects) throws GraphicsException {
		if (effects == null || effects.isIdentity()) {
			this.drawImage(image);
			return;
		}
		final String filterId = this.effectsFilter(effects, image.getWidth(), image.getHeight());
		final float opacity = (float) Math.max(0, Math.min(1, effects.opacity())) * this.getFillAlpha();
		if (image instanceof final SVGFragmentImage fragment) {
			this.writeFragment(fragment, filterId, opacity);
			return;
		}
		try {
			final SVGWriter w = this.writer;
			w.open("g");
			if (filterId != null) {
				w.attr("filter", "url(#" + filterId + ")");
			}
			if (opacity < 1f) {
				w.attr("opacity", opacity);
			}
			this.writeBlendMode(w);
			w.closeStart();
			// Opacity and blending are on <g>, so do not repeat them on the inner <image>.
			try (final State state = this.begin()) {
				this.setFillAlpha(1f);
				this.setBlendMode(net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL);
				this.drawImage(image);
			}
			w.end("g");
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * Wraps an SVG fragment layer in {@code <g>} and inserts it. The layer's coordinate system
	 * is the user space at creation, so emit the current transformation as {@code transform}
	 * (the filter region and σ also resolve in this coordinate system). Transfer text positions
	 * recorded within the layer to the page using the same transformation.
	 */
	private void writeFragment(final SVGFragmentImage fragment, final String filterId, final float opacity)
			throws GraphicsException {
		final AffineTransform ctm = this.currentTransform();
		try {
			final SVGWriter w = this.writer;
			w.open("g");
			if (!ctm.isIdentity()) {
				w.attr("transform", matrix(ctm));
			}
			if (filterId != null) {
				w.attr("filter", "url(#" + filterId + ")");
			}
			if (opacity < 1f) {
				w.attr("opacity", opacity);
			}
			this.writeBlendMode(w);
			w.closeStart();
			w.raw(fragment.svg());
			w.end("g");
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
		for (final PagedSVGResources.TextRun run : fragment.textRuns()) {
			final AffineTransform at = new AffineTransform(ctm);
			at.concatenate(new AffineTransform(run.transform));
			this.page.textRuns.add(new PagedSVGResources.TextRun(run.text, run.font, run.fontSize, at, run.minX,
					run.minY, run.maxX, run.maxY));
		}
	}

	/**
	 * A layer (group image). Writes to a separate buffer as an SVG fragment, without rasterizing
	 * (2026-08-29). Shares {@code defs}, IDs, and {@code @font-face} with the page.
	 */
	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new FragmentGroup(this, width, height);
	}

	/** Layer returned by {@link #createGroupImage}. Writes content to a separate buffer in the same format. */
	private static final class FragmentGroup extends net.zamasoft.foliojet.layout.util.AbstractDelegatingGC
			implements GroupImageGC {
		private final java.io.StringWriter buffer;
		private final PagedSVGResources.PageData page;
		private final double width, height;
		/** Number of text strings at creation. Strings added afterward belong to this layer. */
		private final int textRunStart;

		FragmentGroup(final DirectPagedSVGGC parent, final double width, final double height) {
			this(parent, new java.io.StringWriter(), width, height);
		}

		private FragmentGroup(final DirectPagedSVGGC parent, final java.io.StringWriter buffer, final double width,
				final double height) {
			super(new DirectPagedSVGGC(new SVGWriter(buffer, parent.writer), parent.getFontManager(), parent.resources,
					parent.page));
			this.buffer = buffer;
			this.page = parent.page;
			this.width = width;
			this.height = height;
			this.textRunStart = parent.page.textRuns.size();
		}

		@Override
		public Image finish() throws GraphicsException {
			final java.util.List<PagedSVGResources.TextRun> runs = this.page.textRuns;
			final java.util.List<PagedSVGResources.TextRun> mine = new java.util.ArrayList<>(
					runs.subList(this.textRunStart, runs.size()));
			runs.subList(this.textRunStart, runs.size()).clear();
			return new SVGFragmentImage(this.buffer.toString(), this.width, this.height, mine);
		}
	}

	/**
	 * Writes one image reference.
	 *
	 * <p>
	 * The contract is to draw an image into a rectangle of its own logical dimensions
	 * (the caller adds a scale divided by {@code image.getWidth()/getHeight()} to the transformation).
	 * This is neither a unit rectangle nor pixel dimensions. Confusing these makes only images
	 * appear at the wrong size, while the XML remains valid.
	 * </p>
	 */
	private void writeImageRef(final Image image, final String href) throws GraphicsException {
		try {
			final SVGWriter w = this.writer;
			w.open("image");
			w.attr("x", 0);
			w.attr("y", 0);
			w.attr("width", image.getWidth());
			w.attr("height", image.getHeight());
			w.attr("preserveAspectRatio", "none");
			w.attr("transform", matrix(this.currentTransform()));
			w.attr("xlink:href", href);
			final float alpha = this.getFillAlpha();
			if (alpha < 1f) {
				w.attr("opacity", alpha);
			}
			this.writeBlendMode(w);
			w.closeEmpty();
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * Rasterizes an image. Remembers images whose original JPEG can be emitted unchanged.
	 * Draws non-raster images once through Java2D (without Batik).
	 */
	private BufferedImage toRaster(final Image image) throws GraphicsException {
		Image original = image;
		while (original instanceof final WrappedImage wrapped) {
			original = wrapped.getImage();
		}
		if (original instanceof final RasterImageImpl rasterImage) {
			if (original instanceof final EncodedRasterImage encoded) {
				// An image whose original JPEG can be emitted unchanged. Do not recompress.
				this.resources.rememberOriginal(encoded.getImage(), encoded.getEncoded(), encoded.getMediaType(),
						encoded.getExtension());
			}
			return rasterImage.getImage();
		}
		return this.rasterize(image);
	}

	/** Rasterizes images that cannot be written directly, such as SVG, by drawing once through Java2D. */
	private BufferedImage rasterize(final Image image) throws GraphicsException {
		final double iw = Math.max(1e-6, image.getWidth());
		final double ih = Math.max(1e-6, image.getHeight());
		int w = Math.max(1, (int) Math.ceil(iw * RASTERIZE_SCALE));
		int h = Math.max(1, (int) Math.ceil(ih * RASTERIZE_SCALE));
		// This scale improves quality, so reduce it to fit if it would exceed the pixel limit
		// (2026-10-03, output.image-pixel-limit). Do not fail.
		final long limit = this.resources.rasterPixelLimit();
		if (limit >= 0 && (long) w * h > limit) {
			final double scale = Math.sqrt(Math.max(1, limit) / (iw * ih));
			w = Math.max(1, (int) Math.floor(iw * scale));
			h = Math.max(1, (int) Math.floor(ih * scale));
		}
		final BufferedImage buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2d = buffer.createGraphics();
		try {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.scale(w / iw, h / ih);
			image.drawTo(new net.zamasoft.pdfg2d.g2d.gc.G2DGC(g2d, this.getFontManager()));
		} finally {
			g2d.dispose();
		}
		return buffer;
	}

	private static byte[] encodePng(final BufferedImage image) throws IOException {
		final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		if (!javax.imageio.ImageIO.write(image, "png", bytes)) {
			throw new IOException("No PNG writer is available");
		}
		return bytes.toByteArray();
	}

	private static String matrix(final AffineTransform at) {
		final double[] m = new double[6];
		at.getMatrix(m);
		final StringBuilder sb = new StringBuilder(64).append("matrix(");
		for (int i = 0; i < 6; ++i) {
			if (i != 0) {
				sb.append(' ');
			}
			sb.append(SVGWriter.number(m[i]));
		}
		sb.append(')');
		return sb.toString();
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		final String sourceText = new String(text.getChars(), 0, text.getCharCount());
		final FontSource source = text.getFontMetrics().getFontSource();
		if (!WebFontSubset.allowsEmbedding(source.getEmbeddingLicenseFlags())
				|| !(text.getFontMetrics() instanceof FontMetricsImpl metrics)) {
			this.outlineText(text, sourceText, source.getFontName(), x, y);
			return;
		}
		final Font font = metrics.getFont();
		if (!(font instanceof ShapedFont shaped) || hasColorGlyph(font, text)
				|| !supportedPaint(this.getFillPaint()) || !supportedPaint(this.getStrokePaint())) {
			// **Write core fonts as text** (2026-08-28). PDF core fonts
			// (Helvetica/Times/Courier) have no embeddable font data,
			// so they do not become ShapedFont and previously fell back to outlines. In SVG,
			// browsers have equivalent faces, so they can remain text.
			// Observed: only body-text digits and Latin characters disappeared from <text>.
			final String generic = coreFontFamily(source);
			if (generic != null && !hasColorGlyph(font, text) && supportedPaint(this.getFillPaint())
					&& supportedPaint(this.getStrokePaint()) && text.getCharCount() == text.getGlyphCount()) {
				this.coreText(text, sourceText, generic, metrics, x, y);
				return;
			}
			this.outlineText(text, sourceText, source.getFontName(), x, y);
			return;
		}

		final FontStyle style = text.getFontStyle();
		final boolean vertical = style.getDirection() == FontStyle.Direction.TB;
		final WebFontSubset.Mode mode = !vertical ? WebFontSubset.Mode.HORIZONTAL
				: source.getDirection() == FontStyle.Direction.TB ? WebFontSubset.Mode.VERTICAL_UPRIGHT
						: WebFontSubset.Mode.VERTICAL_SIDEWAYS;
		final boolean oblique = style.getStyle() != FontStyle.Style.NORMAL && !source.isItalic();
		final WebFontSubset subset = this.resources.font(source, shaped, mode, oblique);
		if (!subset.canMap(text.getGlyphIds(), text.getGlyphCount())) {
			this.outlineText(text, sourceText, source.getFontName(), x, y);
			return;
		}
		this.page.useFont(subset);

		final int glyphCount = text.getGlyphCount();
		final int[] gids = text.getGlyphIds();
		final GlyphAdvances adjustments = text.xAdvances();
		final double size = style.getSize();
		final StringBuilder chars = new StringBuilder(glyphCount * 2);
		final StringBuilder xs = new StringBuilder(glyphCount * 12);
		final StringBuilder ys = new StringBuilder(glyphCount * 12);
		// xAdvance[0] is the adjustment before the run's first glyph.
		double pen = adjustments == null ? 0 : adjustments.get(0);
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < glyphCount; ++i) {
			final int gid = gids[i];
			if (i > 0) {
				pen += metrics.getAdvance(gids[i - 1]) + text.getLetterSpacing()
						- metrics.getKerning(gids[i - 1], gid);
				if (adjustments != null) {
					pen += adjustments.get(i);
				}
			}
			chars.appendCodePoint(subset.codePointFor(gid));
			final double gx, gy;
			if (vertical) {
				gx = x;
				gy = y + pen;
			} else {
				gx = x + pen + metrics.getPlacementAdjustment(gid);
				gy = y;
			}
			if (i != 0) {
				xs.append(' ');
				ys.append(' ');
			}
			xs.append(SVGWriter.number(gx));
			ys.append(SVGWriter.number(gy));
			// Set the character box's forward end using glyph advance (the same getAdvance as pen).
			// A fixed 1em makes boxes for half-width digits (two-digit tate-chu-yoko and sideways digits) protrude by 0.5em
			// (user report "character boxes for tate-chu-yoko links", 2026-09-06: the reader text layer exceeded line width).
			final double advance = metrics.getAdvance(gid);
			minX = Math.min(minX, gx - (vertical ? size / 2.0 : 0));
			maxX = Math.max(maxX, gx + (vertical ? size / 2.0 : advance));
			minY = Math.min(minY, gy - (vertical ? 0 : metrics.getAscent()));
			maxY = Math.max(maxY, gy + (vertical ? advance : metrics.getDescent()));
		}
		// Register @font-face **after** assigning code points. Adding glyphs to a carried subset
		// advances its version and changes its URI, so registering before assignment
		// would reference the previous version for glyphs added by this run (2026-08-29).
		this.writer.addFontFace(subset.family(), subset.uri());

		try {
			final SVGWriter w = this.writer;
			w.open("text");
			w.attr("x", xs.toString());
			w.attr("y", ys.toString());
			// **Include the current transformation** (2026-08-28). Coordinates remain in user space;
			// without it, transformed text appears elsewhere. Observed: an article
			// heading appeared at x=7.5 (instead of 43.5), was clipped, and disappeared.
			// Images (writeImageRef) already included it.
			final java.awt.geom.AffineTransform ctm = this.currentTransform();
			if (!ctm.isIdentity()) {
				w.attr("transform", matrix(ctm));
			}
			w.attr("font-family", subset.family());
			w.attr("font-size", size);
			this.writeBlendMode(w);
			// Layout has already fixed advances. Viewer-side kerning or ligatures would distort them.
			w.attr("font-kerning", "none");
			w.attr("font-variant-ligatures", "none");
			w.attr("font-feature-settings", "'kern' 0, 'liga' 0");
			w.attr("text-rendering", "geometricPrecision");
			this.writeSemanticAttributes(w, sourceText, true);
			this.writeTextPaint(w, style, source);
			w.closeStart();
			w.text(chars.toString());
			w.end("text");
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}

		this.recordTextRun(sourceText, subset.family(), size, minX, minY, maxX, maxY);
	}

	/**
	 * CSS font declarations corresponding to PDF core fonts. Returns {@code null}
	 * if no equivalent exists (draws outlines as before).
	 *
	 * <p>
	 * Symbol and ZapfDingbats use custom encodings and cannot be emitted as text.
	 * </p>
	 */
	private static String coreFontFamily(final FontSource source) {
		final String name = source.getFontName();
		if (name == null) {
			return null;
		}
		if (name.startsWith("Helvetica")) {
			return "Helvetica,Arial,sans-serif";
		}
		if (name.startsWith("Times")) {
			return "'Times New Roman',Times,serif";
		}
		if (name.startsWith("Courier")) {
			return "'Courier New',Courier,monospace";
		}
		return null;
	}

	/**
	 * Writes core-font text at the positions determined by layout.
	 * The viewer draws glyphs with equivalent faces.
	 */
	private void coreText(final Text text, final String value, final String family, final FontMetricsImpl metrics,
			final double x, final double y) throws GraphicsException {
		final FontStyle style = text.getFontStyle();
		final boolean vertical = style.getDirection() == FontStyle.Direction.TB;
		final double size = style.getSize();
		final int glyphCount = text.getGlyphCount();
		final int[] gids = text.getGlyphIds();
		final GlyphAdvances adjustments = text.xAdvances();
		final StringBuilder xs = new StringBuilder(glyphCount * 12);
		final StringBuilder ys = new StringBuilder(glyphCount * 12);
		double pen = adjustments == null ? 0 : adjustments.get(0);
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < glyphCount; ++i) {
			if (i > 0) {
				pen += metrics.getAdvance(gids[i - 1]) + text.getLetterSpacing()
						- metrics.getKerning(gids[i - 1], gids[i]);
				if (adjustments != null) {
					pen += adjustments.get(i);
				}
			}
			final double gx = vertical ? x : x + pen + metrics.getPlacementAdjustment(gids[i]);
			final double gy = vertical ? y + pen : y;
			if (i != 0) {
				xs.append(' ');
				ys.append(' ');
			}
			xs.append(SVGWriter.number(gx));
			ys.append(SVGWriter.number(gy));
			// Use glyph advances for character boxes (same as writeSubsetText above; 2026-09-06).
			final double advance = metrics.getAdvance(gids[i]);
			minX = Math.min(minX, gx - (vertical ? size / 2.0 : 0));
			maxX = Math.max(maxX, gx + (vertical ? size / 2.0 : advance));
			minY = Math.min(minY, gy - (vertical ? 0 : metrics.getAscent()));
			maxY = Math.max(maxY, gy + (vertical ? advance : metrics.getDescent()));
		}
		try {
			final SVGWriter w = this.writer;
			w.open("text");
			w.attr("x", xs.toString());
			w.attr("y", ys.toString());
			final java.awt.geom.AffineTransform ctm = this.currentTransform();
			if (!ctm.isIdentity()) {
				w.attr("transform", matrix(ctm));
			}
			w.attr("font-family", family);
			w.attr("font-size", size);
			this.writeBlendMode(w);
			if (style.getStyle() != FontStyle.Style.NORMAL) {
				w.attr("font-style", "italic");
			}
			if (style.getWeight() != null && style.getWeight().w >= 600) {
				w.attr("font-weight", "bold");
			}
			// Layout has already fixed advances.
			w.attr("font-kerning", "none");
			w.attr("font-variant-ligatures", "none");
			w.attr("font-feature-settings", "'kern' 0, 'liga' 0");
			w.attr("text-rendering", "geometricPrecision");
			this.writeSemanticAttributes(w, value, false);
			this.writeTextPaint(w, style, text.getFontMetrics().getFontSource());
			w.closeStart();
			w.text(value);
			w.end("text");
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
		this.recordTextRun(value, family, size, minX, minY, maxX, maxY);
	}

	/**
	 * Text whose glyphs cannot be shared. Draws outlines to preserve appearance
	 * and retains the original text in the page JSON.
	 */
	private void outlineText(final Text text, final String value, final String font, final double x, final double y)
			throws GraphicsException {
		this.recordText(text, value, font, x, y);
		this.drawTextAsOutline(text, x, y);
	}

	private void recordText(final Text text, final String value, final String font, final double x, final double y) {
		final double size = text.getFontStyle().getSize();
		// Use the actual advance (text.getAdvance()) for character boxes even when drawing outlines (2026-09-06).
		this.recordTextRun(value, font, size, x, y - size, x + text.getAdvance(), y);
	}

	/**
	 * Text fill. Synthetic weight (stroking a thin font to make it bold when requested)
	 * follows the same rules as the Batik version.
	 */
	private void writeTextPaint(final SVGWriter w, final FontStyle style, final FontSource source)
			throws IOException {
		final TextMode mode = this.getTextMode();
		if (mode == TextMode.FILL || mode == TextMode.FILL_STROKE) {
			writeColor(w, "fill", (Color) this.getFillPaint(), this.getFillAlpha());
		} else {
			w.attr("fill", "none");
		}
		if (mode == TextMode.STROKE || mode == TextMode.FILL_STROKE) {
			writeColor(w, "stroke", (Color) this.getStrokePaint(), this.getStrokeAlpha());
			w.attr("stroke-width", this.getLineWidth());
		}
		if (mode == TextMode.FILL && style.getWeight().w >= 500 && source.getWeight().w < 500) {
			final double enlargement = switch (style.getWeight()) {
			case W_500 -> style.getSize() / 28.0;
			case W_600 -> style.getSize() / 24.0;
			case W_700 -> style.getSize() / 20.0;
			case W_800 -> style.getSize() / 16.0;
			case W_900 -> style.getSize() / 12.0;
			default -> 0;
			};
			if (enlargement > 0) {
				writeColor(w, "stroke", (Color) this.getFillPaint(), this.getFillAlpha());
				w.attr("stroke-width", enlargement);
				w.attr("paint-order", "stroke fill");
			}
		}
	}

	private static void writeColor(final SVGWriter w, final String name, final Color color, final float stateAlpha)
			throws IOException {
		if (color == null) {
			return;
		}
		w.attr(name, SVGPaintWriter.toHex(color));
		final float alpha = color.getAlpha() * stateAlpha;
		if (alpha != 1) {
			w.attr(name + "-opacity", alpha);
		}
	}

	private static boolean supportedPaint(final Paint paint) {
		return paint == null || paint instanceof Color;
	}

	private static boolean hasColorGlyph(final Font font, final Text text) {
		if (!(font instanceof final ColorGlyphFont colorFont)) {
			return false;
		}
		final int[] gids = text.getGlyphIds();
		for (int i = 0; i < text.getGlyphCount(); ++i) {
			if (colorFont.isColorGlyph(gids[i])) {
				return true;
			}
		}
		return false;
	}
}
