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
 * {@link ImageMetricsCache}のJSON表現です(2026-08-28)。
 *
 * <p>
 * ページ分割SVGの成果物({@code manifest.json}・{@code pages/NNNN.json})と
 * 形式を揃えるため、寸法表もJSONにしました。読む側がブラウザや別の道具に
 * なる場面——書籍ストア、間引き、複数冊のマージ——で扱いが素直になります。
 * 大きさはXMLとほぼ変わりません(実測で91%)。中身がURLと64桁のハッシュで、
 * 記法の差は誤差だからです。
 * </p>
 *
 * <p>
 * <b>記録する幅と高さは出力単位(pt)です。</b> 画素値ではありません——
 * 値は{@code getImage}が返したもの、すなわち{@code output.resolution}による
 * px→pt変換の適用後だからです。EXIFの回転も適用済みです。そのため
 * <b>解像度が違う設定で作った寸法表を混ぜてはいけません</b>。根拠にした
 * {@code output.resolution}を記録し、読み込み時に食い違っていれば
 * その寸法表を丸ごと捨てます。
 * </p>
 *
 * <p>
 * {@code sha256}以下の4つは<b>出力済み資源の同一性</b>です。ページ分割SVGの
 * ページは画像を{@code assets/images/<sha256>.<ext>}という内容ハッシュの
 * 名前で参照するので、これがあると
 * {@code output.paged-svg.resources=omit}の再変換で画像を一度も開かずに
 * 同じ参照を書けます。省略可能です。
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
		// ユーティリティ
	}

	/**
	 * キャッシュの内容をJSONにします。URI順に並べるので、同じ内容からは
	 * 必ず同じバイト列になります。
	 */
	public static byte[] write(final ImageMetricsCache cache, final double resolution) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(256 + cache.size() * 96);
		try (Writer out = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
			out.write("{\n  \"version\": 1,\n  \"resolution\": ");
			out.write(number(resolution));
			out.write(",\n  \"images\": [");
			boolean first = true;
			for (final Map.Entry<String, Image> entry : new TreeMap<>(cache.entries()).entrySet()) {
				final Image image = entry.getValue();
				out.write(first ? "\n    {" : ",\n    {");
				first = false;
				out.write("\"uri\": ");
				quote(out, entry.getKey());
				out.write(", \"width\": ");
				out.write(number(image.getWidth()));
				out.write(", \"height\": ");
				out.write(number(image.getHeight()));
				final ImageMetricsCache.Asset asset = cache.getAsset(entry.getKey());
				if (asset != null) {
					out.write(", \"sha256\": ");
					quote(out, asset.sha256());
					out.write(", \"mediaType\": ");
					quote(out, asset.mediaType());
					out.write(", \"extension\": ");
					quote(out, asset.extension());
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
	 * JSONを読んでキャッシュに入れます。既にある記録は上書きしません——
	 * 実測した寸法のほうが確かなためです。
	 *
	 * @return 読み込んだ件数
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
			// 解像度が違えば寸法の意味が変わる。黙って誤った寸法を使うより
			// 捨てて測り直す
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
	 * この寸法表のためだけのJSON読み手です。
	 *
	 * <p>
	 * 依存を増やさないために手で書いています。読むのは自分が書いた形だけ
	 * なので、数値は{@code double}、オブジェクトは{@link java.util.LinkedHashMap}、
	 * 配列は{@link java.util.ArrayList}へ落とすだけの最小実装です。
	 * 壊れた入力は{@link IOException}にして、呼び出し側(実測へ戻る)へ返します。
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
					// サロゲート対はそのままcharで積む(UTF-16の対がそのまま
					// 対応する符号位置になる)
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

	private static void quote(final Writer out, final String value) throws IOException {
		out.write('"');
		for (int i = 0; i < value.length(); ++i) {
			final char c = value.charAt(i);
			switch (c) {
			case '"' -> out.write("\\\"");
			case '\\' -> out.write("\\\\");
			case '\b' -> out.write("\\b");
			case '\f' -> out.write("\\f");
			case '\n' -> out.write("\\n");
			case '\r' -> out.write("\\r");
			case '\t' -> out.write("\\t");
			default -> {
				if (c < 0x20) {
					out.write(String.format("\\u%04x", (int) c));
				} else {
					out.write(c);
				}
			}
			}
		}
		out.write('"');
	}

	/** 整数は整数のまま書きます(1200.0ではなく1200)。 */
	static String number(final double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value)) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}
}
