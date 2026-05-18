package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-left-width">
 * border-left-width 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: ColumnWidth.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ColumnWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ColumnWidth();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_AUTO) {
			return StyleUtils.NONE;
		}
		return BoxValueUtils.toLength(value).getLength();
	}

	protected ColumnWidth() {
		super("-cssj-column-width");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return AutoValue.AUTO_VALUE;
		}

		LengthValue value = ValueUtils.toLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		if (value.isNegative()) {
			return null;
		}
		return value;
	}

}