package jp.cssj.homare.css.html;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.util.GeneratedValueUtils;
import jp.cssj.homare.css.util.URIUtils;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.AutoValue;
import jp.cssj.homare.css.value.BorderStyleValue;
import jp.cssj.homare.css.value.CSSFloatValue;
import jp.cssj.homare.css.value.ColorValue;
import jp.cssj.homare.css.value.FontFamilyValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.ListStyleTypeValue;
import jp.cssj.homare.css.value.PercentageValue;
import jp.cssj.homare.css.value.QuantityValue;
import jp.cssj.homare.css.value.TextAlignValue;
import jp.cssj.homare.css.value.VerticalAlignValue;
import jp.cssj.homare.css.value.ext.CSSJDirectionModeValue;
import jp.cssj.homare.css.value.internal.CSSJHtmlAlignValue;
import jp.cssj.homare.impl.css.property.BackgroundColor;
import jp.cssj.homare.impl.css.property.BackgroundImage;
import jp.cssj.homare.impl.css.property.BorderBottomStyle;
import jp.cssj.homare.impl.css.property.BorderBottomWidth;
import jp.cssj.homare.impl.css.property.BorderLeftStyle;
import jp.cssj.homare.impl.css.property.BorderLeftWidth;
import jp.cssj.homare.impl.css.property.BorderRightStyle;
import jp.cssj.homare.impl.css.property.BorderRightWidth;
import jp.cssj.homare.impl.css.property.BorderTopStyle;
import jp.cssj.homare.impl.css.property.BorderTopWidth;
import jp.cssj.homare.impl.css.property.CSSColor;
import jp.cssj.homare.impl.css.property.CSSFloat;
import jp.cssj.homare.impl.css.property.CSSFontFamily;
import jp.cssj.homare.impl.css.property.FontSize;
import jp.cssj.homare.impl.css.property.Height;
import jp.cssj.homare.impl.css.property.MarginBottom;
import jp.cssj.homare.impl.css.property.MarginLeft;
import jp.cssj.homare.impl.css.property.MarginRight;
import jp.cssj.homare.impl.css.property.MarginTop;
import jp.cssj.homare.impl.css.property.TextAlign;
import jp.cssj.homare.impl.css.property.VerticalAlign;
import jp.cssj.homare.impl.css.property.Width;
import jp.cssj.homare.impl.css.property.css3.BlockFlow;
import jp.cssj.homare.impl.css.property.ext.CSSJDirectionMode;
import jp.cssj.homare.impl.css.property.internal.CSSJHtmlAlign;
import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.style.util.StyleUtils;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.util.NumberUtils;

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
	 * リストのインデントを設定します。
	 * 
	 * @param style
	 * @param length
	 */
	static void applyListMargins(CSSStyle style, LengthValue length) {
		final CSSStyle pStyle = style.getParentStyle();
		if (pStyle != null && ((CSSJDirectionMode.get(pStyle) == CSSJDirectionModeValue.PHYSICAL
				&& StyleUtils.isVertical(BlockFlow.get(pStyle)))
				|| CSSJDirectionMode.get(pStyle) == CSSJDirectionModeValue.VERTICAL_RL)) {
			// 縦書き
			style.set(MarginTop.INFO, length);
		} else {
			// 横書き
			style.set(MarginLeft.INFO, length);
		}
	}

	/**
	 * 引用ブロックのインデントを指定します。
	 * 
	 * @param style
	 * @param length
	 */
	static void applyQuoteMargins(CSSStyle style, LengthValue length) {
		final CSSStyle pStyle = style.getParentStyle();
		if (pStyle != null && ((CSSJDirectionMode.get(pStyle) == CSSJDirectionModeValue.PHYSICAL
				&& StyleUtils.isVertical(BlockFlow.get(pStyle)))
				|| CSSJDirectionMode.get(pStyle) == CSSJDirectionModeValue.VERTICAL_RL)) {
			// 縦書き
			style.set(MarginTop.INFO, length);
			style.set(MarginBottom.INFO, length);
		} else {
			// 横書き
			style.set(MarginLeft.INFO, length);
			style.set(MarginRight.INFO, length);
		}
	}

	/**
	 * width, height属性を適用します。
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
	 * hspace, vspace属性を適用します。
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
				style.set(MarginLeft.INFO, length);
				style.set(MarginRight.INFO, length);
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
				style.set(MarginTop.INFO, length);
				style.set(MarginBottom.INFO, length);
			} catch (Exception e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "vspace", vspace);
			}
		}
	}

	/**
	 * marginheight, marginwidth属性を適用します。
	 * 
	 * @param style
	 */
	static void applyMarginWidthMarginHeight(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		{
			String str = ce.atts.getValue("marginwidth");
			if (str != null) {
				try {
					QuantityValue length = HTMLStyleUtils.parseLength(ua, str);
					if (length.isNegative()) {
						throw new NumberFormatException();
					}
					style.set(MarginLeft.INFO, length);
					style.set(MarginRight.INFO, length);
				} catch (Exception e) {
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "marginwidth", str);
				}
			}
		}
		{
			String str = ce.atts.getValue("marginheight");
			if (str != null) {
				try {
					QuantityValue length = HTMLStyleUtils.parseLength(ua, str);
					if (length.isNegative()) {
						throw new NumberFormatException();
					}
					style.set(MarginTop.INFO, length);
					style.set(MarginBottom.INFO, length);
				} catch (Exception e) {
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "marginheight", str);
				}
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
				width = AbsoluteLengthValue.create(ua, NumberUtils.parseDouble(str), LengthValue.UNIT_PX);
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
					width = ua.getBorderWidth(UserAgent.BORDER_WIDTH_MEDIUM);
					break;
				}
			}
		}
		if (!width.isZero()) {
			style.set(BorderTopWidth.INFO, width);
			style.set(BorderRightWidth.INFO, width);
			style.set(BorderBottomWidth.INFO, width);
			style.set(BorderLeftWidth.INFO, width);
			style.set(BorderTopStyle.INFO, BorderStyleValue.SOLID_VALUE);
			style.set(BorderRightStyle.INFO, BorderStyleValue.SOLID_VALUE);
			style.set(BorderBottomStyle.INFO, BorderStyleValue.SOLID_VALUE);
			style.set(BorderLeftStyle.INFO, BorderStyleValue.SOLID_VALUE);
		}
	}

	/**
	 * 画像のalign属性を適用します。
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
	 * テーブルのalign属性を適用します。
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
						style.set(MarginLeft.INFO, AutoValue.AUTO_VALUE);
						style.set(MarginRight.INFO, AutoValue.AUTO_VALUE);
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
	 * ブロックのalign属性を適用します。
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
	 * valign属性を適用します。
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
	 * フォントsize属性を適用します。
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
					double normal = ua.getFontSize(UserAgent.FONT_SIZE_MEDIUM);
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
					double normal = ua.getFontSize(UserAgent.FONT_SIZE_MEDIUM);
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
								AbsoluteLengthValue.create(ua, ua.getFontSize(UserAgent.FONT_SIZE_XX_SMALL)));
						break;
					case 2:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(UserAgent.FONT_SIZE_SMALL)));
						break;
					case 3:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(UserAgent.FONT_SIZE_MEDIUM)));
						break;
					case 4:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(UserAgent.FONT_SIZE_LARGE)));
						break;
					case 5:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(UserAgent.FONT_SIZE_X_LARGE)));
						break;
					case 6:
					case 7:
						style.set(FontSize.INFO,
								AbsoluteLengthValue.create(ua, ua.getFontSize(UserAgent.FONT_SIZE_XX_LARGE)));
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
							AbsoluteLengthValue.create(ua, NumberUtils.parseDouble(pointSize), LengthValue.UNIT_PT));
				} catch (NumberFormatException e) {
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, elem, "point-size", size);
				}
			}
		}
	}

	/**
	 * フォントface属性を適用します。
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
	 * フォントcolor属性を適用します。
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
	 * bgcolor属性を適用します。
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
	 * background属性を適用します。
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
				style.set(BackgroundImage.INFO, URIUtils.createURIValue(ua.getDocumentContext().getEncoding(),
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
		StringBuffer buff = new StringBuffer(str.length());
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
		return AbsoluteLengthValue.create(ua, NumberUtils.parseDouble(buff.toString()), LengthValue.UNIT_PX);
	}

	private static boolean isNumber(char c) {
		return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e';
	}

	static ListStyleTypeValue toListStyleType(String ident) {
		if (ident.length() > 0) {
			switch (ident.charAt(0)) {
			case '1':
				return ListStyleTypeValue.DECIMAL_VALUE;
			case 'a':
				return ListStyleTypeValue.LOWER_LATIN_VALUE;
			case 'A':
				return ListStyleTypeValue.UPPER_LATIN_VALUE;
			case 'i':
				return ListStyleTypeValue.LOWER_ROMAN_VALUE;
			case 'I':
				return ListStyleTypeValue.UPPER_ROMAN_VALUE;
			}
		}
		return GeneratedValueUtils.toListStyleType(ident);
	}
}
