package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.BackgroundAttachmentValue;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.TransparentValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BackgroundClipValue;
import net.zamasoft.foliojet.impl.css.property.BackgroundAttachment;
import net.zamasoft.foliojet.impl.css.property.BackgroundColor;
import net.zamasoft.foliojet.impl.css.property.BackgroundImage;
import net.zamasoft.foliojet.impl.css.property.BackgroundPosition;
import net.zamasoft.foliojet.impl.css.property.BackgroundRepeat;
import net.zamasoft.foliojet.impl.css.property.css3.BackgroundClip;
import net.zamasoft.foliojet.impl.css.property.css3.BackgroundSize;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundShorthand.java 1628 2022-05-11 04:05:01Z miyabe $
 */
public class BackgroundShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BackgroundShorthand();

	protected BackgroundShorthand() {
		super("background");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(BackgroundColor.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundImage.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundAttachment.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundRepeat.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundPosition.INFO_X, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundPosition.INFO_Y, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundSize.INFO_WIDTH, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundSize.INFO_HEIGHT, InheritValue.INHERIT_VALUE);
			primitives.set(BackgroundClip.INFO, InheritValue.INHERIT_VALUE);
			return;
		}

		primitives.set(BackgroundColor.INFO, TransparentValue.TRANSPARENT_VALUE);
		primitives.set(BackgroundImage.INFO, NoneValue.NONE_VALUE);
		primitives.set(BackgroundAttachment.INFO, BackgroundAttachmentValue.SCROLL_VALUE);
		primitives.set(BackgroundRepeat.INFO, BackgroundRepeatValue.REPEAT_VALUE);
		primitives.set(BackgroundPosition.INFO_X, PercentageValue.ZERO);
		primitives.set(BackgroundPosition.INFO_Y, PercentageValue.ZERO);
		primitives.set(BackgroundSize.INFO_WIDTH, AutoValue.AUTO_VALUE);
		primitives.set(BackgroundSize.INFO_HEIGHT, AutoValue.AUTO_VALUE);
		primitives.set(BackgroundClip.INFO, BackgroundClipValue.BORDER_BOX_VALUE);
		boolean color = false, none = false, uriValue = false, repeat = false, attachment = false, position = false, size = false, clip = false;
		for (; lu != null; lu = lu.getNextLexicalUnit()) {
			if (ColorValueUtils.isTransparent(lu)) {
				primitives.set(BackgroundColor.INFO, TransparentValue.TRANSPARENT_VALUE);
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
				primitives.set(BackgroundImage.INFO, NoneValue.NONE_VALUE);
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
				ua.message(MessageCodes.WARN_BAD_LINK_URI, lu.getStringValue());
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
			
			if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_SLASH) {
				if (size) {
					throw new PropertyException("sizeが2度指定されています");
				}
				size = true;
				
				Value w, h;

				lu = lu.getNextLexicalUnit();
				if (ValueUtils.isAuto(lu)) {
					w = AutoValue.AUTO_VALUE;
				} else {
					w = ValueUtils.toPercentage(lu);
					if (w == null) {
						w = ValueUtils.toLength(ua, lu);
						if (w == null || ((LengthValue) w).isNegative()) {
							throw new PropertyException();
						}
					} else if (((PercentageValue) w).isNegative()) {
						throw new PropertyException();
					}
				}

				lu = lu.getNextLexicalUnit();
				if (lu == null) {
					h = AutoValue.AUTO_VALUE;
					primitives.set(BackgroundSize.INFO_WIDTH, w);
					primitives.set(BackgroundSize.INFO_HEIGHT, h);
					continue;
				}

				if (ValueUtils.isAuto(lu)) {
					h = AutoValue.AUTO_VALUE;
				} else {
					h = ValueUtils.toPercentage(lu);
					if (h == null) {
						h = ValueUtils.toLength(ua, lu);
						if (h != null && ((LengthValue) h).isNegative()) {
							throw new PropertyException();
						}
					} else if (((PercentageValue) h).isNegative()) {
						throw new PropertyException();
					}
				}
				if (h == null) {
					h = AutoValue.AUTO_VALUE;
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
			if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
				String kw1 = lu.getStringValue().toLowerCase();
				if (!(kw1.equals("top") || kw1.equals("bottom") || kw1.equals("center") || kw1.equals("left")
						|| kw1.equals("right"))) {
					throw new PropertyException();
				}
				String kw2;
				LexicalUnit nextlu = lu.getNextLexicalUnit();
				if (nextlu == null) {
					kw2 = null;
				} else if (nextlu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
					kw2 = nextlu.getStringValue().toLowerCase();
					if (!(kw2.equals("top") || kw2.equals("bottom") || kw2.equals("center") || kw2.equals("left")
							|| kw2.equals("right"))) {
						kw2 = null;
					} else {
						lu = nextlu;
					}
				} else {
					y = ValueUtils.toPercentage(nextlu);
					if (y == null) {
						y = ValueUtils.toLength(ua, nextlu);
					}
					if (y == null) {
						kw2 = null;
					} else {
						lu = nextlu;

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

			LexicalUnit nextlu = lu.getNextLexicalUnit();
			if (nextlu == null) {
				y = x;
				primitives.set(BackgroundPosition.INFO_X, x);
				primitives.set(BackgroundPosition.INFO_Y, y);
				continue;
			}

			if (nextlu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
				String kw2 = nextlu.getStringValue().toLowerCase();
				if (kw2.equals("top")) {
					lu = nextlu;
					y = PercentageValue.ZERO;
				} else if (kw2.equals("center")) {
					lu = nextlu;
					y = PercentageValue.HALF;
				} else if (kw2.equals("bottom")) {
					lu = nextlu;
					y = PercentageValue.FULL;
				} else {
					y = x;
				}
			} else {
				y = ValueUtils.toPercentage(nextlu);
				if (y == null) {
					y = ValueUtils.toLength(ua, nextlu);
				}
				if (y == null) {
					y = x;
				} else {
					lu = nextlu;
				}
			}
			primitives.set(BackgroundPosition.INFO_X, x);
			primitives.set(BackgroundPosition.INFO_Y, y);
		}
	}

}