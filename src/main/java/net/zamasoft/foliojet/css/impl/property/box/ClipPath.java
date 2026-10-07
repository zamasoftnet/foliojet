package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BasicShapes;
import net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code clip-path} (css-shapes-1/css-masking-1, added 2026-08-22).
 *
 * <p>
 * {@code none | [<basic-shape> || <geometry-box>]}. Supported basic-shapes are
 * {@code inset()} (round, corner radii x=y), {@code circle()}, {@code ellipse()}, and
 * {@code polygon()}. {@code path()} and {@code url()} references are unsupported
 * (the entire declaration is ignored). During rendering, {@code AbstractContainerBox.clip()}
 * resolves the shape using the actual reference box dimensions and feeds it into existing
 * clip propagation (the same path as overflow:hidden and the mask-image approximation).
 * </p>
 *
 * <p>
 * Moved {@code <basic-shape>} parsing, absolute-length conversion, and shape construction
 * to {@link BasicShapes} to share them with {@code shape-outside} (2026-08-29).
 * Only the value type and the default reference box (border-box) remain here.
 * </p>
 */
public class ClipPath extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ClipPath();

	/**
	 * Parsed shape specification.
	 *
	 * @param shape    shape (null means only a reference box is specified)
	 * @param box      reference box (border-box if unspecified)
	 */
	public record ClipPathValue(ShapeSpec shape, ClipPathShape.ReferenceBox box) implements Value {
	}

	public static Value get(final CSSStyle style) {
		return style.get(INFO);
	}

	/** Creates a layout shape from the computed value (null for none). */
	public static ClipPathShape toShape(final Value value) {
		if (!(value instanceof ClipPathValue v)) {
			return null;
		}
		return BasicShapes.toShape(v.shape(), v.box());
	}

	protected ClipPath() {
		super("clip-path");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (!(value instanceof ClipPathValue v) || v.shape() == null) {
			return value;
		}
		// Convert font-relative lengths such as em to absolute lengths here (leave % unchanged).
		return new ClipPathValue(BasicShapes.absolutize(v.shape(), style), v.box());
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		ShapeSpec shape = null;
		ClipPathShape.ReferenceBox box = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident ident) {
				if (ident.is("none")) {
					if (shape != null || box != null || tokens.hasNext()) {
						throw new PropertyException();
					}
					return KeywordValue.NONE;
				}
				final ClipPathShape.ReferenceBox rb = BasicShapes.toReferenceBox(ident);
				if (rb == null || box != null) {
					throw new PropertyException();
				}
				box = rb;
				continue;
			}
			if (!(lu instanceof CssToken.Func func) || shape != null) {
				throw new PropertyException();
			}
			shape = BasicShapes.parseFunction(func, ua);
		}
		if (shape == null && box == null) {
			throw new PropertyException();
		}
		return new ClipPathValue(shape, box == null ? ClipPathShape.ReferenceBox.BORDER_BOX : box);
	}
}
