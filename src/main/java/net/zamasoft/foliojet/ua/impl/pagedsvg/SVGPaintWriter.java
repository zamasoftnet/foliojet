package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;
import net.zamasoft.pdfg2d.util.ColorUtils;

/**
 * Converts pdfg2d {@link Paint} to SVG paint specifications.
 *
 * <p>
 * Solid colors can be attribute values ({@code #rrggbb}), but gradients are elements,
 * so put them in {@code defs} and reference them with {@code url(#id)}.
 * This is why <b>gradients become known during drawing</b>,
 * requiring a mechanism to fill the leading {@code defs} later.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGPaintWriter {
	/**
	 * A way to convert pattern images into a form SVG can reference.
	 *
	 * <p>
	 * Choosing shared resources or {@code data:} is outside this textual representation's concerns,
	 * so supply the strategy externally. Return {@code null} for images that cannot be written.
	 * </p>
	 */
	interface ImageHrefs {
		String href(net.zamasoft.pdfg2d.gc.image.Image image) throws IOException;
	}

	private final SVGWriter writer;

	private ImageHrefs images;

	SVGPaintWriter(final SVGWriter writer) {
		this.writer = writer;
	}

	void setImageHrefs(final ImageHrefs images) {
		this.images = images;
	}

	/**
	 * Returns a paint specification: {@code #rrggbb} for solid colors or {@code url(#id)}
	 * for gradients, registering the definition in defs.
	 */
	String toSVGPaint(final Paint paint) throws IOException {
		return this.toSVGPaint(paint, null);
	}

	/**
	 * Returns a paint specification under the current transformation {@code ctm} (2026-09-03).
	 * Paths and clips are written with transformed coordinates, so gradient and tiling coordinate
	 * systems must also map to page coordinates with the same transformation. Otherwise, shapes and
	 * colors misalign in transformed boxes (transform, nine-slice border-image tiles, etc.).
	 * Identity transformations produce the same output as before.
	 */
	String toSVGPaint(final Paint paint, final java.awt.geom.AffineTransform ctm) throws IOException {
		if (paint == null) {
			return "none";
		}
		return switch (paint.getPaintType()) {
		case COLOR -> toHex((Color) paint);
		case LINEAR_GRADIENT -> this.linearGradient((LinearGradient) paint, ctm);
		case RADIAL_GRADIENT -> this.radialGradient((RadialGradient) paint, ctm);
		case PATTERN -> this.pattern((net.zamasoft.pdfg2d.gc.paint.Pattern) paint, ctm);
		// Unknown type. Do not paint.
		default -> null;
		};
	}

	/** The current transformation applied outside the paint's own transformation (null if neither exists). */
	private static java.awt.geom.AffineTransform compose(final java.awt.geom.AffineTransform ctm,
			final java.awt.geom.AffineTransform paintTransform) {
		final boolean hasCtm = ctm != null && !ctm.isIdentity();
		final boolean hasPaint = paintTransform != null && !paintTransform.isIdentity();
		if (!hasCtm) {
			return hasPaint ? paintTransform : null;
		}
		final java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform(ctm);
		if (hasPaint) {
			at.concatenate(paintTransform);
		}
		return at;
	}

	/**
	 * Image tiling. Used for {@code background: url(...)}.
	 *
	 * <p>
	 * SVG {@code pattern} repeats a tile sized by {@code width}/{@code height}.
	 * Set {@code patternUnits="userSpaceOnUse"} and use the image's logical dimensions as the tile size.
	 * Pass the transformation from {@link net.zamasoft.pdfg2d.gc.paint.Pattern}
	 * to {@code patternTransform}.
	 * </p>
	 *
	 * <p>
	 * Returns {@code null} if the image cannot become a reference. <b>Nothing is painted in that case.</b>
	 * Do not substitute a solid color: that would make an appearance different from the original
	 * seem to have been output correctly.
	 * </p>
	 */
	private String pattern(final net.zamasoft.pdfg2d.gc.paint.Pattern pattern,
			final java.awt.geom.AffineTransform ctm) throws IOException {
		if (this.images == null) {
			return null;
		}
		final net.zamasoft.pdfg2d.gc.image.Image image = pattern.getImage();
		final String href = this.images.href(image);
		if (href == null) {
			return null;
		}
		final double width = image.getWidth();
		final double height = image.getHeight();
		if (!(width > 0) || !(height > 0)) {
			return null;
		}
		final String id = this.writer.nextId("pt");
		final StringBuilder def = new StringBuilder(200);
		def.append("<pattern id=\"").append(id).append("\" patternUnits=\"userSpaceOnUse\" width=\"")
				.append(SVGWriter.number(width)).append("\" height=\"").append(SVGWriter.number(height)).append('"');
		final java.awt.geom.AffineTransform at = compose(ctm, pattern.getTransform());
		if (at != null) {
			def.append(" patternTransform=\"").append(matrix(at)).append('"');
		}
		def.append("><image x=\"0\" y=\"0\" width=\"").append(SVGWriter.number(width)).append("\" height=\"")
				.append(SVGWriter.number(height)).append("\" preserveAspectRatio=\"none\" xlink:href=\"");
		SVGWriter.escapeAttribute(def, href);
		def.append("\"/></pattern>");
		this.writer.addDef(def.toString());
		return "url(#" + id + ")";
	}

	private static String matrix(final java.awt.geom.AffineTransform at) {
		return "matrix(" + SVGWriter.number(at.getScaleX()) + ' ' + SVGWriter.number(at.getShearY()) + ' '
				+ SVGWriter.number(at.getShearX()) + ' ' + SVGWriter.number(at.getScaleY()) + ' '
				+ SVGWriter.number(at.getTranslateX()) + ' ' + SVGWriter.number(at.getTranslateY()) + ')';
	}

	/** Paint opacity. Multiplies {@code RGBAColor} alpha by the state's alpha. */
	static float alphaOf(final Paint paint, final float stateAlpha) {
		if (paint instanceof Color color) {
			return color.getAlpha() * stateAlpha;
		}
		return stateAlpha;
	}

	static String toHex(final Color color) {
		return String.format("#%02x%02x%02x", ColorUtils.toOctet(color.getRed()), ColorUtils.toOctet(color.getGreen()),
				ColorUtils.toOctet(color.getBlue()));
	}

	private String linearGradient(final LinearGradient g, final java.awt.geom.AffineTransform ctm)
			throws IOException {
		final String id = this.writer.nextId("lg");
		final StringBuilder def = new StringBuilder(160);
		def.append("<linearGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\" x1=\"")
				.append(SVGWriter.number(g.x1())).append("\" y1=\"").append(SVGWriter.number(g.y1()))
				.append("\" x2=\"").append(SVGWriter.number(g.x2())).append("\" y2=\"")
				.append(SVGWriter.number(g.y2())).append('"');
		appendSpread(def, g.spread());
		appendGradientTransform(def, compose(ctm, g.transform()));
		def.append('>');
		appendStops(def, g.fractions(), g.colors());
		def.append("</linearGradient>");
		this.writer.addDef(def.toString());
		return "url(#" + id + ")";
	}

	private String radialGradient(final RadialGradient g, final java.awt.geom.AffineTransform ctm)
			throws IOException {
		final String id = this.writer.nextId("rg");
		final StringBuilder def = new StringBuilder(160);
		def.append("<radialGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\" cx=\"")
				.append(SVGWriter.number(g.cx())).append("\" cy=\"").append(SVGWriter.number(g.cy()))
				.append("\" r=\"").append(SVGWriter.number(g.radius())).append('"');
		appendSpread(def, g.spread());
		appendGradientTransform(def, compose(ctm, g.transform()));
		def.append('>');
		appendStops(def, g.fractions(), g.colors());
		def.append("</radialGradient>");
		this.writer.addDef(def.toString());
		return "url(#" + id + ")";
	}

	/**
	 * Paint transformation matrix (2026-08-29). Elliptical radial gradients use a matrix
	 * that scales a circle vertically (see {@code RadialGradientValue});
	 * without it, they become circles.
	 */
	private static void appendGradientTransform(final StringBuilder def, final java.awt.geom.AffineTransform at) {
		if (at != null && !at.isIdentity()) {
			def.append(" gradientTransform=\"").append(matrix(at)).append('"');
		}
	}

	/**
	 * Painting outside the domain (2026-08-29). {@code repeating-*-gradient} repeats one period
	 * using {@code spreadMethod="repeat"} (rendered exactly by the browser).
	 */
	private static void appendSpread(final StringBuilder def, final net.zamasoft.pdfg2d.gc.paint.SpreadMethod spread) {
		if (spread == net.zamasoft.pdfg2d.gc.paint.SpreadMethod.REPEAT) {
			def.append(" spreadMethod=\"repeat\"");
		} else if (spread == net.zamasoft.pdfg2d.gc.paint.SpreadMethod.REFLECT) {
			def.append(" spreadMethod=\"reflect\"");
		}
	}

	private static void appendStops(final StringBuilder def, final double[] fractions, final Color[] colors) {
		for (int i = 0; i < colors.length; ++i) {
			final double offset = i < fractions.length ? fractions[i] : 1.0;
			def.append("<stop offset=\"").append(SVGWriter.number(offset)).append("\" stop-color=\"")
					.append(toHex(colors[i])).append('"');
			final float alpha = colors[i].getAlpha();
			if (alpha < 1f) {
				def.append(" stop-opacity=\"").append(SVGWriter.number(alpha)).append('"');
			}
			def.append("/>");
		}
	}
}
