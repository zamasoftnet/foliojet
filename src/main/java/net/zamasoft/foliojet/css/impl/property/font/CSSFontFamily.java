package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSFontFamily extends AbstractPrimitivePropertyInfo {
	public static final AbstractPrimitivePropertyInfo INFO = new CSSFontFamily();

	public static FontFamilyList get(CSSStyle style) {
		return ((FontFamilyValue) style.get(INFO)).asFontFamilyList();
	}

	protected CSSFontFamily() {
		super("font-family");
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getDefaultFontFamily();
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		// 型付き attr()(2026-08-03)。<font face="..."> の移送に要る。
		// 属性の中身は font-family の値そのもの(カンマ区切りの並び)として
		// 解釈するので、解決は計算値の段階で行う
		final CssToken first = tokens.peek();
		if (first instanceof CssToken.Func func && func.is("attr")) {
			tokens.next();
			return net.zamasoft.foliojet.css.value.TypedAttrValue.create(
					func.args().isEmpty() || !(func.args().get(0) instanceof CssToken.Ident name) ? "" : name.name(),
					net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.FONT_FAMILY, null, null);
		}
		final FontFamilyValue fontFamily = FontValueUtils.toFontFamily(ua, tokens);
		if (fontFamily == null) {
			throw new PropertyException();
		}
		return fontFamily;
	}

}
