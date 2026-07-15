package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-left-width">
 * border-left-width 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnRuleWidth.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnRuleWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ColumnRuleWidth();

	public static double get(CSSStyle style) {
		return ((AbsoluteLengthValue) style.get(INFO)).getLength();
	}

	protected ColumnRuleWidth() {
		super("-cssj-column-rule-width");
	}

	public Value getDefault(CSSStyle style) {
		return style.getUserAgent().getBorderWidth(UserAgent.BORDER_WIDTH_MEDIUM);
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		LengthValue value = BorderValueUtils.toBorderWidth(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}