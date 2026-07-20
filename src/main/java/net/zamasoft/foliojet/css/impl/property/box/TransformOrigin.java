package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractCompositePrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * 2026-07-20: {@code -cssj-direction-mode}廃止に伴い、縦書き時のx/y軸
 * 入れ替え(実世界のCSS/ブラウザには存在しない挙動)を削除した。
 * transform-originは常に物理座標のまま扱う。
 *
 * @author MIYABE Tatsuhiko
 */
public class TransformOrigin extends AbstractCompositePrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_X = new TransformOrigin();

	public static final PrimitivePropertyInfo INFO_Y = new TransformOrigin();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_X, INFO_Y };

	public static Offset get(CSSStyle style) {
		Value xValue = style.get(INFO_X);
		Value yValue = style.get(INFO_Y);
		return BoxValueUtils.toOffset(xValue, yValue);
	}

	protected TransformOrigin() {
		super("-cssj-transform-origin");
	}

	public Value getDefault(CSSStyle style) {
		return PercentageValue.HALF;
	}

	public boolean isInherited() {
		return false;
	}

	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}

	/**
	 * 計算値はPercentageValueまたはAbsoluteLengthです。
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	protected Entry[] parseValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.isInherit()) {
			return new Entry[] { new Entry(TransformOrigin.INFO_X, KeywordValue.INHERIT),
					new Entry(TransformOrigin.INFO_Y, KeywordValue.INHERIT) };
		}
		Value x, y;

		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident1) {
			String kw1 = ident1.lower();
			if (!(kw1.equals("top") || kw1.equals("bottom") || kw1.equals("center") || kw1.equals("left")
					|| kw1.equals("right"))) {
				throw new PropertyException();
			}
			String kw2;
			final CssToken second = tokens.next();
			if (second == null || second instanceof CssToken.Ident) {
				if (second == null) {
					kw2 = null;
				} else {
					kw2 = ((CssToken.Ident) second).lower();
					if (!(kw2.equals("top") || kw2.equals("bottom") || kw2.equals("center") || kw2.equals("left")
							|| kw2.equals("right"))) {
						throw new PropertyException();
					}
				}

				if (("top".equals(kw1) && "left".equals(kw2)) || ("left".equals(kw1) && "top".equals(kw2))) {
					x = y = PercentageValue.ZERO;
				} else if (("top".equals(kw1) && kw2 == null) || ("top".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "top".equals(kw2))) {
					x = PercentageValue.HALF;
					y = PercentageValue.ZERO;
				} else if (("right".equals(kw1) && "top".equals(kw2)) || ("top".equals(kw1) && "right".equals(kw2))) {
					x = PercentageValue.FULL;
					y = PercentageValue.ZERO;
				} else if (("left".equals(kw1) && kw2 == null) || ("left".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "left".equals(kw2))) {
					x = PercentageValue.ZERO;
					y = PercentageValue.HALF;
				} else if (("center".equals(kw1) && kw2 == null) || ("center".equals(kw1) && "center".equals(kw2))) {
					x = y = PercentageValue.HALF;
				} else if (("right".equals(kw1) && kw2 == null) || ("right".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "right".equals(kw2))) {
					x = PercentageValue.FULL;
					y = PercentageValue.HALF;
				} else if (("left".equals(kw1) && "bottom".equals(kw2))
						|| ("bottom".equals(kw1) && "left".equals(kw2))) {
					x = PercentageValue.ZERO;
					y = PercentageValue.FULL;
				} else if (("bottom".equals(kw1) && kw2 == null) || ("bottom".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "bottom".equals(kw2))) {
					x = PercentageValue.HALF;
					y = PercentageValue.FULL;
				} else if (("bottom".equals(kw1) && "right".equals(kw2))
						|| ("right".equals(kw1) && "bottom".equals(kw2))) {
					x = y = PercentageValue.FULL;
				} else {
					throw new PropertyException();
				}

				return new Entry[] { new Entry(TransformOrigin.INFO_X, x), new Entry(TransformOrigin.INFO_Y, y) };
			}

			y = ValueUtils.toPercentage(second);
			if (y == null) {
				y = ValueUtils.toLength(ua, second);
			}
			if (y == null) {
				throw new PropertyException();
			}
			if (kw1.equals("left")) {
				x = PercentageValue.ZERO;
			} else if (kw1.equals("center")) {
				x = PercentageValue.HALF;
			} else if (kw1.equals("right")) {
				x = PercentageValue.FULL;
			} else {
				throw new PropertyException();
			}

			return new Entry[] { new Entry(TransformOrigin.INFO_X, x), new Entry(TransformOrigin.INFO_Y, y) };
		}

		x = ValueUtils.toPercentage(lu);
		if (x == null) {
			x = ValueUtils.toLength(ua, lu);
		}
		if (x == null) {
			throw new PropertyException();
		}

		final CssToken second = tokens.next();
		if (second == null) {
			y = x;
			return new Entry[] { new Entry(TransformOrigin.INFO_X, x), new Entry(TransformOrigin.INFO_Y, y) };

		}

		if (second instanceof CssToken.Ident ident2) {
			String kw2 = ident2.lower();
			if (kw2.equals("top")) {
				y = PercentageValue.ZERO;
			} else if (kw2.equals("center")) {
				y = PercentageValue.HALF;
			} else if (kw2.equals("bottom")) {
				y = PercentageValue.FULL;
			} else {
				throw new PropertyException();
			}
		} else {
			y = ValueUtils.toPercentage(second);
			if (y == null) {
				y = ValueUtils.toLength(ua, second);
			}
			if (y == null) {
				throw new PropertyException();
			}
		}
		return new Entry[] { new Entry(TransformOrigin.INFO_X, x), new Entry(TransformOrigin.INFO_Y, y) };

	}

}