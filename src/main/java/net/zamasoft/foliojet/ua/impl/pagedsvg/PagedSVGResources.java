package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.ua.props.PagedSvgResourceMode;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;

/** Per-book resource registry and manifest/page JSON serializer. */
final class PagedSVGResources {
	@FunctionalInterface
	interface ResultEmitter {
		void emit(String uri, String mimeType, byte[] bytes) throws IOException;
	}

	record PageAsset(int number, double width, double height, String svgUri, String svgSha256, String jsonUri,
			String jsonSha256) {
	}

	record ImageAsset(String uri, String sha256, String mediaType, int width, int height, boolean omitted) {
		/**
		 * ページSVGから書く参照先。共有資源は{@code pages/}から見た相対URIに、
		 * 埋め込みはdata:をそのまま使います。
		 */
		String href() {
			return this.uri.startsWith("data:") ? this.uri : "../" + this.uri;
		}
	}

	private record OriginalImage(byte[] bytes, String mediaType, String extension) {
	}

	record LinkData(String href, String contents, double minX, double minY, double maxX, double maxY) {
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

		PageData(final int number, final double width, final double height) {
			this.number = number;
			this.width = width;
			this.height = height;
		}

		void useFont(final WebFontSubset font) {
			this.fonts.put(font.family(), font.uri());
		}

		/**
		 * ページJSONを書き出します。ページSVGと違い1ページ数KBなので、
		 * ここは組み立ててから一度に書きます。
		 */
		void writeJson(final java.io.Writer out) throws IOException {
			out.write(new String(this.json(), StandardCharsets.UTF_8));
		}

		byte[] json() {
			final StringBuilder json = new StringBuilder(256 + this.textRuns.size() * 160);
			json.append("{\n  \"version\":1,\n  \"page\":").append(this.number)
					.append(",\n  \"width\":").append(number(this.width))
					.append(",\n  \"height\":").append(number(this.height)).append(",\n  \"text\":[");
			for (int i = 0; i < this.textRuns.size(); ++i) {
				final TextRun run = this.textRuns.get(i);
				if (i != 0) {
					json.append(',');
				}
				json.append("\n    {\"value\":");
				quote(json, run.text);
				json.append(",\"font\":");
				quote(json, run.font);
				json.append(",\"size\":").append(number(run.fontSize)).append(",\"transform\":[");
				for (int j = 0; j < run.transform.length; ++j) {
					if (j != 0) {
						json.append(',');
					}
					json.append(number(run.transform[j]));
				}
				json.append("],\"bounds\":[").append(number(run.minX)).append(',').append(number(run.minY))
						.append(',').append(number(run.maxX)).append(',').append(number(run.maxY)).append("]}");
			}
			json.append("\n  ],\n  \"links\":[");
			for (int i = 0; i < this.links.size(); ++i) {
				final LinkData link = this.links.get(i);
				if (i != 0) {
					json.append(',');
				}
				json.append("\n    {\"href\":");
				quote(json, link.href);
				if (link.contents != null) {
					json.append(",\"contents\":");
					quote(json, link.contents);
				}
				json.append(",\"bounds\":[").append(number(link.minX)).append(',')
						.append(number(link.minY)).append(',').append(number(link.maxX)).append(',')
						.append(number(link.maxY)).append("]}");
			}
			json.append("\n  ],\n  \"anchors\":[");
			for (int i = 0; i < this.fragments.size(); ++i) {
				final FragmentData fragment = this.fragments.get(i);
				if (i != 0) {
					json.append(',');
				}
				json.append("\n    {\"id\":");
				quote(json, fragment.id);
				json.append(",\"x\":").append(number(fragment.x)).append(",\"y\":")
						.append(number(fragment.y)).append('}');
			}
			json.append("\n  ]\n}\n");
			return json.toString().getBytes(StandardCharsets.UTF_8);
		}
	}

	/** sha256がnull・bytesが-1なら、実体を出力せず参照だけ残した資源です。 */
	private record FontAsset(WebFontSubset subset, String sha256, int bytes) {
		boolean omitted() {
			return this.sha256 == null;
		}
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
	private PagedSvgResourceMode resourceMode = PagedSvgResourceMode.REFERENCE;

	/** 共有WOFF2のBrotli品質。既定は5(11は126倍遅くて5.2ポイント縮むだけ)。 */
	private int fontCompression = 5;
	private final List<FontEntry> fonts = new ArrayList<>();
	private final List<FontAsset> emittedFonts = new ArrayList<>();
	private final Map<String, ImageAsset> images = new LinkedHashMap<>();
	private final Map<RenderedImage, OriginalImage> originalImages = new IdentityHashMap<>();
	private final List<PageAsset> pages = new ArrayList<>();
	private final Map<String, FragmentData> fragments = new LinkedHashMap<>();
	private final List<OutlineData> outline = new ArrayList<>();
	private final List<OutlineData> outlineStack = new ArrayList<>();

	PagedSVGResources(final ResultEmitter emitter) {
		this.emitter = emitter;
	}

	void setFontCompression(final int quality) {
		// Brotliが受ける範囲へ丸める。外れた指定で落とさない
		this.fontCompression = Math.max(1, Math.min(11, quality));
	}

	void setResourceMode(final PagedSvgResourceMode mode) {
		this.resourceMode = mode;
	}

	boolean isEmbedding() {
		return this.resourceMode == PagedSvgResourceMode.EMBED;
	}

	boolean hasOriginal(final RenderedImage image) {
		return this.originalImages.containsKey(image);
	}

	WebFontSubset font(final FontSource source, final ShapedFont font, final WebFontSubset.Mode mode,
			final boolean oblique) {
		for (final FontEntry entry : this.fonts) {
			if (entry.source == source && entry.font == font && entry.mode == mode && entry.oblique == oblique) {
				return entry.subset;
			}
		}
		final WebFontSubset subset = new WebFontSubset(this.fonts.size() + 1, source, font, mode, oblique);
		this.fonts.add(new FontEntry(source, font, mode, oblique, subset));
		return subset;
	}

	void rememberOriginal(final RenderedImage image, final byte[] bytes, final String mediaType,
			final String extension) {
		this.originalImages.put(image, new OriginalImage(bytes, mediaType, extension));
	}

	ImageAsset image(final RenderedImage rendered, final byte[] fallbackPng, final int width, final int height)
			throws IOException {
		final OriginalImage original = this.originalImages.get(rendered);
		final byte[] bytes = original == null ? fallbackPng : original.bytes;
		final String mediaType = original == null ? "image/png" : original.mediaType;
		final String extension = original == null ? "png" : original.extension;
		final String hash = sha256(bytes);
		ImageAsset image = this.images.get(hash);
		if (image == null) {
			if (this.resourceMode == PagedSvgResourceMode.EMBED) {
				// ページSVGだけで完結させる。実体は別ファイルにしない
				final String data = "data:" + mediaType + ";base64,"
						+ java.util.Base64.getEncoder().encodeToString(bytes);
				image = new ImageAsset(data, hash, mediaType, width, height, false);
				this.images.put(hash, image);
				return image;
			}
			final boolean omit = this.resourceMode == PagedSvgResourceMode.OMIT;
			image = new ImageAsset("assets/images/" + hash + '.' + extension, hash, mediaType, width, height, omit);
			if (!omit) {
				this.emitter.emit(image.uri, mediaType, bytes);
			}
			this.images.put(hash, image);
		}
		return image;
	}

	void addPage(final PageAsset page) {
		this.pages.add(page);
	}

	void addLink(final PageData page, final java.awt.Shape shape, final String href, final String contents) {
		final Rectangle2D bounds = shape.getBounds2D();
		page.links.add(new LinkData(href, contents, bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(),
				bounds.getMaxY()));
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

	void emitFonts() throws IOException {
		if (this.resourceMode == PagedSvgResourceMode.OMIT) {
			// build()はBrotli圧縮を伴うので、出力しないなら組み立てもしない。
			// sha256とバイト数はここでしか得られないのでmanifestからも落ちる。
			for (final FontEntry entry : this.fonts) {
				this.emittedFonts.add(new FontAsset(entry.subset, null, -1));
			}
			return;
		}
		// サブセットは互いに独立なので、まとめて組み立てる。Brotliは品質を
		// 上げるほど極端に遅くなるので、並べて回せるぶんは回す。
		// 書き出しはmanifestの並びを保つため、順番どおりにやり直す
		final int quality = this.fontCompression;
		final List<byte[]> built;
		try {
			built = this.fonts.parallelStream().map(entry -> {
				try {
					return entry.subset.build(quality);
				} catch (final IOException e) {
					throw new UncheckedIOException(e);
				}
			}).toList();
		} catch (final UncheckedIOException e) {
			throw e.getCause();
		}
		for (int i = 0; i < this.fonts.size(); ++i) {
			final FontEntry entry = this.fonts.get(i);
			final byte[] bytes = built.get(i);
			this.emitter.emit(entry.subset.uri(), "font/woff2", bytes);
			this.emittedFonts.add(new FontAsset(entry.subset, sha256(bytes), bytes.length));
		}
	}

	byte[] manifest(final Map<String, String> metadata, final String binding) {
		final StringBuilder json = new StringBuilder(1024 + this.pages.size() * 180);
		json.append("{\n  \"version\":1,\n  \"mediaType\":\"application/vnd.copper.paged-svg\",")
				.append("\n  \"pageCount\":").append(this.pages.size()).append(",\n  \"binding\":");
		quote(json, binding);
		json.append(",\n  \"metadata\":{");
		int index = 0;
		for (final var entry : metadata.entrySet()) {
			if (index++ != 0) {
				json.append(',');
			}
			json.append("\n    ");
			quote(json, entry.getKey());
			json.append(':');
			quote(json, entry.getValue());
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
			quote(json, font.subset.family());
			json.append(",\"source\":");
			quote(json, font.subset.sourceName());
			json.append(",\"uri\":");
			quote(json, font.subset.uri());
			if (font.omitted()) {
				json.append(",\"omitted\":true");
			} else {
				json.append(",\"sha256\":\"").append(font.sha256).append("\",\"bytes\":").append(font.bytes);
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
			quote(json, image.uri);
			json.append(",\"sha256\":\"").append(image.sha256).append("\",\"mediaType\":");
			quote(json, image.mediaType);
			json.append(",\"width\":").append(image.width)
					.append(",\"height\":").append(image.height);
			if (image.omitted) {
				json.append(",\"omitted\":true");
			}
			json.append('}');
		}
		json.append("\n  ],\n  \"anchors\":{");
		index = 0;
		for (final FragmentData fragment : this.fragments.values()) {
			if (index++ != 0) {
				json.append(',');
			}
			json.append("\n    ");
			quote(json, fragment.id);
			json.append(":{\"page\":").append(fragment.page).append(",\"x\":")
					.append(number(fragment.x)).append(",\"y\":").append(number(fragment.y)).append('}');
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
					.append(number(page.width)).append(",\"height\":").append(number(page.height)).append(",\"svg\":");
			quote(json, page.svgUri);
			json.append(",\"svgSha256\":\"").append(page.svgSha256).append('"');
			// ページJSONを出さない指定なら、そのURIとハッシュは書かない
			if (page.jsonUri != null) {
				json.append(",\"data\":");
				quote(json, page.jsonUri);
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
			quote(json, item.title == null ? "" : item.title);
			json.append(",\"page\":").append(item.page).append(",\"x\":").append(number(item.x))
					.append(",\"y\":").append(number(item.y));
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

	static String number(final double value) {
		if (value == Math.rint(value)) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}

	static void quote(final StringBuilder out, final String value) {
		out.append('"');
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			switch (ch) {
			case '"' -> out.append("\\\"");
			case '\\' -> out.append("\\\\");
			case '\b' -> out.append("\\b");
			case '\f' -> out.append("\\f");
			case '\n' -> out.append("\\n");
			case '\r' -> out.append("\\r");
			case '\t' -> out.append("\\t");
			default -> {
				if (ch < 0x20) {
					out.append(String.format("\\u%04x", (int) ch));
				} else {
					out.append(ch);
				}
			}
			}
		}
		out.append('"');
	}
}
