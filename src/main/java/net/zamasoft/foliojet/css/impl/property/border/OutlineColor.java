package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * outline-color 特性です(CSS UI 3 §4、2026-08-29)。値は
 * {@code <color> | invert}。invertはPDFでは表現できないので、
 * border-colorの既定と同じくcurrentColorとして扱う。
 *
 * @author MIYABE Tatsuhiko
 */
public class OutlineColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new OutlineColor();

	/** 色を返します。transparentならnull。 */
	public static Color get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == KeywordValue.TRANSPARENT) {
			return null;
		}
		return ((ColorValue) value).getColor();
	}

	protected OutlineColor() {
		super("outline-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == KeywordValue.DEFAULT) {
			// invert / 未指定 → currentColor
			return style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = toOutlineColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	/**
	 * {@code <color> | invert}を値に変換します。該当しなければnull。
	 * outlineショートハンドと共用。
	 */
	public static Value toOutlineColor(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Ident ident && ident.lower().equals("invert")) {
			return KeywordValue.DEFAULT;
		}
		if (ColorValueUtils.isTransparent(token)) {
			return KeywordValue.TRANSPARENT;
		}
		return ColorValueUtils.toColor(ua, token);
	}
}
