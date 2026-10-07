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
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.util.ColorUtils;

/**
 * Utilities applying {@code filter} effects to paints, colors, and raster images
 * (filter-effects-1; added 2026-08-29).
 *
 * <p>
 * Applies color matrices to {@link Color} (RGB/RGBA; CMYK, gray, and spot colors are calculated
 * using RGB equivalents, producing RGB; spot-color specifications are lost, as documented),
 * gradient color stops, and raster images in patterns.
 * For rasters, copies and transforms pixels from decoded {@link RasterImage}, caching by
 * image/filter pair with weak references (avoids scanning pixels on every draw when a document
 * uses the same image repeatedly).
 * </p>
 *
 * <p>
 * Blur uses three box blurs (a Gaussian approximation), converting standard deviation from pt
 * to image pixels (dividing by the scale of the drawing transform).
 * Processes premultiplied values to prevent transparent pixel colors from bleeding into edges.
 * </p>
 */
public final class FilterOps {
	private static final Map<Image, Map<String, RasterImageImpl>> CACHE = new WeakHashMap<Image, Map<String, RasterImageImpl>>();

	private FilterOps() {
		// unused
	}

	/** Applies a color matrix to a color. Returns the original color if the matrix is null. */
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
	 * Applies effects to a paint.
	 *
	 * @param pixelScale pt per raster image pixel (for blur conversion)
	 */
	public static Paint apply(final FilterValue filter, final Paint paint, final double pixelScale) {
		if (paint == null) {
			return null;
		}
		return switch (paint) {
		case Color color -> apply(filter, color);
		case LinearGradient g -> filter.matrix == null ? g
				: new LinearGradient(g.x1(), g.y1(), g.x2(), g.y2(), g.fractions(), apply(filter, g.colors()),
						g.transform(), g.spread());
		case RadialGradient g -> filter.matrix == null ? g
				: new RadialGradient(g.cx(), g.cy(), g.radius(), g.fx(), g.fy(), g.fractions(),
						apply(filter, g.colors()), g.transform(), g.spread());
		case ConicGradient g -> filter.matrix == null ? g
				: new ConicGradient(g.cx(), g.cy(), g.startAngle(), g.fractions(), apply(filter, g.colors()),
						g.transform(), g.spread());
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
	 * Returns a copy of a raster image with effects applied. Returns the original image unchanged
	 * if it is not a raster (SVG, group image, or dimension-only stub).
	 *
	 * @param pixelScale pt per pixel
	 */
	public static Image apply(final FilterValue filter, final Image image, final double pixelScale) {
		if (!filter.hasColorOps()) {
			return image;
		}
		// For wrappers (TransformedImage converting pixels to pt, CenteredImage for centering),
		// transform the contents and restore the same wrapper. The UA wraps all rasters in a
		// TransformedImage mapping 96 dpi pixels to pt; without this, no effects apply
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
			// Image registered directly with PDF. Decode pixels before applying effects
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

	/** Shadow image and padding relative to the original image (in the original image's logical units). */
	public record Shadow(Image image, double padX, double padY) {
	}

	/**
	 * Colors the opacity silhouette of a raster image and returns a blurred shadow image
	 * (for {@code drop-shadow()}). Adds padding (3σ) on all sides for blur overflow, so the
	 * drawing side positions it backward by {@code padX/padY}. Returns null for non-raster images.
	 *
	 * @param sigma blur standard deviation (in {@code image}'s logical units)
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

	/** Applies a color matrix to non-premultiplied ARGB pixels. */
	private static void colorMatrix(final BufferedImage img, final float[] m) {
		final int w = img.getWidth(), h = img.getHeight();
		final int[] row = new int[w];
		// The result for 256 levels × 3 components depends on the input, so cannot use a lookup table,
		// but each matrix row is linear, so calculate directly with floating-point arithmetic
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

	/** Approximates Gaussian blur with standard deviation {@code sigma} (pixels) using three box blurs. */
	private static void blur(final BufferedImage img, final double sigma) {
		final int w = img.getWidth(), h = img.getHeight();
		if (w * (long) h > 40_000_000L) {
			// Skip huge images (memory and time limits)
			return;
		}
		final int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		// Four premultiplied channels
		final float[] a = new float[w * h], r = new float[w * h], g = new float[w * h], b = new float[w * h];
		for (int i = 0; i < px.length; ++i) {
			final int p = px[i];
			final float al = ((p >>> 24) & 0xFF) / 255f;
			a[i] = al;
			r[i] = ((p >> 16) & 0xFF) / 255f * al;
			g[i] = ((p >> 8) & 0xFF) / 255f * al;
			b[i] = (p & 0xFF) / 255f * al;
		}
		// Box widths that match σ over three box blurs (Gwosdek et al. approximation)
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
			px[i] = (ai << 24) | (ColorUtils.toOctet(r[i] / al) << 16) | (ColorUtils.toOctet(g[i] / al) << 8) | ColorUtils.toOctet(b[i] / al);
		}
		img.setRGB(0, 0, w, h, px, 0, w);
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
