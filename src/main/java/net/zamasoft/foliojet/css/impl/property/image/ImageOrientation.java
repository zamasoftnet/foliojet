package net.zamasoft.foliojet.css.impl.property.image;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * {@code image-orientation} (css-images-3 §5.3, 2026-08-30).
 *
 * <p>
 * Whether to honor a raster image's EXIF orientation ({@code from-image}, the initial value)
 * or ignore it and draw the raw pixel order ({@code none}). Inherited.
 *
 * <p>
 * This product applies EXIF orientation when loading, so {@code none} means
 * <b>removing the already applied orientation</b>.
 * {@link net.zamasoft.foliojet.ua.impl.image.RasterImageLoader} uses a dedicated type
 * for the orientation wrapper, so removing just that wrapper is enough
 * ({@code withoutOrientation}). This also restores the intrinsic dimensions.
 *
 * <p>
 * <b>Angle values are rejected.</b> Early css-images-3 drafts had values such as
 * {@code 90deg}, but the current specification dropped them (real browsers do not parse them either).
 */
public class ImageOrientation extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ImageOrientation();

	/**
	 * Returns whether EXIF orientation is honored.
	 */
	public static boolean isFromImage(final CSSStyle style) {
		return style.get(INFO) != KeywordValue.NONE;
	}

	/**
	 * Adjusts an image according to this style's {@code image-orientation}.
	 * For {@code from-image} (the initial value), returns the same instance unchanged.
	 */
	public static Image apply(final CSSStyle style, final Image image) {
		if (image == null || isFromImage(style)) {
			return image;
		}
		return net.zamasoft.foliojet.ua.impl.image.RasterImageLoader.withoutOrientation(image);
	}

	protected ImageOrientation() {
		super("image-orientation");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return KeywordValue.FROM_IMAGE;
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
		final CssToken token = tokens.next();
		if (tokens.hasNext() || !(token instanceof CssToken.Ident ident)) {
			throw new PropertyException();
		}
		if (ident.is("from-image")) {
			return KeywordValue.FROM_IMAGE;
		}
		if (ident.is("none")) {
			return KeywordValue.NONE;
		}
		throw new PropertyException();
	}
}
