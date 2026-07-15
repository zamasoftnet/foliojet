package net.zamasoft.foliojet.impl.css.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * HTMLのテーブルcellpaddingに相当する内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSJHtmlCellPadding extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlCellPadding();

	public static LengthValue get(CSSStyle style) {
		LengthValue value = (LengthValue) style.get(INFO);
		return value;
	}

	public static void set(CSSStyle style, LengthValue value) {
		style.set(INFO, value);
	}

	public CSSJHtmlCellPadding() {
		super("-cssj-html-cellpadding");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.create(style.getUserAgent(), 1, Unit.PX);
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		throw new UnsupportedOperationException();
	}
}