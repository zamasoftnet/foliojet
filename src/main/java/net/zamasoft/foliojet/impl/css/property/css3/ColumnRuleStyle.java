package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnRuleStyle.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnRuleStyle extends AbstractPrimitivePropertyInfo {
	public static final AbstractPrimitivePropertyInfo INFO = new ColumnRuleStyle();

	public static short get(CSSStyle style) {
		BorderStyleValue value = (BorderStyleValue) style.get(INFO);
		return value.getBorderStyle();
	}

	protected ColumnRuleStyle() {
		super("-cssj-column-rule-style");
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
		Value value = BorderValueUtils.toBorderStyle(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}