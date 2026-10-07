package net.zamasoft.foliojet.css.html;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.BackgroundAttachmentValue;
import net.zamasoft.foliojet.css.value.BorderStyleValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.FontWeightValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.QuoteValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.TextDecorationValue;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.value.WhiteSpaceValue;
import net.zamasoft.foliojet.css.value.ext.CSSJRubyValue;
import net.zamasoft.foliojet.css.value.internal.CSSJHtmlAlignValue;
import net.zamasoft.foliojet.css.value.internal.CSSJHtmlTableBorderValue;
import net.zamasoft.foliojet.css.impl.part.AltTextImage;
import net.zamasoft.foliojet.css.impl.part.BrokenImage;
import net.zamasoft.foliojet.css.impl.part.CheckBoxImage;
import net.zamasoft.foliojet.css.impl.part.NullImage;
import net.zamasoft.foliojet.css.impl.part.RadioButtonImage;
import net.zamasoft.foliojet.css.impl.part.SelectImage;
import net.zamasoft.foliojet.css.impl.part.UnprintBrokenImage;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.font.FontWeight;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.impl.property.text.TextDecoration;
import net.zamasoft.foliojet.css.impl.property.text.UnicodeBidi;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJRuby;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJAutoWidth;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlCellPadding;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlTableBorder;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageLoadDiagnostics;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.ImageMap.Area;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputBrokenImage;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.util.NumberUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;
import net.zamasoft.foliojet.ua.CompatibleMode;
public class HTMLStyle {
	/**
	 * Converts an image reference to a URI. data: does not depend on the document base.
	 * As before, first parse it as a raw URI (preserving base64 + / = unchanged;
	 * imageTest legacy/0070-image/040-DATA and 070-TRANSPARENT broke on 2026-09-05).
	 * Encode only when parsing fails, as with an SVG data: URI containing spaces.
	 *
	 * @param encoding the multibyte character encoding
	 * @param baseURI  the document base URI
	 * @param src      the reference string
	 * @return URI
	 * @throws URISyntaxException if parsing fails
	 */
	public static URI imageURI(final String encoding, final URI baseURI, final String src) throws URISyntaxException {
		if (src.regionMatches(true, 0, "data:", 0, 5)) {
			try {
				return new URI(src);
			} catch (final URISyntaxException e) {
				return URIHelper.create(encoding, src);
			}
		}
		return URIHelper.resolve(encoding, baseURI, src);
	}

	private static final RelativeLengthValue EX_20 = RelativeLengthValue.ex(20);
	private static final ValueListValue WBR = new ValueListValue(new Value[] { new StringValue("\u200B") });
	private static final ValueListValue OPEN_QUOTE = new ValueListValue(new Value[] { QuoteValue.OPEN_QUOTE_VALUE });
	private static final ValueListValue CLOSE_QUOTE = new ValueListValue(new Value[] { QuoteValue.CLOSE_QUOTE_VALUE });
	private static final ValueListValue EMPTY = new ValueListValue(new Value[] { new StringValue("") });

	public static void applyAfterStyle(CSSStyle style) {
		// :after
		assert style.getCSSElement() == CSSElement.AFTER;
		CSSElement parentCe = style.getParentStyle().getCSSElement();
		short code = HTMLCodes.code(parentCe);
		switch (code) {
		case HTMLCodes.INPUT:
			// <INPUT>
			byte type = HTMLStyleUtils.getInputType(parentCe.atts.getValue("type"));
			switch (type) {
			case HTMLStyleUtils.INPUT_PASSWORD: {
				String value = parentCe.atts.getValue("value");
				if (value != null) {
					char[] chars = new char[value.length()];
					for (int i = 0; i < chars.length; ++i) {
						chars[i] = '*';
					}
					style.set(Content.INFO, new ValueListValue(new Value[] { new StringValue(new String(chars)+"\u200B") }));
				} else {
					style.set(Content.INFO, WBR);
				}
			}
				break;

			case HTMLStyleUtils.INPUT_FILE: {
				HTMLStyle.applyPseudoButton(style, parentCe.atts.getValue("disabled") != null);
				style.set(Content.INFO, new ValueListValue(new Value[] { new StringValue("選択...") }));
			}
				break;

			case HTMLStyleUtils.INPUT_TEXT:
			case HTMLStyleUtils.INPUT_BUTTON:
			case HTMLStyleUtils.INPUT_SUBMIT:
			case HTMLStyleUtils.INPUT_RESET: {
				String value = parentCe.atts.getValue("value");
				if (value != null) {
					style.set(Content.INFO, new ValueListValue(new Value[] { new StringValue(value+"\u200B") }));
				} else {
					style.set(Content.INFO, WBR);
				}
			}
				break;
			}
			break;
		case HTMLCodes.ISINDEX:
			// <ISINDEX>
			HTMLStyle.applyTextField(style, false, null);
			applyPseudoFieldWidth(style, null);
			style.set(Content.INFO, WBR);
			break;
		case HTMLCodes.Q: {
			// <Q>
			style.set(Content.INFO, CLOSE_QUOTE);
		}
			break;
		case HTMLCodes.WBR:
			// <WBR>
			style.set(Content.INFO, WBR);
			style.set(WhiteSpace.INFO, WhiteSpaceValue.NORMAL_VALUE);
			break;
		case HTMLCodes.SELECT: {
			// <SELECT>
			UserAgent ua = style.getUserAgent();
			CSSStyle parent = style.getParentStyle();
			// **Construct arrow dimensions in pt (the type area unit)** (2026-08-02). Previously,
			// values converted to PX were passed, so arrows placed in pt coordinates were
			// enlarged by 1/0.75 (a 10 pt size rendered at 13.33 pt).
			double size = Height.getLength(parent).getLength();
			style.set(CSSPosition.INFO, PositionValue.ABSOLUTE_VALUE);
			double border = BorderWidth.get(parent, Side.TOP);
			// **Place it inside the box** (2026-08-02). SELECT reserves 1em of right
			// padding, but negative insets placed the arrow outside the box,
			// overlapping subsequent content (measured: for a 31 pt wide box,
			// the arrow occupied x=25..41).
			style.set(Inset.TOP, AbsoluteLengthValue.create(ua, border));
			style.set(Inset.RIGHT, AbsoluteLengthValue.create(ua, border));
			CSSJInternalImage.setImage(style, new SelectImage(parentCe.atts.getValue("disabled") != null, size));
			style.set(Content.INFO, EMPTY);
		}
			break;
		}
	}

	public static void applyBeforeStyle(CSSStyle style) {
		// :before
		assert style.getCSSElement() == CSSElement.BEFORE;
		CSSElement parentCe = style.getParentStyle().getCSSElement();
		short code = HTMLCodes.code(parentCe);
		switch (code) {
		case HTMLCodes.BUTTON: {
			// <BUTTON>
			// ZWSP ensures height (a baseline) even for an empty button. However,
			// in a flex/grid container button, this ::before becomes a separate item
			// occupying a row/cell and pushes the actual content (icons, etc.) outside the box
			// (an actual bug that turned chevrons in NHK News navigation into empty boxes,
			// 2026-08-09). Chrome does not inject this, so skip injection for
			// flex/grid.
			final byte display = Display.get(style.getParentStyle());
			if (display != DisplayValue.FLEX && display != DisplayValue.GRID) {
				style.set(Content.INFO, WBR);
			}
		}
			break;
		case HTMLCodes.INPUT:
			// <INPUT>
			byte type = HTMLStyleUtils.getInputType(parentCe.atts.getValue("type"));
			if (type == HTMLStyleUtils.INPUT_FILE) {
				HTMLStyle.applyTextField(style, parentCe.atts.getValue("disabled") != null,
						parentCe.atts.getValue("size"));
				applyPseudoFieldWidth(style, parentCe.atts.getValue("size"));
				style.set(Content.INFO, WBR);
			}
			break;
		case HTMLCodes.ISINDEX: {
			// <ISINDEX>
			String prompt = parentCe.atts.getValue("prompt");
			if (prompt != null) {
				style.set(Content.INFO, new ValueListValue(new Value[] { new StringValue(prompt) }));
			}
		}
			break;
		case HTMLCodes.Q: {
			// <Q>
			style.set(Content.INFO, OPEN_QUOTE);
		}
			break;
		}
	}

	/**
	 * Selects the print candidate (highest resolution) from {@code srcset}
	 * (2026-08-20). Chooses the highest density for density descriptors (2x)
	 * and the greatest width for width descriptors (640w). No descriptor means 1x.
	 * Returns null if parsing fails.
	 */
	public static String pickFromSrcset(final String srcset) {
		if (srcset == null || srcset.isEmpty()) {
			return null;
		}
		String bestUrl = null;
		double bestScore = -1;
		for (final String part : srcset.split(",")) {
			final String cand = part.trim();
			if (cand.isEmpty()) {
				continue;
			}
			final int sp = cand.indexOf(' ');
			final String url;
			double score = 1;
			if (sp < 0) {
				url = cand;
			} else {
				url = cand.substring(0, sp);
				final String desc = cand.substring(sp + 1).trim().toLowerCase(java.util.Locale.ROOT);
				try {
					if (desc.endsWith("x")) {
						score = Double.parseDouble(desc.substring(0, desc.length() - 1));
					} else if (desc.endsWith("w")) {
						// Width descriptors use a different scale from density; divide by 1000 to keep
						// the magnitudes comparable rather than letting density dominate (choosing the widest is enough).
						score = Double.parseDouble(desc.substring(0, desc.length() - 1)) / 1000.0;
					}
				} catch (final NumberFormatException e) {
					continue;
				}
			}
			if (score > bestScore && !url.isEmpty()) {
				bestScore = score;
				bestUrl = url;
			}
		}
		return bestUrl;
	}

	/** Whether the conversion system can read the image type (a missing type attribute is allowed). */
	public static boolean isSupportedImageType(final String type) {
		if (type == null || type.isEmpty()) {
			return true;
		}
		switch (type.trim().toLowerCase(java.util.Locale.ROOT)) {
		case "image/png":
		case "image/jpeg":
		case "image/jpg":
		case "image/gif":
		case "image/webp":
		case "image/svg+xml":
		case "image/bmp":
			return true;
		default:
			// Skip unsupported formats such as image/avif and image/jxl.
			return false;
		}
	}

	/**
	 * @param fallbackContent
	 *             whether the element has HTML-specified fallback content (children)
	 *             (object/applet). With the default broken-image=none, do not create
	 *             a replaced box; let the children (fallback content) render.
	 */
	private static void applyBrokenImage(CSSStyle style, String alt, boolean fallbackContent) {
		UserAgent ua = style.getUserAgent();
		OutputBrokenImage brokenimage = UAProps.OUTPUT_BROKEN_IMAGE.get(ua);
		final String pdf14PdfX = UAProps.OUTPUT_PDF_VERSION.get(ua).pdf14PdfXName();
		if (brokenimage == OutputBrokenImage.ANNOTATION && pdf14PdfX != null) {
			ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_BROKEN_IMAGE.name, "annotation",
					pdf14PdfX);
			brokenimage = OutputBrokenImage.CROSS;
		}

		switch (brokenimage) {
		case ANNOTATION:
			CSSJInternalImage.setImage(style, new UnprintBrokenImage(ua, alt));
			return;
		case CROSS:
			CSSJInternalImage.setImage(style, new BrokenImage(ua, alt));
			return;
		case HIDDEN:
			CSSJInternalImage.setImage(style, new NullImage(alt));
			return;
		case NONE:
			// **Do not turn object or applet into replaced boxes** (2026-08-07).
			// Their children are the standard HTML fallback mechanism; a replaced
			// box would suppress all children. This surfaced as a regression that removed
			// the acid2 eyes (a nested object with data:PNG inside a failing object)
			// (introduced with AltTextImage on 2026-08-06, identified by bisect).
			if (fallbackContent) {
				return;
			}
			// **Setting no image prevents a replaced box, so CSS width/height
			// are ignored and the box collapses** (2026-08-06, found when a display:table
			// figure caption on woocommerce.com collapsed into a tall column of single words;
			// see the AltTextImage Javadoc). CSSJInternalImage stores an image or text
			// in the same slot (mutually exclusive). Use setImage() instead of setText()
			// and let AltTextImage draw the alt string itself.
			CSSJInternalImage.setImage(style, new AltTextImage(ua, alt));
			return;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Button defaults <b>were moved to html-ua.css</b> (2026-08-03). They are now
	 * UA stylesheet rules rather than attribute-derived defaults (presentational hints),
	 * so author CSS can override them, as the cascade should allow.
	 *
	 * <p>
	 * Previously, {@code height: 1em} was hardcoded here, causing labels to overflow
	 * buttons with line height or vertical padding. This illustrates the lesson that
	 * <b>defaults in Java can linger without anyone noticing</b>.
	 */
	private static void applyButton(CSSStyle style, boolean disabled) {
		// Moved (button / input[type=button], etc. in html-ua.css).
	}

	private static void applyImage(CSSStyle style, String src, final String type, String alt) {
		applyImage(style, src, type, alt, false);
	}

	private static void applyImage(CSSStyle style, String src, final String type, String alt,
			boolean fallbackContent) {
		if (src != null) {
			final UserAgent ua = style.getUserAgent();
			final Image image = ImageLoadDiagnostics.loadImage(ua, src,
					() -> imageURI(ua.getDocumentContext().getEncoding(), ua.getDocumentContext().getBaseURI(), src),
					type, true);
			if (image != null) {
				CSSJInternalImage.setImage(style, image);
				return;
			}
			HTMLStyle.applyBrokenImage(style, alt, fallbackContent);
			if (fallbackContent && CSSJInternalImage.getImage(style) == null) {
				// Let fallback content (children) render; overriding Content with alt
				// would hide the children.
				return;
			}
		}
		if (alt != null) {
			style.set(Content.INFO, new ValueListValue(new Value[] { new StringValue(alt) }));
		}
	}

	/**
	 * Specifies table cell layout.
	 *
	 * @param style
	 */
	private static void applyTableCell(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		// Defaults for display/page-break-inside/vertical-align, own valign/align,
		// and width/height/bgcolor/nowrap were moved to html-ua.css (2026-08-04).
		if (ce.atts.getValue("valign") == null) {
			// **Inherit valign from the nearest ancestor that has it**. Selectors cannot express "nearest".
			CSSStyle parentStyle = style.getParentStyle();
			LOOP: while (parentStyle != null) {
				CSSElement parentCe = parentStyle.getCSSElement();
				switch (HTMLCodes.code(parentCe)) {
				case HTMLCodes.TR:
				case HTMLCodes.THEAD:
				case HTMLCodes.TBODY:
				case HTMLCodes.TFOOT:
				case HTMLCodes.TABLE:
					String str = parentCe.atts.getValue("valign");
					if (str == null) {
						break;
					}
					HTMLStyleUtils.applyVAlign(elem, style, str);
					break LOOP;
				}
				parentStyle = parentStyle.getParentStyle();
			}
		}
		HTMLStyleUtils.applyBackground(elem, style);
		LengthValue cellpadding = CSSJHtmlCellPadding.get(style);
		style.set(Padding.TOP, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.RIGHT, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.BOTTOM, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.LEFT, cellpadding, CSSStyle.MODE_WEAK);
		CSSJHtmlTableBorderValue border = CSSJHtmlTableBorder.get(style);
		if (!border.getWidth().isZero()) {
			ColorValue borderColor = border.getColor();
			BorderStyleValue borderStyle;
			if (borderColor == null) {
				borderStyle = BorderStyleValue.INSET_VALUE;
			} else {
				borderStyle = BorderStyleValue.SOLID_VALUE;
				style.set(BorderColor.TOP, borderColor);
				style.set(BorderColor.RIGHT, borderColor);
				style.set(BorderColor.BOTTOM, borderColor);
				style.set(BorderColor.LEFT, borderColor);
			}
			LengthValue thin = ua.getBorderWidth(BorderWidthKeyword.THIN);
			style.set(BorderStyle.TOP, borderStyle);
			style.set(BorderWidth.TOP, thin);
			style.set(BorderStyle.RIGHT, borderStyle);
			style.set(BorderWidth.RIGHT, thin);
			style.set(BorderStyle.BOTTOM, borderStyle);
			style.set(BorderWidth.BOTTOM, thin);
			style.set(BorderStyle.LEFT, borderStyle);
			style.set(BorderWidth.LEFT, thin);
		}
		CSSStyle parent = style.getParentStyle();
		for (; parent != null; parent = parent.getParentStyle()) {
			CSSElement parentCe = parent.getCSSElement();
			if (HTMLCodes.code(parentCe) == HTMLCodes.TABLE) {
				String rules = parentCe.atts.getValue("rules");
				if (rules != null) {
					if (rules.equalsIgnoreCase("all")) {
						style.set(BorderStyle.RIGHT, BorderStyleValue.SOLID_VALUE);
						style.set(BorderWidth.RIGHT, ua.getBorderWidth(BorderWidthKeyword.THIN));
						style.set(BorderStyle.LEFT, BorderStyleValue.SOLID_VALUE);
						style.set(BorderWidth.LEFT, ua.getBorderWidth(BorderWidthKeyword.THIN));
						style.set(BorderStyle.TOP, BorderStyleValue.SOLID_VALUE);
						style.set(BorderWidth.TOP, ua.getBorderWidth(BorderWidthKeyword.THIN));
						style.set(BorderStyle.BOTTOM, BorderStyleValue.SOLID_VALUE);
						style.set(BorderWidth.BOTTOM, ua.getBorderWidth(BorderWidthKeyword.THIN));
					} else if (rules.equalsIgnoreCase("cols")) {
						style.set(BorderStyle.RIGHT, BorderStyleValue.SOLID_VALUE);
						style.set(BorderWidth.RIGHT, ua.getBorderWidth(BorderWidthKeyword.THIN));
						style.set(BorderStyle.LEFT, BorderStyleValue.SOLID_VALUE);
						style.set(BorderWidth.LEFT, ua.getBorderWidth(BorderWidthKeyword.THIN));
						style.set(BorderStyle.TOP, BorderStyleValue.NONE_VALUE);
						style.set(BorderStyle.BOTTOM, BorderStyleValue.NONE_VALUE);
					} else {
						style.set(BorderStyle.TOP, BorderStyleValue.NONE_VALUE);
						style.set(BorderStyle.BOTTOM, BorderStyleValue.NONE_VALUE);
						style.set(BorderStyle.RIGHT, BorderStyleValue.NONE_VALUE);
						style.set(BorderStyle.LEFT, BorderStyleValue.NONE_VALUE);
					}
				}
				break;
			}
		}
	}

	private static void applyTableColumn(String elem, CSSStyle style) {
		// bgcolor/width/align/valign were moved to html-ua.css (2026-08-04).
		// Only cellpadding, distributed from the table to its cells, remains.
		LengthValue cellpadding = CSSJHtmlCellPadding.get(style);
		style.set(Padding.TOP, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.RIGHT, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.BOTTOM, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.LEFT, cellpadding, CSSStyle.MODE_WEAK);
	}

	/**
	 * <b>Pseudo-element button</b> ("Choose..." for {@code <input type=file>}).
	 * Element button defaults were moved to html-ua.css (2026-08-03),
	 * but those selectors cannot reach buttons created with {@code ::before}, so this remains here.
	 * The values match applyButton before the move.
	 */
	private static void applyPseudoButton(CSSStyle style, boolean disabled) {
		final UserAgent ua = style.getUserAgent();
		style.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
		if (disabled) {
			style.set(CSSColor.INFO, ColorValueUtils.DIMGRAY);
		}
		style.set(TextAlign.INFO, TextAlignValue.CENTER_VALUE);
		style.set(BackgroundColor.INFO, ColorValueUtils.LIGHTGRAY);
		final AbsoluteLengthValue thin = ua.getBorderWidth(BorderWidthKeyword.THIN);
		style.set(BorderStyle.TOP, BorderStyleValue.OUTSET_VALUE);
		style.set(BorderWidth.TOP, thin);
		style.set(BorderStyle.LEFT, BorderStyleValue.OUTSET_VALUE);
		style.set(BorderWidth.LEFT, thin);
		style.set(BorderStyle.BOTTOM, BorderStyleValue.OUTSET_VALUE);
		style.set(BorderWidth.BOTTOM, thin);
		style.set(BorderStyle.RIGHT, BorderStyleValue.OUTSET_VALUE);
		style.set(BorderWidth.RIGHT, thin);
		style.set(Padding.TOP, thin);
		style.set(Padding.BOTTOM, thin);
		style.set(Padding.LEFT, thin);
		style.set(Padding.RIGHT, thin);
		style.set(WhiteSpace.INFO, WhiteSpaceValue.NOWRAP_VALUE);
	}

	/**
	 * <b>Pseudo-element input width</b>. Element widths were moved to html-ua.css
	 * (2026-08-03), but those selectors cannot reach inputs created with {@code ::before}
	 * because pseudo-elements do not have the originating element's attributes.
	 * Only this part remains in Java.
	 */
	private static void applyPseudoFieldWidth(CSSStyle style, String size) {
		if (size != null) {
			try {
				style.set(CSSJAutoWidth.INFO, RelativeLengthValue.ex(NumberUtils.parseDouble(size)));
				return;
			} catch (NumberFormatException e) {
				style.getUserAgent().message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "INPUT", "size", size);
			}
		}
		style.set(CSSJAutoWidth.INFO, EX_20);
	}

	private static void applyTextField(CSSStyle style, boolean disabled, String size) {
		UserAgent ua = style.getUserAgent();
		style.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
		// Width was moved to html-ua.css (2026-08-03, made expressible by typed attr()).
		// **Keep the default 20ex in CSS too**. Calling style.set here
		// would place it in the attribute-derived layer (stronger than the UA sheet),
		// causing the CSS rule to lose.

		style.set(Height.INFO, KeywordValue.AUTO);
		if (disabled) {
			style.set(CSSColor.INFO, ColorValueUtils.DIMGRAY);
			style.set(BackgroundColor.INFO, ColorValueUtils.LIGHTGRAY);
		} else {
			style.set(BackgroundColor.INFO, ColorValueUtils.WHITE);
		}
		LengthValue thin = ua.getBorderWidth(BorderWidthKeyword.THIN);
		style.set(BorderStyle.TOP, BorderStyleValue.INSET_VALUE);
		style.set(BorderWidth.TOP, thin);
		style.set(BorderStyle.LEFT, BorderStyleValue.INSET_VALUE);
		style.set(BorderWidth.LEFT, thin);
		style.set(BorderStyle.BOTTOM, BorderStyleValue.INSET_VALUE);
		style.set(BorderWidth.BOTTOM, thin);
		style.set(BorderStyle.RIGHT, BorderStyleValue.INSET_VALUE);
		style.set(BorderWidth.RIGHT, thin);
		style.set(Padding.TOP, thin);
		style.set(Padding.BOTTOM, thin);
		style.set(Padding.LEFT, thin);
		style.set(Padding.RIGHT, thin);
		style.set(WhiteSpace.INFO, WhiteSpaceValue.NOWRAP_VALUE);
	}

	public static boolean hasAfterContent(CSSElement ce) {
		short code = HTMLCodes.code(ce);
		switch (code) {
		case HTMLCodes.INPUT:
		case HTMLCodes.ISINDEX:
		case HTMLCodes.Q:
		case HTMLCodes.WBR:
		case HTMLCodes.SELECT:
			return true;
		}
		return false;
	}

	public static boolean hasBeforeContent(CSSElement ce) {
		short code = HTMLCodes.code(ce);
		switch (code) {
		case HTMLCodes.BUTTON:
		case HTMLCodes.INPUT:
		case HTMLCodes.ISINDEX:
		case HTMLCodes.Q:
			return true;
		}
		return false;
	}

	private ColorValue linkColor = null;

	/**
	 * URLs of {@code <source>} candidates awaiting selection inside {@code <picture>}
	 * (added 2026-08-20). The list becomes active at the start of picture and accumulates
	 * candidates selected from {@code <source srcset>} elements with supported types.
	 * The following {@code <img>} consumes the first candidate. Null outside picture
	 * to avoid accidentally collecting {@code <source>} from video/audio.
	 */
	private java.util.List<String> pictureSources = null;

	private ImageMap imageMap = null;

	public void applyStyle(CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		assert ce != CSSElement.BEFORE && ce != CSSElement.AFTER;

		// @dir
		{
			String dir = ce.atts.getValue("dir");
			if (dir != null) {
				if (dir.equalsIgnoreCase("ltr")) {
					style.set(UnicodeBidi.INFO, UnicodeBidiValue.ISOLATE_VALUE);
					style.set(Direction.INFO, DirectionValue.LTR_VALUE);
				} else if (dir.equalsIgnoreCase("rtl")) {
					style.set(UnicodeBidi.INFO, UnicodeBidiValue.ISOLATE_VALUE);
					style.set(Direction.INFO, DirectionValue.RTL_VALUE);
				} else if (dir.equalsIgnoreCase("auto")) {
					style.set(UnicodeBidi.INFO, UnicodeBidiValue.PLAINTEXT_VALUE);
				} else {
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "*", "dir", dir);
				}
			}
		}

		short code = HTMLCodes.code(ce);
		switch (code) {
		case HTMLCodes.A: {
			// <A>
			if (ce.isPseudoClass(CSSElement.PC_LINK)) {
				// A:link
				style.set(TextDecoration.INFO, TextDecorationValue.create(TextDecorationValue.UNDERLINE));
				if (this.linkColor == null) {
					this.linkColor = ColorValueUtils.BLUE;
				}
				style.set(CSSColor.INFO, linkColor);
			}
		}
			break;
		// ABBR/ACRONYM: no attribute-driven logic or defaults, so no Java case is needed.
		// ADDRESS: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		case HTMLCodes.APPLET: {
			// <APPLET width height hspace vspace alt align>
			HTMLStyleUtils.applyWidthHeight("APPLET", style);
			HTMLStyleUtils.applyHSpaceVSpace("APPLET", style);
			HTMLStyleUtils.applyImageAlign("APPLET", style);
			// applet children are fallback content, just like object children.
			HTMLStyle.applyBrokenImage(style, ce.atts.getValue("alt"), true);
		}
			break;
		case HTMLCodes.AREA: {
			// <AREA href shape coords>
			if (this.imageMap == null) {
				break;
			}
			String href = ce.atts.getValue("href");
			if (href == null) {
				break;
			}
			String shape = ce.atts.getValue("shape");
			String coords = ce.atts.getValue("coords");
			Shape realShape = null;
			// shape="default" (or omitted shape/coords) means "the entire image".
			// If no shape can be created (unknown shape or insufficient coordinates),
			// **discard that area** instead of using the entire image. Promoting invalid input
			// to a whole-image link creates an unintended broad link (2026-07-25).
			final boolean wholeImage = shape == null || shape.equalsIgnoreCase("default") || coords == null;
			if (wholeImage) {
				realShape = null;
			} else {
				shape = shape.toLowerCase();
				String[] coordsArray = coords.split(",");
				double[] realCoords = new double[coordsArray.length];
				for (int i = 0; i < realCoords.length; ++i) {
					try {
						realCoords[i] = Double.parseDouble(coordsArray[i].trim());
					} catch (NumberFormatException e) {
						ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "AREA", "coords", coords);
						realCoords[i] = 0;
					}
				}
				try {
					if (shape.startsWith("circ")) {
						// The bounding rectangle of coords="cx,cy,r" is (cx-r, cy-r, 2r, 2r).
						// Previously cx-r/2 shifted the center by half the radius
						// (2026-07-25, found in an independent review).
						realShape = new Ellipse2D.Double(realCoords[0] - realCoords[2], realCoords[1] - realCoords[2],
								realCoords[2] * 2, realCoords[2] * 2);
					} else if (shape.startsWith("rect")) {
						realShape = new Rectangle2D.Double(realCoords[0], realCoords[1], realCoords[2] - realCoords[0],
								realCoords[3] - realCoords[1]);
					} else if (shape.startsWith("poly")) {
						Path2D.Double path = new Path2D.Double();
						path.moveTo(realCoords[0], realCoords[1]);
						for (int i = 2; i < realCoords.length; i += 2) {
							path.lineTo(realCoords[i], realCoords[i + 1]);
						}
						path.closePath();
						realShape = path;
					} else {
						ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "AREA", "shape", shape);
					}
				} catch (ArrayIndexOutOfBoundsException e) {
					ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "AREA", "coords", coords);
				}
			}
			if (realShape == null && !wholeImage) {
				// Could not create a shape (unknown shape or insufficient coordinates). Already warned above.
				break;
			}
			try {
				Area area = new Area(realShape, URIHelper.resolve(ua.getDocumentContext().getEncoding(),
						ua.getDocumentContext().getBaseURI(), href));
				this.imageMap.add(area);
			} catch (URISyntaxException e) {
				ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "AREA", "href", shape);
			}
		}
			break;
		// Display defaults for HTML5 sectioning/flow content elements and hidden metadata elements
		// were moved to the UA default stylesheet (html-ua.css).
		// (2026-07-18).
		case HTMLCodes.BDI:
			// bdi without dir is an isolated range whose direction comes from its first strong character.
			if (ce.atts.getValue("dir") == null) {
				style.set(UnicodeBidi.INFO, UnicodeBidiValue.PLAINTEXT_VALUE);
			}
			break;
		// B/BASE: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		case HTMLCodes.BASEFONT: {
			// <BASEFONT size color face>
			HTMLStyleUtils.applyFontSize("BASEFONT", style);
			HTMLStyleUtils.applyFontFace(style);
			HTMLStyleUtils.applyFontColor("BASEFONT", style);
		}
			break;
		// BGSOUND: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		case HTMLCodes.BDO:
			// Prefer the bdo-specific override over the dir attribute isolate.
			style.set(UnicodeBidi.INFO, UnicodeBidiValue.ISOLATE_OVERRIDE_VALUE);
			break;
		case HTMLCodes.BODY: {
			// <BODY background bgproperties link -vlink -alink>
			//
			// **Margin attributes (marginwidth/marginheight/topmargin/rightmargin/
			// leftmargin/bottommargin), bgcolor, and text were moved to html-ua.css**
			// (2026-08-03, typed attr()). Only background image resource resolution and
			// link colors that must be distributed to descendants remain here.
			{
				String str = ce.atts.getValue("bgproperties");
				if (str != null && str.equalsIgnoreCase("fixed")) {
					style.set(BackgroundAttachment.INFO, BackgroundAttachmentValue.FIXED_VALUE);
				}
			}
			{
				String str = ce.atts.getValue("link");
				if (str != null) {
					this.linkColor = HTMLStyleUtils.parseColor(str);
					if (this.linkColor == null) {
						ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "BODY", "link", str);
					}
				}
			}
			HTMLStyleUtils.applyBackground("BODY", style);
		}
			break;
		case HTMLCodes.BR:
			// <BR clear> was moved to html-ua.css (2026-08-03).
			break;
		case HTMLCodes.BUTTON: {
			// <BUTTON disabled>
			// font-size: medium moved to html-ua.css (2026-08-02).
			HTMLStyle.applyButton(style, ce.atts.getValue("disabled") != null);
		}
			break;
		case HTMLCodes.CAPTION:
			// <CAPTION align valign> was moved to html-ua.css (2026-08-03).
			break;
		case HTMLCodes.CENTER: {
			// <CENTER> text-align: center moved to html-ua.css (2026-08-02).
			// Keep -cssj-html-align because it is an internal property (not settable from CSS text).
			style.set(CSSJHtmlAlign.INFO, CSSJHtmlAlignValue.CENTER_VALUE);
		}
			break;
		// CITE: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		case HTMLCodes.CODE: {
			// <CODE>
			// Moving font-family to CSS was deferred because of the asymmetry with fallback insertion
			// in FontValueUtils.toFontFamily() (see the html-ua.css comment). Keep it in Java.
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.COLGROUP: {
			// <COLGROUP align bgcolor -charoff span valign width>
			CSSStyle parent = style.getParentStyle();
			for (; parent != null; parent = parent.getParentStyle()) {
				CSSElement parentCe = parent.getCSSElement();
				CSSJHtmlTableBorderValue border = CSSJHtmlTableBorder.get(style);
				if (!border.getWidth().isZero()) {

					if (HTMLCodes.code(parentCe) == HTMLCodes.TABLE) {
						if ("groups".equalsIgnoreCase(parentCe.atts.getValue("rules"))) {
							style.set(BorderStyle.RIGHT, BorderStyleValue.SOLID_VALUE);
							style.set(BorderWidth.RIGHT, ua.getBorderWidth(BorderWidthKeyword.THIN));
							style.set(BorderStyle.LEFT, BorderStyleValue.SOLID_VALUE);
							style.set(BorderWidth.LEFT, ua.getBorderWidth(BorderWidthKeyword.THIN));
						}
						break;
					}
				}
			}
			applyTableColumn("COLGROUP", style);
		}
			break;
		case HTMLCodes.COL: {
			// <COL align bgcolor -charoff span valign width>
			applyTableColumn("COL", style);
		}
			break;
		// COMMENT: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		// DD: defaults (margin-inline-start/page-break-before) moved to html-ua.css (2026-08-02).
		// DEL: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		// DFN: no attribute-driven logic or defaults, so no Java case is needed.
		case HTMLCodes.DIR:
			// <DIR type> was moved to html-ua.css (2026-08-03; testing the first character
			// is equivalent to using a prefix-matching attribute selector).
			break;
		case HTMLCodes.DIV: {
			// <DIV align>
			HTMLStyleUtils.applyBlockAlign("DIV", style);
		}
			break;
		// DL: defaults (margin-block/page-break-before) moved to html-ua.css (2026-08-02).
		// DT/EM: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		case HTMLCodes.EMBED: {
			// <EMBED border
			// width height type
			// hspace vspace
			// alt -hidden -frameborder -units>
			HTMLStyleUtils.applyWidthHeight("EMBED", style);
			HTMLStyleUtils.applyHSpaceVSpace("EMBED", style);
			HTMLStyleUtils.applyImageBorder("EMBED", style);
			String src = ce.atts.getValue("src");
			String type = ce.atts.getValue("type");
			String alt = ce.atts.getValue("alt");
			HTMLStyle.applyImage(style, src, type, alt);
		}
			break;
		case HTMLCodes.FIELDSET: {
			// <FIELDSET align> static defaults (margin-block/padding/border)
			// moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("FIELDSET", style);
		}
			break;
		case HTMLCodes.FONT: {
			// <FONT size color face font-weight point-size>
			HTMLStyleUtils.applyFontSize("FONT", style);
			HTMLStyleUtils.applyFontColor("FONT", style);
			HTMLStyleUtils.applyFontFace(style);
			{
				String str = ce.atts.getValue("font-weight");
				if (str != null) {
					try {
						int fontWeight = Integer.parseInt(str);
						fontWeight = Math.max(100, fontWeight);
						fontWeight = Math.min(900, fontWeight);
						style.set(FontWeight.INFO, FontWeightValue.create(fontWeight));
					} catch (NumberFormatException e) {
						ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "FONT", "font-weight", str);

					}
				}
			}
		}
			break;
		case HTMLCodes.H1: {
			// <H1 align> static defaults moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("H1", style);
		}
			break;
		case HTMLCodes.H2: {
			// <H2 align> static defaults moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("H2", style);
		}
			break;
		case HTMLCodes.H3: {
			// <H3 align> static defaults moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("H3", style);
		}
			break;
		case HTMLCodes.H4: {
			// <H4 align> static defaults moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("H4", style);
		}
			break;
		case HTMLCodes.H5: {
			// <H5 align> static defaults moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("H5", style);
		}
			break;
		case HTMLCodes.H6: {
			// <H6 align> static defaults moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("H6", style);
		}
			break;
		// HEAD: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		case HTMLCodes.HR:
			// <HR align color noshade size width> was moved to html-ua.css
			// (2026-08-03). Implementing border-block-end-* made it possible
			// to express a border on only one side.
			break;
		case HTMLCodes.IFRAME:
			// <IFRAME width height hspace vspace align marginwidth marginheight
			// frameborder> was moved to html-ua.css (2026-08-03).
			break;
		case HTMLCodes.IMG: {
			// <IMG src srcset alt border width height hspace vspace align usemap>
			HTMLStyleUtils.applyWidthHeight("IMG", style);
			HTMLStyleUtils.applyHSpaceVSpace("IMG", style);
			HTMLStyleUtils.applyImageAlign("IMG", style);
			String src = ce.atts.getValue("src");
			// Prefer the picture>source candidate (HTML selection order). If absent, use the highest
			// resolution from the image itself via srcset; if that is absent too, use src (2026-08-20).
			if (this.pictureSources != null && !this.pictureSources.isEmpty()) {
				src = this.pictureSources.get(0);
			} else {
				final String fromSrcset = pickFromSrcset(ce.atts.getValue("srcset"));
				if (fromSrcset != null && (src == null || src.isEmpty())) {
					src = fromSrcset;
				} else if (fromSrcset != null) {
					// Even when both src and srcset exist, prefer the high-resolution candidate for print
					// (density descriptors assume different resolutions of the same image).
					src = fromSrcset;
				}
			}
			this.pictureSources = null;
			String alt = ce.atts.getValue("alt");
			HTMLStyle.applyImage(style, src, null, alt);
			HTMLStyleUtils.applyImageBorder("IMG", style);
		}
			break;
		case HTMLCodes.INPUT: {
			// <INPUT type disabled size src border width height align>
			//
			// **font-size, hidden, align, and single-line input appearance were moved to
			// html-ua.css** (2026-08-03). Only resource resolution (type=image) and
			// internally drawn checkbox/radio button images remain here.
			byte type = HTMLStyleUtils.getInputType(ce.atts.getValue("type"));
			switch (type) {
			case HTMLStyleUtils.INPUT_IMAGE: {
				HTMLStyleUtils.applyWidthHeight("INPUT", style);
				String src = ce.atts.getValue("src");
				String alt = ce.atts.getValue("alt");
				HTMLStyle.applyImage(style, src, null, alt);
				HTMLStyleUtils.applyImageBorder("INPUT", style);
			}
				break;
			case HTMLStyleUtils.INPUT_CHECKBOX:
				CSSJInternalImage.setImage(style,
						new CheckBoxImage(ce.atts.getValue("checked") != null, ce.atts.getValue("disabled") != null));
				break;
			case HTMLStyleUtils.INPUT_RADIO:
				CSSJInternalImage.setImage(style, new RadioButtonImage(ce.atts.getValue("checked") != null,
						ce.atts.getValue("disabled") != null));
				break;
			default:
				break;
			}
		}
			break;
		case HTMLCodes.KBD: {
			// <KBD>
			// Moving font-family to CSS was deferred because of the asymmetry with fallback insertion
			// in FontValueUtils.toFontFamily() (see the html-ua.css comment). Keep it in Java.
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.LEGEND: {
			// <LEGEND> position/margin-top moved to html-ua.css (2026-08-02).
			// Keep background color inheritance from ancestors because CSS cannot express it.
			CSSStyle parent = style;
			for (;;) {
				Value color = parent.get(BackgroundColor.INFO);
				if (color != KeywordValue.TRANSPARENT) {
					style.set(BackgroundColor.INFO, color);
					break;
				}
				parent = parent.getParentStyle();
				if (parent == null) {
					style.set(BackgroundColor.INFO, ua.getMatColor());
					break;
				}
			}
		}
			break;
		case HTMLCodes.LI:
			// <LI type> was moved to html-ua.css (2026-08-03; testing the first character
			// is equivalent to using a prefix-matching attribute selector).
			break;
		case HTMLCodes.LISTING: {
			// <LISTING> white-space/text-align moved to html-ua.css (2026-08-02).
			// font-family remains in Java because of the toFontFamily() asymmetry.
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.MAP: {
			// <MAP name>

			Map<Object, ImageMap> imageMaps = style.getUserAgent().getUAContext().getImageMaps();
			String mapName = ce.atts.getValue("name");
			if (mapName != null && !imageMaps.containsKey(mapName)) {
				this.imageMap = new ImageMap();
				imageMaps.put(mapName, this.imageMap);
			} else {
				this.imageMap = null;
			}
		}
			break;
		case HTMLCodes.MARQUEE: {
			// <MARQUEE bgcolor width height hspace vspace>
			HTMLStyleUtils.applyBGColor("MARQUEE", style);
			HTMLStyleUtils.applyWidthHeight("MARQUEE", style);
			HTMLStyleUtils.applyHSpaceVSpace("MARQUEE", style);
		}
			break;
		// MENU: defaults (margin/page-break-before) moved to html-ua.css (2026-08-02).
		// NOBR: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		// NOEMBED/NOFRAMES/NOLAYER/NOSCRIPT: no attribute-driven logic or defaults, so no Java case is needed.
		case HTMLCodes.OBJECT: {
			// <OBJECT border width height hspace vspace alt align usemap>
			HTMLStyleUtils.applyWidthHeight("OBJECT", style);
			HTMLStyleUtils.applyHSpaceVSpace("OBJECT", style);
			HTMLStyleUtils.applyImageAlign("OBJECT", style);
			String src = ce.atts.getValue("data");
			String type = ce.atts.getValue("type");
			String alt = ce.atts.getValue("alt");
			// object children are HTML-specified fallback content (see applyBrokenImage).
			HTMLStyle.applyImage(style, src, type, alt, true);
			HTMLStyleUtils.applyImageBorder("OBJECT", style);
		}
			break;
		case HTMLCodes.OL:
			// <OL type> was moved to html-ua.css (2026-08-03; testing the first character
			// is equivalent to using a prefix-matching attribute selector).
			break;
		case HTMLCodes.PICTURE:
			// <PICTURE>: activate the list of source selection candidates inside it (2026-08-20).
			this.pictureSources = new java.util.ArrayList<>();
			break;
		case HTMLCodes.SOURCE: {
			// <SOURCE srcset type media> (for picture; sources inside video/audio
			// are not collected because pictureSources is null).
			if (this.pictureSources != null) {
				final String type = ce.atts.getValue("type");
				final String media = ce.atts.getValue("media");
				// Variants with media are for art direction. Conservatively skip them during
				// static evaluation for print and fall back to an unconditional source or img.
				// Accept only types the conversion system can read.
				if (media == null && isSupportedImageType(type)) {
					final String picked = pickFromSrcset(ce.atts.getValue("srcset"));
					if (picked != null) {
						this.pictureSources.add(picked);
					}
				}
			}
		}
			break;
		case HTMLCodes.P: {
			// <P align> margin-block moved to html-ua.css (2026-08-02).
			HTMLStyleUtils.applyBlockAlign("P", style);
		}
			break;
		case HTMLCodes.PLAINTEXT: {
			// <PLAINTEXT> white-space/text-align moved to html-ua.css (2026-08-02).
			// font-family remains in Java because of the toFontFamily() asymmetry.
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.PRE: {
			// <PRE cols width wrap> was moved to html-ua.css (2026-08-03).
			// **Only font-family remains**. FontValueUtils.toFontFamily() implicitly
			// adds the default family, so writing monospace in CSS would change
			// the value (a known asymmetry).
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.RUBY: {
			// <RUBY>
			style.set(CSSJRuby.INFO, CSSJRubyValue.RUBY_VALUE);
		}
			break;
		case HTMLCodes.RB: {
			// <RB> is nonstandard in XHTML5.
			style.set(CSSJRuby.INFO, CSSJRubyValue.RB_VALUE);
		}
			break;
		case HTMLCodes.RT: {
			// <RT>
			style.set(CSSJRuby.INFO, CSSJRubyValue.RT_VALUE);
		}
			break;
		case HTMLCodes.RTC: {
			// <RTC>: annotation level container in CSS Ruby.
			style.set(CSSJRuby.INFO, CSSJRubyValue.RTC_VALUE);
		}
			break;
		// S/SCRIPT: defaults moved to the UA default stylesheet (html-ua.css) (2026-07-19).
		case HTMLCodes.SAMP: {
			// <SAMP>
			// Moving font-family to CSS was deferred because of the asymmetry with fallback insertion
			// in FontValueUtils.toFontFamily() (see the html-ua.css comment). Keep it in Java.
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.SELECT: {
			// <SELECT size> display/position/overflow/line-height/background/
			// border/white-space defaults moved to html-ua.css (2026-08-02).
			// size (height) and disabled colors were also moved to html-ua.css (2026-08-04).
			// **Only padding remains**. The right padding is a device-unit value matching
			// the actual arrow size and cannot be expressed as a CSS length.
			LengthValue thin = ua.getBorderWidth(BorderWidthKeyword.THIN);
			style.set(Padding.TOP, thin, CSSStyle.MODE_IMPORTANT);
			// **Reserve the actual rendered arrow width** (2026-08-02). SelectImage uses
			// fixed 16-unit coordinates (constant width 16), so reserving only 1em
			// overlapped the selected text at small font sizes. Scaling the drawing is the proper
			// fix, but risks visual regressions, so match the reserved space to the actual size.
			style.set(Padding.RIGHT, AbsoluteLengthValue.create(ua, 16),
					CSSStyle.MODE_IMPORTANT);
			style.set(Padding.BOTTOM, thin, CSSStyle.MODE_IMPORTANT);
			style.set(Padding.LEFT, thin, CSSStyle.MODE_IMPORTANT);
		}
			break;
		// SMALL/SPAN/STRIKE/STRONG/STYLE/SUB/SUP: defaults moved to the UA default stylesheet
		// (html-ua.css) (2026-07-19). SPAN had no attribute-driven logic or defaults,
		// so no Java case was needed.
		case HTMLCodes.TABLE: {
			// Only <TABLE background> remains.
			//
			// **cellspacing, cellpadding, border, bordercolor, frame, rules,
			// width, height, hspace, vspace, bgcolor, and align were moved to html-ua.css**
			// (2026-08-03). The internal properties that distribute values to cells
			// (-cssj-html-table-border / -cssj-html-cell-padding) were made writable from CSS,
			// and attr() now resolves at the computed-value stage.
			//
			// font-size remains here because it is set only in compatibility mode.
			if (style.getUserAgent().getDocumentContext().getCompatibleMode() == CompatibleMode.NORMAL) {
				style.set(FontSize.INFO, AbsoluteLengthValue.create(ua, ua.getFontSize(AbsoluteFontSize.MEDIUM)));
			}
			HTMLStyleUtils.applyTableAlign("TABLE", style);
			HTMLStyleUtils.applyBackground("TABLE", style);
		}
			break;
		case HTMLCodes.TD: {
			// <TD bordercolor background bgcolor
			// align valign height width nowrap colspan rowspan
			// -charoff,-bordercolordark,-bordercolorlight>
			HTMLStyle.applyTableCell("TD", style);
		}
			break;
		case HTMLCodes.TH: {
			// <TH bordercolor background bgcolor
			// align valign height width nowrap colspan rowspan
			// -charoff,-bordercolordark,-bordercolorlight>
			// font-weight/text-align moved to html-ua.css (2026-08-02).
			HTMLStyle.applyTableCell("TH", style);
		}
			break;
		case HTMLCodes.TR: {
			// <TR bordercolor background bgcolor align valign height
			// -charoff,-bordercolordark,-bordercolorlight>
			// align/bgcolor/height/rules=rows were moved to html-ua.css
			// (2026-08-04). Only background remains because it requires resource resolution.
			HTMLStyleUtils.applyBackground("TR", style);
		}
			break;
		case HTMLCodes.TT: {
			// <TT>
			// Moving font-family to CSS was deferred because of the asymmetry with fallback insertion
			// in FontValueUtils.toFontFamily() (see the html-ua.css comment). Keep it in Java.
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.TEXTAREA:
			// <TEXTAREA cols rows disabled wrap> was moved to html-ua.css.
			// (2026-08-03)
			break;
		case HTMLCodes.UL:
			// <UL type> was moved to html-ua.css (2026-08-03; testing the first character
			// is equivalent to using a prefix-matching attribute selector).
			break;
		case HTMLCodes.VIDEO: {
			// <VIDEO width height poster> (display:inline-block moved to html-ua.css).
			HTMLStyleUtils.applyWidthHeight("VIDEO", style);
			String poster = ce.atts.getValue("poster");
			if (poster != null) {
				HTMLStyle.applyImage(style, poster, null, "");
			}
		}
			break;
		// WBR: no attribute-driven logic or defaults, so no Java case is needed.
		case HTMLCodes.XMP: {
			// <XMP>
			// display/white-space/text-align were moved to the UA default stylesheet (html-ua.css)
			// (2026-07-19). Only font-family remains in Java to avoid the asymmetry
			// with fallback insertion in FontValueUtils.toFontFamily().
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		}

		// @hidden
		{
			String hidden = ce.atts.getValue("hidden");
			if (hidden != null) {
				style.set(Display.INFO, DisplayValue.NONE_VALUE);
			}
		}

		// @popover (Popover API, 2026-08-07). The UA default is
		// `[popover]:not(:popover-open){display:none}`. :popover-open becomes true
		// only after JS calls showPopover(), a state not reflected in static HTML
		// (this engine's input). Thus, display:none is always the correct default
		// for elements with a popover attribute (observed on vercel.com: the logo-click
		// menu and product mega-menu rendered over page content because
		// the popover attribute's default hiding behavior was not implemented).
		{
			String popover = ce.atts.getValue("popover");
			if (popover != null) {
				style.set(Display.INFO, DisplayValue.NONE_VALUE);
			}
		}
	}
}
