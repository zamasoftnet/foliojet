package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.IOException;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;

/**
 * pdfg2dの{@link Paint}をSVGの塗り指定へ直します。
 *
 * <p>
 * 単色は属性値({@code #rrggbb})にできますが、グラデーションは要素なので
 * {@code defs}へ入れて{@code url(#id)}で参照します。<b>グラデーションが
 * 描画の途中で判明する</b>のはこのためで、先頭の{@code defs}を後から
 * 埋める仕組みが要ります。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class SVGPaintWriter {
	/**
	 * パターンの絵を、SVGから参照できる形へ直す手だてです。
	 *
	 * <p>
	 * 共有資源へ出すのか{@code data:}へするのかは、この字面の外の都合なので
	 * 外から渡してもらいます。書けない絵なら{@code null}を返してください。
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
	 * 塗りの指定を返します。単色なら{@code #rrggbb}、グラデーションなら
	 * {@code url(#id)}で、defsへ定義を登録します。
	 */
	String toSVGPaint(final Paint paint) throws IOException {
		if (paint == null) {
			return "none";
		}
		return switch (paint.getPaintType()) {
		case COLOR -> toHex((Color) paint);
		case LINEAR_GRADIENT -> this.linearGradient((LinearGradient) paint);
		case RADIAL_GRADIENT -> this.radialGradient((RadialGradient) paint);
		case PATTERN -> this.pattern((net.zamasoft.pdfg2d.gc.paint.Pattern) paint);
		// 知らない種類。塗らない
		default -> null;
		};
	}

	/**
	 * 絵の敷き詰め。{@code background: url(...)}がこれになります。
	 *
	 * <p>
	 * SVGの{@code pattern}は、1枚ぶんの升目を{@code width}/{@code height}で決めて
	 * 繰り返します。{@code patternUnits="userSpaceOnUse"}にして、升目の大きさは
	 * 絵の論理寸法をそのまま使います。{@link net.zamasoft.pdfg2d.gc.paint.Pattern}の
	 * 変換は{@code patternTransform}へ渡します。
	 * </p>
	 *
	 * <p>
	 * 絵を参照にできないときは{@code null}を返します。<b>その場合は塗られません。</b>
	 * 中途半端に単色で代えると、元と違う見た目が「正しく出ている」ように見えるので
	 * そうしません。
	 * </p>
	 */
	private String pattern(final net.zamasoft.pdfg2d.gc.paint.Pattern pattern) throws IOException {
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
		final java.awt.geom.AffineTransform at = pattern.getTransform();
		if (at != null && !at.isIdentity()) {
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

	/** 塗りの不透明度。{@code RGBAColor}のalphaと状態のalphaを掛けます。 */
	static float alphaOf(final Paint paint, final float stateAlpha) {
		if (paint instanceof Color color) {
			return color.getAlpha() * stateAlpha;
		}
		return stateAlpha;
	}

	static String toHex(final Color color) {
		return String.format("#%02x%02x%02x", clamp(color.getRed()), clamp(color.getGreen()),
				clamp(color.getBlue()));
	}

	private static int clamp(final float v) {
		final int i = Math.round(v * 255f);
		return i < 0 ? 0 : i > 255 ? 255 : i;
	}

	private String linearGradient(final LinearGradient g) throws IOException {
		final String id = this.writer.nextId("lg");
		final StringBuilder def = new StringBuilder(160);
		def.append("<linearGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\" x1=\"")
				.append(SVGWriter.number(g.x1())).append("\" y1=\"").append(SVGWriter.number(g.y1()))
				.append("\" x2=\"").append(SVGWriter.number(g.x2())).append("\" y2=\"")
				.append(SVGWriter.number(g.y2())).append('"');
		appendGradientTransform(def, g.transform());
		def.append('>');
		appendStops(def, g.fractions(), g.colors());
		def.append("</linearGradient>");
		this.writer.addDef(def.toString());
		return "url(#" + id + ")";
	}

	private String radialGradient(final RadialGradient g) throws IOException {
		final String id = this.writer.nextId("rg");
		final StringBuilder def = new StringBuilder(160);
		def.append("<radialGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\" cx=\"")
				.append(SVGWriter.number(g.cx())).append("\" cy=\"").append(SVGWriter.number(g.cy()))
				.append("\" r=\"").append(SVGWriter.number(g.radius())).append('"');
		appendGradientTransform(def, g.transform());
		def.append('>');
		appendStops(def, g.fractions(), g.colors());
		def.append("</radialGradient>");
		this.writer.addDef(def.toString());
		return "url(#" + id + ")";
	}

	/**
	 * 塗りの変換行列(2026-08-29)。楕円の放射グラデーションは円を縦に
	 * 伸縮する行列で表す({@code RadialGradientValue}参照)ので、これが
	 * 無いと真円になる。
	 */
	private static void appendGradientTransform(final StringBuilder def, final java.awt.geom.AffineTransform at) {
		if (at != null && !at.isIdentity()) {
			def.append(" gradientTransform=\"").append(matrix(at)).append('"');
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
