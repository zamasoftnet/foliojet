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
		 * ページSVGから書く参照先。共有資源には
		 * {@code output.paged-svg.base-uri}の前置きを付け、
		 * 埋め込みはdata:を、取得元参照({@code resources=source})は取得元の
		 * 絶対 URL をそのまま使います。
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

	/**
	 * manifestへ書くフォント資源1件。同じサブセットが育つと、持ち越した版と
	 * 育った版の2件になる(ページは閉じた時点の版を指している)。
	 * {@code omitted}は、受け手が前回の変換から既に持っている版を出さなかった印
	 * ({@code resources=omit})。
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
	/** セッションをまたぐサブセットの控え。nullなら持ち越さない。 */
	private final PagedSvgFontCarry carry;
	private PagedSvgResourceMode resourceMode = PagedSvgResourceMode.REFERENCE;

	/**
	 * 共有WOFF2を作るときのBrotliの品質。
	 *
	 * <p>
	 * 5に固定している。7.75MBのフォントで計った実測では、品質5が0.11秒で49.0%、
	 * 9が1.04秒で46.4%、11が13.85秒で43.8%だった。<b>11は5の126倍の時間をかけて
	 * 5.2ポイント縮めるだけ</b>で、割に合わない。設定として出す価値も無い。
	 * </p>
	 */
	private static final int FONT_COMPRESSION = 5;
	private final List<FontEntry> fonts = new ArrayList<>();

	/**
	 * サブセットの範囲({@code output.paged-svg.font-scope})。
	 * {@code PAGE}なら<b>ページごと</b>に作り、ページを閉じるたびに出す。
	 * {@code DOCUMENT}は文書全体——ただしEPUBでは項目(含まれるXHTML)が
	 * 文書の単位なので、項目ごとに1つになる(2026-09-02)。
	 */
	private PagedSvgFontScope fontScope = PagedSvgFontScope.DOCUMENT;

	/** いま開いている範囲(ページまたは文書)で作ったサブセットの開始位置。 */
	private int scopeFontsFrom = 0;

	/** 持ち越しの鍵に入れる文書の名前。EPUBの項目のパス。単一の文書なら空。 */
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

	/** 共有資源を指すときの前置き({@code output.paged-svg.base-uri})。 */
	private String baseUri = "../";

	void setBaseUri(final String baseUri) {
		this.baseUri = baseUri;
	}

	// ---- 画像の方針(2026-09-03、cti.li の要望: 版面で小さくしか描かれない写真も原寸で入っていた)

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

	void setPageChecksums(final boolean pageChecksums) {
		this.pageChecksums = pageChecksums;
	}

	/** 同じ組版から出した PDF の結果 URI(無ければ null)。manifest の {@code pdf}。 */
	private String pdfUri;

	void setPdfUri(final String pdfUri) {
		this.pdfUri = pdfUri;
	}

	/** 方針を当てた後の画像。 */
	private record Encoded(byte[] bytes, String mediaType, String extension, int width, int height) {
	}

	/**
	 * 画像の方針(縮小・JPEG 再圧縮)を当てます。何もしないなら null。PDF の
	 * {@code output.pdf.image.*} と同じ判定: 幅+高さが閾値を超え、透明部分が
	 * 無いものだけ非可逆に。既に JPEG の画像は縮小しない限り再圧縮しない。
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
		// 縮小した JPEG も JPEG のまま(可逆に膨らませない)
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

	/** 持ち越しの鍵に入れる文書の名前(EPUBの項目のパス)。 */
	void setDocument(final String document) {
		this.document = document == null ? "" : document;
	}

	/**
	 * 開いている範囲(ページまたは文書)のサブセットを組んで出し、範囲を閉じます
	 * (2026-09-02)。
	 *
	 * <p>
	 * {@code font-scope: page}では<b>ページSVGより先に</b>呼ぶ。受け手はページが
	 * 届いた時点で字形を持っているので、そのまま描ける。{@code document}では
	 * 文書の終わり({@link #emitFonts()})で呼ばれ、EPUBの項目ならその項目の
	 * ページのあと、次の項目のページより前に出る。
	 * </p>
	 *
	 * <p>
	 * 持ち越した版で足りたサブセットは、1ページ目より先に出してあるので
	 * ここでは出さない({@link #emitCarriedFonts()})。育ったサブセットと
	 * 初めてのサブセットは組み上げて出す——{@code omit}でも出す。受け手が
	 * 持っているはずのない字形だからで、名前が版付きなので前回のものと
	 * 取り違えることもない(2026-08-28の実測では、連番名のまま省くと文字
	 * サイズを変えた再変換で数字が消えたり別の位置に出たりした)。
	 * 書き終えた並びは{@link PagedSvgFontCarry}へ控え、次の変換で先に出す。
	 * 持ち越しは{@code document}の範囲でだけ意味がある(ページごとの
	 * サブセットは次の変換でページ割りが変わると使えない)。
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
		// サブセットは互いに独立なので、まとめて組み立てる。Brotliは品質を
		// 上げるほど極端に遅くなるので、並べて回せるぶんは回す。
		// 書き出しはmanifestの並びを保つため、順番どおりにやり直す
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
				// 持ち越した版。先に出してある(omitなら出していない)
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
				// ページごとの範囲では出したサブセットを二度と組まない。輪郭を
				// 持ち続けるとページ数×フォント数で膨らむ(設計レビュー §3-6)
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
	 * 持ち越しを使うか。{@code document}の範囲でだけ。ページごとのサブセットを
	 * 持ち越すと、同じ鍵でページごとに種を蒔いて同じURIを毎ページ出してしまう
	 * (2026-09-02の設計レビューで指摘)。
	 */
	private boolean carriesFonts() {
		return this.carry != null && this.fontScope == PagedSvgFontScope.DOCUMENT;
	}

	boolean isEmbedding() {
		return this.resourceMode == PagedSvgResourceMode.EMBED;
	}

	/** 取得元の URL をそのまま参照する設定か({@code resources=source})。 */
	boolean referencesSources() {
		return this.resourceMode == PagedSvgResourceMode.SOURCE;
	}

	/**
	 * 取得元の URL をそのまま参照する画像です(2026-09-02、{@code resources=source})。
	 * 実体は出さず、manifest には取得元を {@code source} として書く。同一性(sha256)は
	 * 受け取ったバイト列から取る(取得元が同じでも内容が違えば別の資源)。
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
		// **開いている範囲で作った分だけ**から探す。前の範囲(ページ・項目)の
		// サブセットは既に出してしまっているので、育てられない
		for (final FontEntry entry : this.fonts.subList(this.scopeFontsFrom, this.fonts.size())) {
			if (entry.source == source && entry.font == font && entry.mode == mode && entry.oblique == oblique) {
				return entry.subset;
			}
		}
		final WebFontSubset subset;
		if (this.carry == null) {
			subset = new WebFontSubset(this.fonts.size() + 1, source, font, mode, oblique);
		} else if (!this.carriesFonts()) {
			// 番号だけは変換をまたいで重ならないものを使う
			subset = new WebFontSubset(this.carry.allocateId(this.document), source, font, mode, oblique);
		} else {
			final PagedSvgFontCarry.Key key = new PagedSvgFontCarry.Key(this.document, source.getFontName(),
					mode.name(), oblique);
			final PagedSvgFontCarry.Entry carried = this.carry.get(key);
			if (carried == null) {
				subset = new WebFontSubset(this.carry.allocateId(this.document), source, font, mode, oblique);
			} else {
				// 前回と同じ並びで符号を割り当てる。前回の字形で足りる限り、
				// 1ページ目より先に出した前回のバイト列がそのまま使える
				subset = new WebFontSubset(carried.id(), carried.version(), source, font, mode, oblique);
				subset.seed(carried.gids(), carried.bytes(), carried.sha256());
			}
		}
		this.fonts.add(new FontEntry(source, font, mode, oblique, subset));
		return subset;
	}

	/**
	 * 持ち越したサブセットを、<b>1ページ目より先に</b>出します(2026-08-29)。
	 *
	 * <p>
	 * どのフォントを使うかは描いてみるまで分からないが、同じ本を組み直す
	 * 典型では前回と同じ集合になる。使われなかった分は小さい(実測0.1MB/件)
	 * ので、全部を先に出す。{@code resources=omit}では受け手が前回の変換から
	 * 持っているので出さず、manifestに{@code omitted}で記す。
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
				// ページSVGだけで完結させる。実体は別ファイルにしない
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

	/** 資源の同一性を寸法表へ控える先(PagedSVGUserAgentが設定)。 */
	private java.util.function.BiConsumer<URI, ImageAsset> assetRecorder;

	void setAssetRecorder(final java.util.function.BiConsumer<URI, ImageAsset> recorder) {
		this.assetRecorder = recorder;
	}

	/**
	 * 描いた画像の資源同一性を、その取得元URIと結び付けて控えます
	 * (2026-08-28)。URIは{@link SourcedImage}が運びます。
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
	 * 前回の出力で書かれた資源を、バイト列を読まずに登録します
	 * (2026-08-28、{@code resources=omit}の再変換用)。実体は出さないので
	 * {@code omitted}として扱い、manifestにも前回と同じ同一性を書きます。
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

	/**
	 * 文書の終わりに、まだ出していないサブセットを書き出します。
	 * ページごとのときはページを閉じるたびに出してあるので、ここでは何も残っていない。
	 */
	void emitFonts() throws IOException {
		this.closeFontScope();
	}

	/**
	 * サブセットを組み立て、{@code data:}のURIの表({@code サブセットのURI → data:})を
	 * 返します(B-1、2026-08-29)。1枚で完結するSVG用で、資源は結果として
	 * 出さずSVGの中へ入ります。
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
		quote(json, binding);
		// 頁の進む向き(2026-09-02)。binding が single でも縦組みなら rtl——
		// 読み器は綴じではなくこれで並べる(cti.li の要望)
		json.append(",\n  \"pageProgressionDirection\":");
		quote(json, pageProgression);
		if (this.pdfUri != null) {
			json.append(",\n  \"pdf\":");
			quote(json, this.pdfUri);
		}
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
			quote(json, font.uri);
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
			quote(json, image.uri);
			json.append(",\"sha256\":\"").append(image.sha256).append("\",\"mediaType\":");
			quote(json, image.mediaType);
			json.append(",\"width\":").append(image.width)
					.append(",\"height\":").append(image.height);
			if (image.omitted) {
				json.append(",\"omitted\":true");
			}
			if (image.source != null) {
				json.append(",\"source\":");
				quote(json, image.source);
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
			if (this.pageChecksums) {
				json.append(",\"svgSha256\":\"").append(page.svgSha256).append('"');
			}
			json.append(",\"data\":");
			quote(json, page.jsonUri);
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
