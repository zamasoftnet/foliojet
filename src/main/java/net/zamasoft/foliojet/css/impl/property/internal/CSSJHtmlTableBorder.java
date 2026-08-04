package net.zamasoft.foliojet.css.impl.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * HTMLのテーブルborderに相当する内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSJHtmlTableBorder extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlTableBorder();

	public static CSSJHtmlTableBorderValue get(CSSStyle style) {
		CSSJHtmlTableBorderValue value = (CSSJHtmlTableBorderValue) style.get(INFO);
		return value;
	}

	public static void set(CSSStyle style, CSSJHtmlTableBorderValue value) {
		style.set(INFO, value);
	}

	public CSSJHtmlTableBorder() {
		super("-cssj-html-table-border");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CSSJHtmlTableBorderValue.NULL_BORDER;
	}

	public boolean isInherited() {
		return true;
	}

	/**
	 * <b>CSSから書けるようにした</b>(2026-08-03)。構文は
	 * {@code -cssj-html-table-border: <length> <color>?}。
	 * 表の {@code border}/{@code bordercolor} 属性をCSSへ移送するために要る
	 * ——この値は表からセルへ罫線の幅と色を配る内部の通り道である。
	 */
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final net.zamasoft.foliojet.css.value.LengthValue width = net.zamasoft.foliojet.css.util.BorderValueUtils
				.toBorderWidth(ua, lu);
		if (width == null) {
			throw new PropertyException();
		}
		net.zamasoft.foliojet.css.value.ColorValue color = null;
		if (tokens.hasNext()) {
			color = net.zamasoft.foliojet.css.util.ColorValueUtils.toColor(ua, tokens.next());
			if (color == null) {
				throw new PropertyException();
			}
		}
		return new net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue(width, color);
	}
}