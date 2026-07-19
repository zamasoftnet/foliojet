package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.RectValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class Clip extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Clip();

	public static RectValue get(CSSStyle style) {
		Value value = style.get(INFO);
		return value == KeywordValue.AUTO ? null : (RectValue) value;
	}

	private Clip() {
		super("clip");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			return KeywordValue.AUTO;
		}
		if (lu instanceof CssToken.Func func && func.is("rect")) {
			final TokenStream params = func.argStream();
			final LengthValue top = toSide(ua, params);
			final LengthValue left = toSide(ua, params);
			final LengthValue bottom = toSide(ua, params);
			final LengthValue right = toSide(ua, params);
			final RectValue rect = new RectValue(top, left, bottom, right);
			return rect;
		}
		throw new PropertyException(String.valueOf(lu));
	}

	private static LengthValue toSide(UserAgent ua, TokenStream params) throws PropertyException {
		params.eatComma();
		final CssToken token = params.next();
		if (token == null) {
			throw new PropertyException("rectの値が不足しています");
		}
		if (ValueUtils.isAuto(token)) {
			return AbsoluteLengthValue.ZERO;
		}
		final LengthValue length = ValueUtils.toLength(ua, token);
		if (length == null) {
			throw new PropertyException(String.valueOf(token));
		}
		return length;
	}

}