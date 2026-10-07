package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class TextFillColor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextFillColor();

	public static net.zamasoft.pdfg2d.gc.paint.Color get(CSSStyle style) {
		Value value = style.get(TextFillColor.INFO);
		if (value == KeywordValue.TRANSPARENT) {
			// Return a fully transparent color object (2026-08-18). Previously returned null,
			// but "do not set a color for null" in the renderer (AbstractTextBox, etc.) means
			// **draw with the default black**, so supposedly transparent text appeared black.
			// This made code appear twice in prism-editor (transparent textarea over highlighted pre),
			// an actual defect in chartjs-docs.
			// Drawing with alpha 0 behaves like color:transparent,
			// while correctly preserving outlined text that also uses text-stroke.
			return net.zamasoft.pdfg2d.gc.paint.RGBAColor.create(0, 0, 0, 0);
		}
		if (value == KeywordValue.DEFAULT) {
			return CSSColor.get(style);
		}
		return ((ColorValue) value).getColor();
	}

	protected TextFillColor() {
		super("-cssj-text-fill-color");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.DEFAULT;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && ident.is("currentcolor")) {
			return KeywordValue.DEFAULT;
		}
		if (ColorValueUtils.isTransparent(lu)) {
			return KeywordValue.TRANSPARENT;
		}
		Value value = ColorValueUtils.toColor(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}