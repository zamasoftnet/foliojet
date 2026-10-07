package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.impl.svg.SVGImageLoader;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * <b>Approximation of gradient forms of mask-image</b> (added 2026-08-09).
 *
 * <p>
 * Real sites use an excerpt idiom that fixes the body text excerpt with {@code max-height}
 * and fades its bottom edge with {@code mask-image: linear-gradient(#000 60%, transparent)}.
 * They sometimes omit {@code overflow: hidden}; the mask makes overflow transparent,
 * so this works on screen. Ignoring the mask entirely renders overflowing body text
 * over subsequent content (measured on the 5ch.io thread list).
 * </p>
 *
 * <p>
 * Full alpha mask compositing would require major PDF output changes, so <b>approximate gradient
 * masks with a paint box clip</b>: when the value contains a gradient function,
 * use {@link KeywordValue#CLIP} as the computed value and apply only the same paint clip
 * as {@code overflow: hidden} via {@code BlockParams.paintClip}
 * (with no layout effects such as establishing a BFC).
 * Drawing inside the box remains unfaded. This compromise prioritizes hiding overflow
 * over fading for print.
 * {@code url()} masks (icon cutouts, etc.) cannot be approximated by clipping,
 * so they remain ignored ({@link KeywordValue#NONE}).
 * </p>
 */
public class MaskImage extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaskImage();

	/** Whether to clip painting to the box as an approximation of a gradient mask. */
	public static boolean isClip(CSSStyle style) {
		return style.get(INFO) == KeywordValue.CLIP;
	}

	/**
	 * Returns a URL mask as an SVG image tinted with the background color.
	 *
	 * <p>This approximation directly draws a solid-color SVG on a transparent background,
	 * rather than using a PDF alpha mask for an arbitrary image.
	 * It gives the same appearance for icon masks on MDN and similar sites,
	 * avoiding the previous defect where only a background-colored rectangle remained.</p>
	 */
	public static Image getImage(final CSSStyle style) {
		final Value value = style.get(INFO);
		if (!(value instanceof URIValue uriValue)) {
			return null;
		}
		final PaintValue backgroundPaint = BackgroundColor.get(style);
		if (backgroundPaint == null) {
			return null;
		}
		final Color color = backgroundPaint instanceof ColorValue colorValue
				? colorValue.getColor()
				: CSSColor.get(style);
		final URI uri = uriValue.getURI();
		final String key = uri + "#mask-color=" + Math.round(color.getRed() * 255f) + ","
				+ Math.round(color.getGreen() * 255f) + "," + Math.round(color.getBlue() * 255f) + ","
				+ Math.round(color.getAlpha() * 255f);
		final UserAgent ua = style.getUserAgent();
		Image image = ua.getDocumentContext().getMaskImage(key);
		if (image != null) {
			return image;
		}
		image = ImageLoadDiagnostics.load(ua, uri, (resolvedUri, source) -> {
			final SVGImageLoader loader = new SVGImageLoader();
			return loader.match(source) ? loader.loadImage(ua, source, color) : null;
		});
		if (image != null) {
			ua.getDocumentContext().putMaskImage(key, image);
		}
		return image;
	}

	private MaskImage() {
		super("mask-image");
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
		final CssToken first = tokens.next();
		if (ValueUtils.isNone(first)) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.NONE;
		}
		try {
			// url() and image-set() (2026-08-29).
			final URIValue uriValue = ValueUtils.toImage(ua, uri, first);
			if (uriValue != null) {
				if (tokens.hasNext()) {
					throw new PropertyException();
				}
				return uriValue;
			}
			if (ValueUtils.isImage(first)) {
				throw new PropertyException();
			}
		} catch (URISyntaxException e) {
			ua.message(MessageCodes.WARN_BAD_LINK_URI, ValueUtils.uriText(first));
			throw new PropertyException();
		}

		// Read all tokens, including multiple comma-separated layers. If any gradient
		// function is present, apply the clipping approximation.
		boolean clip = false;
		if (first instanceof CssToken.Func func
				&& func.name().toLowerCase(Locale.ROOT).endsWith("gradient")) {
			clip = true;
		}
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (token instanceof CssToken.Func func
					&& func.name().toLowerCase(Locale.ROOT).endsWith("gradient")) {
				clip = true;
			}
		}
		if (!clip) {
			throw new PropertyException();
		}
		return KeywordValue.CLIP;
	}

}
