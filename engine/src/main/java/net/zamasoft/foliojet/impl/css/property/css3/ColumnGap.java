package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.EmLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-left-width">
 * border-left-width 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnGap.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnGap extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ColumnGap();

	public static double get(CSSStyle style) {
		return ((AbsoluteLengthValue) style.get(INFO)).getLength();
	}

	protected ColumnGap() {
		super("-cssj-column-gap");
	}

	public Value getDefault(CSSStyle style) {
		return EmLengthValue.create(1).toAbsoluteLength(style);
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isNormal(lu)) {
			return EmLengthValue.create(1);
		}
		LengthValue value = BorderValueUtils.toBorderWidth(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}