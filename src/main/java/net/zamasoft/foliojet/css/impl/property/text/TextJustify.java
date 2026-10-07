package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TextJustifyValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * CSS Text 3 {@code text-justify} (added 2026-09-02).
 *
 * <p>
 * Determines where to distribute remaining line space for justification ({@code text-align: justify}).
 * Inherited. The default {@code auto} <b>depends on language</b>: Japanese lines use
 * JLREQ's staged distribution (word spacing → spacing between Japanese and Latin text → character spacing).
 * Korean ({@code lang}={@code ko}) uses <b>word spacing only</b>: Chrome measurements showed
 * that only spaces expanded, with syllable advances unchanged by even one pixel (2026-09-01).
 * Other languages distribute evenly over separable boundaries as before.
 * </p>
 *
 * <p>
 * {@code inter-word} distributes only to word spaces (lines without them remain unchanged);
 * {@code inter-character} (alias {@code distribute}) also distributes between characters;
 * {@code none} disables justification.
 * </p>
 */
public class TextJustify extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextJustify();

	public static byte get(final CSSStyle style) {
		return ((TextJustifyValue) style.get(INFO)).getTextJustify();
	}

	protected TextJustify() {
		super("text-justify");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return TextJustifyValue.AUTO_VALUE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			final Value value = toValue(((CssToken.Ident) lu).lower());
			if (value != null) {
				return value;
			}
		}
		throw new PropertyException();
	}

	/** Converts an identifier to a value. {@code null} if unknown. */
	public static Value toValue(final String ident) {
		switch (ident) {
		case "auto":
			return TextJustifyValue.AUTO_VALUE;
		case "none":
			return TextJustifyValue.NONE_VALUE;
		case "inter-word":
			return TextJustifyValue.INTER_WORD_VALUE;
		case "inter-character":
		case "distribute":
			return TextJustifyValue.INTER_CHARACTER_VALUE;
		default:
			return null;
		}
	}
}
