package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.LineBreakValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code line-break} (css-text-3 §5.2, added 2026-08-29).
 *
 * <p>
 * {@code auto | loose | normal | strict | anywhere}. Inherited; defaults to
 * {@code auto}. {@code LanguageProfile_ja} combines kinsoku (line-breaking rules) strictness
 * with {@code word-break} and passes it to {@code JlreqBreakingRules}.
 * {@code auto} is at UA discretion (per the specification); this implementation treats it
 * as {@code strict}, using JLREQ line-start and line-end restrictions unchanged
 * to preserve the print-oriented default (browser {@code auto} is equivalent to {@code normal},
 * allowing small kana and prolonged sound marks at line starts).
 * </p>
 */
public class LineBreak extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineBreak();

	public static LineBreakValue get(final CSSStyle style) {
		return (LineBreakValue) style.get(INFO);
	}

	protected LineBreak() {
		super("line-break");
	}

	public Value getDefault(final CSSStyle style) {
		return LineBreakValue.AUTO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (token instanceof CssToken.Ident ident && !tokens.hasNext()) {
			for (final LineBreakValue value : LineBreakValue.values()) {
				if (ident.is(value.toString())) {
					return value;
				}
			}
		}
		throw new PropertyException();
	}
}
