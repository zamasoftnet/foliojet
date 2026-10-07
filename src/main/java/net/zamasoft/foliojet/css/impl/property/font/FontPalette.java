package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FontPaletteValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-palette} (CSS Fonts).
 *
 * <p>
 * Accepts {@code normal | light | dark | <palette-identifier>} and stores it as an inherited value.
 * FolioJet/pdfg2d does not support color font palette selection,
 * so this currently has no rendering effect.
 * </p>
 */
public final class FontPalette extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontPalette();

	private FontPalette() {
		super("font-palette");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return FontPaletteValue.NORMAL_VALUE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (value instanceof FontPaletteValue palette
				&& palette.getKind() == FontPaletteValue.Kind.IDENTIFIER) {
			return palette.resolve(style.getUserAgent().getUAContext().getFontPaletteValues()
					.lookup(palette.getIdentifier()));
		}
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (!(token instanceof CssToken.Ident ident) || tokens.hasNext()) {
			throw new PropertyException();
		}
		if (ident.is("normal")) {
			return FontPaletteValue.NORMAL_VALUE;
		}
		if (ident.is("light")) {
			return FontPaletteValue.LIGHT_VALUE;
		}
		if (ident.is("dark")) {
			return FontPaletteValue.DARK_VALUE;
		}
		final String lower = ident.lower();
		if (switch (lower) {
		case "initial", "inherit", "unset", "default", "revert", "revert-layer" -> true;
		default -> false;
		}) {
			throw new PropertyException();
		}
		return FontPaletteValue.identifier(ident.name());
	}
}
