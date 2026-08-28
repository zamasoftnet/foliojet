package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BoxShadowValue;
import net.zamasoft.foliojet.css.value.css3.BoxShadowValue.Shadow;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * box-shadow 特性です(CSS Backgrounds 3 §7、2026-08-29)。
 *
 * <p>
 * 構文は {@code none | <shadow>#}、
 * {@code <shadow> = inset? && <length>{2,4} && <color>?}。長さの順は
 * offset-x, offset-y, blur-radius, spread-radius。ぼかし半径は負を
 * 拒否する。色はtransparentなら影そのものを落とす(text-shadowと同じ)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class BoxShadow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BoxShadow();

	/**
	 * 使用値の影の列を返します。影が無ければnull。先頭の影が最前面。
	 */
	public static net.zamasoft.foliojet.layout.box.params.BoxShadow[] get(CSSStyle style) {
		final BoxShadowValue value = (BoxShadowValue) style.get(INFO);
		final Shadow[] src = value.getShadows();
		if (src.length == 0) {
			return null;
		}
		final net.zamasoft.foliojet.layout.box.params.BoxShadow[] shadows = new net.zamasoft.foliojet.layout.box.params.BoxShadow[src.length];
		for (int i = 0; i < src.length; ++i) {
			final Shadow s = src[i];
			final Color color = s.color == null ? CSSColor.get(style) : s.color.getColor();
			shadows[i] = new net.zamasoft.foliojet.layout.box.params.BoxShadow(toLength(s.x, style),
					toLength(s.y, style), toLength(s.blur, style), toLength(s.spread, style), color, s.inset);
		}
		return shadows;
	}

	private static double toLength(LengthValue value, CSSStyle style) {
		if (value == null) {
			return 0;
		}
		return ((AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(value, style)).getLength();
	}

	protected BoxShadow() {
		super("box-shadow");
	}

	public Value getDefault(CSSStyle style) {
		return BoxShadowValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isNone(tokens.peek())) {
			tokens.next();
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return BoxShadowValue.NONE;
		}
		final List<Shadow> shadows = new ArrayList<Shadow>();
		for (final TokenStream part : tokens.splitComma()) {
			final Shadow shadow = parseShadow(part, ua);
			if (shadow != null) {
				shadows.add(shadow);
			}
		}
		return BoxShadowValue.create(shadows.toArray(new Shadow[shadows.size()]));
	}

	/**
	 * 1つの影を解析します。transparentの影はnull(描いても見えない)。
	 */
	private static Shadow parseShadow(TokenStream tokens, UserAgent ua) throws PropertyException {
		final LengthValue[] lengths = new LengthValue[4];
		int count = 0;
		// 長さは連続していなければならない(色やinsetを挟んだら打ち切り)
		boolean lengthsClosed = false;
		Value color = null;
		boolean inset = false;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident ident && ident.lower().equals("inset")) {
				if (inset) {
					throw new PropertyException();
				}
				inset = true;
				if (count > 0) {
					lengthsClosed = true;
				}
				continue;
			}
			final LengthValue length = ValueUtils.toLength(ua, lu);
			if (length != null) {
				if (lengthsClosed || count >= 4) {
					throw new PropertyException();
				}
				// ぼかし半径(3つ目)は負を許さない
				if (count == 2 && length.isNegative()) {
					throw new PropertyException();
				}
				lengths[count++] = length;
				continue;
			}
			if (color == null) {
				if (ColorValueUtils.isTransparent(lu)) {
					color = net.zamasoft.foliojet.css.value.KeywordValue.TRANSPARENT;
				} else {
					color = ColorValueUtils.toColor(ua, lu);
				}
				if (color != null) {
					if (count > 0) {
						lengthsClosed = true;
					}
					continue;
				}
			}
			throw new PropertyException();
		}
		if (count < 2) {
			throw new PropertyException();
		}
		if (color == net.zamasoft.foliojet.css.value.KeywordValue.TRANSPARENT) {
			return null;
		}
		return new Shadow(lengths[0], lengths[1], lengths[2], lengths[3], (ColorValue) color, inset);
	}
}
