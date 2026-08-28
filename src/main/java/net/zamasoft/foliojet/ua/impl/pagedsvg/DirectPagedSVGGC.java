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
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.GlyphAdvances;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * Paged SVG用の、Batikを介さないグラフィックスコンテキストです。
 *
 * <p>
 * 文字は組版で確定したGIDをBMP私用領域へ割り当てた符号位置で書き、共有WOFF2で
 * 表示します。字形を取れない場合(埋め込み不可・カラー字形・単色以外のpaint・
 * PUAを使い切った)は<b>アウトラインへ退避</b>し、元の文字列はページJSONに残します。
 * この判断はBatik版と同じです。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class DirectPagedSVGGC extends DirectSVGGC {
	private final PagedSVGResources resources;

	private final PagedSVGResources.PageData page;

	DirectPagedSVGGC(final SVGWriter writer, final FontManager fonts, final PagedSVGResources resources,
			final PagedSVGResources.PageData page) {
		super(writer, fonts);
		this.resources = resources;
		this.page = page;
		// 敷き詰めの絵も、ふつうの画像と同じ共有資源にする
		this.paints().setImageHrefs(this::assetHref);
	}

	/** 絵を共有資源にして、ページSVGから辿れるURIを返します。書けないならnull。 */
	private String assetHref(final Image image) throws IOException {
		final BufferedImage raster = this.toRaster(image);
		if (raster == null) {
			return null;
		}
		final byte[] png = this.resources.hasOriginal(raster) ? null : encodePng(raster);
		return this.resources.image(raster, png, raster.getWidth(), raster.getHeight()).href();
	}

	/** ベクタ画像を画素へ落とすときの倍率。等倍では拡大時に粗くなる。 */
	private static final double RASTERIZE_SCALE = 4.0;

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		Image original = image;
		while (original instanceof final WrappedImage wrapped) {
			original = wrapped.getImage();
		}
		if (original instanceof final KnownAssetImage known) {
			// 前回の出力の資源をそのまま指す(2026-08-28)。バイト列は読まない
			this.writeImageRef(image, this.resources.knownImage(known.asset).href());
			return;
		}
		if (!(original instanceof RasterImageImpl)) {
			// **まず絵に自分で描かせること。** 箇条書きの黒丸のように、
			// GCの基本操作だけで描ける絵は多い。ここを飛ばして画素へ
			// 落とすと、ベクタで済むものがPNGになって共有資源も増える。
			// Java2Dを直に要求する絵だけが例外を投げるので、それだけ拾う
			try {
				image.drawTo(this);
				return;
			} catch (final ClassCastException e) {
				// G2DGCを要求する絵。下のラスタ化へ回す。
				// 実装は先頭でGCを型変換するので、ここまでに何も描いていない
			}
		}
		final BufferedImage raster = this.toRaster(image);
		try {
			final byte[] png = this.resources.hasOriginal(raster) ? null : encodePng(raster);
			final PagedSVGResources.ImageAsset asset = this.resources.image(raster, png, raster.getWidth(),
					raster.getHeight());
			// 次の再変換で画像を開かずに済むよう、資源の同一性を寸法表へ
			// 控える(2026-08-28)。URIはUAが画像に添えている
			this.resources.rememberAssetOf(image, asset);
			this.writeImageRef(image, asset.href());
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * 画像参照を1つ書きます。
	 *
	 * <p>
	 * 画像は「自分の論理寸法の升目」へ描かれる約束(呼び出し側は
	 * {@code image.getWidth()/getHeight()}で割った倍率を変換に積んでくる)。
	 * 単位矩形でも画素数でもない。ここを取り違えると画像だけが別の大きさで
	 * 出て、しかもXMLとしては妥当なままになる。
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
	 * 絵を画素にします。元のJPEGをそのまま出せるものはここで覚えておきます。
	 * ラスタでないものはJava2Dへ一度描きます(Batikは使いません)。
	 */
	private BufferedImage toRaster(final Image image) throws GraphicsException {
		Image original = image;
		while (original instanceof final WrappedImage wrapped) {
			original = wrapped.getImage();
		}
		if (original instanceof final RasterImageImpl rasterImage) {
			if (original instanceof final EncodedRasterImage encoded) {
				// 元のJPEGをそのまま出せる画像。再圧縮しない
				this.resources.rememberOriginal(encoded.getImage(), encoded.getEncoded(), encoded.getMediaType(),
						encoded.getExtension());
			}
			return rasterImage.getImage();
		}
		return this.rasterize(image);
	}

	/** SVG画像など、直接書けないものをJava2Dで一度描いて画素にします。 */
	private BufferedImage rasterize(final Image image) throws GraphicsException {
		final double iw = Math.max(1e-6, image.getWidth());
		final double ih = Math.max(1e-6, image.getHeight());
		final int w = Math.max(1, (int) Math.ceil(iw * RASTERIZE_SCALE));
		final int h = Math.max(1, (int) Math.ceil(ih * RASTERIZE_SCALE));
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
			// **コアフォントは文字として書く**(2026-08-28)。PDFのコア
			// フォント(Helvetica/Times/Courier)は埋め込む実体が無いので
			// ShapedFontにならず、従来はアウトラインへ落ちていた。SVGでは
			// ブラウザが同等の書体を持っているので、文字のまま置ける——
			// 実測では本文の数字とラテン文字だけが<text>から消えていた
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
		// xAdvance[0]はrun先頭glyphの手前の調整。
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
			minX = Math.min(minX, gx - (vertical ? size / 2.0 : 0));
			maxX = Math.max(maxX, gx + (vertical ? size / 2.0 : size));
			minY = Math.min(minY, gy - (vertical ? 0 : metrics.getAscent()));
			maxY = Math.max(maxY, gy + (vertical ? size : metrics.getDescent()));
		}
		// 符号を割り当てた**後**に@font-faceを登録する。持ち越したサブセットは
		// 新しい字形で版が進みURIが変わるので、割り当て前に登録すると
		// このrunで育った分だけ前の版を指してしまう(2026-08-29)
		this.writer.addFontFace(subset.family(), subset.uri());

		try {
			final SVGWriter w = this.writer;
			this.openTransformGroup();
			w.open("text");
			w.attr("x", xs.toString());
			w.attr("y", ys.toString());
			// **現在の変換を付ける**(2026-08-28)。座標は利用者空間のままなので、
			// 付けないと変換の下にある文字が別の場所へ出る——実測では記事の
			// 見出しがx=7.5(本来43.5)に出てクリップされ、消えていた。
			// 画像(writeImageRef)は以前から付けている
			final java.awt.geom.AffineTransform ctm = this.currentTransform();
			if (!ctm.isIdentity()) {
				w.attr("transform", matrix(ctm));
			}
			w.attr("font-family", subset.family());
			w.attr("font-size", size);
			this.writeBlendMode(w);
			// 字送りは組版側で確定済み。閲覧側が詰めたり合字にしたりすると崩れる
			w.attr("font-kerning", "none");
			w.attr("font-variant-ligatures", "none");
			w.attr("font-feature-settings", "'kern' 0, 'liga' 0");
			w.attr("text-rendering", "geometricPrecision");
			w.attr("role", "img");
			w.attr("aria-label", sourceText);
			w.attr("data-copper-text", sourceText);
			this.writeTextPaint(w, style, source);
			w.closeStart();
			w.text(chars.toString());
			w.end("text");
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}

		this.page.textRuns.add(new PagedSVGResources.TextRun(sourceText, subset.family(), size,
				new AffineTransform(this.currentTransform()), minX, minY, maxX, maxY));
	}

	/**
	 * PDFのコアフォントに対応するCSSのフォント指定。対応が無ければ
	 * {@code null}(従来どおりアウトラインで描く)。
	 *
	 * <p>
	 * SymbolとZapfDingbatsは独自の符号化なので文字としては置けません。
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
	 * コアフォントの文字列を、組版が決めた位置のまま書き出します。
	 * 字形は閲覧側の同等書体で描かれます。
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
			minX = Math.min(minX, gx - (vertical ? size / 2.0 : 0));
			maxX = Math.max(maxX, gx + (vertical ? size / 2.0 : size));
			minY = Math.min(minY, gy - (vertical ? 0 : metrics.getAscent()));
			maxY = Math.max(maxY, gy + (vertical ? size : metrics.getDescent()));
		}
		try {
			final SVGWriter w = this.writer;
			this.openTransformGroup();
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
			// 字送りは組版側で確定済み
			w.attr("font-kerning", "none");
			w.attr("font-variant-ligatures", "none");
			w.attr("font-feature-settings", "'kern' 0, 'liga' 0");
			w.attr("text-rendering", "geometricPrecision");
			this.writeTextPaint(w, style, text.getFontMetrics().getFontSource());
			w.closeStart();
			w.text(value);
			w.end("text");
		} catch (final IOException e) {
			throw new GraphicsException(e);
		}
		this.page.textRuns.add(new PagedSVGResources.TextRun(value, family, size,
				new AffineTransform(this.currentTransform()), minX, minY, maxX, maxY));
	}

	/**
	 * 字形を共有できない文字。見た目を保つためアウトラインで描き、
	 * 元の文字列はページJSONに残します。
	 */
	private void outlineText(final Text text, final String value, final String font, final double x, final double y)
			throws GraphicsException {
		this.recordText(text, value, font, x, y);
		this.drawTextAsOutline(text, x, y);
	}

	private void recordText(final Text text, final String value, final String font, final double x, final double y) {
		final double size = text.getFontStyle().getSize();
		this.page.textRuns.add(new PagedSVGResources.TextRun(value, font, size,
				new AffineTransform(this.currentTransform()), x, y - size, x + size * value.length(), y));
	}

	/**
	 * 文字の塗り。太さの合成(細いフォントで太字を求められたとき縁取りで太らせる)は
	 * Batik版と同じ規則です。
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
