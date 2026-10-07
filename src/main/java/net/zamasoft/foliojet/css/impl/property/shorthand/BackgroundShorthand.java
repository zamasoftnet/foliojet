package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.BackgroundAttachmentValue;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BackgroundClipValue;
import net.zamasoft.foliojet.css.value.css3.BackgroundOriginValue;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundClip;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundOrigin;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundSize;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class BackgroundShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BackgroundShorthand();

	protected BackgroundShorthand() {
		super("background");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(BackgroundColor.INFO, global);
			primitives.set(BackgroundImage.INFO, global);
			primitives.set(BackgroundAttachment.INFO, global);
			primitives.set(BackgroundRepeat.INFO, global);
			primitives.set(BackgroundPosition.INFO_X, global);
			primitives.set(BackgroundPosition.INFO_Y, global);
			primitives.set(BackgroundSize.INFO_WIDTH, global);
			primitives.set(BackgroundSize.INFO_HEIGHT, global);
			primitives.set(BackgroundClip.INFO, global);
			primitives.set(BackgroundOrigin.INFO, global);
			return;
		}

		primitives.set(BackgroundColor.INFO, KeywordValue.TRANSPARENT);
		primitives.set(BackgroundImage.INFO, KeywordValue.NONE);
		primitives.set(BackgroundAttachment.INFO, BackgroundAttachmentValue.SCROLL_VALUE);
		primitives.set(BackgroundRepeat.INFO, BackgroundRepeatValue.REPEAT_VALUE);
		primitives.set(BackgroundPosition.INFO_X, PercentageValue.ZERO);
		primitives.set(BackgroundPosition.INFO_Y, PercentageValue.ZERO);
		primitives.set(BackgroundSize.INFO_WIDTH, KeywordValue.AUTO);
		primitives.set(BackgroundSize.INFO_HEIGHT, KeywordValue.AUTO);
		primitives.set(BackgroundClip.INFO, BackgroundClipValue.BORDER_BOX_VALUE);
		primitives.set(BackgroundOrigin.INFO, BackgroundOriginValue.PADDING_BOX_VALUE);
		// Multiple backgrounds (comma-separated, 2026-08-29): take only the first layer.
		// Collect <color>, allowed only in the final layer, as the background color.
		final java.util.List<TokenStream> layers = tokens.splitComma();
		if (layers.isEmpty()) {
			throw new PropertyException();
		}
		if (layers.size() > 1) {
			final TokenStream last = layers.get(layers.size() - 1);
			final int mark = last.position();
			while (last.hasNext()) {
				final CssToken lu = last.next();
				if (ColorValueUtils.isTransparent(lu)) {
					primitives.set(BackgroundColor.INFO, KeywordValue.TRANSPARENT);
				} else if (ColorValueUtils.isCurrentColor(lu)) {
					primitives.set(BackgroundColor.INFO, KeywordValue.DEFAULT);
				} else {
					final Value c = ColorValueUtils.toColor(ua, lu);
					if (c != null) {
						primitives.set(BackgroundColor.INFO, c);
					}
				}
			}
			// The following image/origin parsing reads the same final layer.
			last.rewind(mark);
		}
		// From the second layer onward, collect only images, gradients, and none (2026-08-29).
		// Repeat, position, and size values are shared from the first layer
		// (see Background; a documented approximation).
		final java.util.List<Value> extraLayers = new java.util.ArrayList<Value>();
		final java.util.List<BackgroundOriginValue> extraOrigins = new java.util.ArrayList<BackgroundOriginValue>();
		for (int i = 1; i < layers.size(); ++i) {
			final TokenStream layer = layers.get(i);
			BackgroundOriginValue layerOrigin = BackgroundOriginValue.PADDING_BOX_VALUE;
			int layerBoxes = 0;
			while (layer.hasNext()) {
				final CssToken lu = layer.next();
				if (i == layers.size() - 1 && (ColorValueUtils.isTransparent(lu) || ColorValueUtils.isCurrentColor(lu)
						|| ColorValueUtils.toColor(ua, lu) != null)) {
					continue;
				}
				final BackgroundOriginValue originValue = ColorValueUtils.toBackgroundOrigin(lu);
				final BackgroundClipValue clipValue = ColorValueUtils.toBackgroundClip(lu);
				if (originValue != null) {
					if (++layerBoxes > 2) {
						throw new PropertyException("boxが3度指定されています");
					}
					if (layerBoxes == 1) {
						layerOrigin = originValue;
					}
					continue;
				} else if (clipValue != null) {
					if (layerBoxes > 1) {
						throw new PropertyException("clipが2度指定されています");
					}
					layerBoxes = 2;
					continue;
				}
				final Value image = BackgroundImage.parseLayer(ua, uri, lu);
				if (image != null) {
					extraLayers.add(image);
				}
			}
			extraOrigins.add(layerOrigin);
		}
		tokens = layers.get(0);
		boolean color = false, none = false, uriValue = false, repeat = false, attachment = false, position = false, size = false;
		int boxes = 0;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (ColorValueUtils.isTransparent(lu)) {
				primitives.set(BackgroundColor.INFO, KeywordValue.TRANSPARENT);
				continue;
			}
			if (ColorValueUtils.isCurrentColor(lu)) {
				// currentcolor (2026-08-29). Same DEFAULT sentinel as BackgroundColor.
				primitives.set(BackgroundColor.INFO, KeywordValue.DEFAULT);
				continue;
			}
			Value value = ColorValueUtils.toGradient(ua, lu);
			if (value != null) {
				// Gradients are image layers (BackgroundImage.getPaint). Previously stored
				// in the background color slot; separate them so they can coexist with a color,
				// as in `linear-gradient(...), #fff` (2026-08-29).
				if (uriValue) {
					throw new PropertyException("urlが2度指定されています");
				}
				uriValue = true;
				primitives.set(BackgroundImage.INFO, value);
				continue;
			}
			value = ColorValueUtils.toPaint(ua, lu);
			if (value != null) {
				if (color) {
					throw new PropertyException("colorが2度指定されています");
				}
				color = true;

				primitives.set(BackgroundColor.INFO, value);
				continue;
			}
			if (ValueUtils.isNone(lu)) {
				if (none) {
					throw new PropertyException("noneが2度指定されています");
				}
				none = true;
				primitives.set(BackgroundImage.INFO, KeywordValue.NONE);
				continue;
			}
			try {
				// url() and image-set() (2026-08-29).
				value = ValueUtils.toImage(ua, uri, lu);
				if (value != null) {
					if (uriValue) {
						throw new PropertyException("urlが2度指定されています");
					}
					uriValue = true;
					primitives.set(BackgroundImage.INFO, value);
					continue;
				}
				if (ValueUtils.isImage(lu)) {
					throw new PropertyException("image-set()に採れる候補がありません");
				}
			} catch (URISyntaxException e) {
				uriValue = true;
				ua.message(MessageCodes.WARN_BAD_LINK_URI, ValueUtils.uriText(lu));
				continue;
			}
			value = ColorValueUtils.toBackgroundRepeat(lu);
			if (value != null) {
				if (repeat) {
					throw new PropertyException("repeatが2度指定されています");
				}
				repeat = true;
				primitives.set(BackgroundRepeat.INFO, value);
				continue;
			}
			value = ColorValueUtils.toBackgroundAttachment(lu);
			if (value != null) {
				if (attachment) {
					throw new PropertyException("attachmentが2度指定されています");
				}
				attachment = true;
				primitives.set(BackgroundAttachment.INFO, value);
				continue;
			}

			final BackgroundOriginValue originValue = ColorValueUtils.toBackgroundOrigin(lu);
			final BackgroundClipValue clipValue = ColorValueUtils.toBackgroundClip(lu);
			if (originValue != null) {
				if (++boxes > 2) {
					throw new PropertyException("boxが3度指定されています");
				}
				if (boxes == 1) {
					primitives.set(BackgroundOrigin.INFO, originValue);
				}
				primitives.set(BackgroundClip.INFO, clipValue);
				continue;
			} else if (clipValue != null) {
				if (boxes > 1) {
					throw new PropertyException("clipが2度指定されています");
				}
				boxes = 2;
				primitives.set(BackgroundClip.INFO, clipValue);
				continue;
			}

			if (lu == CssToken.Op.SLASH) {
				if (size) {
					throw new PropertyException("sizeが2度指定されています");
				}
				size = true;

				Value w, h;

				final CssToken wToken = tokens.next();
				// contain/cover keywords (css-backgrounds-3 §3.9, standalone values only).
				// Paired with the longhand parser (BackgroundSize.parseValues). Previously unsupported:
				// PropertyException **discarded the entire background declaration**,
				// leaving only background-image specified separately as a longhand,
				// cropped to a dark area at its intrinsic size and default position
				// (asahi.com video ranking thumbnails appeared black, 2026-08-27).
				if (wToken instanceof CssToken.Ident sizeKw
						&& ("cover".equals(sizeKw.lower()) || "contain".equals(sizeKw.lower()))) {
					final Value kw = "cover".equals(sizeKw.lower()) ? KeywordValue.COVER : KeywordValue.CONTAIN;
					primitives.set(BackgroundSize.INFO_WIDTH, kw);
					primitives.set(BackgroundSize.INFO_HEIGHT, kw);
					continue;
				}
				if (ValueUtils.isAuto(wToken)) {
					w = KeywordValue.AUTO;
				} else {
					w = ValueUtils.toPercentage(wToken);
					if (w == null) {
						w = ValueUtils.toLength(ua, wToken);
						if (w == null || ((LengthValue) w).isNegative()) {
							throw new PropertyException();
						}
					} else if (((PercentageValue) w).isNegative()) {
						throw new PropertyException();
					}
				}

				if (!tokens.hasNext()) {
					h = KeywordValue.AUTO;
					primitives.set(BackgroundSize.INFO_WIDTH, w);
					primitives.set(BackgroundSize.INFO_HEIGHT, h);
					continue;
				}

				final CssToken hToken = tokens.next();
				if (ValueUtils.isAuto(hToken)) {
					h = KeywordValue.AUTO;
				} else {
					h = ValueUtils.toPercentage(hToken);
					if (h == null) {
						h = ValueUtils.toLength(ua, hToken);
						if (h != null && ((LengthValue) h).isNegative()) {
							throw new PropertyException();
						}
					} else if (((PercentageValue) h).isNegative()) {
						throw new PropertyException();
					}
				}
				if (h == null) {
					h = KeywordValue.AUTO;
				}
				primitives.set(BackgroundSize.INFO_WIDTH, w);
				primitives.set(BackgroundSize.INFO_HEIGHT, h);
				continue;
			}

			if (position) {
				throw new PropertyException("positionが2度指定されています");
			}
			position = true;

			// Delegate <position> to the longhand parser (2026-08-29). Previously, only
			// one/two values were parsed manually, so css-values-3 four-value syntax
			// (right 10px bottom 20px) and three-value syntax caused the entire background
			// declaration to be discarded (four cases on real sites). Collect up to four
			// consecutive tokens usable in a position and pass them to the parser.
			if (!isPositionToken(ua, lu)) {
				throw new PropertyException();
			}
			final java.util.List<CssToken> pos = new java.util.ArrayList<>();
			pos.add(lu);
			while (pos.size() < 4 && tokens.hasNext() && isPositionToken(ua, tokens.peek())) {
				pos.add(tokens.next());
			}
			for (final net.zamasoft.foliojet.css.property.CompositeProperty.Entry entry : ((BackgroundPosition) BackgroundPosition.INFO_X)
					.parsePositionValues(new TokenStream(pos), ua, uri)) {
				primitives.set(entry.getPrimitivePropertyInfo(), entry.getValue());
			}
		}
		if (!extraLayers.isEmpty()) {
			// Append the second and subsequent layers after the first layer image (frontmost first).
			final Value first = primitives.get(BackgroundImage.INFO);
			if (first != null) {
				extraLayers.add(0, first);
			}
			final boolean hasImage = extraLayers.stream().anyMatch(v -> v != KeywordValue.NONE);
			primitives.set(BackgroundImage.INFO, hasImage
					? new BackgroundImage.LayersValue(extraLayers.toArray(new Value[extraLayers.size()]))
					: KeywordValue.NONE);
		}
		if (!extraOrigins.isEmpty()) {
			final java.util.List<BackgroundOriginValue> origins = new java.util.ArrayList<BackgroundOriginValue>(
					extraOrigins.size() + 1);
			origins.add((BackgroundOriginValue) primitives.get(BackgroundOrigin.INFO));
			origins.addAll(extraOrigins);
			primitives.set(BackgroundOrigin.INFO, BackgroundOrigin.toValue(origins));
		}
	}

	/** Whether a token can be a &lt;position&gt; component (keyword, length, or percentage). */
	static boolean isPositionToken(final UserAgent ua, final CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			return isPositionKeyword(ident.lower());
		}
		if (token instanceof CssToken.Func) {
			// calc(), etc.
			return ValueUtils.toPercentage(token) != null || ValueUtils.toLength(ua, token) != null;
		}
		return token instanceof CssToken.Percent || token instanceof CssToken.Dim || token instanceof CssToken.Num;
	}

	private static boolean isPositionKeyword(String kw) {
		return kw.equals("top") || kw.equals("bottom") || kw.equals("center") || kw.equals("left")
				|| kw.equals("right");
	}

}
