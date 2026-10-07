package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.font.FontSynthesisStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontSynthesisWeight;
import net.zamasoft.foliojet.css.impl.property.font.FontSynthesisSmallCaps;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-synthesis} (css-fonts-4, added 2026-08-20).
 *
 * <p>
 * {@code none | [ weight || style || small-caps || position ]}.
 * Prohibits synthesis types not listed (e.g. {@code font-synthesis: style} prohibits synthetic bold
 * and permits synthetic italics). This engine actually synthesizes only weight
 * (stroke thickening) and style (shear). It accepts small-caps/position syntactically,
 * but they have no effect because the corresponding synthesis mechanisms do not exist.
 * </p>
 */
public class FontSynthesisShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new FontSynthesisShorthand();

	protected FontSynthesisShorthand() {
		super("font-synthesis");
	}

	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(FontSynthesisWeight.INFO, global);
			primitives.set(FontSynthesisStyle.INFO, global);
			primitives.set(FontSynthesisSmallCaps.INFO, global);
			return;
		}
		boolean weight = false, style = false, smallCaps = false, any = false;
		boolean first = true;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (!(lu instanceof CssToken.Ident ident)) {
				throw new PropertyException();
			}
			if (first && ident.is("none")) {
				if (tokens.hasNext()) {
					throw new PropertyException();
				}
				primitives.set(FontSynthesisWeight.INFO, KeywordValue.NONE);
				primitives.set(FontSynthesisStyle.INFO, KeywordValue.NONE);
				primitives.set(FontSynthesisSmallCaps.INFO, KeywordValue.NONE);
				return;
			}
			first = false;
			if (ident.is("weight")) {
				if (weight) {
					throw new PropertyException();
				}
				weight = true;
			} else if (ident.is("style")) {
				if (style) {
					throw new PropertyException();
				}
				style = true;
			} else if (ident.is("small-caps")) {
				smallCaps = true;
			} else if (ident.is("position")) {
				// Accepted only (no corresponding synthesis mechanism).
			} else {
				throw new PropertyException();
			}
			any = true;
		}
		if (!any) {
			throw new PropertyException();
		}
		primitives.set(FontSynthesisWeight.INFO, weight ? KeywordValue.AUTO : KeywordValue.NONE);
		primitives.set(FontSynthesisStyle.INFO, style ? KeywordValue.AUTO : KeywordValue.NONE);
		primitives.set(FontSynthesisSmallCaps.INFO, smallCaps ? KeywordValue.AUTO : KeywordValue.NONE);
	}
}
