package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code mask-origin} (css-masking-1 §7.5). Holds the positioning reference for each
 * comma-separated mask layer.
 *
 * <p>Applies {@code padding-box}/{@code content-box} to URL mask rendering.
 * The initial {@code border-box} retains the existing positioning path to preserve output
 * byte for byte when unspecified. Accepts and retains SVG-specific {@code fill-box}/{@code stroke-box}/
 * {@code view-box} values, but does not apply them to the current HTML box mask rendering.</p>
 */
public final class MaskOrigin extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskOrigin();

	/** mask-origin keywords. */
	public enum OriginValue implements Value {
		BORDER_BOX("border-box", Background.BORDER_BOX),
		PADDING_BOX("padding-box", Background.PADDING_BOX),
		CONTENT_BOX("content-box", Background.CONTENT_BOX),
		FILL_BOX("fill-box", -1),
		STROKE_BOX("stroke-box", -1),
		VIEW_BOX("view-box", -1);

		private final String cssText;
		private final byte backgroundOrigin;

		OriginValue(String cssText, int backgroundOrigin) {
			this.cssText = cssText;
			this.backgroundOrigin = (byte) backgroundOrigin;
		}

		/** True if convertible to a Background positioning reference. */
		public boolean isPaintSupported() {
			return this.backgroundOrigin >= 0;
		}

		/** Background positioning reference. Unusable if {@link #isPaintSupported()} is false. */
		public byte getBackgroundOrigin() {
			return this.backgroundOrigin;
		}

		@Override
		public String toString() {
			return this.cssText;
		}
	}

	/** Values for multiple mask layers (frontmost first). */
	public record LayersValue(OriginValue[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	private MaskOrigin() {
		super("mask-origin");
	}

	/** Returns values for all layers (frontmost first). */
	public static OriginValue[] getLayers(CSSStyle style) {
		final Value value = style.get(INFO);
		return value instanceof LayersValue layers ? layers.layers() : new OriginValue[] { (OriginValue) value };
	}

	/** Returns the value of the first layer, which is currently rendered. */
	public static OriginValue get(CSSStyle style) {
		return getLayers(style)[0];
	}

	/** Returns a keyword value for a single layer, or bundles multiple layers into a layer value. */
	public static Value toValue(List<OriginValue> values) {
		return values.size() == 1 ? values.get(0)
				: new LayersValue(values.toArray(new OriginValue[values.size()]));
	}

	/** Converts a keyword token to a value. */
	public static OriginValue fromToken(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		return switch (ident.lower()) {
		case "border-box" -> OriginValue.BORDER_BOX;
		case "padding-box" -> OriginValue.PADDING_BOX;
		case "content-box" -> OriginValue.CONTENT_BOX;
		case "fill-box" -> OriginValue.FILL_BOX;
		case "stroke-box" -> OriginValue.STROKE_BOX;
		case "view-box" -> OriginValue.VIEW_BOX;
		default -> null;
		};
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return OriginValue.BORDER_BOX;
	}

	@Override
	public boolean isInherited() {
		return false;
	}

	@Override
	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final List<OriginValue> values = new ArrayList<OriginValue>();
		for (final TokenStream layer : tokens.splitComma()) {
			final OriginValue value = fromToken(layer.next());
			if (value == null || layer.hasNext()) {
				throw new PropertyException();
			}
			values.add(value);
		}
		if (values.isEmpty()) {
			throw new PropertyException();
		}
		return toValue(values);
	}
}
