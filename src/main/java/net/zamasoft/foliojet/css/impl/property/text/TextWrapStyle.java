package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TextWrapStyleValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * CSS Text 4 {@code text-wrap-style} (added 2026-07-25;
 * replaces the legacy {@code text.line-breaker} conversion property).
 *
 * <p>
 * An inherited, opt-in line-breaking strategy. Defaults to {@code auto}=greedy
 * (performance); {@code pretty} selects Knuth-Plass global optimization (quality).
 * This is the same distinction used by browsers (Chrome/Firefox/Safari).
 * </p>
 *
 * <p>
 * <b>{@code balance} and {@code stable} are accepted syntactically
 * but treated as {@code auto}}</b> (unsupported; this limitation is explicitly listed
 * in the support table).
 * {@code balance} requests balanced lines; {@code stable} requests stable initial lines
 * during relayout. Neither has a counterpart in this engine's two strategies (greedy/K-P).
 * </p>
 *
 * <p>
 * K-P applies to paragraphs, so only the value computed on the block establishing
 * a paragraph takes effect. Specifying it on inline elements or {@code ::first-line}
 * does not change paragraph layout (paragraphs with {@code ::first-line} are
 * ineligible for K-P in the first place).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class TextWrapStyle extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextWrapStyle();

	public static byte get(CSSStyle style) {
		return ((TextWrapStyleValue) style.get(INFO)).getTextWrapStyle();
	}

	protected TextWrapStyle() {
		super("text-wrap-style");
	}

	public Value getDefault(CSSStyle style) {
		return TextWrapStyleValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			final Value value = toValue(((CssToken.Ident) lu).lower());
			if (value != null) {
				return value;
			}
		}
		throw new PropertyException();
	}

	/**
	 * Converts an identifier to a value (shared with the {@code text-wrap} shorthand).
	 * Returns null for unknown identifiers.
	 */
	public static Value toValue(final String ident) {
		switch (ident) {
		case "auto":
			return TextWrapStyleValue.AUTO_VALUE;

		case "pretty":
			return TextWrapStyleValue.PRETTY_VALUE;

		case "balance":
		case "stable":
			// Accepted syntactically but treated as auto (unsupported).
			return TextWrapStyleValue.AUTO_VALUE;

		default:
			return null;
		}
	}
}
