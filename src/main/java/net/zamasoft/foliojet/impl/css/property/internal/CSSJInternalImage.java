package net.zamasoft.foliojet.impl.css.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.internal.CSSJImageValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * 画像(置換ボックス)の内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSJInternalImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJInternalImage();

	public static Image getImage(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof CSSJImageValue image) {
			return image.getImage();
		}
		return null;
	}

	public static String getText(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof StringValue string) {
			return string.getString();
		}
		return null;
	}

	public static void setImage(CSSStyle style, Image image) {
		style.set(INFO, new CSSJImageValue(image));
	}

	public static void setText(CSSStyle style, String text) {
		style.set(INFO, new StringValue(text));
	}

	public CSSJInternalImage() {
		super("-cssj-internal-image");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		throw new UnsupportedOperationException();
	}
}