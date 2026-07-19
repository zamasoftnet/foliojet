package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class WordSpacing extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WordSpacing();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NORMAL) {
			return 0;
		}
		if (value instanceof AbsoluteLengthValue length) {
			return length.getLength();
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	protected WordSpacing() {
		super("word-spacing");
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
		final LengthValue value = ValueUtils.toLength(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}