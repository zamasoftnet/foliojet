package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextOrientationValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/** {@code text-orientation: mixed | upright | sideways}. Inherited property. */
public class TextOrientation extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextOrientation();

	public static FontStyle.TextOrientation get(final CSSStyle style) {
		return ((TextOrientationValue) style.get(INFO)).orientation();
	}

	private TextOrientation() {
		super("text-orientation");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return TextOrientationValue.MIXED;
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
		final TextOrientationValue value;
		if (tokens.eat("mixed")) {
			value = TextOrientationValue.MIXED;
		} else if (tokens.eat("upright")) {
			value = TextOrientationValue.UPRIGHT;
		} else if (tokens.eat("sideways") || tokens.eat("sideways-right")) {
			// sideways-right and vertical-right are the earlier names that EPUB 3 CSS uses (2026-10-08); Chrome takes them
			// too (vertical-right as -webkit-text-orientation)
			value = TextOrientationValue.SIDEWAYS;
		} else if (tokens.eat("vertical-right")) {
			value = TextOrientationValue.MIXED;
		} else {
			throw new PropertyException();
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}
}
