package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontKerningValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * {@code font-kerning: auto | normal | none} (css-fonts-4 §6.3, 2026-08-29).
 *
 * <p>
 * Kerning itself was implemented; only the control to disable it with {@code none} was missing
 * (confirmed by warnings on real sites). Pass {@code none} to the font feature sequence
 * as explicit {@code kern} off; pdfg2d's {@code FontMetricsImpl} disables pair adjustment
 * only when explicitly off. Explicit {@code font-feature-settings} are applied afterward
 * and therefore take precedence.
 * </p>
 */
public class FontKerning extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontKerning();

	/** Feature sequence that explicitly turns {@code kern} off. */
	private static final FontFeatureSet KERN_OFF = FontFeatureSet.of(new int[] { FontFeatureSet.packTag("kern") },
			new int[] { 0 });

	public static FontKerningValue get(final CSSStyle style) {
		return (FontKerningValue) style.get(INFO);
	}

	/** {@code kern} 0 for {@code none}; otherwise empty (overrides nothing). */
	public static FontFeatureSet featureSet(final CSSStyle style) {
		return get(style) == FontKerningValue.NONE_VALUE ? KERN_OFF : FontFeatureSet.EMPTY;
	}

	protected FontKerning() {
		super("font-kerning");
	}

	public Value getDefault(final CSSStyle style) {
		return FontKerningValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && !tokens.hasNext()) {
			switch (ident.lower()) {
			case "auto":
				return FontKerningValue.AUTO_VALUE;
			case "normal":
				return FontKerningValue.NORMAL_VALUE;
			case "none":
				return FontKerningValue.NONE_VALUE;
			default:
				break;
			}
		}
		throw new PropertyException();
	}
}
