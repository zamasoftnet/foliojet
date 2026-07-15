package net.zamasoft.foliojet.impl.css.property.text;

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
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class LetterSpacing extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LetterSpacing();

	public static Length get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NORMAL) {
			return Length.create(0, Length.TYPE_ABSOLUTE);
		}
		if (value instanceof PercentageValue percentage) {
			return Length.create(percentage.getRatio(), Length.TYPE_RELATIVE);
		}
		if (value instanceof AbsoluteLengthValue length) {
			return Length.create(length.getLength(), Length.TYPE_ABSOLUTE);
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	protected LetterSpacing() {
		super("letter-spacing");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNormal(lu)) {
			return KeywordValue.NORMAL;
		}
		final Value value;
		value = ValueUtils.toLength(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}