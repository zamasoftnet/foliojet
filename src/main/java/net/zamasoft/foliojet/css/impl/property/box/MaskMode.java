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
 * {@code mask-mode} (css-masking-1 §7.10). Accepts and retains each comma-separated value.
 *
 * <p>Current mask rendering has only the existing alpha-equivalent approximation path,
 * so {@code alpha}/{@code luminance}/{@code match-source} do not switch rendering modes.
 * This avoids changing the appearance by approximating unimplemented values with another mode.</p>
 */
public final class MaskMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskMode();

	/** mask-mode keywords. */
	public enum ModeValue implements Value {
		ALPHA("alpha"), LUMINANCE("luminance"), MATCH_SOURCE("match-source");

		private final String cssText;

		ModeValue(String cssText) {
			this.cssText = cssText;
		}

		@Override
		public String toString() {
			return this.cssText;
		}
	}

	/** Values for multiple mask layers (frontmost first). */
	public record LayersValue(ModeValue[] layers) implements Value {
		@Override
		public String toString() {
			return java.util.Arrays.toString(this.layers);
		}
	}

	private MaskMode() {
		super("mask-mode");
	}

	/** Returns a keyword value for a single layer, or bundles multiple layers into a layer value. */
	public static Value toValue(List<ModeValue> values) {
		return values.size() == 1 ? values.get(0)
				: new LayersValue(values.toArray(new ModeValue[values.size()]));
	}

	/** Converts a keyword token to a value. */
	public static ModeValue fromToken(CssToken token) {
		if (!(token instanceof CssToken.Ident ident)) {
			return null;
		}
		return switch (ident.lower()) {
		case "alpha" -> ModeValue.ALPHA;
		case "luminance" -> ModeValue.LUMINANCE;
		case "match-source" -> ModeValue.MATCH_SOURCE;
		default -> null;
		};
	}

	@Override
	public Value getDefault(CSSStyle style) {
		return ModeValue.MATCH_SOURCE;
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
		final List<ModeValue> values = new ArrayList<ModeValue>();
		for (final TokenStream layer : tokens.splitComma()) {
			final ModeValue value = fromToken(layer.next());
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
