package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.FontValueUtils;
import net.zamasoft.foliojet.css.value.FontVariantValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-variant-caps} (CSS Fonts).
 *
 * <p>
 * Connects directly to the {@link FontVariantValue} previously stored by
 * {@code font-variant: small-caps}, and carries OpenType
 * {@code smcp/c2sc/pcap/c2pc/unic/titl} to rendering.
 * {@code all-small-caps}, etc. combine the corresponding uppercase and lowercase features,
 * so they use the font's glyphs rather than approximations.
 * Small-caps synthesis for fonts without these features is not implemented.
 * </p>
 */
public final class FontVariantCaps extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontVariantCaps();

	public static FontVariantValue get(final CSSStyle style) {
		return (FontVariantValue) style.get(INFO);
	}

	private FontVariantCaps() {
		super("font-variant-caps");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return FontVariantValue.NORMAL_VALUE;
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
		final FontVariantValue value = FontValueUtils.toFontVariant(tokens.next());
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
