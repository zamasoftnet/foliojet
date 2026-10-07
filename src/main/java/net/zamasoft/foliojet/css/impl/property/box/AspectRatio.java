package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AspectRatioValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code aspect-ratio} (css-sizing-4 §5, 2026-08-29).
 * {@code auto | <ratio> | auto && <ratio>}, where {@code <ratio>} is
 * {@code <number> [ / <number> ]?} (nonnegative). Not inherited; defaults to {@code auto}.
 * Occurred 424 times (23 sites) in a conversion sweep of 50 sites; this alone determines
 * the height of 16:9 thumbnails (yomiuri/cnn/cookpad).
 *
 * <p>
 * Semantics for print: replaced elements prefer their intrinsic ratio when {@code auto}
 * is also specified ({@code AbstractReplacedBox}). Non-replaced boxes use the ratio to
 * determine height when the inline dimension is definite and the page-direction dimension
 * is {@code auto} ({@code FlowBlockBox}/{@code AbstractStaticBlockBox}). If content exceeds
 * the ratio-derived height, {@code overflow:visible} lets the box grow to fit the content
 * (an approximation of the specification's {@code min-height:auto}=content size).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class AspectRatio extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new AspectRatio();

	public static AspectRatioValue get(CSSStyle style) {
		return (AspectRatioValue) style.get(INFO);
	}

	protected AspectRatio() {
		super("aspect-ratio");
	}

	public Value getDefault(CSSStyle style) {
		return AspectRatioValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		boolean auto = false;
		double ratio = 0;
		boolean hasRatio = false;
		while (tokens.hasNext()) {
			if (!auto && tokens.eat("auto")) {
				auto = true;
				continue;
			}
			if (hasRatio) {
				throw new PropertyException();
			}
			final CssToken.Num width = tokens.number();
			if (width == null || width.value() < 0) {
				throw new PropertyException();
			}
			double height = 1;
			if (tokens.eatSlash()) {
				final CssToken.Num h = tokens.number();
				if (h == null || h.value() < 0) {
					throw new PropertyException();
				}
				height = h.value();
			}
			ratio = height == 0 ? Double.POSITIVE_INFINITY : width.value() / height;
			hasRatio = true;
		}
		if (!auto && !hasRatio) {
			throw new PropertyException();
		}
		return AspectRatioValue.create(auto, ratio);
	}
}
