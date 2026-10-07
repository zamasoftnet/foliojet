package net.zamasoft.foliojet.css.value.css3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * A {@code filter} value (filter-effects-1, added on 2026-08-29).
 *
 * <p>
 * Parsing folds the function sequence into four types of effects: a color matrix
 * (grayscale/sepia/saturate/hue-rotate/invert/brightness/contrast, all 4×5 color matrices
 * that can be multiplied in sequence into one), opacity (opacity(), multiplied by group
 * opacity at rendering time), blur (blur(), standard deviation in pt), and shadow
 * (drop-shadow(), only one: the last wins if there are several).
 * </p>
 *
 * <p>
 * The specification applies effects after combining the whole element into one image, but this
 * implementation applies them to each drawing component (background, border, text, image),
 * as with mix-blend-mode/opacity. To deliver effects to descendants' drawing components,
 * composes computed values with the parent's computed values ({@link #compose}): multiply
 * color matrices, multiply opacities, and add blurs. Shadows draw only on the frame of the
 * element declaring them (to avoid shadows on every descendant), so composition does not inherit them.
 * </p>
 */
public final class FilterValue implements Value {
	/** {@code drop-shadow(x y blur color)}. */
	public record DropShadow(double x, double y, double blur, Color color) {
	}

	public static final FilterValue NONE = new FilterValue(1f, null, 0, null, null);

	/** The factor multiplied by group opacity at rendering time. */
	public final float opacity;
	/** A 4×5 color matrix (row-major, multiplied by [r g b a 1]). Null for the identity. */
	public final float[] matrix;
	/** The blur standard deviation (pt). Zero means none. */
	public final double blur;
	public final DropShadow shadow;
	/** The declaration's text (when declared on this element). Null for inherited-only values. */
	public final String declared;
	/** This element's own declared value. Null immediately after parsing (={@code this}). */
	private final FilterValue own;
	/** The parent element's composed value. Null at the root. */
	private final FilterValue inherited;

	public FilterValue(final float opacity, final float[] matrix, final double blur, final DropShadow shadow,
			final String declared) {
		this(opacity, matrix, blur, shadow, declared, null, null);
	}

	private FilterValue(final float opacity, final float[] matrix, final double blur, final DropShadow shadow,
			final String declared, final FilterValue own, final FilterValue inherited) {
		this.opacity = opacity;
		this.matrix = matrix;
		this.blur = blur;
		this.shadow = shadow;
		this.declared = declared;
		this.own = own;
		this.inherited = inherited;
	}

	public boolean isNone() {
		return this.opacity == 1f && this.matrix == null && this.blur <= 0 && this.shadow == null;
	}

	/** Whether a color matrix or blur exists (whether drawing needs a FilterGC wrapper). */
	public boolean hasColorOps() {
		return this.matrix != null || this.blur > 0;
	}

	/** Whether the entire element needs to be combined into a single layer. */
	public boolean needsGroup() {
		return this.hasColorOps() || this.shadow != null;
	}

	/** Returns this element's own declared value. */
	public FilterValue own() {
		return this.own == null ? this : this.own;
	}

	/** Copies a shared parsed value into a value with an element-specific identity. */
	public FilterValue forElement() {
		return this.isNone() ? NONE
				: new FilterValue(this.opacity, this.matrix, this.blur, this.shadow, this.declared, null, null);
	}

	/**
	 * Composes the child's effects with the parent's effects. Applies the child's effects
	 * to its drawing first, then applies the parent's effects to the result.
	 */
	public FilterValue compose(final FilterValue child) {
		if (child == null) {
			return this;
		}
		if (child.isNone()) {
			return new FilterValue(this.opacity, this.matrix, this.blur, null, null, child.own(), this);
		}
		if (this.isNone()) {
			return new FilterValue(child.opacity, child.matrix, child.blur, child.shadow, child.declared, child.own(),
					this);
		}
		final float[] m = this.matrix == null ? child.matrix
				: child.matrix == null ? this.matrix : multiply(this.matrix, child.matrix);
		return new FilterValue(this.opacity * child.opacity, m, this.blur + child.blur, child.shadow, child.declared,
				child.own(), this);
	}

	/** Returns the composed value excluding declarations already applied by enclosing element layers. */
	public FilterValue excluding(final Set<FilterValue> grouped) {
		if (grouped == null || grouped.isEmpty()) {
			return this;
		}
		final Deque<FilterValue> values = new ArrayDeque<>();
		for (FilterValue value = this; value != null; value = value.inherited) {
			values.push(value.own());
		}
		FilterValue result = NONE;
		while (!values.isEmpty()) {
			final FilterValue value = values.pop();
			result = result.compose(grouped.contains(value) ? NONE : value);
		}
		return result.isNone() ? NONE : result;
	}

	/** Matrix product {@code a × b} (applies b first). */
	public static float[] multiply(final float[] a, final float[] b) {
		final float[] r = new float[20];
		for (int row = 0; row < 4; ++row) {
			for (int col = 0; col < 5; ++col) {
				float v = 0;
				for (int k = 0; k < 4; ++k) {
					v += a[row * 5 + k] * b[k * 5 + col];
				}
				if (col == 4) {
					v += a[row * 5 + 4];
				}
				r[row * 5 + col] = v;
			}
		}
		return r;
	}

	/** Applies the color matrix to a color. Leaves alpha unchanged because the alpha row is the identity. */
	public static float[] apply(final float[] m, final float r, final float g, final float b, final float a) {
		final float[] out = new float[3];
		for (int row = 0; row < 3; ++row) {
			final float v = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * a
					+ m[row * 5 + 4];
			out[row] = v < 0 ? 0 : v > 1 ? 1 : v;
		}
		return out;
	}

	/** Textual representation of the effects for the raster cache key. */
	public String key() {
		final StringBuilder s = new StringBuilder();
		if (this.matrix != null) {
			for (final float v : this.matrix) {
				s.append(String.format(java.util.Locale.ROOT, "%.4f,", v));
			}
		}
		s.append("blur=").append(String.format(java.util.Locale.ROOT, "%.3f", this.blur));
		return s.toString();
	}

	@Override
	public String toString() {
		return this.declared == null ? (this.isNone() ? "none" : "(inherited)") : this.declared;
	}
}
