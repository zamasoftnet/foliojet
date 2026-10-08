package net.zamasoft.foliojet.css.impl.property.internal;

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
 * Internal property for an image (replaced box).
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSJInternalImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJInternalImage();

	/**
	 * The image of a replaced box.
	 *
	 * <p>
	 * This is <b>the single point through which all replaced-image paths pass</b>,
	 * so {@code image-orientation} (2026-08-30) is also applied here. The cascade has not
	 * finished at load time, and rendering is too late to determine intrinsic dimensions.
	 * This entry point reads intrinsic dimensions, so it is the right place.
	 */
	public static Image getImage(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof CSSJImageValue image) {
			return net.zamasoft.foliojet.css.impl.property.image.ImageOrientation.apply(style, image.getImage());
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
		// An image makes a replaced element: display computes grid/flex/table to block (2026-10-08). An inline SVG
		// gets its image at its end tag, when display was already computed as grid: the box opened as a grid
		// container was never closed (no end for an image), so following SVGs nested inside it, and from the fourth
		// the conversion failed (TwoPass NO_RANGE).
		style.recompute(net.zamasoft.foliojet.css.impl.property.box.Display.INFO);
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