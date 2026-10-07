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
 * <li>{@code attr(bgcolor type(<color>))}, {@code attr(cols type(<integer>))}</li>
 * <li>Each can be followed by {@code , fallback}
 * ({@code attr(width px, auto)})</li>
 * </ul>
 *
 * <p>
 * <b>Cannot be used inside {@code url()}</b> (as specified, since it could exfiltrate information).
 * Called only from length, color, and numeric contexts.
 */
public final class AttrValueUtils {
	private AttrValueUtils() {
		// utility
	}

	/**
	 * Returns an unresolved value if the token is a typed {@code attr()}, otherwise null.
	 *
	 * @param defaultKind type assumed if no type is specified (LENGTH in a length context)
	 */
	public static Value toTypedAttr(UserAgent ua, CssToken token, TypedAttrValue.Kind defaultKind) {
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
				if (unit == null) {
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
		if (kind == null) {
			kind = defaultKind;
		}
		// Fallback
		Value fallback = null;
		if (i < args.size() && args.get(i) == CssToken.Op.COMMA) {
			++i;
			if (i < args.size()) {
				fallback = parseFallback(ua, args.get(i), kind);
				if (fallback == null) {
					// Invalidate the entire declaration if its fallback cannot be interpreted
					// (silently ignoring it would lay out using an unintended default).
					return null;
				}
			}
		}
		return TypedAttrValue.create(name, kind, unit, fallback);
	}

	private static TypedAttrValue.Kind kindOf(List<CssToken> args) {
		// **Angle brackets may arrive as separate tokens** (observed on 2026-08-03).
		// `type(<length>)` does not always arrive as a single Ident, so pick the type-name
		// identifier out of the arguments.
		for (final CssToken token : args) {
			if (!(token instanceof CssToken.Ident ident)) {
				continue;
			}
			final String s = ident.name().replace("<", "").replace(">", "").toLowerCase();
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

	private static Value parseFallback(UserAgent ua, CssToken token, TypedAttrValue.Kind kind) {
		switch (kind) {
		case COLOR:
			return ColorValueUtils.toPaint(ua, token);
		case NUMBER:
		case INTEGER:
			return ValueUtils.toReal(token);
		case LENGTH:
		default:
			if (token instanceof CssToken.Ident ident && ident.is("auto")) {
				return net.zamasoft.foliojet.css.value.KeywordValue.AUTO;
			}
			if (token instanceof CssToken.Percent) {
				return ValueUtils.toPercentage(token);
			}
			return ValueUtils.toLength(ua, token);
		}
	}
}
