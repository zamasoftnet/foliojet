package net.zamasoft.foliojet.layout.util;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.layout.part.CenteredImage;
import net.zamasoft.foliojet.ua.impl.pdf.PixelBackedImage;
import net.zamasoft.pdfg2d.g2d.image.RasterImage;
import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;

/**
 * {@code filter}の効果を塗り・色・ラスタ画像へ掛ける小道具です
 * (filter-effects-1、2026-08-29新設)。
 *
 * <p>
 * 色行列は{@link Color}(RGB/RGBA。CMYK・グレー・特色はRGB相当値で
 * 計算し、結果はRGBになる——特色の指定は失われる、記録済み)と、
 * グラデーションの色停止、パターンのラスタ画像に掛ける。ラスタは
 * 復号済み{@link RasterImage}の画素を複製して変換し、画像とフィルタの
 * 組で弱参照キャッシュする(同じ画像が繰り返し描かれる文書で毎回
 * 画素を舐めない)。
 * </p>
 *
 * <p>
 * ぼかしは3回の箱ぼかし(ガウスの近似)で、標準偏差はptから画像の
 * 画素へ換算する(描画時の変換行列の拡大率で割る)。プリマルチプライド
 * で処理し、透明画素の色が縁へ滲まないようにする。
 * </p>
 */
public final class FilterOps {
	private static final Map<Image, Map<String, RasterImageImpl>> CACHE = new WeakHashMap<Image, Map<String, RasterImageImpl>>();

	private FilterOps() {
		// unused
	}

	/** 色行列を色へ掛けます。行列がnullなら元の色。 */
	public static Color apply(final FilterValue filter, final Color color) {
		if (filter.matrix == null || color == null) {
			return color;
		}
		final float[] rgb = FilterValue.apply(filter.matrix, color.getRed(), color.getGreen(), color.getBlue(),
				color.getAlpha());
		final float alpha = color.getAlpha();
		if (alpha >= 1f) {
			return RGBColor.create(rgb[0], rgb[1], rgb[2]);
		}
		return RGBAColor.create(rgb[0], rgb[1], rgb[2], alpha);
	}

	/**
	 * 塗りへ効果を掛けます。
	 *
	 * @param pixelScale ラスタ画像の1画素あたりのpt(ぼかしの換算用)
	 */
	public static Paint apply(final FilterValue filter, final Paint paint, final double pixelScale) {
		if (paint == null) {
			return null;
		}
		return switch (paint) {
		case Color color -> apply(filter, color);
		case LinearGradient g -> filter.matrix == null ? g
				: new LinearGradient(g.x1(), g.y1(), g.x2(), g.y2(), g.fractions(), apply(filter, g.colors()),
						g.transform());
		case RadialGradient g -> filter.matrix == null ? g
				: new RadialGradient(g.cx(), g.cy(), g.radius(), g.fx(), g.fy(), g.fractions(),
						apply(filter, g.colors()), g.transform());
		case Pattern p -> {
			final Image image = apply(filter, p.getImage(), pixelScale);
			yield image == p.getImage() ? p : new Pattern(image, p.getTransform());
		}
		};
	}

	private static Color[] apply(final FilterValue filter, final Color[] colors) {
		final Color[] out = new Color[colors.length];
		for (int i = 0; i < colors.length; ++i) {
			out[i] = apply(filter, colors[i]);
		}
		return out;
	}

	/**
	 * ラスタ画像へ効果を掛けた複製を返します。ラスタでなければ(SVG・
	 * グループ画像・寸法だけのスタブ)元の画像をそのまま返す。
	 *
	 * @param pixelScale 1画素あたりのpt
	 */
	public static Image apply(final FilterValue filter, final Image image, final double pixelScale) {
		if (!filter.hasColorOps()) {
			return image;
		}
		// 包み紙(画素→pt変換のTransformedImage、中央寄せのCenteredImage)は
		// 中身を変換して同じ包み紙へ戻す。UAは96dpiの画素をptへ写す
		// TransformedImageで全ラスタを包むので、これが無いと何も掛からない
		if (image instanceof TransformedImage t) {
			final AffineTransform at = t.getTransform();
			final Image inner = apply(filter, t.getImage(), pixelScale * Math.sqrt(Math.abs(at.getDeterminant())));
			return inner == t.getImage() ? image : new TransformedImage(inner, at);
		}
		if (image instanceof CenteredImage c) {
			final Image inner = apply(filter, c.getImage(), pixelScale * c.getScale());
			return inner == c.getImage() ? image : new CenteredImage(inner, c.getBoxWidth(), c.getBoxHeight());
		}
		if (image instanceof PixelBackedImage pb) {
			// PDFへ直接登録した画像。画素を復号してから掛ける
			final Image pixels = pb.getPixels();
			if (pixels == null) {
				return image;
			}
			final Image inner = apply(filter, pixels, pixelScale);
			return inner == pixels ? image : inner;
		}
		if (!(image instanceof RasterImage raster)) {
			return image;
		}
		final double sigmaPx = filter.blur > 0 && pixelScale > 0 ? filter.blur / pixelScale : 0;
		final String key = filter.key() + "@" + String.format(java.util.Locale.ROOT, "%.2f", sigmaPx);
		synchronized (CACHE) {
			final Map<String, RasterImageImpl> byKey = CACHE.get(image);
			if (byKey != null) {
				final RasterImageImpl cached = byKey.get(key);
				if (cached != null) {
					return cached;
				}
			}
		}
		final BufferedImage src = raster.getImage();
		if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
			return image;
		}
		final BufferedImage out = toARGB(src);
		if (filter.matrix != null) {
			colorMatrix(out, filter.matrix);
		}
		if (sigmaPx > 0) {
			blur(out, sigmaPx);
		}
		final RasterImageImpl result = new RasterImageImpl(out, image.getAltString());
		synchronized (CACHE) {
			CACHE.computeIfAbsent(image, k -> new HashMap<String, RasterImageImpl>()).put(key, result);
		}
		return result;
	}

	/** 影の画像と、元画像に対する余白(元画像の論理単位)。 */
	public record Shadow(Image image, double padX, double padY) {
	}

	/**
	 * ラスタ画像の不透明度のシルエットに色を付け、ぼかした影の画像を
	 * 返します({@code drop-shadow()}用)。ぼかしがはみ出す分の余白
	 * (3σ)を四方に足すので、描く側は{@code padX/padY}ぶん戻して置く。
	 * ラスタでなければnull。
	 *
	 * @param sigma ぼかしの標準偏差({@code image}の論理単位)
	 */
	public static Shadow shadowOf(final Image image, final Color color, final double sigma) {
		if (image instanceof TransformedImage t) {
			final AffineTransform at = t.getTransform();
			final double k = Math.sqrt(Math.abs(at.getDeterminant()));
			final Shadow inner = shadowOf(t.getImage(), color, k > 0 ? sigma / k : 0);
			if (inner == null) {
				return null;
			}
			return new Shadow(new TransformedImage(inner.image(), at), inner.padX() * Math.abs(at.getScaleX()),
					inner.padY() * Math.abs(at.getScaleY()));
		}
		if (image instanceof PixelBackedImage pb) {
			return pb.getPixels() == null ? null : shadowOf(pb.getPixels(), color, sigma);
		}
		if (!(image instanceof RasterImage raster)) {
			return null;
		}
		final double sigmaPx = sigma;
		final BufferedImage src = raster.getImage();
		if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
			return null;
		}
		final int w = src.getWidth(), h = src.getHeight();
		final int pad = sigmaPx > 0 ? (int) Math.ceil(sigmaPx * 3) : 0;
		final BufferedImage out = new BufferedImage(w + pad * 2, h + pad * 2, BufferedImage.TYPE_INT_ARGB);
		final int[] row = new int[w];
		final int r = Math.round(color.getRed() * 255), g = Math.round(color.getGreen() * 255),
				b = Math.round(color.getBlue() * 255);
		final float ca = color.getAlpha();
		for (int y = 0; y < h; ++y) {
			src.getRGB(0, y, w, 1, row, 0, w);
			for (int x = 0; x < w; ++x) {
				final int a = Math.round(((row[x] >>> 24) & 0xFF) * ca);
				row[x] = (a << 24) | (r << 16) | (g << 8) | b;
			}
			out.setRGB(pad, y + pad, w, 1, row, 0, w);
		}
		if (sigmaPx > 0) {
			blur(out, sigmaPx);
		}
		return new Shadow(new RasterImageImpl(out), pad, pad);
	}

	private static BufferedImage toARGB(final BufferedImage src) {
		final BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		final java.awt.Graphics2D g = out.createGraphics();
		try {
			g.setComposite(java.awt.AlphaComposite.Src);
			g.drawImage(src, 0, 0, null);
		} finally {
			g.dispose();
		}
		return out;
	}

	/** 非プリマルチプライドのARGB画素へ色行列を掛けます。 */
	private static void colorMatrix(final BufferedImage img, final float[] m) {
		final int w = img.getWidth(), h = img.getHeight();
		final int[] row = new int[w];
		// 256階調×3成分の結果は入力に依存するので表引きはできないが、
		// 行列の各行は線形なので浮動小数点で素直に計算する
		for (int y = 0; y < h; ++y) {
			img.getRGB(0, y, w, 1, row, 0, w);
			for (int x = 0; x < w; ++x) {
				final int p = row[x];
				final int a = (p >>> 24) & 0xFF;
				final float r = ((p >> 16) & 0xFF) / 255f, g = ((p >> 8) & 0xFF) / 255f, b = (p & 0xFF) / 255f;
				final float[] o = FilterValue.apply(m, r, g, b, a / 255f);
				row[x] = (a << 24) | (Math.round(o[0] * 255) << 16) | (Math.round(o[1] * 255) << 8)
						| Math.round(o[2] * 255);
			}
			img.setRGB(0, y, w, 1, row, 0, w);
		}
	}

	/** 標準偏差{@code sigma}(画素)のガウスぼかしを箱ぼかし3回で近似します。 */
	private static void blur(final BufferedImage img, final double sigma) {
		final int w = img.getWidth(), h = img.getHeight();
		if (w * (long) h > 40_000_000L) {
			// 巨大画像は諦める(メモリと時間の上限)
			return;
		}
		final int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		// プリマルチプライドの4チャネル
		final float[] a = new float[w * h], r = new float[w * h], g = new float[w * h], b = new float[w * h];
		for (int i = 0; i < px.length; ++i) {
			final int p = px[i];
			final float al = ((p >>> 24) & 0xFF) / 255f;
			a[i] = al;
			r[i] = ((p >> 16) & 0xFF) / 255f * al;
			g[i] = ((p >> 8) & 0xFF) / 255f * al;
			b[i] = (p & 0xFF) / 255f * al;
		}
		// 3回の箱ぼかしでσに合わせる箱幅(Gwosdek et al.の近似)
		final double ideal = Math.sqrt(12 * sigma * sigma / 3 + 1);
		int radius = (int) Math.max(1, Math.round((ideal - 1) / 2));
		final float[] tmp = new float[w * h];
		for (final float[] ch : new float[][] { a, r, g, b }) {
			for (int pass = 0; pass < 3; ++pass) {
				boxBlurH(ch, tmp, w, h, radius);
				boxBlurV(tmp, ch, w, h, radius);
			}
		}
		for (int i = 0; i < px.length; ++i) {
			final float al = a[i];
			final int ai = Math.round(al * 255);
			if (ai <= 0) {
				px[i] = 0;
				continue;
			}
			px[i] = (ai << 24) | (clamp255(r[i] / al) << 16) | (clamp255(g[i] / al) << 8) | clamp255(b[i] / al);
		}
		img.setRGB(0, 0, w, h, px, 0, w);
	}

	private static int clamp255(final float v) {
		final int i = Math.round(v * 255);
		return i < 0 ? 0 : i > 255 ? 255 : i;
	}

	private static void boxBlurH(final float[] src, final float[] dst, final int w, final int h, final int radius) {
		final float norm = 1f / (radius * 2 + 1);
		for (int y = 0; y < h; ++y) {
			final int base = y * w;
			float sum = 0;
			for (int x = -radius; x <= radius; ++x) {
				sum += src[base + clampIndex(x, w)];
			}
			for (int x = 0; x < w; ++x) {
				dst[base + x] = sum * norm;
				sum += src[base + clampIndex(x + radius + 1, w)] - src[base + clampIndex(x - radius, w)];
			}
		}
	}

	private static void boxBlurV(final float[] src, final float[] dst, final int w, final int h, final int radius) {
		final float norm = 1f / (radius * 2 + 1);
		for (int x = 0; x < w; ++x) {
			float sum = 0;
			for (int y = -radius; y <= radius; ++y) {
				sum += src[clampIndex(y, h) * w + x];
			}
			for (int y = 0; y < h; ++y) {
				dst[y * w + x] = sum * norm;
				sum += src[clampIndex(y + radius + 1, h) * w + x] - src[clampIndex(y - radius, h) * w + x];
			}
		}
	}

	private static int clampIndex(final int i, final int n) {
		return i < 0 ? 0 : i >= n ? n - 1 : i;
	}
}
