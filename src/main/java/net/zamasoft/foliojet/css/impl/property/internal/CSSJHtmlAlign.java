package net.zamasoft.foliojet.css.impl.property.internal;

import net.zamasoft.foliojet.layout.box.params.Align;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.internal.CSSJHtmlAlignValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * HTMLの水平アラインメント相当する内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSJHtmlAlign extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlAlign();

	public static Align get(CSSStyle style) {
		CSSJHtmlAlignValue value = (CSSJHtmlAlignValue) style.get(INFO);
		return value.getHtmlAlign();
	}

	public CSSJHtmlAlign() {
		super("-cssj-html-align");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CSSJHtmlAlignValue.START_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	/** CSSから書けるようにした(2026-08-03)。start | end | center。 */
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			final String name = ident.name().toLowerCase();
			switch (name) {
			case "start":
				return net.zamasoft.foliojet.css.value.internal.CSSJHtmlAlignValue.START_VALUE;
			case "end":
				return net.zamasoft.foliojet.css.value.internal.CSSJHtmlAlignValue.END_VALUE;
			case "center":
				return net.zamasoft.foliojet.css.value.internal.CSSJHtmlAlignValue.CENTER_VALUE;
			default:
				break;
			}
		}
		throw new PropertyException();
	}
}