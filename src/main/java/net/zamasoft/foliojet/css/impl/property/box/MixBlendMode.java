package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.BlendMode;

/**
 * {@code mix-blend-mode} (compositing-1 §4, added 2026-08-29).
 *
 * <p>
 * Not inherited; defaults to {@code normal}. Accepts 16 blend modes and passes them to
 * pdfg2d's {@code GC.setBlendMode} during rendering ({@code /BM} in PDF ExtGState).
 * </p>
 *
 * <p>
 * <b>Approximation</b>: the specification composites the entire element with the backdrop as one group,
 * but this implementation, like opacity, applies the mode to each drawing element
 * (background, border, text, image) of the element and its descendants. To pass it to descendants,
 * the computed value is "the parent's value if this element is normal"
 * (analogous to opacity multiplying by the parent's value).
 * Unlike the specification, overlapping drawings within the element are also composited
 * using the same mode as the backdrop (there is no difference in the typical case of an opaque
 * solid background plus text). {@code isolation} is accepted but has no effect.
 * </p>
 */
public class MixBlendMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MixBlendMode();

	/** Blend mode value. */
	public record BlendModeValue(BlendMode mode) implements Value {
		@Override
		public String toString() {
			return this.mode.cssName;
		}
	}

	private static final BlendModeValue NORMAL = new BlendModeValue(BlendMode.NORMAL);

	/**
	 * Parses one {@code <blend-mode>}. Background layer blending uses the same keyword set,
	 * so value conversion is shared here.
	 */
	public static BlendMode parseBlendMode(final CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			return BlendMode.fromCssName(ident.lower());
		}
		return null;
	}

	public static BlendMode get(final CSSStyle style) {
		return ((BlendModeValue) style.get(INFO)).mode();
	}

	protected MixBlendMode() {
		super("mix-blend-mode");
	}

	public Value getDefault(final CSSStyle style) {
		return NORMAL;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		final CSSStyle parent = style.getParentStyle();
		if (parent == null || ((BlendModeValue) value).mode() != BlendMode.NORMAL) {
			return value;
		}
		// Pass the parent mode to descendant drawing elements (see the approximation at the start of this class).
		return parent.get(INFO);
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final BlendMode mode = parseBlendMode(lu);
		if (mode != null && !tokens.hasNext()) {
			return mode == BlendMode.NORMAL ? NORMAL : new BlendModeValue(mode);
		}
		throw new PropertyException();
	}
}
