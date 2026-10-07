package net.zamasoft.foliojet.ua;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * JSON representation of {@link ImageMetricsCache} (2026-08-28).
 *
 * <p>
 * The metrics table also uses JSON to match page-split SVG artifacts
 * ({@code manifest.json}/{@code pages/NNNN.json}). This simplifies use when readers are
 * browsers or other tools: bookstores, thinning pages, and merging multiple books.
 * Its size is nearly the same as XML (measured at 91%): the content is URLs and 64-digit hashes,
 * so notation differences are negligible.
 * </p>
 *
 * <p>
 * <b>Recorded width and height use output units (pt).</b> They are not pixel values:
 * they are the values returned by {@code getImage}, after px → pt conversion using
 * {@code output.resolution}. EXIF rotation has also been applied.
 * Therefore, <b>do not mix metrics tables produced with different resolution settings</b>.
 * Records the underlying {@code output.resolution}; if it differs on load,
 * discards the entire metrics table.
 * </p>
 *
 * <p>
 * The four fields starting with {@code sha256} describe <b>the identity of an emitted resource</b>.
 * Page-split SVG pages reference images by content-hash names such as
 * {@code assets/images/<sha256>.<ext>}, so these allow reconversion with
 * {@code output.paged-svg.resources=omit} to write the same reference without opening the image
 * even once. They are optional.
 * </p>
 *
 * <pre>
 * {
 *   "version": 1,
 *   "resolution": 96,
 *   "images": [
 *     {"uri": "https://example.com/a.png", "width": 900, "height": 600,
 *      "sha256": "…", "mediaType": "image/png", "extension": "png",
 *      "pixelWidth": 1200, "pixelHeight": 800}
 *   ]
 * }
 * </pre>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ImageMetricsJSON {

	private ImageMetricsJSON() {
		// Utility
	}

	/**
	 * Converts cache contents to JSON. Sorts by URI, so identical contents always produce
	 * identical byte sequences.
	 */
	public static byte[] write(final ImageMetricsCache cache, final double resolution) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(256 + cache.size() * 96);
		try (Writer out = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
			out.write("{\n  \"version\": 1,\n  \"resolution\": ");
			out.write(JsonText.number(resolution));
			out.write(",\n  \"images\": [");
			boolean first = true;
			for (final Map.Entry<String, Image> entry : new TreeMap<>(cache.entries()).entrySet()) {
				final Image image = entry.getValue();
				out.write(first ? "\n    {" : ",\n    {");
				first = false;
				out.write("\"uri\": ");
				out.write(JsonText.quoted(entry.getKey()));
				out.write(", \"width\": ");
				out.write(JsonText.number(image.getWidth()));
				out.write(", \"height\": ");
				out.write(JsonText.number(image.getHeight()));
				final ImageMetricsCache.Asset asset = cache.getAsset(entry.getKey());
				if (asset != null) {
					out.write(", \"sha256\": ");
					out.write(JsonText.quoted(asset.sha256()));
					out.write(", \"mediaType\": ");
					out.write(JsonText.quoted(asset.mediaType()));
					out.write(", \"extension\": ");
					out.write(JsonText.quoted(asset.extension()));
					out.write(", \"pixelWidth\": ");
					out.write(Integer.toString(asset.pixelWidth()));
					out.write(", \"pixelHeight\": ");
					out.write(Integer.toString(asset.pixelHeight()));
				}
				out.write('}');
			}
			out.write(first ? "]\n}\n" : "\n  ]\n}\n");
		}
		return bytes.toByteArray();
	}

	/**
	 * Reads JSON into the cache. Does not overwrite existing entries, since measured dimensions
	 * are more reliable.
	 *
	 * @return number of entries loaded
	 */
	public static int read(final InputStream in, final ImageMetricsCache cache, final double resolution)
			throws IOException {
		final Parser parser = new Parser(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		final Object root = parser.value();
		if (!(root instanceof final Map<?, ?> doc)) {
			throw new IOException("寸法表の根がオブジェクトではありません");
		}
		final Object recorded = doc.get("resolution");
		if (recorded instanceof final Number r && Math.abs(r.doubleValue() - resolution) >= 1e-6) {
			// Different resolutions change the meaning of dimensions. Discard and remeasure
			// rather than silently use incorrect dimensions
			return 0;
		}
		if (!(doc.get("images") instanceof final java.util.List<?> images)) {
			return 0;
		}
		int count = 0;
		for (final Object element : images) {
			if (!(element instanceof final Map<?, ?> image)) {
				continue;
			}
			if (!(image.get("uri") instanceof final String uri)
					|| !(image.get("width") instanceof final Number width)
					|| !(image.get("height") instanceof final Number height)) {
				continue;
			}
			final double w = width.doubleValue(), h = height.doubleValue();
			if (!(w > 0) || !(h > 0) || cache.get(uri) != null) {
				continue;
			}
			cache.putSize(uri, w, h);
			if (image.get("sha256") instanceof final String sha256
					&& image.get("mediaType") instanceof final String mediaType
					&& image.get("extension") instanceof final String extension
					&& image.get("pixelWidth") instanceof final Number pw
					&& image.get("pixelHeight") instanceof final Number ph
					&& pw.intValue() > 0 && ph.intValue() > 0) {
				cache.putAsset(uri,
						new ImageMetricsCache.Asset(sha256, mediaType, extension, pw.intValue(), ph.intValue()));
			}
			++count;
		}
		return count;
	}

	/**
	 * JSON reader dedicated to this metrics table.
	 *
	 * <p>
	 * Handwritten to avoid adding a dependency. Since it reads only the format it writes,
	 * this minimal implementation simply maps numbers to {@code double}, objects to
	 * {@link java.util.LinkedHashMap}, and arrays to {@link java.util.ArrayList}.
	 * Returns malformed input as {@link IOException} to the caller (which falls back to measurement).
	 * </p>
	 */
	private static final class Parser {
		private final String text;
		private int pos;

		Parser(final String text) {
			this.text = text;
		}

		Object value() throws IOException {
			this.skipSpace();
			if (this.pos >= this.text.length()) {
				throw new IOException("JSONが空です");
			}
			final char c = this.text.charAt(this.pos);
			switch (c) {
			case '{':
				return this.object();
			case '[':
				return this.array();
			case '"':
				return this.string();
			case 't':
				this.expect("true");
				return Boolean.TRUE;
			case 'f':
				this.expect("false");
				return Boolean.FALSE;
			case 'n':
				this.expect("null");
				return null;
			default:
				return this.number();
			}
		}

		private Map<String, Object> object() throws IOException {
			final Map<String, Object> map = new java.util.LinkedHashMap<>();
			++this.pos; // '{'
			this.skipSpace();
			if (this.peek() == '}') {
				++this.pos;
				return map;
			}
			for (;;) {
				this.skipSpace();
				final String key = this.string();
				this.skipSpace();
				if (this.peek() != ':') {
					throw new IOException("':'がありません: " + this.pos);
				}
				++this.pos;
				map.put(key, this.value());
				this.skipSpace();
				final char c = this.peek();
				++this.pos;
				if (c == '}') {
					return map;
				}
				if (c != ',') {
					throw new IOException("','か'}'がありません: " + this.pos);
				}
			}
		}

		private java.util.List<Object> array() throws IOException {
			final java.util.List<Object> list = new java.util.ArrayList<>();
			++this.pos; // '['
			this.skipSpace();
			if (this.peek() == ']') {
				++this.pos;
				return list;
			}
			for (;;) {
				list.add(this.value());
				this.skipSpace();
				final char c = this.peek();
				++this.pos;
				if (c == ']') {
					return list;
				}
				if (c != ',') {
					throw new IOException("','か']'がありません: " + this.pos);
				}
			}
		}

		private String string() throws IOException {
			if (this.peek() != '"') {
				throw new IOException("文字列ではありません: " + this.pos);
			}
			++this.pos;
			final StringBuilder sb = new StringBuilder();
			for (;;) {
				if (this.pos >= this.text.length()) {
					throw new IOException("文字列が閉じていません");
				}
				final char c = this.text.charAt(this.pos++);
				if (c == '"') {
					return sb.toString();
				}
				if (c != '\\') {
					sb.append(c);
					continue;
				}
				if (this.pos >= this.text.length()) {
					throw new IOException("エスケープが閉じていません");
				}
				final char e = this.text.charAt(this.pos++);
				switch (e) {
				case '"', '\\', '/' -> sb.append(e);
				case 'b' -> sb.append('\b');
				case 'f' -> sb.append('\f');
				case 'n' -> sb.append('\n');
				case 'r' -> sb.append('\r');
				case 't' -> sb.append('\t');
				case 'u' -> {
					if (this.pos + 4 > this.text.length()) {
						throw new IOException("\\uが短すぎます");
					}
					// Append surrogate pairs directly as chars (the UTF-16 pair itself represents
					// the corresponding code point)
					sb.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
					this.pos += 4;
				}
				default -> throw new IOException("不正なエスケープ: \\" + e);
				}
			}
		}

		private Double number() throws IOException {
			final int start = this.pos;
			while (this.pos < this.text.length() && "+-.eE0123456789".indexOf(this.text.charAt(this.pos)) >= 0) {
				++this.pos;
			}
			try {
				return Double.valueOf(this.text.substring(start, this.pos));
			} catch (final NumberFormatException e) {
				throw new IOException("数値ではありません: " + this.text.substring(start, this.pos));
			}
		}

		private void expect(final String word) throws IOException {
			if (!this.text.startsWith(word, this.pos)) {
				throw new IOException("予期しない語: " + this.pos);
			}
			this.pos += word.length();
		}

		private char peek() throws IOException {
			if (this.pos >= this.text.length()) {
				throw new IOException("JSONが途中で終わっています");
			}
			return this.text.charAt(this.pos);
		}

		private void skipSpace() {
			while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
				++this.pos;
			}
		}
	}

}
