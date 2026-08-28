package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * @author MIYABE Tatsuhiko
 */
public class BackgroundColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundColor();

	public static PaintValue get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.TRANSPARENT) {
			return null;
		}
		return (PaintValue)value;
	}

	protected BackgroundColor() {
		super("background-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.TRANSPARENT;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		// 型付き attr()(2026-08-03)。色も属性から取れる(bgcolor/text/link等の
		// 移送に要る)。解決の窓口は長さと同じ
		value = ValueUtils.emExToAbsoluteLength(value, style);
		// CSS Color 4: background-colorのcurrentcolorは、この要素のcolorの
		// computed valueへ解決する。MaskImage等の利用側へ未解決キーワードを
		// 漏らさず、通常の背景描画にも同じ色実体を渡す。
		if (value == KeywordValue.DEFAULT) {
			return style.get(CSSColor.INFO);
		}
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && ident.is("currentcolor")) {
			return KeywordValue.DEFAULT;
		}
		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		Value value = ColorValueUtils.toPaint(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}
