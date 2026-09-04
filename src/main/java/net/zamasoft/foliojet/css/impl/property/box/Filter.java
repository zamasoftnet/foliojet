package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * {@code filter}です(filter-effects-1 §7、2026-08-29新設。それまでは
 * 意図的無視(2821)の対象だった)。
 *
 * <p>
 * 非継承・既定{@code none}。関数列を{@link FilterValue}へ畳む。
 * {@code url()}(SVGフィルタ)は読み飛ばす(受理はするが効果なし)。
 * {@code backdrop-filter}は引き続き無視する。
 * </p>
 *
 * <p>
 * <b>近似</b>({@link FilterValue}参照): 色系の関数は単色の塗り
 * (背景・境界・文字・グラデーションの色停止)とラスタ画像の画素に
 * 同じ色行列を掛ける。{@code blur()}はラスタ画像だけに効く(ベクタの
 * ぼかしはPDFに無い)。{@code drop-shadow()}は箱の境界形状の影
 * (box-shadowと同じ階段状の近似)、ラスタ画像では不透明度の
 * シルエットをぼかした影。{@code opacity()}は{@code opacity}と同じ
 * グループ不透明度。SVG画像の画素・ぼかしは対象外。
 * </p>
 */
public class Filter extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Filter();

	public static FilterValue get(final CSSStyle style) {
		return (FilterValue) style.get(INFO);
	}

	protected Filter() {
		super("filter");
	}

	public Value getDefault(final CSSStyle style) {
		return FilterValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		final FilterValue own = ((FilterValue) value).forElement();
		final CSSStyle parent = style.getParentStyle();
		if (parent == null) {
			return own;
		}
		final FilterValue inherited = get(parent);
		if (inherited.isNone()) {
			return own;
		}
		// 子孫の描画要素へ親の効果を届ける(FilterValue冒頭の近似の説明参照)
		return inherited.compose(own);
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken first = tokens.peek();
		if (first instanceof CssToken.Ident ident && ident.is("none") && tokens.size() == 1) {
			return FilterValue.NONE;
		}
		float opacity = 1f;
		float[] matrix = null;
		double blur = 0;
		FilterValue.DropShadow shadow = null;
		final List<String> text = new ArrayList<String>();
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token instanceof CssToken.Uri) {
				// SVGフィルタ参照。効果なし
				text.add("url()");
				continue;
			}
			if (!(token instanceof CssToken.Func func)) {
				throw new PropertyException();
			}
			final TokenStream args = func.argStream();
			final String name = func.name().toLowerCase(java.util.Locale.ROOT);
			float[] m = null;
			switch (name) {
			case "grayscale": {
				final float a = amount(args, 1);
				final float i = 1 - a;
				m = new float[] { 0.2126f + 0.7874f * i, 0.7152f - 0.7152f * i, 0.0722f - 0.0722f * i, 0, 0,
						0.2126f - 0.2126f * i, 0.7152f + 0.2848f * i, 0.0722f - 0.0722f * i, 0, 0,
						0.2126f - 0.2126f * i, 0.7152f - 0.7152f * i, 0.0722f + 0.9278f * i, 0, 0, 0, 0, 0, 1, 0 };
				text.add(String.format(java.util.Locale.ROOT, "grayscale(%.2f)", a));
				break;
			}
			case "sepia": {
				final float a = amount(args, 1);
				final float i = 1 - a;
				m = new float[] { 0.393f + 0.607f * i, 0.769f - 0.769f * i, 0.189f - 0.189f * i, 0, 0,
						0.349f - 0.349f * i, 0.686f + 0.314f * i, 0.168f - 0.168f * i, 0, 0, 0.272f - 0.272f * i,
						0.534f - 0.534f * i, 0.131f + 0.869f * i, 0, 0, 0, 0, 0, 1, 0 };
				text.add(String.format(java.util.Locale.ROOT, "sepia(%.2f)", a));
				break;
			}
			case "saturate": {
				final float s = amount(args, Float.MAX_VALUE);
				m = new float[] { 0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s, 0, 0,
						0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s, 0, 0, 0.213f - 0.213f * s,
						0.715f - 0.715f * s, 0.072f + 0.928f * s, 0, 0, 0, 0, 0, 1, 0 };
				text.add(String.format(java.util.Locale.ROOT, "saturate(%.2f)", s));
				break;
			}
			case "hue-rotate": {
				final CssToken t = args.next();
				final Double rad = t instanceof CssToken.Num num && num.value() == 0 ? Double.valueOf(0)
						: ColorValueUtils.toAngleRadians(t);
				if (rad == null || args.hasNext()) {
					throw new PropertyException();
				}
				final float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
				m = new float[] { 0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f,
						0.072f - c * 0.072f + s * 0.928f, 0, 0, 0.213f - c * 0.213f + s * 0.143f,
						0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0, 0,
						0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f,
						0.072f + c * 0.928f + s * 0.072f, 0, 0, 0, 0, 0, 1, 0 };
				text.add(String.format(java.util.Locale.ROOT, "hue-rotate(%.0fdeg)", Math.toDegrees(rad)));
				break;
			}
			case "invert": {
				final float a = amount(args, 1);
				final float k = 1 - 2 * a;
				m = new float[] { k, 0, 0, 0, a, 0, k, 0, 0, a, 0, 0, k, 0, a, 0, 0, 0, 1, 0 };
				text.add(String.format(java.util.Locale.ROOT, "invert(%.2f)", a));
				break;
			}
			case "brightness": {
				final float a = amount(args, Float.MAX_VALUE);
				m = new float[] { a, 0, 0, 0, 0, 0, a, 0, 0, 0, 0, 0, a, 0, 0, 0, 0, 0, 1, 0 };
				text.add(String.format(java.util.Locale.ROOT, "brightness(%.2f)", a));
				break;
			}
			case "contrast": {
				final float a = amount(args, Float.MAX_VALUE);
				final float o = 0.5f - 0.5f * a;
				m = new float[] { a, 0, 0, 0, o, 0, a, 0, 0, o, 0, 0, a, 0, o, 0, 0, 0, 1, 0 };
				text.add(String.format(java.util.Locale.ROOT, "contrast(%.2f)", a));
				break;
			}
			case "opacity": {
				final float a = amount(args, 1);
				opacity *= a;
				text.add(String.format(java.util.Locale.ROOT, "opacity(%.2f)", a));
				break;
			}
			case "blur": {
				double radius = 0;
				if (args.hasNext()) {
					final Value length = ValueUtils.toLength(ua, args.next());
					if (!(length instanceof AbsoluteLengthValue abs) || args.hasNext()) {
						throw new PropertyException();
					}
					radius = Math.max(0, abs.getLength());
				}
				blur += radius;
				text.add(String.format(java.util.Locale.ROOT, "blur(%.2fpt)", radius));
				break;
			}
			case "drop-shadow": {
				shadow = parseDropShadow(ua, args);
				text.add(String.format(java.util.Locale.ROOT, "drop-shadow(%.2f %.2f %.2f)", shadow.x(), shadow.y(),
						shadow.blur()));
				break;
			}
			default:
				throw new PropertyException();
			}
			if (m != null) {
				// 後の関数は前の結果に掛かる
				matrix = matrix == null ? m : FilterValue.multiply(m, matrix);
			}
		}
		if (text.isEmpty()) {
			throw new PropertyException();
		}
		return new FilterValue(opacity, matrix, blur, shadow, String.join(" ", text));
	}

	/** {@code <number> | <percentage>}(省略時1)。負は不可、{@code max}で切る。 */
	private static float amount(final TokenStream args, final float max) throws PropertyException {
		if (!args.hasNext()) {
			return 1f;
		}
		final CssToken t = args.next();
		final float v;
		if (t instanceof CssToken.Num num) {
			v = (float) num.value();
		} else if (t instanceof CssToken.Percent percent) {
			v = (float) (percent.value() / 100);
		} else {
			throw new PropertyException();
		}
		if (v < 0 || args.hasNext()) {
			throw new PropertyException();
		}
		return Math.min(v, max);
	}

	/** {@code drop-shadow([<color>]? <length>{2,3} [<color>]?)}。 */
	private static FilterValue.DropShadow parseDropShadow(final UserAgent ua, final TokenStream args)
			throws PropertyException {
		Color color = null;
		final List<Double> lengths = new ArrayList<Double>(3);
		while (args.hasNext()) {
			final CssToken t = args.next();
			final ColorValue c = ColorValueUtils.toColor(ua, t);
			if (c != null) {
				if (color != null) {
					throw new PropertyException();
				}
				color = c.getColor();
				continue;
			}
			final Value length = ValueUtils.toLength(ua, t);
			if (!(length instanceof AbsoluteLengthValue abs) || lengths.size() >= 3) {
				throw new PropertyException();
			}
			lengths.add(abs.getLength());
		}
		if (lengths.size() < 2) {
			throw new PropertyException();
		}
		final double blur = lengths.size() >= 3 ? Math.max(0, lengths.get(2)) : 0;
		return new FilterValue.DropShadow(lengths.get(0), lengths.get(1), blur,
				color == null ? RGBColor.BLACK : color);
	}
}
