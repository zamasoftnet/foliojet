package net.zamasoft.foliojet.css.util;

import java.util.List;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.TypedAttrValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Parses <b>typed {@code attr()}</b> (CSS Values 5) (added 2026-08-03).
 *
 * <p>
 * Accepted forms match <b>the syntax shipped by Chrome</b> (133 and later).
 *
 * <ul>
 * <li>{@code attr(width px)}: unit shorthand. Adds the unit to a unitless attribute
 * value ({@code width="200"} → {@code 200px})</li>
 * <li>{@code attr(width type(<length>))}: type annotation. The attribute value supplies the unit</li>
 * <li>{@code attr(bgcolor type(<color>))}</li>
 * <li>Each can be followed by {@code , fallback}
 * ({@code attr(width px, auto)})</li>
 * </ul>
 *
 * <p>
 * The type must be one the property takes. {@code type(<number>)} and {@code type(<integer>)} are read, but no
 * property takes a number from an attribute yet, so a declaration with them is invalid.
 *
 * <p>
 * <b>Cannot be used inside {@code url()}</b> (as specified, since it could exfiltrate information).
 * Called only from length and color contexts.
 */
public final class AttrValueUtils {
	private AttrValueUtils() {
		// utility
	}

	/**
	 * Returns an unresolved value if the token is a typed {@code attr()} of a type the property takes, otherwise
	 * null. A length or a percentage must not be negative ({@link #toTypedAttr(UserAgent, CssToken,
	 * TypedAttrValue.Kind, boolean)} for the properties that take negative ones).
	 *
	 * @param expected the type of the values the receiving property takes: {@code LENGTH}, {@code LENGTH_PERCENTAGE}
	 *                 or {@code COLOR}; also the type assumed if the {@code attr()} names none
	 */
	public static Value toTypedAttr(UserAgent ua, CssToken token, TypedAttrValue.Kind expected) {
		return toTypedAttr(ua, token, expected, false);
	}

	/**
	 * Returns an unresolved value if the token is a typed {@code attr()} of a type the property takes, otherwise
	 * null.
	 *
	 * <p>
	 * The parser of the property says here what the property takes, and the {@code attr()} resolves to nothing
	 * else: its own type must be that type, its fallback must be a value of it, and at computed-value time an
	 * attribute that does not hold such a value gives the fallback ({@link TypedAttrValue#resolve}). The type and
	 * the fallback used to be taken as written, so {@code width: attr(x type(<color>))} or a percentage from an
	 * attribute reached a property that reads lengths.
	 * </p>
	 *
	 * @param expected the type of the values the receiving property takes: {@code LENGTH}, {@code LENGTH_PERCENTAGE}
	 *                 or {@code COLOR}; also the type assumed if the {@code attr()} names none
	 * @param negative for lengths and percentages, whether the property takes negative ones
	 */
	public static Value toTypedAttr(UserAgent ua, CssToken token, TypedAttrValue.Kind expected, boolean negative) {
		if (!(token instanceof CssToken.Func func) || !func.is("attr")) {
			return null;
		}
		final List<CssToken> args = func.args();
		if (args.isEmpty() || !(args.get(0) instanceof CssToken.Ident nameToken)) {
			return null;
		}
		final String name = nameToken.name();
		int i = 1;
		TypedAttrValue.Kind kind = null;
		Unit unit = Unit.PX;
		// Type or unit (up to the comma)
		if (i < args.size() && args.get(i) != CssToken.Op.COMMA) {
			final CssToken typeToken = args.get(i);
			if (typeToken instanceof CssToken.Ident ident) {
				unit = Unit.of(ident.name());
				// Unit.of names every unit, also those that are not lengths (deg, s, hz) and unknown ones
				// (OTHER). Accept only a unit the length parser builds a length from: TypedAttrValue
				// builds the attribute's number the same way.
				if (ValueUtils.toLength(ua, new CssToken.Dim(1, unit, ident.name())) == null) {
					return null;
				}
				kind = TypedAttrValue.Kind.LENGTH;
			} else if (typeToken instanceof CssToken.Func typeFunc && typeFunc.is("type")) {
				kind = kindOf(typeFunc.args());
				if (kind == null) {
					return null;
				}
			} else {
				return null;
			}
			++i;
		}
		if (kind == null || kind == TypedAttrValue.Kind.LENGTH && expected == TypedAttrValue.Kind.LENGTH_PERCENTAGE) {
			// No type, or a length for a property that takes percentages as well: the attribute may hold
			// whatever the property takes (width="50%" with width: attr(width px))
			kind = expected;
		} else if (kind != expected) {
			// A type the property does not take (a color for a width, a number for a color)
			return null;
		}
		// Fallback
		Value fallback = null;
		if (i < args.size() && args.get(i) == CssToken.Op.COMMA) {
			++i;
			if (i < args.size()) {
				fallback = parseFallback(ua, args.get(i), kind, negative);
				if (fallback == null) {
					// Invalidate the entire declaration if its fallback cannot be interpreted
					// (silently ignoring it would lay out using an unintended default).
					return null;
				}
			}
		}
		return TypedAttrValue.create(name, kind, unit, fallback, negative);
	}

	private static TypedAttrValue.Kind kindOf(List<CssToken> args) {
		// **Angle brackets may arrive as separate tokens** (observed on 2026-08-03).
		// `type(<length>)` does not always arrive as a single Ident, so pick the type-name
		// identifier out of the arguments.
		for (final CssToken token : args) {
			if (!(token instanceof CssToken.Ident ident)) {
				continue;
			}
			final String s = ident.name().replace("<", "").replace(">", "").toLowerCase(java.util.Locale.ROOT);
			switch (s) {
			case "length":
				return TypedAttrValue.Kind.LENGTH;
			case "color":
				return TypedAttrValue.Kind.COLOR;
			case "number":
				return TypedAttrValue.Kind.NUMBER;
			case "integer":
				return TypedAttrValue.Kind.INTEGER;
			default:
				break;
			}
		}
		return null;
	}

	/** The fallback as a value of the type, or null if it is not one. */
	private static Value parseFallback(UserAgent ua, CssToken token, TypedAttrValue.Kind kind, boolean negative) {
		final net.zamasoft.foliojet.css.value.QuantityValue quantity;
		switch (kind) {
		case COLOR:
			return ColorValueUtils.toPaint(ua, token);
		case NUMBER:
		case INTEGER:
			return ValueUtils.toReal(token);
		case LENGTH_PERCENTAGE:
			if (token instanceof CssToken.Ident ident && ident.is("auto")) {
				return net.zamasoft.foliojet.css.value.KeywordValue.AUTO;
			}
			quantity = token instanceof CssToken.Percent ? ValueUtils.toPercentage(token)
					: ValueUtils.toLength(ua, token);
			break;
		case LENGTH:
		default:
			// A length only: auto and a percentage are no values of a border width or a border spacing
			quantity = ValueUtils.toLength(ua, token);
			break;
		}
		if (quantity != null && !negative && quantity.isNegative()) {
			return null;
		}
		return quantity;
	}
}
