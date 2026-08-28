package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.WhiteSpaceValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class WhiteSpace extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WhiteSpace();

	public static byte get(CSSStyle style) {
		return ((WhiteSpaceValue) style.get(INFO)).getWhiteSpace();
	}

	protected WhiteSpace() {
		super("white-space");
	}

	public Value getDefault(CSSStyle style) {
		return WhiteSpaceValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			final Value value;
			if (ident.equals("normal")) {
				value = WhiteSpaceValue.NORMAL_VALUE;
			} else if (ident.equals("pre")) {
				value = WhiteSpaceValue.PRE_VALUE;
			} else if (ident.equals("nowrap")) {
				value = WhiteSpaceValue.NOWRAP_VALUE;
			} else if (ident.equals("pre-wrap")) {
				value = WhiteSpaceValue.PRE_WRAP_VALUE;
			} else if (ident.equals("pre-line")) {
				value = WhiteSpaceValue.PRE_LINE_VALUE;
			} else if (ident.equals("break-spaces") || ident.equals("-moz-pre-wrap") || ident.equals("-pre-wrap")
					|| ident.equals("-o-pre-wrap")) {
				// break-spaces(css-text-3)は行末の連続空白の扱いだけが
				// pre-wrapと違う——pre-wrapで近似。接頭辞つきはIE/旧Firefox/
				// Opera向けのpre-wrap別名(2026-08-29)
				value = WhiteSpaceValue.PRE_WRAP_VALUE;
			} else if (ident.equals("wrap")) {
				// css-text-4のwhite-space短縮形の折り返し指定。normalと同じ
				value = WhiteSpaceValue.NORMAL_VALUE;
			} else {
				throw new PropertyException();
			}
			return value;

		}
		throw new PropertyException();
	}

}