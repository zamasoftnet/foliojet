package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PaintValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BackgroundImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundImage();

	/**
	 * Value for multiple backgrounds (2026-08-29). Each layer is a {@link URIValue},
	 * a {@link PaintValue} (gradient), or none to preserve positional correspondence; the first is frontmost.
	 * A single layer holds its value directly and does not use this type.
	 */
	public record LayersValue(Value[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	/** All layers (frontmost first). Empty for none. */
	public static Value[] getLayers(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value instanceof LayersValue layers) {
			return layers.layers();
		}
		if (value == KeywordValue.NONE || value == null) {
			return new Value[0];
		}
		return new Value[] { value };
	}

	/**
	 * Loads a layer image. Returns null if it cannot be read.
	 *
	 * <p>
	 * Applies {@code image-orientation} (2026-08-30) to the loaded image.
	 * Backgrounds, masks, and {@code border-image} all use this entry point.
	 */
	public static Image load(CSSStyle style, URIValue uriValue) {
		return net.zamasoft.foliojet.css.impl.property.image.ImageOrientation.apply(style, loadRaw(style, uriValue));
	}

	private static Image loadRaw(CSSStyle style, URIValue uriValue) {
		UserAgent ua = style.getUserAgent();
		URI uri = uriValue.getURI();
		return ImageLoadDiagnostics.loadImage(ua, uri, true);
	}

	protected BackgroundImage() {
		super("background-image");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		// Multiple layers (comma-separated, 2026-08-29). Keep none entries in the list
		// to preserve layer positions (collapse to a single none only when all layers are none).
		final java.util.List<Value> layers = new java.util.ArrayList<Value>();
		boolean hasImage = false;
		for (final TokenStream layer : tokens.splitComma()) {
			final CssToken lu = layer.next();
			if (lu == null || layer.hasNext()) {
				throw new PropertyException();
			}
			final Value value = parseLayer(ua, uri, lu);
			if (value == null) {
				throw new PropertyException();
			}
			layers.add(value);
			hasImage |= value != KeywordValue.NONE;
		}
		if (layers.isEmpty() || !hasImage) {
			return KeywordValue.NONE;
		}
		if (layers.size() == 1) {
			return layers.get(0);
		}
		return new LayersValue(layers.toArray(new Value[layers.size()]));
	}

	/** One layer ({@code none}, url(), or gradient). Returns null if it cannot be parsed. */
	public static Value parseLayer(UserAgent ua, URI uri, CssToken lu) {
		if (ValueUtils.isNone(lu)) {
			return KeywordValue.NONE;
		}
		try {
			// url() and image-set() (2026-08-29, the candidate closest to the output resolution).
			final URIValue value = ValueUtils.toImage(ua, uri, lu);
			if (value != null) {
				return value;
			}
			if (ValueUtils.isImage(lu)) {
				return null; // image-set() with no usable candidate (the caller invalidates the declaration).
			}
		} catch (URISyntaxException e) {
			ua.message(MessageCodes.WARN_BAD_LINK_URI, ValueUtils.uriText(lu));
		}
		return net.zamasoft.foliojet.css.util.ColorValueUtils.toGradient(ua, lu);
	}

}
