package net.zamasoft.foliojet.css.html;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
import net.zamasoft.foliojet.css.value.internal.CSSJHtmlAlignValue;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.box.CSSFloat;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.impl.property.box.VerticalAlign;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.util.NumberUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;
public final class HTMLStyleUtils {
	private HTMLStyleUtils() {
		// unused
	}

	static final byte INPUT_TEXT = 1;
	static final byte INPUT_PASSWORD = 2;
	static final byte INPUT_CHECKBOX = 3;
	static final byte INPUT_RADIO = 4;
	static final byte INPUT_FILE = 5;
	static final byte INPUT_HIDDEN = 6;
	static final byte INPUT_SUBMIT = 7;
	static final byte INPUT_RESET = 8;
	static final byte INPUT_BUTTON = 9;
	static final byte INPUT_IMAGE = 10;

	static byte getInputType(String type) {
		if (type == null || type.length() == 0) {
			return INPUT_TEXT;
		}
		switch (type.charAt(0)) {
		case 'P':
		case 'p':
			if (type.equalsIgnoreCase("password")) {
				return INPUT_PASSWORD;
			}
			break;
		case 'C':
		case 'c':
			if (type.equalsIgnoreCase("checkbox")) {
				return INPUT_CHECKBOX;
			}
			break;
		case 'R':
		case 'r':
			if (type.equalsIgnoreCase("radio")) {
				return INPUT_RADIO;
			} else if (type.equalsIgnoreCase("reset")) {
				return INPUT_RESET;
			}
			break;
		case 'F':
		case 'f':
			if (type.equalsIgnoreCase("file")) {
				return INPUT_FILE;
			}
			break;
		case 'H':
		case 'h':
			if (type.equalsIgnoreCase("hidden")) {
				return INPUT_HIDDEN;
			}
			break;
		case 'S':
		case 's':
			if (type.equalsIgnoreCase("submit")) {
				return INPUT_SUBMIT;
			}
			break;
		case 'B':
		case 'b':
			if (type.equalsIgnoreCase("button")) {
				return INPUT_BUTTON;
			}
			break;
		case 'I':
		case 'i':
			if (type.equalsIgnoreCase("image")) {
				return INPUT_IMAGE;
			}
			break;
		}
		return INPUT_TEXT;
	}

	/**
	 * Applies the width and height attributes.
	 *
	 * @param style
	 */
	public static void applyWidthHeight(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String width = ce.atts.getValue("width");
		if (width != null) {
			try {
				QuantityValue length = HTMLStyleUtils.parseLength(ua, width);
				if (length.isNegative()) {
					throw new NumberFormatException();
				}
				style.set(Width.INFO, length);
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "width", width);
			}
		}
		String height = ce.atts.getValue("height");
		if (height != null) {
			try {
				QuantityValue length = HTMLStyleUtils.parseLength(ua, height);
				if (length.isNegative()) {
					throw new NumberFormatException();
				}
				style.set(Height.INFO, length);
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "height", height);
			}
		}
	}

	/**
	 * Applies the width and height attributes of an inline svg, which come after the cascade, only to a size the CSS
	 * does not declare (2026-10-10): they are presentation hints, below any author rule. Applied over the CSS, an icon
	 * with width="16" stayed 16px under width: 1em (Material UI's chips), and the BBC logo's height="48" won over
	 * height: 32px. A declared width: auto counts too when the CSS sizes the height and both attributes are absolute:
	 * the width then follows the height through their ratio (Chrome: width: auto; height: 30pt makes a 160 x 80 svg
	 * 60 x 30pt), where the attribute took over. With one attribute and a viewBox the attribute still applies (Chrome
	 * takes the viewBox's ratio there; Copper would make the svg square, 2026-10-11). Otherwise the attribute stays the size of a declared auto
	 * (2026-10-11: Bootstrap's placeholder, width="100%" height="250" under height: auto, is 250px high in Chrome; it
	 * came out from a made-up ratio).
	 *
	 * @param elem the element name, for messages
	 * @param style the svg's computed style
	 */
	public static void applySvgWidthHeight(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String width = ce.atts.getValue("width");
		final String heightAttr = ce.atts.getValue("height");
		final boolean ratio = absoluteLength(ua, width) && absoluteLength(ua, heightAttr);
		final boolean widthHint = width != null && !ratioDecides(style, false, ratio);
		final boolean heightHint = heightAttr != null && !ratioDecides(style, true, ratio);
		if (width != null && widthHint) {
			try {
				QuantityValue length = HTMLStyleUtils.parseLength(ua, width);
				if (length.isNegative()) {
					throw new NumberFormatException();
				}
				style.set(Width.INFO, length);
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "width", width);
			}
		}
		String height = heightAttr;
		if (height != null && heightHint) {
			try {
				QuantityValue length = HTMLStyleUtils.parseLength(ua, height);
				if (length.isNegative()) {
					throw new NumberFormatException();
				}
				style.set(Height.INFO, length);
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "height", height);
			}
		}
	}

	/**
	 * Whether the CSS leaves the width (or the height) to the ratio, so that the attribute does not apply: it declares
	 * the size, and either not auto, or auto while it declares the other axis other than auto and both attributes are
	 * absolute lengths, which give the natural ratio (Chrome takes it from them before the viewBox).
	 */
	private static boolean ratioDecides(final CSSStyle style, final boolean height, final boolean ratio) {
		if (!declaredSize(style, height)) {
			return false;
		}
		return !isAuto(style, height) || ratio && declaredSize(style, !height) && !isAuto(style, !height);
	}

	private static boolean isAuto(final CSSStyle style, final boolean height) {
		final boolean vertical = net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style).isVertical();
		final net.zamasoft.foliojet.css.property.PrimitivePropertyInfo logical = height != vertical
				? net.zamasoft.foliojet.css.impl.property.box.BlockSize.INFO
				: net.zamasoft.foliojet.css.impl.property.box.InlineSize.INFO;
		if (style.isDeclared(logical) && style.get(logical) != net.zamasoft.foliojet.css.value.KeywordValue.AUTO) {
			// inline-size: 30px lands on the width (codex, 2026-10-11)
			return false;
		}
		if (style.isDeclaredInherit(height ? Height.INFO : Width.INFO) || style.isDeclaredInherit(logical)) {
			// An inherited size is the parent's, which may come from the other property of the pair (width: inherit
			// under inline-size: 30px is 30px; codex, 2026-10-11)
			return false;
		}
		return style.get(height ? Height.INFO : Width.INFO) == net.zamasoft.foliojet.css.value.KeywordValue.AUTO;
	}

	private static boolean absoluteLength(final UserAgent ua, final String value) {
		if (value == null || value.trim().endsWith("%")) {
			return false;
		}
		try {
			// A zero gives no ratio (width="0": Chrome keeps the width 0; codex, 2026-10-11)
			final QuantityValue length = HTMLStyleUtils.parseLength(ua, value);
			return !length.isNegative() && !length.isZero();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Whether the CSS declares the width (or the height), physically or through the logical size that lands on it in
	 * the element's writing mode.
	 */
	private static boolean declaredSize(final CSSStyle style, final boolean height) {
		final boolean vertical = net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style).isVertical();
		return style.isDeclared(height ? Height.INFO : Width.INFO)
				|| style.isDeclared(height != vertical ? net.zamasoft.foliojet.css.impl.property.box.BlockSize.INFO
						: net.zamasoft.foliojet.css.impl.property.box.InlineSize.INFO);
	}

	/**
	 * Applies the hspace and vspace attributes.
	 *
	 * @param style
	 */
	static void applyHSpaceVSpace(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String hspace = ce.atts.getValue("hspace");
		if (hspace != null) {
			try {
				QuantityValue length = HTMLStyleUtils.parseLength(ua, hspace);
				if (length.isNegative()) {
					throw new NumberFormatException();
				}
				style.set(Margin.LEFT, length);
				style.set(Margin.RIGHT, length);
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "hspace", hspace);
			}
		}
		String vspace = ce.atts.getValue("vspace");
		if (vspace != null) {
			try {
				QuantityValue length = HTMLStyleUtils.parseLength(ua, vspace);
				if (length.isNegative()) {
					throw new NumberFormatException();
				}
				style.set(Margin.TOP, length);
				style.set(Margin.BOTTOM, length);
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "vspace", vspace);
			}
		}
	}

	static void applyImageBorder(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		LengthValue width;
		String str = ce.atts.getValue("border");
		if (str != null) {
			try {
				width = AbsoluteLengthValue.create(ua, NumberUtils.parseDouble(str), Unit.PX);
				if (width.isNegative()) {
					throw new NumberFormatException();
				}
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "border", str);
				return;
			}
		} else {
			width = AbsoluteLengthValue.ZERO;
			for (CSSStyle parentStyle = style.getParentStyle(); parentStyle != null; parentStyle = parentStyle
					.getParentStyle()) {
				if (parentStyle.getCSSElement().isPseudoClass(CSSElement.PC_LINK)) {
					width = ua.getBorderWidth(BorderWidthKeyword.MEDIUM);
					break;
				}
			}
		}
		if (!width.isZero()) {
			style.set(BorderWidth.TOP, width);
			style.set(BorderWidth.RIGHT, width);
			style.set(BorderWidth.BOTTOM, width);
			style.set(BorderWidth.LEFT, width);
			style.set(BorderStyle.TOP, BorderStyleValue.SOLID_VALUE);
			style.set(BorderStyle.RIGHT, BorderStyleValue.SOLID_VALUE);
			style.set(BorderStyle.BOTTOM, BorderStyleValue.SOLID_VALUE);
			style.set(BorderStyle.LEFT, BorderStyleValue.SOLID_VALUE);
		}
	}

	/**
	 * Applies the align attribute of an image.
	 *
	 * @param style
	 */
	static void applyImageAlign(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String align = ce.atts.getValue("align");
		if (align != null) {
			align = align.trim();
			if (align.length() > 0) {
				switch (align.charAt(0)) {
				case 'a':
				case 'A':
					if (align.equalsIgnoreCase("absbottom")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.TEXT_BOTTOM_VALUE);
					} else if (align.equalsIgnoreCase("absmiddle")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.MIDDLE_VALUE);
					}
					break;
				case 'b':
				case 'B':
					if (align.equalsIgnoreCase("bottom")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.BOTTOM_VALUE);
					} else if (align.equalsIgnoreCase("baseline")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.BASELINE_VALUE);
					}
					break;
				case 'c':
				case 'C':
					if (align.equalsIgnoreCase("center")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.MIDDLE_VALUE);
					}
					break;
				case 'l':
				case 'L':
					if (align.equalsIgnoreCase("left")) {
						style.set(CSSFloat.INFO, CSSFloatValue.LEFT_VALUE);
					}
					break;
				case 'm':
				case 'M':
					if (align.equalsIgnoreCase("middle")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.MIDDLE_VALUE);
					}
					break;
				case 'r':
				case 'R':
					if (align.equalsIgnoreCase("right")) {
						style.set(CSSFloat.INFO, CSSFloatValue.RIGHT_VALUE);
					}
					break;
				case 't':
				case 'T':
					if (align.equalsIgnoreCase("top")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.TOP_VALUE);
					} else if (align.equalsIgnoreCase("texttop")) {
						style.set(VerticalAlign.INFO, VerticalAlignValue.TEXT_TOP_VALUE);
					}
					break;
				default:
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "align", align);
					break;
				}
			} else {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "align", align);
			}
		}
	}

	/**
	 * Applies the align attribute of a table.
	 *
	 * @param style
	 */
	static void applyTableAlign(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String align = ce.atts.getValue("align");
		if (align != null) {
			align = align.trim();
			if (align.length() > 0) {
				switch (align.charAt(0)) {
				case 'c':
				case 'C':
					if (align.equalsIgnoreCase("center")) {
						style.set(Margin.LEFT, KeywordValue.AUTO);
						style.set(Margin.RIGHT, KeywordValue.AUTO);
					}
					break;
				case 'l':
				case 'L':
					if (align.equalsIgnoreCase("left")) {
						style.set(CSSFloat.INFO, CSSFloatValue.LEFT_VALUE);
					}
					break;
				case 'r':
				case 'R':
					if (align.equalsIgnoreCase("right")) {
						style.set(CSSFloat.INFO, CSSFloatValue.RIGHT_VALUE);
					}
					break;
				default:
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "align", align);
					break;
				}
			} else {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "align", align);
			}
		}
	}

	/**
	 * Applies the align attribute of a block.
	 *
	 * @param style
	 */
	static void applyBlockAlign(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String align = ce.atts.getValue("align");
		if (align != null) {
			align = align.trim();
			if (align.length() > 0) {
				if (align.equalsIgnoreCase("center") || align.equalsIgnoreCase("middle")) {
					style.set(TextAlign.INFO, TextAlignValue.CENTER_VALUE);
					style.set(CSSJHtmlAlign.INFO, CSSJHtmlAlignValue.CENTER_VALUE);
				} else if (align.equalsIgnoreCase("left")) {
					style.set(TextAlign.INFO, TextAlignValue.LEFT_VALUE);
					style.set(CSSJHtmlAlign.INFO, CSSJHtmlAlignValue.START_VALUE);
				} else if (align.equalsIgnoreCase("right")) {
					style.set(TextAlign.INFO, TextAlignValue.RIGHT_VALUE);
					style.set(CSSJHtmlAlign.INFO, CSSJHtmlAlignValue.END_VALUE);
				} else if (align.equalsIgnoreCase("justify")) {
					style.set(TextAlign.INFO, TextAlignValue.JUSTIFY_VALUE);
					style.set(CSSJHtmlAlign.INFO, CSSJHtmlAlignValue.START_VALUE);
				}
			} else {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "align", align);
			}
		}
	}

	/**
	 * Applies the valign attribute.
	 *
	 * @param style
	 */
	static void applyVAlign(String elem, CSSStyle style, String valign) {
		if (valign != null) {
			UserAgent ua = style.getUserAgent();
			valign = valign.trim();
			if (valign.length() > 0) {
				if (valign.equalsIgnoreCase("baseline")) {
					style.set(VerticalAlign.INFO, VerticalAlignValue.BASELINE_VALUE);
				} else if (valign.equalsIgnoreCase("bottom")) {
					style.set(VerticalAlign.INFO, VerticalAlignValue.BOTTOM_VALUE);
				} else if (valign.equalsIgnoreCase("center") || valign.equalsIgnoreCase("middle")) {
					style.set(VerticalAlign.INFO, VerticalAlignValue.MIDDLE_VALUE);
				} else if (valign.equalsIgnoreCase("top")) {
					style.set(VerticalAlign.INFO, VerticalAlignValue.TOP_VALUE);
				}
			} else {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "valign", valign);
			}
		}
	}

	/**
	 * Applies the font size attribute.
	 *
	 * @param style
	 */
	static void applyFontSize(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String size = ce.atts.getValue("size");
		if (size != null) {
			size = size.trim();
			try {
				if (size.startsWith("+")) {
					int sizeNum = Integer.parseInt(size.substring(1));
					double normal = ua.getFontSize(AbsoluteFontSize.MEDIUM);
					switch (sizeNum) {
					case 1:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * 1.2));
						break;
					case 2:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * 1.44));
						break;
					case 3:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * 1.73));
						break;
					case 4:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * 2.07));
						break;
					case 5:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * 2.48));
						break;
					case 6:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * 2.99));
					default:
						break;
					}
				} else if (size.startsWith("-")) {
					int sizeNum = Integer.parseInt(size.substring(1));
					double normal = ua.getFontSize(AbsoluteFontSize.MEDIUM);
					switch (sizeNum) {
					case 1:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * .83));
						break;
					case 2:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * .69));
						break;
					case 3:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * .58));
						break;
					case 4:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * .48));
						break;
					case 5:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * .40));
						break;
					case 6:
						style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, normal * .33));
					default:
						break;
					}
				} else {
					int sizeNum = Integer.parseInt(size);
					switch (sizeNum) {
					case 1:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.XX_SMALL)));
						break;
					case 2:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.SMALL)));
						break;
					case 3:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.MEDIUM)));
						break;
					case 4:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.LARGE)));
						break;
					case 5:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.X_LARGE)));
						break;
					case 6:
					case 7:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.XX_LARGE)));
					default:
						break;
					}
				}
			} catch (NumberFormatException e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "size", size);
			}
		} else {
			String pointSize = ce.atts.getValue("point-size");
			if (pointSize != null) {
				try {
					style.set(FontSize.INFO,
							AbsoluteLengthValue.create(ua, NumberUtils.parseDouble(pointSize), Unit.PT));
				} catch (NumberFormatException e) {
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "point-size", size);
				}
			}
		}
	}

	/**
	 * Applies the font face attribute.
	 *
	 * @param style
	 */
	static void applyFontFace(CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String faces = ce.atts.getValue("face");
		if (faces != null) {
			faces = faces.trim();
			List<FontFamily> list = new ArrayList<FontFamily>();
			for (StringTokenizer st = new StringTokenizer(faces, ","); st.hasMoreTokens();) {
				String face = st.nextToken();
				list.add(FontFamily.create(face));
			}
			FontFamilyValue defaultFamily = ua.getDefaultFontFamily();
			for (int i = 0; i < defaultFamily.getLength(); ++i) {
				list.add(defaultFamily.get(i));
			}
			style.set(CSSFontFamily.INFO,
					new FontFamilyValue((FontFamily[]) list.toArray(new FontFamily[list.size()])));
		}
	}

	static ColorValue parseColor(String color) {
		color = color.trim();
		ColorValue value = ColorValueUtils.toColorValue(color);
		if (value != null) {
			return value;
		}
		if (color.startsWith("#")) {
			color = color.substring(1).trim();
		}
		return ColorValueUtils.parseRGBHexColor(color);
	}

	/**
	 * Applies the font color attribute.
	 *
	 * @param style
	 */
	static void applyFontColor(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String color = ce.atts.getValue("color");
		if (color != null) {
			ColorValue value = parseColor(color);
			if (value == null) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "color", color);
				return;
			}
			style.set(CSSColor.INFO, value);
		}
	}

	/**
	 * Applies the bgcolor attribute.
	 *
	 * @param style
	 */
	static void applyBGColor(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String bgcolor = ce.atts.getValue("bgcolor");
		if (bgcolor != null) {
			ColorValue value = parseColor(bgcolor);
			if (value == null) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "bgcolor", bgcolor);
				return;
			}
			style.set(BackgroundColor.INFO, value);
		}
	}

	/**
	 * Applies the background attribute.
	 *
	 * @param style
	 */
	static void applyBackground(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		String background = ce.atts.getValue("background");
		if (background != null) {
			background = background.trim();
			if (background == null) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "background", background);
				return;
			}
			try {
				style.set(BackgroundImage.INFO, ValueUtils.createURIValue(ua.getDocumentContext().getEncoding(),
						ua.getDocumentContext().getBaseURI(), background));
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "background", background);
			}
		}
	}

	static QuantityValue parseLength(UserAgent ua, String str) {
		if (str.endsWith("%")) {
			double percentage = NumberUtils.parseDouble(str.substring(0, str.length() - 1));
			return PercentageValue.create(percentage);
		}
		try {
			return ValueUtils.toLength(ua, true, str);
		} catch (Exception e) {
			// ignore
		}
		StringBuilder buff = new StringBuilder(str.length());
		int i = 0;
		for (; i < str.length(); ++i) {
			char c = str.charAt(i);
			if (isNumber(c)) {
				break;
			}
		}
		for (; i < str.length(); ++i) {
			char c = str.charAt(i);
			if (isNumber(c)) {
				buff.append(c);
			} else {
				break;
			}
		}
		return AbsoluteLengthValue.create(ua, NumberUtils.parseDouble(buff.toString()), Unit.PX);
	}

	private static boolean isNumber(char c) {
		return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e';
	}

}
