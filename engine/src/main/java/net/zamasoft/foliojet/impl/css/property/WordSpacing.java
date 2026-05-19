package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.NormalValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WordSpacing.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class WordSpacing extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WordSpacing();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		switch (value.getValueType()) {
		case Value.TYPE_NORMAL:
			return 0;
		case Value.TYPE_ABSOLUTE_LENGTH:
			return ((AbsoluteLengthValue) value).getLength();
		default:
			throw new IllegalStateException();
		}
	}

	protected WordSpacing() {
		super("word-spacing");
	}

	public Value getDefault(CSSStyle style) {
		return NormalValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isNormal(lu)) {
			return NormalValue.NORMAL_VALUE;
		}
		final LengthValue value = ValueUtils.toLength(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}