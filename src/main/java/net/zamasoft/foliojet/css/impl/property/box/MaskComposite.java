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
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code mask-composite} (css-masking-1 §7.8). Accepts and retains each comma-separated value.
 *
 * <p>Current mask rendering approximates a single mask using the existing alpha/add equivalent;
 * it does not perform {@code subtract}/{@code intersect}/{@code exclude} compositing between masks.
 * This avoids changing the appearance by approximating unimplemented values with another operation.</p>
 */
public final class MaskComposite extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskComposite();

	/** mask-composite keywords. */
	public enum CompositeValue implements Value {
		ADD("add"), SUBTRACT("subtract"), INTERSECT("intersect"), EXCLUDE("exclude");

		private final String cssText;

		CompositeValue(String cssText) {
			this.cssText = cssText;
		}

		@Override
		public String toString() {
			return this.cssText;
		}
	}

	/** Values for multiple mask layers (frontmost first). */
	public record LayersValue(CompositeValue[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	private MaskComposite() {
		super("mask-composite");
	}

	/** Returns a keyword value for a single layer, or bundles multiple layers into a layer value. */
	public static Value toValue(List<CompositeValue> values) {
		return values.size() == 1 ? values.get(0)
				: new LayersValue(values.toArray(new CompositeValue[values.size()]));
	}

	/** Converts a keyword token to a value. */
	public static CompositeValue fromToken(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		return switch (ident.lower()) {
		case "add" -> CompositeValue.ADD;
		case "subtract" -> CompositeValue.SUBTRACT;
		case "intersect" -> CompositeValue.INTERSECT;
		case "exclude" -> CompositeValue.EXCLUDE;
		default -> null;
		};
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return CompositeValue.ADD;
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
		final List<CompositeValue> values = new ArrayList<CompositeValue>();
		for (final TokenStream layer : tokens.splitComma()) {
			final CompositeValue value = fromToken(layer.next());
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
