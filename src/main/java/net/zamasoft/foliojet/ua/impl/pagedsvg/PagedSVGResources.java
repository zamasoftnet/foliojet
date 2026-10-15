package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.ua.props.PagedSvgFontScope;
import net.zamasoft.foliojet.ua.props.PagedSvgImageCompression;
import net.zamasoft.foliojet.ua.props.PagedSvgResourceMode;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.foliojet.ua.JsonText;

/** Per-book resource registry and manifest/page JSON serializer. */
final class PagedSVGResources {
	@FunctionalInterface
	interface ResultEmitter {
		void emit(String uri, String mimeType, byte[] bytes) throws IOException;
	}

	record PageAsset(int number, double width, double height, String svgUri, String svgSha256, String jsonUri,
			String jsonSha256) {
	}

	record ImageAsset(String uri, String sha256, String mediaType, int width, int height, boolean omitted,
			String baseUri, String source) {
		ImageAsset(String uri, String sha256, String mediaType, int width, int height, boolean omitted,
				String baseUri) {
			this(uri, sha256, mediaType, width, height, omitted, baseUri, null);
		}

		/**
		 * Reference written in the page SVG. Prefix shared resources with
		 * {@code output.paged-svg.base-uri}. Use data: for embedded resources, and the original
		 * absolute URL unchanged for source references ({@code resources=source}).
		 */
		String href() {
			if (this.source != null) {
				return this.source;
			}
			return this.uri.startsWith("data:") ? this.uri : this.baseUri + this.uri;
		}
	}

	private record OriginalImage(byte[] bytes, String mediaType, String extension) {
	}

	record LinkData(String href, String contents, double minX, double minY, double maxX, double maxY) {
	}

	/**
	 * One audio or video file (2026-10-15, TECH-20261015-005): emitted under {@code assets/media/} by content, or
	 * referenced at its http(s) source with {@code resources=source}.
	 */
	record MediaAsset(String uri, String sha256, String mediaType, long bytes, boolean omitted, String baseUri,
			String source) {
		/** Reference written in the page JSON, relative to the page as image references in the page SVG are. */
		String href() {
			return this.source != null ? this.source : this.baseUri + this.uri;
		}
	}

	/**
	 * One candidate of a playable element: the file and the type the page data gives the reader. The type is the
	 * {@code type} attribute as written, codecs included, so that the browser can tell which candidate it plays
	 * (codex review 2026-10-15); without one, the file's type.
	 */
	record MediaSourceRef(MediaAsset asset, String type) {
	}

	/** A playable element on a page: its sources in order and the content box in page units. */
	record MediaData(String tag, List<MediaSourceRef> sources, boolean controls, boolean autoplay, boolean loop,
			boolean muted, boolean poster, String label, double minX, double minY, double maxX, double maxY) {
	}

	record FragmentData(String id, int page, double x, double y) {
	}

	static final class OutlineData {
		final String title;
		final int page;
		final double x, y;
		final List<OutlineData> children = new ArrayList<>();

		OutlineData(final String title, final int page, final double x, final double y) {
			this.title = title;
			this.page = page;
			this.x = x;
			this.y = y;
		}
	}

	static final class TextRun {
		final String text;
		final String font;
		final double fontSize;
		final double[] transform;
		final double minX, minY, maxX, maxY;

		TextRun(final String text, final String font, final double fontSize, final AffineTransform transform,
				final double minX, final double minY, final double maxX, final double maxY) {
			this.text = text;
			this.font = font;
			this.fontSize = fontSize;
			this.transform = new double[6];
			transform.getMatrix(this.transform);
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
		}
	}

	static final class PageData {
		final int number;
		final double width, height;
		final List<TextRun> textRuns = new ArrayList<>();
		final Map<String, String> fonts = new LinkedHashMap<>();
		final List<LinkData> links = new ArrayList<>();
		final List<FragmentData> fragments = new ArrayList<>();
		final List<MediaData> media = new ArrayList<>();

		PageData(final int number, final double width, final double height) {
			this.number = number;
			this.width = width;
			this.height = height;
		}

		void useFont(final WebFontSubset font) {
			this.fonts.put(font.family(), font.uri());
		}

		/**
		 * Writes page JSON. Unlike page SVG, this is only a few KB per page,
		 * so assemble it and write it in one operation.
		 */
		void writeJson(final java.io.Writer out) throws IOException {
			out.write(new String(this.json(), StandardCharsets.UTF_8));
		}

		byte[] json() {
			final StringBuilder json = new StringBuilder(256 + this.textRuns.size() * 160);
			json.append("{\n  \"version\":1,\n  \"page\":").append(this.number)
					.append(",\n  \"width\":").append(JsonText.number(this.width))
					.append(",\n  \"height\":").append(JsonText.number(this.height)).append(",\n  \"text\":[");
			for (int i = 0; i < this.textRuns.size(); ++i) {
				final TextRun run = this.textRuns.get(i);
				if (i != 0) {
					json.append(',');
				}
				json.append("\n    {\"value\":");
				json.append(JsonText.quoted(run.text));
				json.append(",\"font\":");
				json.append(JsonText.quoted(run.font));
				json.append(",\"size\":").append(JsonText.number(run.fontSize)).append(",\"transform\":[");
				for (int j = 0; j < run.transform.length; ++j) {
					if (j != 0) {
						json.append(',');
					}
					json.append(JsonText.number(run.transform[j]));
				}
				json.append("],\"bounds\":[").append(JsonText.number(run.minX)).append(',').append(JsonText.number(run.minY))
						.append(',').append(JsonText.number(run.maxX)).append(',').append(JsonText.number(run.maxY)).append("]}");
			}
			json.append("\n  ],\n  \"links\":[");
			for (int i = 0; i < this.links.size(); ++i) {
				final LinkData link = this.links.get(i);
				if (i != 0) {
					json.append(',');
				}
				json.append("\n    {\"href\":");
				json.append(JsonText.quoted(link.href));
				if (link.contents != null) {
					json.append(",\"contents\":");
					json.append(JsonText.quoted(link.contents));
				}
				json.append(",\"bounds\":[").append(JsonText.number(link.minX)).append(',')
						.append(JsonText.number(link.minY)).append(',').append(JsonText.number(link.maxX)).append(',')
						.append(JsonText.number(link.maxY)).append("]}");
			}
			json.append("\n  ],\n  \"anchors\":[");
			for (int i = 0; i < this.fragments.size(); ++i) {
				final FragmentData fragment = this.fragments.get(i);
				if (i != 0) {
					json.append(',');
				}
				json.append("\n    {\"id\":");
				json.append(JsonText.quoted(fragment.id));
				json.append(",\"x\":").append(JsonText.number(fragment.x)).append(",\"y\":")
						.append(JsonText.number(fragment.y)).append('}');
			}
			json.append("\n  ]");
			if (!this.media.isEmpty()) {
				json.append(",\n  \"media\":[");
				for (int i = 0; i < this.media.size(); ++i) {
					final MediaData media = this.media.get(i);
					if (i != 0) {
						json.append(',');
					}
					json.append("\n    {\"tag\":");
					json.append(JsonText.quoted(media.tag));
					json.append(",\"sources\":[");
					for (int j = 0; j < media.sources.size(); ++j) {
						final MediaSourceRef source = media.sources.get(j);
						if (j != 0) {
							json.append(',');
						}
						json.append("{\"src\":");
						json.append(JsonText.quoted(source.asset.href()));
						json.append(",\"type\":");
						json.append(JsonText.quoted(source.type));
						json.append('}');
					}
					json.append("],\"controls\":").append(media.controls).append(",\"autoplay\":").append(media.autoplay)
							.append(",\"loop\":").append(media.loop).append(",\"muted\":").append(media.muted)
							.append(",\"poster\":").append(media.poster);
					if (media.label != null) {
						json.append(",\"label\":");
						json.append(JsonText.quoted(media.label));
					}
					json.append(",\"bounds\":[").append(JsonText.number(media.minX)).append(',')
							.append(JsonText.number(media.minY)).append(',').append(JsonText.number(media.maxX)).append(',')
							.append(JsonText.number(media.maxY)).append("]}");
				}
				json.append("\n  ]");
			}
			json.append("\n}\n");
			return json.toString().getBytes(StandardCharsets.UTF_8);
		}
	}

	/**
	 * One font resource in the manifest. If a subset grows, both its carried version and its
	 * expanded version have entries (pages reference the version current when they close).
	 * {@code omitted} marks a version not emitted because the consumer already has it from
	 * the previous conversion ({@code resources=omit}).
	 */
	private record FontAsset(WebFontSubset subset, String uri, String sha256, int bytes, boolean omitted) {
	}

	private static final class FontEntry {
		final FontSource source;
		final ShapedFont font;
		final WebFontSubset.Mode mode;
		final boolean oblique;
		final WebFontSubset subset;

		FontEntry(final FontSource source, final ShapedFont font, final WebFontSubset.Mode mode,
				final boolean oblique, final WebFontSubset subset) {
			this.source = source;
			this.font = font;
			this.mode = mode;
			this.oblique = oblique;
			this.subset = subset;
		}
	}

	private final ResultEmitter emitter;
	/** Subset cache carried across sessions. Null disables carryover. */
	private final PagedSvgFontCarry carry;
	private PagedSvgResourceMode resourceMode = PagedSvgResourceMode.REFERENCE;

	/**
	 * Brotli quality for building shared WOFF2.
	 *
	 * <p>
	 * Fixed at 5. Measurements with a 7.75 MB font: quality 5 took 0.11 seconds for 49.0%,
	 * 9 took 1.04 seconds for 46.4%, and 11 took 13.85 seconds for 43.8%.
	 * <b>11 takes 126 times as long as 5 to save only 5.2 percentage points</b>, which is not worthwhile.
	 * It is not worth exposing as a setting, either.
	 * </p>
	 */
	private static final int FONT_COMPRESSION = 5;
	private final List<FontEntry> fonts = new ArrayList<>();

	/**
	 * Subset scope ({@code output.paged-svg.font-scope}).
	 * {@code PAGE} creates subsets <b>per page</b> and emits them whenever a page closes.
	 * {@code DOCUMENT} spans the entire document, but for EPUB the document unit is an item
	 * (an included XHTML document), so there is one per item (2026-09-02).
	 */
	private PagedSvgFontScope fontScope = PagedSvgFontScope.DOCUMENT;

	/** Starting index of subsets created in the currently open scope (page or document). */
	private int scopeFontsFrom = 0;

	/** Document name in the carryover key. The EPUB item path; empty for a standalone document. */
	private String document = "";
	private final List<FontAsset> emittedFonts = new ArrayList<>();
	private final Map<String, ImageAsset> images = new LinkedHashMap<>();
	private final Map<RenderedImage, OriginalImage> originalImages = new IdentityHashMap<>();
	private final List<PageAsset> pages = new ArrayList<>();
	private final Map<String, FragmentData> fragments = new LinkedHashMap<>();
	private final List<OutlineData> outline = new ArrayList<>();
	private final List<OutlineData> outlineStack = new ArrayList<>();

	PagedSVGResources(final ResultEmitter emitter) {
		this(emitter, null);
	}

	PagedSVGResources(final ResultEmitter emitter, final PagedSvgFontCarry carry) {
		this.emitter = emitter;
		this.carry = carry;
	}

	/** Prefix for shared resource references ({@code output.paged-svg.base-uri}). */
	private String baseUri = "../";

	void setBaseUri(final String baseUri) {
		this.baseUri = baseUri;
	}

	// ---- Image policy (2026-09-03, cti.li request: even photos drawn small in the type area were included at full size)

	private PagedSvgImageCompression imageCompression = PagedSvgImageCompression.NONE;
	private int imageCompressionLossless = 200;
	private int imageMaxWidth = 0;
	private int imageMaxHeight = 0;
	private boolean pageChecksums = true;

	void setImagePolicy(final PagedSvgImageCompression compression, final int lossless, final int maxWidth,
			final int maxHeight) {
		this.imageCompression = compression;
		this.imageCompressionLossless = lossless;
		this.imageMaxWidth = Math.max(0, maxWidth);
		this.imageMaxHeight = Math.max(0, maxHeight);
	}

	/** Maximum pixels per rasterized image (output.image-pixel-limit; negative means unlimited; 2026-10-03) */
	private long rasterPixelLimit = -1;

	void setRasterPixelLimit(final long limit) {
		this.rasterPixelLimit = limit;
	}

	long rasterPixelLimit() {
		return this.rasterPixelLimit;
	}

	void setPageChecksums(final boolean pageChecksums) {
		this.pageChecksums = pageChecksums;
	}

	/** Result URI of the PDF from the same layout (null if absent). The manifest's {@code pdf}. */
	private String pdfUri;

	void setPdfUri(final String pdfUri) {
		this.pdfUri = pdfUri;
	}

	/** An image after applying the policy. */
	private record Encoded(byte[] bytes, String mediaType, String extension, int width, int height) {
	}

	/**
	 * Applies the image policy (downscaling and JPEG recompression). Returns null if no action is needed.
	 * Uses the same criteria as PDF's {@code output.pdf.image.*}: apply lossy compression only if
	 * width + height exceeds the threshold and there is no transparency.
	 * Do not recompress existing JPEGs unless downscaling.
	 */
	private Encoded applyImagePolicy(final RenderedImage rendered, final String mediaType, final int width,
			final int height) throws IOException {
		double scale = 1;
		if (this.imageMaxWidth > 0 && width > this.imageMaxWidth) {
			scale = Math.min(scale, (double) this.imageMaxWidth / width);
		}
		if (this.imageMaxHeight > 0 && height > this.imageMaxHeight) {
			scale = Math.min(scale, (double) this.imageMaxHeight / height);
		}
		final boolean alpha = rendered.getColorModel() != null && rendered.getColorModel().hasAlpha();
		final boolean jpegWanted = this.imageCompression == PagedSvgImageCompression.JPEG && !alpha
				&& width + height > this.imageCompressionLossless;
		final boolean alreadyJpeg = "image/jpeg".equals(mediaType);
		if (scale >= 1 && (!jpegWanted || alreadyJpeg)) {
			return null;
		}
		final java.awt.image.BufferedImage source = toBuffered(rendered);
		java.awt.image.BufferedImage out = source;
		int w = width;
		int h = height;
		if (scale < 1) {
			w = Math.max(1, (int) Math.round(width * scale));
			h = Math.max(1, (int) Math.round(height * scale));
			out = new java.awt.image.BufferedImage(w, h, alpha ? java.awt.image.BufferedImage.TYPE_INT_ARGB
					: java.awt.image.BufferedImage.TYPE_INT_RGB);
			final java.awt.Graphics2D g = out.createGraphics();
			try {
				g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
						java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(source, 0, 0, w, h, null);
			} finally {
				g.dispose();
			}
		}
		final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
		// Keep downscaled JPEGs as JPEGs (do not inflate them with lossless encoding).
		if (jpegWanted || (alreadyJpeg && !alpha)) {
			if (out.getType() != java.awt.image.BufferedImage.TYPE_INT_RGB) {
				final java.awt.image.BufferedImage rgb = new java.awt.image.BufferedImage(w, h,
						java.awt.image.BufferedImage.TYPE_INT_RGB);
				final java.awt.Graphics2D g = rgb.createGraphics();
				try {
					g.setColor(java.awt.Color.WHITE);
					g.fillRect(0, 0, w, h);
					g.drawImage(out, 0, 0, null);
				} finally {
					g.dispose();
				}
				out = rgb;
			}
			final javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
			try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(buffer)) {
				final javax.imageio.ImageWriteParam params = writer.getDefaultWriteParam();
				params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
				params.setCompressionQuality(.8f);
				writer.setOutput(ios);
				writer.write(null, new javax.imageio.IIOImage(out, null, null), params);
			} finally {
				writer.dispose();
			}
			return new Encoded(buffer.toByteArray(), "image/jpeg", "jpg", w, h);
		}
		javax.imageio.ImageIO.write(out, "png", buffer);
		return new Encoded(buffer.toByteArray(), "image/png", "png", w, h);
	}

	private static java.awt.image.BufferedImage toBuffered(final RenderedImage rendered) {
		if (rendered instanceof final java.awt.image.BufferedImage buffered) {
			return buffered;
		}
		final java.awt.image.BufferedImage buffered = new java.awt.image.BufferedImage(rendered.getWidth(),
				rendered.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = buffered.createGraphics();
		try {
			g.drawRenderedImage(rendered, new AffineTransform());
		} finally {
			g.dispose();
		}
		return buffered;
	}

	String getBaseUri() {
		return this.baseUri;
	}

	void setResourceMode(final PagedSvgResourceMode mode) {
		this.resourceMode = mode;
	}

	void setFontScope(final PagedSvgFontScope scope) {
		this.fontScope = scope;
	}

	PagedSvgFontScope getFontScope() {
		return this.fontScope;
	}

	/** Document name in the carryover key (the EPUB item path). */
	void setDocument(final String document) {
		this.document = document == null ? "" : document;
	}

	/**
	 * Builds and emits subsets for the open scope (page or document), then closes the scope
	 * (2026-09-02).
	 *
	 * <p>
	 * With {@code font-scope: page}, call <b>before the page SVG</b>. The consumer has the glyphs
	 * when the page arrives and can draw it immediately. With {@code document}, this is called
	 * at the end of the document ({@link #emitFonts()}); for an EPUB item, output follows that
	 * item's pages and precedes the next item's pages.
	 * </p>
	 *
	 * <p>
	 * Do not emit subsets whose carried version suffices: they have already been emitted before
	 * the first page ({@link #emitCarriedFonts()}). Build and emit expanded and new subsets,
	 * even with {@code omit}, because the consumer cannot already have their glyphs.
	 * Versioned names also prevent confusion with previous resources (in measurements on
	 * 2026-08-28, omitting resources with sequential names made digits disappear or move
	 * after reconversion with a changed font size). Store the completed sequence in
	 * {@link PagedSvgFontCarry} for emission ahead of the next conversion.
	 * Carryover is meaningful only for {@code document} scope (per-page subsets cannot be
	 * reused when pagination changes in the next conversion).
	 * </p>
	 */
	void closeFontScope() throws IOException {
		final int from = this.scopeFontsFrom;
		final int to = this.fonts.size();
		this.scopeFontsFrom = to;
		if (from >= to) {
			return;
		}
		final List<FontEntry> scope = this.fonts.subList(from, to);
		// Subsets are independent, so build them together. Higher Brotli quality is
		// dramatically slower, so run in parallel where possible.
		// Perform output in order to preserve the manifest sequence.
		final int quality = FONT_COMPRESSION;
		final List<byte[]> built;
		try {
			built = scope.parallelStream().map(entry -> {
				try {
					final WebFontSubset subset = entry.subset;
					return subset.seeded() && !subset.grown() ? null : subset.build(quality);
				} catch (final IOException e) {
					throw new UncheckedIOException(e);
				}
			}).toList();
		} catch (final UncheckedIOException e) {
			throw e.getCause();
		}
		for (int i = 0; i < scope.size(); ++i) {
			final WebFontSubset subset = scope.get(i).subset;
			if (subset.seeded()) {
				// Carried version. Already emitted (not emitted with omit).
				this.emittedFonts.add(new FontAsset(subset, subset.seededUri(), subset.seededSha256(),
						subset.seededBytes().length, this.resourceMode == PagedSvgResourceMode.OMIT));
			}
			final byte[] bytes = built.get(i);
			if (bytes == null) {
				continue;
			}
			this.emitter.emit(subset.uri(), "font/woff2", bytes);
			this.emittedFonts.add(new FontAsset(subset, subset.uri(), sha256(bytes), bytes.length, false));
			if (this.fontScope == PagedSvgFontScope.PAGE) {
				// In per-page scope, never rebuild emitted subsets. Retaining outlines would
				// grow memory with page count × font count (design review §3-6).
				subset.releaseShapes();
			}
		}
		if (this.carriesFonts()) {
			for (int i = 0; i < scope.size(); ++i) {
				final WebFontSubset subset = scope.get(i).subset;
				final byte[] bytes = built.get(i);
				this.carry.put(subset.carryKey(this.document), bytes == null
						? new PagedSvgFontCarry.Entry(subset.id(), subset.version(), subset.gids(),
								subset.seededBytes(), subset.seededSha256())
						: new PagedSvgFontCarry.Entry(subset.id(), subset.version(), subset.gids(), bytes,
								sha256(bytes)));
			}
		}
	}

	/**
	 * Whether to use carryover. Only for {@code document} scope. Carrying per-page subsets
	 * would seed every page with the same key and emit the same URI on every page
	 * (noted in the design review on 2026-09-02).
	 */
	private boolean carriesFonts() {
		return this.carry != null && this.fontScope == PagedSvgFontScope.DOCUMENT;
	}

	/** Whether to reference source URLs directly ({@code resources=source}). */
	boolean referencesSources() {
		return this.resourceMode == PagedSvgResourceMode.SOURCE;
	}

	/**
	 * An image that references its source URL directly (2026-09-02, {@code resources=source}).
	 * Do not emit its data; record the source as {@code source} in the manifest. Derive identity
	 * (sha256) from the received bytes (different content at the same source is a different resource).
	 */
	ImageAsset sourceImage(final URI source, final RenderedImage rendered, final byte[] fallbackPng,
			final int width, final int height) throws IOException {
		final OriginalImage original = this.originalImages.get(rendered);
		final byte[] bytes = original == null ? fallbackPng : original.bytes;
		final String mediaType = original == null ? "image/png" : original.mediaType;
		final String hash = sha256(bytes);
		ImageAsset image = this.images.get(hash);
		if (image == null) {
			image = new ImageAsset(source.toString(), hash, mediaType, width, height, true, this.baseUri,
					source.toString());
			this.images.put(hash, image);
		}
		return image;
	}

	boolean hasOriginal(final RenderedImage image) {
		return this.originalImages.containsKey(image);
	}

	WebFontSubset font(final FontSource source, final ShapedFont font, final WebFontSubset.Mode mode,
			final boolean oblique) {
		// Search **only subsets created in the open scope**. Subsets from previous scopes
		// (pages or items) have already been emitted and cannot grow.
		for (final FontEntry entry : this.fonts.subList(this.scopeFontsFrom, this.fonts.size())) {
			if (entry.source == source && entry.font == font && entry.mode == mode && entry.oblique == oblique) {
				return entry.subset;
			}
		}
		final WebFontSubset subset;
		if (this.carry == null) {
			subset = new WebFontSubset(this.fonts.size() + 1, source, font, mode, oblique);
		} else if (!this.carriesFonts()) {
			// Use numbers that remain unique across conversions.
			subset = new WebFontSubset(this.carry.allocateId(this.document), source, font, mode, oblique);
		} else {
			final PagedSvgFontCarry.Key key = new PagedSvgFontCarry.Key(this.document, source.getFontName(),
					mode.name(), oblique);
			final PagedSvgFontCarry.Entry carried = this.carry.get(key);
			if (carried == null) {
				subset = new WebFontSubset(this.carry.allocateId(this.document), source, font, mode, oblique);
			} else {
				// Assign code points in the same order as before. As long as the previous glyphs suffice,
				// the previous bytes emitted before the first page can be reused unchanged.
				subset = new WebFontSubset(carried.id(), carried.version(), source, font, mode, oblique);
				subset.seed(carried.gids(), carried.bytes(), carried.sha256());
			}
		}
		this.fonts.add(new FontEntry(source, font, mode, oblique, subset));
		return subset;
	}

	/**
	 * Emits carried subsets <b>before the first page</b> (2026-08-29).
	 *
	 * <p>
	 * The fonts needed are unknown until drawing, but relaying out the same book typically
	 * uses the same set. Unused subsets are small (measured at 0.1 MB each), so emit them all
	 * in advance. With {@code resources=omit}, the consumer has them from the previous conversion,
	 * so do not emit them; mark them {@code omitted} in the manifest.
	 * </p>
	 */
	void emitCarriedFonts() throws IOException {
		if (!this.carriesFonts() || this.resourceMode == PagedSvgResourceMode.OMIT) {
			return;
		}
		for (final PagedSvgFontCarry.Entry entry : this.carry.entries(this.document)) {
			this.emitter.emit(WebFontSubset.uri(entry.id(), entry.version()), "font/woff2", entry.bytes());
		}
	}

	void rememberOriginal(final RenderedImage image, final byte[] bytes, final String mediaType,
			final String extension) {
		this.originalImages.put(image, new OriginalImage(bytes, mediaType, extension));
	}

	ImageAsset image(final RenderedImage rendered, final byte[] fallbackPng, final int inputWidth,
			final int inputHeight) throws IOException {
		final OriginalImage original = this.originalImages.get(rendered);
		byte[] bytes = original == null ? fallbackPng : original.bytes;
		String mediaType = original == null ? "image/png" : original.mediaType;
		String extension = original == null ? "png" : original.extension;
		int width = inputWidth;
		int height = inputHeight;
		final Encoded encoded = this.applyImagePolicy(rendered, mediaType, inputWidth, inputHeight);
		if (encoded != null) {
			bytes = encoded.bytes;
			mediaType = encoded.mediaType;
			extension = encoded.extension;
			width = encoded.width;
			height = encoded.height;
		}
		final String hash = sha256(bytes);
		ImageAsset image = this.images.get(hash);
		if (image == null) {
			if (this.resourceMode == PagedSvgResourceMode.EMBED) {
				// Make the page SVG self-contained. Do not put the data in separate files.
				final String data = "data:" + mediaType + ";base64,"
						+ java.util.Base64.getEncoder().encodeToString(bytes);
				image = new ImageAsset(data, hash, mediaType, width, height, false, this.baseUri);
				this.images.put(hash, image);
				return image;
			}
			final boolean omit = this.resourceMode == PagedSvgResourceMode.OMIT;
			image = new ImageAsset("assets/images/" + hash + '.' + extension, hash, mediaType, width, height, omit,
					this.baseUri);
			if (!omit) {
				this.emitter.emit(image.uri, mediaType, bytes);
			}
			this.images.put(hash, image);
		}
		return image;
	}

	/** Destination for recording resource identities in the dimension table (set by PagedSVGUserAgent). */
	private java.util.function.BiConsumer<URI, ImageAsset> assetRecorder;

	void setAssetRecorder(final java.util.function.BiConsumer<URI, ImageAsset> recorder) {
		this.assetRecorder = recorder;
	}

	/**
	 * Records the resource identity of a drawn image, associated with its source URI
	 * (2026-08-28). {@link SourcedImage} carries the URI.
	 */
	void rememberAssetOf(final net.zamasoft.pdfg2d.gc.image.Image image, final ImageAsset asset) {
		if (this.assetRecorder == null || asset.uri().startsWith("data:")) {
			return;
		}
		net.zamasoft.pdfg2d.gc.image.Image i = image;
		while (i != null) {
			if (i instanceof final SourcedImage sourced) {
				this.assetRecorder.accept(sourced.uri, asset);
				return;
			}
			i = i instanceof final net.zamasoft.pdfg2d.gc.image.WrappedImage wrapped ? wrapped.getImage() : null;
		}
	}

	/**
	 * Registers a resource written by the previous output without reading its bytes
	 * (2026-08-28, for reconversion with {@code resources=omit}). Its data is not emitted,
	 * so treat it as {@code omitted} and write the same identity as before in the manifest.
	 */
	ImageAsset knownImage(final net.zamasoft.foliojet.ua.ImageMetricsCache.Asset known) {
		final ImageAsset existing = this.images.get(known.sha256());
		if (existing != null) {
			return existing;
		}
		final ImageAsset image = new ImageAsset("assets/images/" + known.sha256() + '.' + known.extension(),
				known.sha256(), known.mediaType(), known.pixelWidth(), known.pixelHeight(), true, this.baseUri);
		this.images.put(known.sha256(), image);
		return image;
	}

	/**
	 * Adds a page to the list. Pages deferred for one-pass target-counter() (2026-10-04)
	 * arrive later, so sort by page number.
	 */
	void addPage(final PageAsset page) {
		int i = this.pages.size();
		while (i > 0 && this.pages.get(i - 1).number() > page.number()) {
			--i;
		}
		this.pages.add(i, page);
	}

	void addLink(final PageData page, final java.awt.Shape shape, final String href, final String contents) {
		final Rectangle2D bounds = shape.getBounds2D();
		page.links.add(new LinkData(href, contents, bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(),
				bounds.getMaxY()));
	}

	// ---- Audio and video (2026-10-15, TECH-20261015-005)

	/** Writes one result as a stream (media files can be large). Set by PagedSVGUserAgent; null emits no media. */
	@FunctionalInterface
	interface StreamEmitter {
		void emit(String uri, String mimeType, PagedSVGUserAgent.ContentWriter content) throws IOException;
	}

	private StreamEmitter streamEmitter;

	void setStreamEmitter(final StreamEmitter streamEmitter) {
		this.streamEmitter = streamEmitter;
	}

	/** Media files by content hash, and the outcome of each source address (null for one that failed). */
	private final Map<String, MediaAsset> media = new LinkedHashMap<>();
	private final Map<String, MediaAsset> mediaBySource = new java.util.HashMap<>();

	/**
	 * Adds a playable element to the page: its readable sources in order and its place. Sources go through the user
	 * agent's resolver, so the input ACL applies even to those referenced at their address.
	 */
	void addMedia(final net.zamasoft.foliojet.ua.UserAgent ua, final PageData page, final java.awt.Shape shape,
			final net.zamasoft.foliojet.objects.media.MediaImage media) {
		if (this.streamEmitter == null) {
			return;
		}
		final List<MediaSourceRef> sources = new ArrayList<>();
		for (final net.zamasoft.foliojet.objects.media.MediaImage.Source source : media.sources) {
			final MediaAsset asset = this.mediaAsset(ua, source.uri(), source.type());
			if (asset != null) {
				final String type = source.type() != null && !source.type().isBlank() ? source.type().trim()
						: asset.mediaType;
				sources.add(new MediaSourceRef(asset, type));
			}
		}
		if (sources.isEmpty()) {
			return;
		}
		final Rectangle2D bounds = shape.getBounds2D();
		page.media.add(new MediaData(media.tag, sources, media.controls, media.autoplay, media.loop, media.muted,
				media.getPoster() != null, media.label, bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(),
				bounds.getMaxY()));
	}

	/**
	 * The file of one source, or null if it cannot be read (warned once per address). Only reading fails this way:
	 * a failure to write the result or an abort ends the conversion as for any other result (codex review).
	 */
	private MediaAsset mediaAsset(final net.zamasoft.foliojet.ua.UserAgent ua, final URI uri, final String type) {
		final String key = uri.toString();
		if (this.mediaBySource.containsKey(key)) {
			return this.mediaBySource.get(key);
		}
		MediaAsset asset = null;
		try {
			asset = this.loadMedia(ua, uri, type);
		} catch (final IOException e) {
			ua.message(net.zamasoft.foliojet.message.MessageCodes.WARN_MISSING_MEDIA, withoutUserInfo(uri),
					String.valueOf(e.getMessage()));
		}
		this.mediaBySource.put(key, asset);
		return asset;
	}

	private MediaAsset loadMedia(final net.zamasoft.foliojet.ua.UserAgent ua, final URI uri, final String type)
			throws IOException {
		final net.zamasoft.zstream.resolver.Source source = ua.resolve(uri);
		try {
			final String mediaType = mediaType(type, source.getMimeType(), uri);
			final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
			if (this.resourceMode == PagedSvgResourceMode.SOURCE && (scheme.equals("http") || scheme.equals("https"))) {
				// The reader fetches it from where it is; the resolver has checked the address against the ACL.
				return new MediaAsset(key(uri), null, mediaType, -1, true, this.baseUri, uri.toString());
			}
			// Hash while copying to a temporary file: the name needs the hash, and the file may be large. The copy keeps
			// to input.resource-size-limit by itself: a file inside an EPUB comes through the book's own resolver,
			// past the byte budget of external resources (codex review).
			final long limit = net.zamasoft.foliojet.ua.props.UAProps.INPUT_RESOURCE_SIZE_LIMIT.getLong(ua);
			final java.nio.file.Path temp = java.nio.file.Files.createTempFile("copper-media-", ".tmp");
			try {
				final MessageDigest digest = MessageDigest.getInstance("SHA-256");
				long bytes = 0;
				try (java.io.InputStream in = source.getInputStream();
						java.io.OutputStream out = new java.security.DigestOutputStream(
								java.nio.file.Files.newOutputStream(temp), digest)) {
					final byte[] buffer = new byte[64 * 1024];
					for (int n; (n = in.read(buffer)) != -1;) {
						bytes += n;
						if (limit >= 0 && bytes > limit) {
							throw new IOException("too-large > " + limit);
						}
						out.write(buffer, 0, n);
						ua.checkAbort(jp.cssj.cti2.CTISession.ABORT_NORMAL);
					}
				}
				final String hash = HexFormat.of().formatHex(digest.digest());
				MediaAsset asset = this.media.get(hash);
				if (asset == null) {
					final boolean omit = this.resourceMode == PagedSvgResourceMode.OMIT;
					asset = new MediaAsset("assets/media/" + hash + '.' + extension(mediaType, uri), hash, mediaType,
							bytes, omit, this.baseUri, null);
					if (!omit) {
						try {
							this.streamEmitter.emit(asset.uri, mediaType, out -> java.nio.file.Files.copy(temp, out));
						} catch (final IOException e) {
							// Writing the result failed: not a missing file.
							throw new java.io.UncheckedIOException(e);
						}
					}
					this.media.put(hash, asset);
				}
				return asset;
			} catch (final NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			} finally {
				java.nio.file.Files.deleteIfExists(temp);
			}
		} finally {
			ua.release(source);
		}
	}

	private static String key(final URI uri) {
		return uri.toString();
	}

	private static String withoutUserInfo(final URI uri) {
		if (uri.getRawUserInfo() == null) {
			return uri.toString();
		}
		try {
			return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(),
					uri.getFragment()).toString();
		} catch (final java.net.URISyntaxException e) {
			return "";
		}
	}

	private static final Map<String, String> MEDIA_TYPES = Map.ofEntries(Map.entry("mp3", "audio/mpeg"),
			Map.entry("m4a", "audio/mp4"), Map.entry("aac", "audio/aac"), Map.entry("wav", "audio/wav"),
			Map.entry("oga", "audio/ogg"), Map.entry("ogg", "audio/ogg"), Map.entry("opus", "audio/ogg"),
			Map.entry("flac", "audio/flac"), Map.entry("mp4", "video/mp4"), Map.entry("m4v", "video/mp4"),
			Map.entry("webm", "video/webm"), Map.entry("ogv", "video/ogg"), Map.entry("mov", "video/quicktime"));

	/** The {@code type} attribute without parameters, else the source's type, else by the file extension. */
	static String mediaType(final String type, final String sourceType, final URI uri) {
		if (type != null && !type.isBlank()) {
			return type.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
		}
		if (sourceType != null && !sourceType.isBlank() && !sourceType.startsWith("application/octet-stream")
				&& !sourceType.startsWith("text/")) {
			return sourceType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
		}
		final String byName = MEDIA_TYPES.get(pathExtension(uri));
		return byName != null ? byName : "application/octet-stream";
	}

	private static String extension(final String mediaType, final URI uri) {
		final String byPath = pathExtension(uri);
		if (MEDIA_TYPES.containsKey(byPath)) {
			return byPath;
		}
		for (final Map.Entry<String, String> entry : new java.util.TreeMap<>(MEDIA_TYPES).entrySet()) {
			if (entry.getValue().equals(mediaType)) {
				return entry.getKey();
			}
		}
		return "bin";
	}

	private static String pathExtension(final URI uri) {
		final String path = uri.getPath() != null ? uri.getPath() : uri.getSchemeSpecificPart();
		if (path == null) {
			return "";
		}
		final int slash = path.lastIndexOf('/');
		final int dot = path.lastIndexOf('.');
		return dot > slash && dot + 1 < path.length() ? path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
	}

	void addFragment(final PageData page, final String id, final Point2D location) {
		final FragmentData fragment = new FragmentData(id, page.number, location.getX(), location.getY());
		page.fragments.add(fragment);
		this.fragments.putIfAbsent(id, fragment);
	}

	void startOutline(final PageData page, final String title, final Point2D location) {
		final OutlineData item = new OutlineData(title, page.number, location.getX(), location.getY());
		if (this.outlineStack.isEmpty()) {
			this.outline.add(item);
		} else {
			this.outlineStack.get(this.outlineStack.size() - 1).children.add(item);
		}
		this.outlineStack.add(item);
	}

	void endOutline() {
		if (!this.outlineStack.isEmpty()) {
			this.outlineStack.remove(this.outlineStack.size() - 1);
		}
	}

	/**
	 * Writes subsets not yet emitted at the end of the document.
	 * In per-page mode, subsets are emitted whenever a page closes, so nothing remains here.
	 */
	void emitFonts() throws IOException {
		this.closeFontScope();
	}

	/**
	 * Builds subsets and returns a map of {@code data:} URIs ({@code subset URI → data:})
	 * (B-1, 2026-08-29). For self-contained SVG: resources are included in the SVG
	 * instead of being emitted as results.
	 */
	Map<String, String> inlineFontSources() throws IOException {
		final Map<String, String> sources = new LinkedHashMap<>();
		for (final FontEntry entry : this.fonts) {
			final WebFontSubset subset = entry.subset;
			final byte[] bytes = subset.build(FONT_COMPRESSION);
			sources.put(subset.uri(), "data:font/woff2;base64,"
					+ java.util.Base64.getEncoder().encodeToString(bytes));
		}
		return sources;
	}

	byte[] manifest(final Map<String, String> metadata, final String binding, final String pageProgression) {
		final StringBuilder json = new StringBuilder(1024 + this.pages.size() * 180);
		json.append("{\n  \"version\":1,\n  \"mediaType\":\"application/vnd.copper.paged-svg\",")
				.append("\n  \"pageCount\":").append(this.pages.size()).append(",\n  \"binding\":");
		json.append(JsonText.quoted(binding));
		// Page progression direction (2026-09-02). Vertical writing uses rtl even with binding single.
		// The reader uses this, rather than binding, to order pages (cti.li request).
		json.append(",\n  \"pageProgressionDirection\":");
		json.append(JsonText.quoted(pageProgression));
		if (this.pdfUri != null) {
			json.append(",\n  \"pdf\":");
			json.append(JsonText.quoted(this.pdfUri));
		}
		json.append(",\n  \"metadata\":{");
		int index = 0;
		for (final var entry : metadata.entrySet()) {
			if (index++ != 0) {
				json.append(',');
			}
			json.append("\n    ");
			json.append(JsonText.quoted(entry.getKey()));
			json.append(':');
			json.append(JsonText.quoted(entry.getValue()));
		}
		if (!metadata.isEmpty()) {
			json.append('\n');
		}
		json.append("  },\n  \"fonts\":[");
		for (int i = 0; i < this.emittedFonts.size(); ++i) {
			final FontAsset font = this.emittedFonts.get(i);
			if (i != 0) {
				json.append(',');
			}
			json.append("\n    {\"family\":");
			json.append(JsonText.quoted(font.subset.family()));
			json.append(",\"source\":");
			json.append(JsonText.quoted(font.subset.sourceName()));
			json.append(",\"uri\":");
			json.append(JsonText.quoted(font.uri));
			json.append(",\"sha256\":\"").append(font.sha256).append("\",\"bytes\":").append(font.bytes);
			if (font.omitted) {
				json.append(",\"omitted\":true");
			}
			json.append(",\"glyphs\":").append(font.subset.glyphCount() - 1)
					.append(",\"fsType\":").append(font.subset.embeddingLicenseFlags()).append('}');
		}
		json.append("\n  ],\n  \"images\":[");
		index = 0;
		for (final ImageAsset image : this.images.values()) {
			if (index++ != 0) {
				json.append(',');
			}
			json.append("\n    {\"uri\":");
			json.append(JsonText.quoted(image.uri));
			json.append(",\"sha256\":\"").append(image.sha256).append("\",\"mediaType\":");
			json.append(JsonText.quoted(image.mediaType));
			json.append(",\"width\":").append(image.width)
					.append(",\"height\":").append(image.height);
			if (image.omitted) {
				json.append(",\"omitted\":true");
			}
			if (image.source != null) {
				json.append(",\"source\":");
				json.append(JsonText.quoted(image.source));
			}
			json.append('}');
		}
		json.append("\n  ],");
		if (!this.media.isEmpty() || this.mediaBySource.values().stream().anyMatch(m -> m != null && m.source != null)) {
			// Audio and video files (2026-10-15), as images; a source reference has no hash.
			json.append("\n  \"media\":[");
			index = 0;
			final java.util.Set<MediaAsset> listed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			for (final MediaAsset media : this.mediaBySource.values()) {
				if (media == null || !listed.add(media)) {
					continue;
				}
				if (index++ != 0) {
					json.append(',');
				}
				json.append("\n    {\"uri\":");
				json.append(JsonText.quoted(media.uri));
				if (media.sha256 != null) {
					json.append(",\"sha256\":\"").append(media.sha256).append('"');
				}
				json.append(",\"mediaType\":");
				json.append(JsonText.quoted(media.mediaType));
				if (media.bytes >= 0) {
					json.append(",\"bytes\":").append(media.bytes);
				}
				if (media.omitted) {
					json.append(",\"omitted\":true");
				}
				if (media.source != null) {
					json.append(",\"source\":");
					json.append(JsonText.quoted(media.source));
				}
				json.append('}');
			}
			json.append("\n  ],");
		}
		json.append("\n  \"anchors\":{");
		index = 0;
		for (final FragmentData fragment : this.fragments.values()) {
			if (index++ != 0) {
				json.append(',');
			}
			json.append("\n    ");
			json.append(JsonText.quoted(fragment.id));
			json.append(":{\"page\":").append(fragment.page).append(",\"x\":")
					.append(JsonText.number(fragment.x)).append(",\"y\":").append(JsonText.number(fragment.y)).append('}');
		}
		if (!this.fragments.isEmpty()) {
			json.append('\n');
		}
		json.append("  },\n  \"outline\":[");
		appendOutline(json, this.outline, 2);
		json.append("\n  ],\n  \"pages\":[");
		for (int i = 0; i < this.pages.size(); ++i) {
			final PageAsset page = this.pages.get(i);
			if (i != 0) {
				json.append(',');
			}
			json.append("\n    {\"number\":").append(page.number).append(",\"width\":")
					.append(JsonText.number(page.width)).append(",\"height\":").append(JsonText.number(page.height)).append(",\"svg\":");
			json.append(JsonText.quoted(page.svgUri));
			if (this.pageChecksums) {
				json.append(",\"svgSha256\":\"").append(page.svgSha256).append('"');
			}
			json.append(",\"data\":");
			json.append(JsonText.quoted(page.jsonUri));
			if (this.pageChecksums) {
				json.append(",\"dataSha256\":\"").append(page.jsonSha256).append('"');
			}
			json.append('}');
		}
		json.append("\n  ]\n}\n");
		return json.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void appendOutline(final StringBuilder json, final List<OutlineData> items, final int depth) {
		for (int i = 0; i < items.size(); ++i) {
			final OutlineData item = items.get(i);
			if (i != 0) {
				json.append(',');
			}
			json.append('\n').append("  ".repeat(depth)).append("{\"title\":");
			json.append(JsonText.quoted(item.title == null ? "" : item.title));
			json.append(",\"page\":").append(item.page).append(",\"x\":").append(JsonText.number(item.x))
					.append(",\"y\":").append(JsonText.number(item.y));
			if (!item.children.isEmpty()) {
				json.append(",\"children\":[");
				appendOutline(json, item.children, depth + 1);
				json.append('\n').append("  ".repeat(depth)).append(']');
			}
			json.append('}');
		}
	}

	static String sha256(final byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
