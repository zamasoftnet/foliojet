package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextIndent.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextIndent extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextIndent();

	public static Length get(CSSStyle style) {
		Value value = style.get(INFO);
		switch (value.getValueType()) {
		case Value.TYPE_PERCENTAGE:
			return Length.create(((PercentageValue) value).getRatio(), Length.TYPE_RELATIVE);
		case Value.TYPE_ABSOLUTE_LENGTH:
			return Length.create(((AbsoluteLengthValue) value).getLength(), Length.TYPE_ABSOLUTE);
		default:
			throw new IllegalStateException();
		}
	}

	public TextIndent() {
		super("text-indent");
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		Value value = ValueUtils.toLength(ua, lu);
		if (value == null) {
			value = ValueUtils.toPercentage(lu);
		}
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}