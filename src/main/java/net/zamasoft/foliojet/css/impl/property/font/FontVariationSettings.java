package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * {@code font-variation-settings} (css-fonts-4, added 2026-08-20).
 *
 * <p>
 * Supported <b>as an @font-face descriptor</b>: creates a fixed instance of a variable font
 * at the specified axis coordinates (e.g. {@code "wdth" 75, "slnt" -10})
 * ({@code VariableFontInstancer}). If wght is explicit, generates only that instance
 * without sweeping weights. <b>Application as an element property is unsupported</b>
 * (per-element axis application is expensive with static instances;
 * use the normal font-weight mechanism per element).
 * </p>
 */
public class FontVariationSettings extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariationSettings();

	/** List value of axis tag→coordinate mappings. */
	public record AxesValue(Map<String, Double> axes) implements Value {
	}

	/** Specified axis map (null for normal/unspecified). */
	public static Map<String, Double> get(final CSSStyle style) {
		final Value value = style.get(FontVariationSettings.INFO);
		return value instanceof AxesValue v ? v.axes() : null;
	}

	protected FontVariationSettings() {
		super("font-variation-settings");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final Map<String, Double> axes = new LinkedHashMap<>();
		boolean first = true;
		while (tokens.hasNext()) {
			if (!first) {
				tokens.eatComma();
				if (!tokens.hasNext()) {
					break;
				}
			}
			final CssToken lu = tokens.next();
			if (first && lu instanceof CssToken.Ident ident && ident.is("normal")) {
				return KeywordValue.NORMAL;
			}
			first = false;
			if (!(lu instanceof CssToken.Str str) || str.value().length() != 4) {
				throw new PropertyException();
			}
			if (!tokens.hasNext()) {
				throw new PropertyException();
			}
			final CssToken num = tokens.next();
			if (!(num instanceof CssToken.Num n)) {
				throw new PropertyException();
			}
			axes.put(str.value(), n.value());
		}
		if (axes.isEmpty()) {
			throw new PropertyException();
		}
		return new AxesValue(axes);
	}
}
