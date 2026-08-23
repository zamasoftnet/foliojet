package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.geom.AffineTransform;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.zamasoft.pdfg2d.font.ColorGlyphFont;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.ShapedFont;
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.GlyphAdvances;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.foliojet.ua.impl.image.EncodedRasterImage;

/** Text-preserving SVG GC backed by exact, private-codepoint webfont subsets. */
final class PagedSVGGC extends G2DGC {
	private static final String SVG_NS = "http://www.w3.org/2000/svg";
	private final PagedSVGGraphics2D svg;
	private final PagedSVGResources resources;
	private final PagedSVGResources.PageData page;

	PagedSVGGC(final PagedSVGGraphics2D svg, final FontManager fonts, final PagedSVGResources resources,
			final PagedSVGResources.PageData page) {
		super(svg, fonts);
		this.svg = svg;
		this.resources = resources;
		this.page = page;
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		Image original = image;
		while (original instanceof final WrappedImage wrapped) {
			original = wrapped.getImage();
		}
		if (original instanceof final EncodedRasterImage encoded) {
			this.resources.rememberOriginal(encoded.getImage(), encoded.getEncoded(), encoded.getMediaType(),
					encoded.getExtension());
		}
		super.drawImage(image);
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		final String sourceText = new String(text.getChars(), 0, text.getCharCount());
		final FontSource source = text.getFontMetrics().getFontSource();
		if (!WebFontSubset.allowsEmbedding(source.getEmbeddingLicenseFlags())) {
			recordText(text, sourceText, source.getFontName(), x, y);
			super.drawText(text, x, y);
			return;
		}
		if (!(text.getFontMetrics() instanceof FontMetricsImpl metrics)) {
			recordText(text, sourceText, source.getFontName(), x, y);
			super.drawText(text, x, y);
			return;
		}
		final Font font = metrics.getFont();
		if (!(font instanceof ShapedFont shaped) || hasColorGlyph(font, text)
				|| !supportedPaint(this.getFillPaint()) || !supportedPaint(this.getStrokePaint())) {
			recordText(text, sourceText, source.getFontName(), x, y);
			super.drawText(text, x, y);
			return;
		}

		this.drewAnything = true;
		final FontStyle style = text.getFontStyle();
		final boolean vertical = style.getDirection() == FontStyle.Direction.TB;
		final WebFontSubset.Mode mode = !vertical ? WebFontSubset.Mode.HORIZONTAL
				: source.getDirection() == FontStyle.Direction.TB ? WebFontSubset.Mode.VERTICAL_UPRIGHT
						: WebFontSubset.Mode.VERTICAL_SIDEWAYS;
		final boolean oblique = style.getStyle() != FontStyle.Style.NORMAL && !source.isItalic();
		final WebFontSubset subset = this.resources.font(source, shaped, mode, oblique);
		if (!subset.canMap(text.getGlyphIds(), text.getGlyphCount())) {
			recordText(text, sourceText, source.getFontName(), x, y);
			super.drawText(text, x, y);
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
		// xAdvance[0]はrun先頭glyphの手前の調整。style run境界の
		// 和文約物詰めにも使うため、2文字目まで先送りしてはいけない。
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
			final double gx;
			final double gy;
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
			xs.append(PagedSVGResources.number(gx));
			ys.append(PagedSVGResources.number(gy));
			minX = Math.min(minX, gx - (vertical ? size / 2.0 : 0));
			maxX = Math.max(maxX, gx + (vertical ? size / 2.0 : size));
			minY = Math.min(minY, gy - (vertical ? 0 : metrics.getAscent()));
			maxY = Math.max(maxY, gy + (vertical ? size : metrics.getDescent()));
		}

		final Document document = this.svg.getDOMFactory();
		final Element element = document.createElementNS(SVG_NS, "text");
		element.setAttribute("x", xs.toString());
		element.setAttribute("y", ys.toString());
		element.setAttribute("font-family", subset.family());
		element.setAttribute("font-size", PagedSVGResources.number(size));
		element.setAttribute("font-kerning", "none");
		element.setAttribute("font-variant-ligatures", "none");
		element.setAttribute("font-feature-settings", "'kern' 0, 'liga' 0");
		element.setAttribute("text-rendering", "geometricPrecision");
		element.setAttribute("role", "img");
		element.setAttribute("aria-label", sourceText);
		element.setAttribute("data-copper-text", sourceText);
		applyPaint(element, style, source);
		element.setTextContent(chars.toString());
		this.svg.addTextElement(element);
		this.page.textRuns.add(new PagedSVGResources.TextRun(sourceText, subset.family(), size,
				new AffineTransform(this.getTransform()), minX, minY, maxX, maxY));
	}

	private void recordText(final Text text, final String value, final String font, final double x, final double y) {
		final double size = text.getFontStyle().getSize();
		this.page.textRuns.add(new PagedSVGResources.TextRun(value, font, size,
				new AffineTransform(this.getTransform()), x, y - text.getAscent(), x + text.getAdvance(),
				y + text.getDescent()));
	}

	private static boolean hasColorGlyph(final Font font, final Text text) {
		if (!(font instanceof ColorGlyphFont color)) {
			return false;
		}
		for (int i = 0; i < text.getGlyphCount(); ++i) {
			if (color.isColorGlyph(text.getGlyphIds()[i])) {
				return true;
			}
		}
		return false;
	}

	private static boolean supportedPaint(final net.zamasoft.pdfg2d.gc.paint.Paint paint) {
		return paint == null || paint instanceof Color;
	}

	private void applyPaint(final Element element, final FontStyle style, final FontSource source) {
		final TextMode mode = this.getTextMode();
		if (mode == TextMode.FILL || mode == TextMode.FILL_STROKE) {
			setColor(element, "fill", (Color) this.getFillPaint(), this.getFillAlpha());
		} else {
			element.setAttribute("fill", "none");
		}
		if (mode == TextMode.STROKE || mode == TextMode.FILL_STROKE) {
			setColor(element, "stroke", (Color) this.getStrokePaint(), this.getStrokeAlpha());
			element.setAttribute("stroke-width", PagedSVGResources.number(this.getLineWidth()));
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
				setColor(element, "stroke", (Color) this.getFillPaint(), this.getFillAlpha());
				element.setAttribute("stroke-width", PagedSVGResources.number(enlargement));
				element.setAttribute("paint-order", "stroke fill");
			}
		}
	}

	private static void setColor(final Element element, final String name, final Color color, final float stateAlpha) {
		if (color == null) {
			return;
		}
		final int red = Math.round(color.getRed() * 255);
		final int green = Math.round(color.getGreen() * 255);
		final int blue = Math.round(color.getBlue() * 255);
		element.setAttribute(name, "rgb(" + red + ',' + green + ',' + blue + ')');
		final float alpha = color.getAlpha() * stateAlpha;
		if (alpha != 1) {
			element.setAttribute(name + "-opacity", Float.toString(alpha));
		}
	}
}
