package net.zamasoft.foliojet.css.impl.property.border;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * outline-style 特性です(CSS UI 3 §4、2026-08-29)。値は
 * {@code auto | <outline-line-style>}(border-styleからhiddenを除いたもの)。
 * {@code auto}はUA任せのフォーカスリング表現なので、solidとして扱う。
 *
 * @author MIYABE Tatsuhiko
 */
public class OutlineStyle extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new OutlineStyle();

	/** {@link net.zamasoft.foliojet.layout.box.params.Border}のスタイル定数を返します。 */
	public static short get(CSSStyle style) {
		return ((BorderStyleValue) style.get(INFO)).getBorderStyle();
	}

	protected OutlineStyle() {
		super("outline-style");
	}

	public Value getDefault(CSSStyle style) {
		return BorderStyleValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final BorderStyleValue value = toOutlineStyle(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	/**
	 * {@code auto | <outline-line-style>}を値に変換します。該当しなければnull。
	 * outlineショートハンドと共用。
	 */
	public static BorderStyleValue toOutlineStyle(CssToken token) {
		if (token instanceof CssToken.Ident ident && ident.lower().equals("auto")) {
			return BorderStyleValue.SOLID_VALUE;
		}
		final BorderStyleValue value = BorderValueUtils.toBorderStyle(token);
		if (value == BorderStyleValue.HIDDEN_VALUE) {
			// hiddenはoutline-styleに存在しない
			return null;
		}
		return value;
	}
}
