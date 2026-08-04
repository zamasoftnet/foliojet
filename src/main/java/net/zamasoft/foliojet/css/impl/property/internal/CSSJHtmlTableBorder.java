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

	/**
	 * <b>宣言した表で解いてから継承させる</b>(2026-08-03)。属性由来の値
	 * ({@code attr(border px)})は要素依存なので、未解決のまま継承すると
	 * セル側で解けない。
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		if (value instanceof Unresolved unresolved) {
			return unresolved.resolve(style);
		}
		return value;
	}

	/**
	 * 解析直後の未解決値。{@code attr()}を含みうるので、具象化は計算値の
	 * 段階({@link #getComputedValue})で行う。
	 */
	private record Unresolved(Value width, Value color) implements Value {
		Value resolve(CSSStyle style) {
			final Value w = net.zamasoft.foliojet.css.util.ValueUtils.emExToAbsoluteLength(this.width, style);
			final Value c = this.color == null ? null
					: net.zamasoft.foliojet.css.util.ValueUtils.emExToAbsoluteLength(this.color, style);
			if (!(w instanceof net.zamasoft.foliojet.css.value.LengthValue length)) {
				return net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue.NULL_BORDER;
			}
			return new net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue(length,
					c instanceof net.zamasoft.foliojet.css.value.ColorValue color ? color : null);
		}
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
		Value width = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, lu,
				net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.LENGTH);
		if (width == null) {
			width = net.zamasoft.foliojet.css.util.BorderValueUtils.toBorderWidth(ua, lu);
		}
		if (width == null) {
			throw new PropertyException();
		}
		Value color = null;
		if (tokens.hasNext()) {
			final net.zamasoft.foliojet.css.token.CssToken next = tokens.next();
			color = net.zamasoft.foliojet.css.util.AttrValueUtils.toTypedAttr(ua, next,
					net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR);
			if (color == null) {
				color = net.zamasoft.foliojet.css.util.ColorValueUtils.toColor(ua, next);
			}
			if (color == null) {
				throw new PropertyException();
			}
		}
		return new Unresolved(width, color);
	}
}