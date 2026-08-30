package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;
import java.util.Locale;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontVariantLigaturesValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-variant-ligatures}(CSS Fonts)です。
 *
 * <p>
 * 値をOpenTypeの{@code liga/clig/dlig/hlig/calt}へ変換してFontStyleまで
 * 搬送します。ただし現在のpdfg2dは合字・文脈置換lookupを処理しないため、
 * 描画上は受理・保持相当です。
 * </p>
 */
public final class FontVariantLigatures extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariantLigatures();

	public static FontVariantLigaturesValue get(final CSSStyle style) {
		return (FontVariantLigaturesValue) style.get(INFO);
	}

	private FontVariantLigatures() {
		super("font-variant-ligatures");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return FontVariantLigaturesValue.NORMAL_VALUE;
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
		if (tokens.size() == 1 && tokens.eat("normal")) {
			return FontVariantLigaturesValue.NORMAL_VALUE;
		}
		if (tokens.size() == 1 && tokens.eat("none")) {
			return FontVariantLigaturesValue.NONE_VALUE;
		}
		boolean commonSpecified = false;
		boolean commonEnabled = false;
		boolean discretionarySpecified = false;
		boolean discretionaryEnabled = false;
		boolean historicalSpecified = false;
		boolean historicalEnabled = false;
		boolean contextualSpecified = false;
		boolean contextualEnabled = false;
		while (tokens.hasNext()) {
			final String raw = tokens.ident();
			if (raw == null) {
				throw new PropertyException();
			}
			final String ident = raw.toLowerCase(Locale.ROOT);
			switch (ident) {
			case "common-ligatures", "no-common-ligatures":
				if (commonSpecified) {
					throw new PropertyException();
				}
				commonSpecified = true;
				commonEnabled = ident.equals("common-ligatures");
				break;
			case "discretionary-ligatures", "no-discretionary-ligatures":
				if (discretionarySpecified) {
					throw new PropertyException();
				}
				discretionarySpecified = true;
				discretionaryEnabled = ident.equals("discretionary-ligatures");
				break;
			case "historical-ligatures", "no-historical-ligatures":
				if (historicalSpecified) {
					throw new PropertyException();
				}
				historicalSpecified = true;
				historicalEnabled = ident.equals("historical-ligatures");
				break;
			case "contextual", "no-contextual":
				if (contextualSpecified) {
					throw new PropertyException();
				}
				contextualSpecified = true;
				contextualEnabled = ident.equals("contextual");
				break;
			default:
				throw new PropertyException();
			}
		}
		if (!commonSpecified && !discretionarySpecified && !historicalSpecified && !contextualSpecified) {
			throw new PropertyException();
		}
		return FontVariantLigaturesValue.create(commonSpecified, commonEnabled, discretionarySpecified,
				discretionaryEnabled, historicalSpecified, historicalEnabled, contextualSpecified,
				contextualEnabled);
	}
}
