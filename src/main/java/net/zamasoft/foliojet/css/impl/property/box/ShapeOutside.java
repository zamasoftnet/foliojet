package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BasicShapes;
import net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;

/**
 * {@code shape-outside} (css-shapes-1 §4.1, added 2026-08-29).
 *
 * <p>
 * {@code none | [<basic-shape> || <shape-box>] | <shape-box> | <image>}.
 * Parses basic-shape with {@link BasicShapes}, shared with {@code clip-path}.
 * When only basic-shape is specified, the reference box is margin-box, as specified
 * (unlike the border-box default for {@code clip-path}). {@code <image>} supports
 * only {@code url()} (gradients, etc. are unsupported; the entire declaration is ignored).
 * </p>
 *
 * <p>
 * Has no effect on anything other than floats (float:left/right), as specified.
 * For layout, {@code BoxStyleMapper.setupFloatPos} sets {@code FloatPos.shapeOutside}
 * via {@link #toParams}. Only line placement
 * ({@code TextBuilder.locateLine}→{@code ExclusionSpace.scanLineBand}) consults it.
 * Other floats and blocks establishing a BFC still avoid the margin box, as specified
 * (§4.1: "float positioning and stacking are not affected").
 * </p>
 */
public class ShapeOutside extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ShapeOutside();

	/**
	 * Parsed value. A non-null {@code image} specifies an image;
	 * {@code shape}/{@code box} are then unused.
	 *
	 * @param shape shape (null means only a reference box)
	 * @param box   reference box (margin-box if only basic-shape is specified)
	 * @param image {@code url()} image
	 */
	public record ShapeOutsideValue(ShapeSpec shape, ClipPathShape.ReferenceBox box, URIValue image)
			implements Value {
	}

	/**
	 * Creates float parameters from the computed value (null for none).
	 * Also bundles {@code shape-margin} and {@code shape-image-threshold} here.
	 *
	 * <p>
	 * An image specification becomes an image shape only if the UA can provide pixels at this point.
	 * Measurement and structural scan passes return dimension-only stub images
	 * ({@code AbstractUserAgent.loadImage}), so no pixels are available and they fall back to
	 * margin-box. Line wrapping can therefore differ between measurement and actual layout.
	 * Even for documents with pass-count≧2, actual layout determines final output, so the impact
	 * is limited to measurement discrepancies (a known limitation; see the manual).
	 * </p>
	 */
	public static ShapeOutsideParams toParams(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (!(value instanceof ShapeOutsideValue v)) {
			return null;
		}
		final net.zamasoft.foliojet.layout.box.params.Length margin = ShapeMargin.get(style);
		if (v.image() != null) {
			final ShapeOutsideParams.ShapeImage image = loadShapeImage(style, v.image(),
					ShapeImageThreshold.get(style));
			if (image != null) {
				return new ShapeOutsideParams(null, image, margin);
			}
			return new ShapeOutsideParams(new ClipPathShape.BoxOnly(ClipPathShape.ReferenceBox.MARGIN_BOX), null,
					margin);
		}
		return new ShapeOutsideParams(BasicShapes.toShape(v.shape(), v.box()), null, margin);
	}

	/** Loads the image and extracts contour ranges using the threshold. Returns null if pixels are unavailable. */
	private static ShapeOutsideParams.ShapeImage loadShapeImage(final CSSStyle style, final URIValue uriValue,
			final double threshold) {
		final UserAgent ua = style.getUserAgent();
		final URI uri = uriValue.getURI();
		final Image image = ImageLoadDiagnostics.loadImage(ua, uri, false);
		if (image == null) {
			return null;
		}
		Image original = image;
		while (original instanceof WrappedImage wrapped) {
			original = wrapped.getImage();
		}
		// Non-raster images such as SVG and dimension-only stubs have no pixels.
		if (!(original instanceof net.zamasoft.pdfg2d.g2d.image.RasterImage raster)) {
			return null;
		}
		final java.awt.image.BufferedImage pixels = raster.getImage();
		if (pixels == null || pixels.getWidth() <= 0 || pixels.getHeight() <= 0) {
			return null;
		}
		return ShapeOutsideParams.ShapeImage.extract(pixels, threshold);
	}

	protected ShapeOutside() {
		super("shape-outside");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (!(value instanceof ShapeOutsideValue v) || v.shape() == null) {
			return value;
		}
		return new ShapeOutsideValue(BasicShapes.absolutize(v.shape(), style), v.box(), null);
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
			if (lu instanceof CssToken.Uri) {
				if (shape != null || box != null || tokens.hasNext()) {
					throw new PropertyException();
				}
				try {
					final URIValue image = ValueUtils.toURI(ua, uri, lu);
					if (image == null) {
						throw new PropertyException();
					}
					return new ShapeOutsideValue(null, null, image);
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, ((CssToken.Uri) lu).uri());
					throw new PropertyException();
				}
			}
			if (!(lu instanceof CssToken.Func func) || shape != null) {
				throw new PropertyException();
			}
			shape = BasicShapes.parseFunction(func, ua);
		}
		if (shape == null && box == null) {
			throw new PropertyException();
		}
		return new ShapeOutsideValue(shape, box == null ? ClipPathShape.ReferenceBox.MARGIN_BOX : box, null);
	}
}
