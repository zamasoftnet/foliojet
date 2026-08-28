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
import net.zamasoft.foliojet.css.impl.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundClip;
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
		boolean color = false, none = false, uriValue = false, repeat = false, attachment = false, position = false, size = false, clip = false;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (ColorValueUtils.isTransparent(lu)) {
				primitives.set(BackgroundColor.INFO, KeywordValue.TRANSPARENT);
				continue;
			}
			Value value = ColorValueUtils.toPaint(ua, lu);
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
				value = ValueUtils.toURI(ua, uri, lu);
				if (value != null) {
					if (uriValue) {
						throw new PropertyException("urlが2度指定されています");
					}
					uriValue = true;
					primitives.set(BackgroundImage.INFO, value);
					continue;
				}
			} catch (URISyntaxException e) {
				uriValue = true;
				ua.message(MessageCodes.WARN_BAD_LINK_URI, ((CssToken.Uri) lu).uri());
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

			value = ColorValueUtils.toBackgroundClip(lu);
			if (value != null) {
				if (clip) {
					throw new PropertyException("clipが2度指定されています");
				}
				clip = true;
				primitives.set(BackgroundClip.INFO, value);
				continue;
			}

			if (lu == CssToken.Op.SLASH) {
				if (size) {
					throw new PropertyException("sizeが2度指定されています");
				}
				size = true;

				Value w, h;

				final CssToken wToken = tokens.next();
				// contain/coverキーワード(css-backgrounds-3 §3.9、単独値のみ)。
				// longhand側(BackgroundSize.parseValues)と対。従来未対応で
				// PropertyExceptionにより**background宣言全体が破棄**され、
				// 別途longhandで指定されたbackground-imageだけが残って
				// 原寸・既定位置の暗部クロップになっていた
				// (asahi.comの動画ランキングのサムネイルが黒く見えた、2026-08-27)
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

			Value x, y;
			if (lu instanceof CssToken.Ident ident1) {
				String kw1 = ident1.lower();
				if (!isPositionKeyword(kw1)) {
					throw new PropertyException();
				}
				String kw2 = null;
				final CssToken nextlu = tokens.peek();
				if (nextlu == null) {
					kw2 = null;
				} else if (nextlu instanceof CssToken.Ident ident2) {
					kw2 = ident2.lower();
					if (!isPositionKeyword(kw2)) {
						kw2 = null;
					} else {
						tokens.next();
					}
				} else {
					y = ValueUtils.toPercentage(nextlu);
					if (y == null) {
						y = ValueUtils.toLength(ua, nextlu);
					}
					if (y == null) {
						kw2 = null;
					} else {
						tokens.next();

						if (kw1.equals("left")) {
							x = PercentageValue.ZERO;
						} else if (kw1.equals("center")) {
							x = PercentageValue.HALF;
						} else if (kw1.equals("right")) {
							x = PercentageValue.FULL;
						} else {
							throw new PropertyException();
						}

						primitives.set(BackgroundPosition.INFO_X, x);
						primitives.set(BackgroundPosition.INFO_Y, y);
						continue;
					}
				}

				if (("top".equals(kw1) && "left".equals(kw2)) || ("left".equals(kw1) && "top".equals(kw2))) {
					x = y = PercentageValue.ZERO;
				} else if (("top".equals(kw1) && kw2 == null) || ("top".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "top".equals(kw2))) {
					x = PercentageValue.HALF;
					y = PercentageValue.ZERO;
				} else if (("right".equals(kw1) && "top".equals(kw2)) || ("top".equals(kw1) && "right".equals(kw2))) {
					x = PercentageValue.FULL;
					y = PercentageValue.ZERO;
				} else if (("left".equals(kw1) && kw2 == null) || ("left".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "left".equals(kw2))) {
					x = PercentageValue.ZERO;
					y = PercentageValue.HALF;
				} else if (("center".equals(kw1) && kw2 == null) || ("center".equals(kw1) && "center".equals(kw2))) {
					x = y = PercentageValue.HALF;
				} else if (("right".equals(kw1) && kw2 == null) || ("right".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "right".equals(kw2))) {
					x = PercentageValue.FULL;
					y = PercentageValue.HALF;
				} else if (("left".equals(kw1) && "bottom".equals(kw2))
						|| ("bottom".equals(kw1) && "left".equals(kw2))) {
					x = PercentageValue.ZERO;
					y = PercentageValue.FULL;
				} else if (("bottom".equals(kw1) && kw2 == null) || ("bottom".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "bottom".equals(kw2))) {
					x = PercentageValue.HALF;
					y = PercentageValue.FULL;
				} else if (("bottom".equals(kw1) && "right".equals(kw2))
						|| ("right".equals(kw1) && "bottom".equals(kw2))) {
					x = y = PercentageValue.FULL;
				} else {
					throw new PropertyException();
				}

				primitives.set(BackgroundPosition.INFO_X, x);
				primitives.set(BackgroundPosition.INFO_Y, y);
				continue;
			}

			x = ValueUtils.toPercentage(lu);
			if (x == null) {
				x = ValueUtils.toLength(ua, lu);
			}
			if (x == null) {
				throw new PropertyException();
			}

			final CssToken nextlu = tokens.peek();
			if (nextlu == null) {
				// SPEC css-values <position>: 値が1つだけの場合の2つ目はcenter
				// (2026-08-27。longhand側の修正と対)
				y = PercentageValue.HALF;
				primitives.set(BackgroundPosition.INFO_X, x);
				primitives.set(BackgroundPosition.INFO_Y, y);
				continue;
			}

			if (nextlu instanceof CssToken.Ident ident2) {
				String kw2 = ident2.lower();
				if (kw2.equals("top")) {
					tokens.next();
					y = PercentageValue.ZERO;
				} else if (kw2.equals("center")) {
					tokens.next();
					y = PercentageValue.HALF;
				} else if (kw2.equals("bottom")) {
					tokens.next();
					y = PercentageValue.FULL;
				} else {
					// SPEC css-values <position>: 値が1つだけの場合の2つ目はcenter
				// (2026-08-27。longhand側の修正と対)
				y = PercentageValue.HALF;
				}
			} else {
				y = ValueUtils.toPercentage(nextlu);
				if (y == null) {
					y = ValueUtils.toLength(ua, nextlu);
				}
				if (y == null) {
					// SPEC css-values <position>: 値が1つだけの場合の2つ目はcenter
				// (2026-08-27。longhand側の修正と対)
				y = PercentageValue.HALF;
				} else {
					tokens.next();
				}
			}
			primitives.set(BackgroundPosition.INFO_X, x);
			primitives.set(BackgroundPosition.INFO_Y, y);
		}
	}

	private static boolean isPositionKeyword(String kw) {
		return kw.equals("top") || kw.equals("bottom") || kw.equals("center") || kw.equals("left")
				|| kw.equals("right");
	}

}