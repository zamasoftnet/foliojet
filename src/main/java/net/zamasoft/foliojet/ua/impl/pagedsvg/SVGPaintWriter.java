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
	private final SVGWriter writer;

	SVGPaintWriter(final SVGWriter writer) {
		this.writer = writer;
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
		// パターンはまだ直に書けないので、目に見える形で落とさず灰色で代替せず
		// 「塗らない」を返す。呼び出し側がアウトラインへ退避する
		default -> null;
		};
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
				.append(SVGWriter.number(g.y2())).append("\">");
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
				.append("\" r=\"").append(SVGWriter.number(g.radius())).append("\">");
		appendStops(def, g.fractions(), g.colors());
		def.append("</radialGradient>");
		this.writer.addDef(def.toString());
		return "url(#" + id + ")";
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
