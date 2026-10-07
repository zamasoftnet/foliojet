package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-synthesis-weight} (css-fonts-4, added 2026-08-20).
 *
 * <p>
 * With {@code none}, if no bold font is found, draws with the available weight
 * without synthetic bold (thickening the outline stroke).
 * Defaults to {@code auto} (synthesizes as before). Does not affect font selection
 * (css-fonts-4 §7.4). The shorthand is {@code font-synthesis}.
 * </p>
 */
public class FontSynthesisWeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontSynthesisWeight();

	/** Whether synthetic bold is allowed. */
	public static boolean get(final CSSStyle style) {
		return style.get(INFO) != KeywordValue.NONE;
	}

	protected FontSynthesisWeight() {
		super("font-synthesis-weight");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident) {
			if (ident.is("auto")) {
				return KeywordValue.AUTO;
			}
			if (ident.is("none")) {
				return KeywordValue.NONE;
			}
		}
		throw new PropertyException();
	}
}
