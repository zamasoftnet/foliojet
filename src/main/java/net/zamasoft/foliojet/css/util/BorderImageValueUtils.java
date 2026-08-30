package net.zamasoft.foliojet.css.util;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BorderImageOutsetValue;
import net.zamasoft.foliojet.css.value.BorderImageRepeatValue;
import net.zamasoft.foliojet.css.value.BorderImageRepeatValue.Mode;
import net.zamasoft.foliojet.css.value.BorderImageSliceValue;
import net.zamasoft.foliojet.css.value.BorderImageWidthValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** border-image の各値文法をlonghandとshorthandで共有するためのパーサです。 */
public final class BorderImageValueUtils {
	private BorderImageValueUtils() {
	}

	public static BorderImageSliceValue parseSlice(TokenStream tokens, UserAgent ua) throws PropertyException {
		final List<Value> values = new ArrayList<Value>(4);
		boolean fill = false;
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (ValueUtils.isKeyword(token, "fill")) {
				if (fill) {
					throw new PropertyException("fillが2度指定されています");
				}
				fill = true;
				continue;
			}
			final Value value = numberOrPercentage(ua, token);
			if (value == null || ((QuantityValue) value).isNegative() || values.size() == 4) {
				throw new PropertyException();
			}
			values.add(value);
		}
		if (values.isEmpty()) {
			throw new PropertyException();
		}
		final Value[] quad = expand(values);
		return new BorderImageSliceValue(quad[0], quad[1], quad[2], quad[3], fill);
	}

	public static BorderImageWidthValue parseWidth(TokenStream tokens, UserAgent ua) throws PropertyException {
		final List<Value> values = parseQuad(tokens, ua, Kind.WIDTH);
		final Value[] quad = expand(values);
		return new BorderImageWidthValue(quad[0], quad[1], quad[2], quad[3]);
	}

	public static BorderImageOutsetValue parseOutset(TokenStream tokens, UserAgent ua) throws PropertyException {
		final List<Value> values = parseQuad(tokens, ua, Kind.OUTSET);
		final Value[] quad = expand(values);
		return new BorderImageOutsetValue(quad[0], quad[1], quad[2], quad[3]);
	}

	public static BorderImageRepeatValue parseRepeat(TokenStream tokens) throws PropertyException {
		final Mode first = repeat(tokens.next());
		if (first == null) {
			throw new PropertyException();
		}
		final Mode second;
		if (tokens.hasNext()) {
			second = repeat(tokens.next());
			if (second == null || tokens.hasNext()) {
				throw new PropertyException();
			}
		} else {
			second = first;
		}
		return first == Mode.STRETCH && second == Mode.STRETCH ? BorderImageRepeatValue.STRETCH
				: new BorderImageRepeatValue(first, second);
	}

	public static Mode repeat(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		return switch (ident.lower()) {
		case "stretch" -> Mode.STRETCH;
		case "repeat" -> Mode.REPEAT;
		case "round" -> Mode.ROUND;
		case "space" -> Mode.SPACE;
		default -> null;
		};
	}

	private enum Kind {
		WIDTH, OUTSET
	}

	private static List<Value> parseQuad(TokenStream tokens, UserAgent ua, Kind kind) throws PropertyException {
		final List<Value> values = new ArrayList<Value>(4);
		while (tokens.hasNext()) {
			if (values.size() == 4) {
				throw new PropertyException();
			}
			final CssToken token = tokens.next();
			final Value value = kind == Kind.WIDTH ? width(ua, token) : outset(ua, token);
			if (value == null || value instanceof QuantityValue quantity && quantity.isNegative()) {
				throw new PropertyException();
			}
			values.add(value);
		}
		if (values.isEmpty()) {
			throw new PropertyException();
		}
		return values;
	}

	private static Value width(UserAgent ua, CssToken token) {
		if (ValueUtils.isAuto(token)) {
			return net.zamasoft.foliojet.css.value.KeywordValue.AUTO;
		}
		Value value = ValueUtils.toReal(token);
		if (value != null) {
			return value;
		}
		value = ValueUtils.toPercentage(token);
		if (value != null) {
			return value;
		}
		value = ValueUtils.toLength(ua, token);
		if (value != null) {
			return value;
		}
		value = CalcValueUtils.toCalc(ua, token);
		return value instanceof RealValue || value instanceof PercentageValue || value instanceof LengthValue
				|| value instanceof CalcLengthValue || value instanceof CalcFontRelativeValue ? value : null;
	}

	private static Value outset(UserAgent ua, CssToken token) {
		Value value = ValueUtils.toReal(token);
		if (value != null) {
			return value;
		}
		value = ValueUtils.toLength(ua, token);
		if (value != null) {
			return value;
		}
		value = CalcValueUtils.toCalc(ua, token);
		if (value instanceof CalcFontRelativeValue calc) {
			return calc.getRatio() == 0 ? value : null;
		}
		return value instanceof RealValue || value instanceof LengthValue ? value : null;
	}

	private static Value numberOrPercentage(UserAgent ua, CssToken token) {
		Value value = ValueUtils.toReal(token);
		if (value == null) {
			value = ValueUtils.toPercentage(token);
		}
		if (value == null) {
			value = CalcValueUtils.toCalc(ua, token);
		}
		return value instanceof RealValue || value instanceof PercentageValue ? value : null;
	}

	private static Value[] expand(List<Value> values) {
		final Value top = values.get(0);
		final Value right = values.size() >= 2 ? values.get(1) : top;
		final Value bottom = values.size() >= 3 ? values.get(2) : top;
		final Value left = values.size() >= 4 ? values.get(3) : right;
		return new Value[] { top, right, bottom, left };
	}
}
