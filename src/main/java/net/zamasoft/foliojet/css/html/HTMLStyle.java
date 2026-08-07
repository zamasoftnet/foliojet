package net.zamasoft.foliojet.css.html;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import net.zamasoft.foliojet.css.impl.property.box.LogicalSide;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJRuby;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJAutoWidth;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlCellPadding;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlTableBorder;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.ImageMap;
import net.zamasoft.foliojet.ua.ImageMap.Area;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputBrokenImage;
import net.zamasoft.foliojet.ua.props.OutputPdfVersion;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.SourceWrapper;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.util.NumberUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;
import net.zamasoft.foliojet.ua.BorderWidthKeyword;
import net.zamasoft.foliojet.ua.CompatibleMode;
public class HTMLStyle {
	private static final Logger LOG = Logger.getLogger(HTMLStyle.class.getName());

	private static final RelativeLengthValue EX_20 = RelativeLengthValue.ex(20);
	private static final RelativeLengthValue EM_4 = RelativeLengthValue.em(4);
	private static final RelativeLengthValue EM_1_12 = RelativeLengthValue.em(1.12);
	private static final RelativeLengthValue EM_1 = RelativeLengthValue.em(1);
	private static final RelativeLengthValue EM__5 = RelativeLengthValue.em(.5);
	private static final RelativeLengthValue _EM_1 = RelativeLengthValue.em(-1);
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
			// **矢印の寸法はpt(版面の単位)で作る**(2026-08-02)。従来は
			// PXへ変換した値を渡していたため、pt座標へ置かれた矢印が
			// 1/0.75倍に膨らんでいた(10pt指定が13.33ptで描かれる)
			double size = Height.getLength(parent).getLength();
			style.set(CSSPosition.INFO, PositionValue.ABSOLUTE_VALUE);
			double border = BorderWidth.get(parent, Side.TOP);
			// **箱の内側へ置く**(2026-08-02)。SELECT本体は右に1em分の
			// paddingを確保しているのに、矢印は負のinsetで箱の外へ出て
			// おり、後続の内容と重なっていた(実測: 幅31ptの箱に対して
			// 矢印がx=25..41)
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
		case HTMLCodes.BUTTON:
			// <BUTTON>
			style.set(Content.INFO, WBR);
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
	 * @param fallbackContent
	 *            要素がHTML仕様のフォールバック内容(子要素)を持つか
	 *            (object/applet)。既定のbroken-image=noneでは置換ボックスを
	 *            作らず、子=フォールバックを描かせる
	 */
	private static void applyBrokenImage(CSSStyle style, String alt, boolean fallbackContent) {
		UserAgent ua = style.getUserAgent();
		OutputBrokenImage brokenimage = UAProps.OUTPUT_BROKEN_IMAGE.get(ua);
		if (brokenimage == OutputBrokenImage.ANNOTATION
				&& UAProps.OUTPUT_PDF_VERSION.get(ua) == OutputPdfVersion.V1_4X1) {
			ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_BROKEN_IMAGE.name, "annotation",
					"PDF/X-1a");
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
			// **objectとappletは置換ボックス化してはならない**(2026-08-07)。
			// これらの子要素はHTML仕様の正規のフォールバック手段で、置換
			// ボックスにすると子が丸ごと描かれない——acid2の目(失敗する
			// objectの中の入れ子objectのdata:PNG)が消える退行として発覚した
			// (2026-08-06のAltTextImage導入で混入、bisectで特定)。
			if (fallbackContent) {
				return;
			}
			// **画像を全く設定しないと置換ボックスにならず、CSSのwidth/height
			// が無視されて縮退する**(2026-08-06、woocommerce.comのdisplay:table
			// 図キャプションが単語ごとの縦長列に潰れる欠陥で発覚。詳細は
			// AltTextImageのjavadoc参照)。CSSJInternalImageは画像とテキストを
			// 同じ枠で管理する(排他)——setText()の代わりにsetImage()して
			// alt文字列はAltTextImage自身に描かせる
			CSSJInternalImage.setImage(style, new AltTextImage(ua, alt));
			return;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * ボタンの既定値は<b>html-ua.cssへ移送済み</b>(2026-08-03)。属性由来の
	 * 既定値(presentational hint)ではなくUAスタイルシートの規則になったので、
	 * 著者CSSが上書きできる——本来の強さ関係である。
	 *
	 * <p>
	 * 移送前はここに {@code height: 1em} が埋まっており、行の高さや上下
	 * パディングを持つボタンでラベルが箱の外へはみ出していた。<b>Javaの中の
	 * 既定値は誰も見ないまま残る</b>という教訓の実例。
	 */
	private static void applyButton(CSSStyle style, boolean disabled) {
		// 移送済み(html-ua.css の button / input[type=button] ほか)
	}

	private static void applyImage(CSSStyle style, String src, final String type, String alt) {
		applyImage(style, src, type, alt, false);
	}

	private static void applyImage(CSSStyle style, String src, final String type, String alt,
			boolean fallbackContent) {
		if (src != null) {
			final UserAgent ua = style.getUserAgent();
			final URI uri;
			try {
				uri = URIHelper.resolve(ua.getDocumentContext().getEncoding(), ua.getDocumentContext().getBaseURI(),
						src);
				final Source source = ua.resolve(uri);
				try {
					Source wrappedSource = new SourceWrapper(source) {
						public String getMimeType() throws IOException {
							return type == null ? super.getMimeType() : type;
						}
					};
					final Image image = ua.getImage(wrappedSource);
					if (image != null) {
						CSSJInternalImage.setImage(style, image);
						return;
					}
				} finally {
					ua.release(source);
				}
			} catch (Exception e) {
				LOG.log(Level.FINE, "Missing image", e);
				ua.message(MessageCodes.WARN_MISSING_IMAGE, src);
			}
			HTMLStyle.applyBrokenImage(style, alt, fallbackContent);
			if (fallbackContent && CSSJInternalImage.getImage(style) == null) {
				// フォールバック内容(子)を描かせる——altでContentを
				// 上書きすると子が消える
				return;
			}
		}
		if (alt != null) {
			style.set(Content.INFO, new ValueListValue(new Value[] { new StringValue(alt) }));
		}
	}

	/**
	 * 段落の前後のマージンを設定します。
	 * 
	 * @param style
	 * @param length
	 */
	private static void applyParagraphMargins(CSSStyle style, LengthValue length) {
		final CSSStyle pStyle = style.getParentStyle();
		if (pStyle == null) {
			return;
		}
		// 段落マージンはblock-start/block-end両側(2026-07-20、
		// -cssj-direction-mode廃止により論理プロパティへ一本化)
		style.set(Margin.forSide(LogicalSide.BLOCK_START.toPhysical(pStyle)), length);
		style.set(Margin.forSide(LogicalSide.BLOCK_END.toPhysical(pStyle)), length);
	}

	/**
	 * テーブルセルのレイアウトを指定します。
	 * 
	 * @param style
	 */
	private static void applyTableCell(String elem, CSSStyle style) {
		UserAgent ua = style.getUserAgent();
		CSSElement ce = style.getCSSElement();
		// display/page-break-inside/vertical-alignの既定、自分のvalign・align、
		// width/height/bgcolor/nowrapはhtml-ua.cssへ移送済み(2026-08-04)
		if (ce.atts.getValue("valign") == null) {
			// **祖先のうち最も近いvalignを継ぐ**。セレクタでは「近さ」を表せない
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
		// bgcolor/width/align/valignはhtml-ua.cssへ移送済み(2026-08-04)。
		// 表からセルへ配るcellpaddingだけが残る
		LengthValue cellpadding = CSSJHtmlCellPadding.get(style);
		style.set(Padding.TOP, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.RIGHT, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.BOTTOM, cellpadding, CSSStyle.MODE_WEAK);
		style.set(Padding.LEFT, cellpadding, CSSStyle.MODE_WEAK);
	}

	/**
	 * <b>擬似要素の入力欄の幅</b>。要素側の幅はhtml-ua.cssへ移送したが
	 * (2026-08-03)、{@code ::before}で作る入力欄にはその選択子が届かない
	 * ——擬似要素は元の要素の属性を持たないため。ここだけJavaに残る。
	 */
	/**
	 * <b>擬似要素のボタン</b>({@code <input type=file>}の「選択...」)。
	 * 要素側のボタンの既定値はhtml-ua.cssへ移送したが(2026-08-03)、
	 * {@code ::before}で作るボタンにはその選択子が届かないのでここに残る。
	 * 値は移送前のapplyButtonと同じ。
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
		// 幅は html-ua.css へ移送済み(2026-08-03、型付きattr()の実装で
		// 書けるようになった)。**既定の20exもCSS側に置く**——ここで
		// style.set すると属性由来の層(UAシートより強い)になり、CSSの
		// 規則が負けるため

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
					style.set(UnicodeBidi.INFO, UnicodeBidiValue.EMBED_VALUE);
					style.set(Direction.INFO, DirectionValue.LTR_VALUE);
				} else if (dir.equalsIgnoreCase("rtl")) {
					style.set(UnicodeBidi.INFO, UnicodeBidiValue.EMBED_VALUE);
					style.set(Direction.INFO, DirectionValue.RTL_VALUE);
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
		// ABBR/ACRONYM: 属性駆動のロジックがなく既定値も無いため、Javaケース自体が不要
		// ADDRESS: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		case HTMLCodes.APPLET: {
			// <APPLET width height hspace vspace alt align>
			HTMLStyleUtils.applyWidthHeight("APPLET", style);
			HTMLStyleUtils.applyHSpaceVSpace("APPLET", style);
			HTMLStyleUtils.applyImageAlign("APPLET", style);
			// appletの子もobjectと同じフォールバック内容
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
			// shape="default"(および shape/coords 省略)は「画像全体」を表す。
			// 形が作れなかった場合(未知のshape・座標不足)は、画像全体では
			// なく**そのareaを捨てる**——不正な入力を「全体リンク」へ昇格
			// させると意図しない広域リンクになる(2026-07-25)
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
						// coords="cx,cy,r" の外接矩形は (cx-r, cy-r, 2r, 2r)。
						// 従来は cx-r/2 で、中心が半径の1/2ずれていた
						// (2026-07-25、独立レビューで発見)
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
				// 形が作れなかった(未知のshape・座標不足)。警告は上で出済み
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
		// HTML5セクショニング/フローコンテンツ要素・メタデータ非表示要素の
		// display既定値はUAデフォルトスタイルシート(html-ua.css)に移行した
		// (2026-07-18)。BDIのみ、専用の自動方向判定(dir="auto")は先読みが
		// 要るため対象外(継承カスケードに委ねる、CSSでは表現不要)。
		// B/BASE: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		case HTMLCodes.BASEFONT: {
			// <BASEFONT size color face>
			HTMLStyleUtils.applyFontSize("BASEFONT", style);
			HTMLStyleUtils.applyFontFace(style);
			HTMLStyleUtils.applyFontColor("BASEFONT", style);
		}
			break;
		// BGSOUND: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		case HTMLCodes.BDO:
			// <BDO dir> は html-ua.css へ移送済み(2026-08-03)
			break;
		case HTMLCodes.BODY: {
			// <BODY background bgproperties link -vlink -alink>
			//
			// **余白属性(marginwidth/marginheight/topmargin/rightmargin/
			// leftmargin/bottommargin)・bgcolor・text は html-ua.css へ移送済み**
			// (2026-08-03、型付きattr())。ここに残るのは背景画像の資源解決と、
			// 子孫へ配る必要のある link 色。
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
			// <BR clear> は html-ua.css へ移送済み(2026-08-03)
			break;
		case HTMLCodes.BUTTON: {
			// <BUTTON disabled>
			// font-size: mediumはhtml-ua.cssに移行(2026-08-02)
			HTMLStyle.applyButton(style, ce.atts.getValue("disabled") != null);
		}
			break;
		case HTMLCodes.CAPTION:
			// <CAPTION align valign> は html-ua.css へ移送済み(2026-08-03)
			break;
		case HTMLCodes.CENTER: {
			// <CENTER> text-align: centerはhtml-ua.cssに移行(2026-08-02)。
			// -cssj-html-alignは内部プロパティ(CSSテキストから設定不可)のため残す
			style.set(CSSJHtmlAlign.INFO, CSSJHtmlAlignValue.CENTER_VALUE);
		}
			break;
		// CITE: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		case HTMLCodes.CODE: {
			// <CODE>
			// font-familyのCSS化はFontValueUtils.toFontFamily()のフォールバック追加と
			// 非対称になるため見送り(html-ua.cssのコメント参照)。Java側に残す。
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
		// COMMENT: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		// DD: 既定値(margin-inline-start/page-break-before)はhtml-ua.cssに移行(2026-08-02)
		// DEL: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		// DFN: 属性駆動のロジックがなく既定値も無いため、Javaケース自体が不要
		case HTMLCodes.DIR:
			// <DIR type> は html-ua.css へ移送済み(2026-08-03、先頭1文字判定は
			// 前方一致の属性セレクタで同値)
			break;
		case HTMLCodes.DIV: {
			// <DIV align>
			HTMLStyleUtils.applyBlockAlign("DIV", style);
		}
			break;
		// DL: 既定値(margin-block/page-break-before)はhtml-ua.cssに移行(2026-08-02)
		// DT/EM: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
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
			// <FIELDSET align> 静的既定値(margin-block/padding/border)は
			// html-ua.cssに移行(2026-08-02)
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
			// <H1 align> 静的既定値はhtml-ua.cssに移行(2026-08-02)
			HTMLStyleUtils.applyBlockAlign("H1", style);
		}
			break;
		case HTMLCodes.H2: {
			// <H2 align> 静的既定値はhtml-ua.cssに移行(2026-08-02)
			HTMLStyleUtils.applyBlockAlign("H2", style);
		}
			break;
		case HTMLCodes.H3: {
			// <H3 align> 静的既定値はhtml-ua.cssに移行(2026-08-02)
			HTMLStyleUtils.applyBlockAlign("H3", style);
		}
			break;
		case HTMLCodes.H4: {
			// <H4 align> 静的既定値はhtml-ua.cssに移行(2026-08-02)
			HTMLStyleUtils.applyBlockAlign("H4", style);
		}
			break;
		case HTMLCodes.H5: {
			// <H5 align> 静的既定値はhtml-ua.cssに移行(2026-08-02)
			HTMLStyleUtils.applyBlockAlign("H5", style);
		}
			break;
		case HTMLCodes.H6: {
			// <H6 align> 静的既定値はhtml-ua.cssに移行(2026-08-02)
			HTMLStyleUtils.applyBlockAlign("H6", style);
		}
			break;
		// HEAD: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		case HTMLCodes.HR:
			// <HR align color noshade size width> は html-ua.css へ移送済み
			// (2026-08-03)。片側罫線は border-block-end-* を実装して書けるように
			// なった
			break;
		case HTMLCodes.IFRAME:
			// <IFRAME width height hspace vspace align marginwidth marginheight
			// frameborder> は html-ua.css へ移送済み(2026-08-03)
			break;
		case HTMLCodes.IMG: {
			// <IMG src alt border width height hspace vspace align usemap>
			HTMLStyleUtils.applyWidthHeight("IMG", style);
			HTMLStyleUtils.applyHSpaceVSpace("IMG", style);
			HTMLStyleUtils.applyImageAlign("IMG", style);
			String src = ce.atts.getValue("src");
			String alt = ce.atts.getValue("alt");
			HTMLStyle.applyImage(style, src, null, alt);
			HTMLStyleUtils.applyImageBorder("IMG", style);
		}
			break;
		case HTMLCodes.INPUT: {
			// <INPUT type disabled size src border width height align>
			//
			// **font-size・hidden・align・一行入力欄の見た目は html-ua.css へ
			// 移送済み**(2026-08-03)。ここに残るのは資源解決(type=image)と、
			// 内部で描くチェックボックス・ラジオボタンの絵。
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
			// font-familyのCSS化はFontValueUtils.toFontFamily()のフォールバック追加と
			// 非対称になるため見送り(html-ua.cssのコメント参照)。Java側に残す。
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.LEGEND: {
			// <LEGEND> position/margin-topはhtml-ua.cssに移行(2026-08-02)。
			// 背景色の祖先継承はCSSで表現できないため残す
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
			// <LI type> は html-ua.css へ移送済み(2026-08-03、先頭1文字判定は
			// 前方一致の属性セレクタで同値)
			break;
		case HTMLCodes.LISTING: {
			// <LISTING> white-space/text-alignはhtml-ua.cssに移行(2026-08-02)。
			// font-familyはtoFontFamily()の非対称性のためJava側に残す
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
		// MENU: 既定値(margin/page-break-before)はhtml-ua.cssに移行(2026-08-02)
		// NOBR: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		// NOEMBED/NOFRAMES/NOLAYER/NOSCRIPT: 属性駆動のロジックがなく既定値も無いため、Javaケース自体が不要
		case HTMLCodes.OBJECT: {
			// <OBJECT border width height hspace vspace alt align usemap>
			HTMLStyleUtils.applyWidthHeight("OBJECT", style);
			HTMLStyleUtils.applyHSpaceVSpace("OBJECT", style);
			HTMLStyleUtils.applyImageAlign("OBJECT", style);
			String src = ce.atts.getValue("data");
			String type = ce.atts.getValue("type");
			String alt = ce.atts.getValue("alt");
			// objectの子はHTML仕様のフォールバック内容(applyBrokenImage参照)
			HTMLStyle.applyImage(style, src, type, alt, true);
			HTMLStyleUtils.applyImageBorder("OBJECT", style);
		}
			break;
		case HTMLCodes.OL:
			// <OL type> は html-ua.css へ移送済み(2026-08-03、先頭1文字判定は
			// 前方一致の属性セレクタで同値)
			break;
		case HTMLCodes.P: {
			// <P align> margin-blockはhtml-ua.cssに移行(2026-08-02)
			HTMLStyleUtils.applyBlockAlign("P", style);
		}
			break;
		case HTMLCodes.PLAINTEXT: {
			// <PLAINTEXT> white-space/text-alignはhtml-ua.cssに移行(2026-08-02)。
			// font-familyはtoFontFamily()の非対称性のためJava側に残す
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.PRE: {
			// <PRE cols width wrap> は html-ua.css へ移送済み(2026-08-03)。
			// **font-family だけ残る**——FontValueUtils.toFontFamily() が
			// 既定ファミリを暗黙に足すため、CSSで monospace と書くと値が
			// 変わってしまう(既知の非対称性)
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.RUBY: {
			// <RUBY>
			style.set(CSSJRuby.INFO, CSSJRubyValue.RUBY_VALUE);
		}
			break;
		case HTMLCodes.RB: {
			// <RB> XHTML5では非標準
			style.set(CSSJRuby.INFO, CSSJRubyValue.RB_VALUE);
		}
			break;
		case HTMLCodes.RT: {
			// <RT>
			style.set(CSSJRuby.INFO, CSSJRubyValue.RT_VALUE);
		}
			break;
		// S/SCRIPT: 既定値はUAデフォルトスタイルシート(html-ua.css)に移行(2026-07-19)
		case HTMLCodes.SAMP: {
			// <SAMP>
			// font-familyのCSS化はFontValueUtils.toFontFamily()のフォールバック追加と
			// 非対称になるため見送り(html-ua.cssのコメント参照)。Java側に残す。
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.SELECT: {
			// <SELECT size> display/position/overflow/line-height/background/
			// border/white-spaceの既定はhtml-ua.cssに移行(2026-08-02)。
			// size(高さ)とdisabledの配色もhtml-ua.cssへ移送済み(2026-08-04)。
			// **パディングだけ残る**——右のパディングは矢印の実寸に合わせた
			// 装置単位の値で、CSSの長さでは書けない
			LengthValue thin = ua.getBorderWidth(BorderWidthKeyword.THIN);
			style.set(Padding.TOP, thin, CSSStyle.MODE_IMPORTANT);
			// **矢印の実際の描画幅ぶん空ける**(2026-08-02)。SelectImageは
			// 16単位固定の座標で描かれる(幅は定数16)ため、1emだけ空けても
			// 小さいフォントでは選択中の文字と重なっていた。描画側を比例化
			// するのが本筋だが、視覚回帰の危険があるので確保量を実寸へ合わせる
			style.set(Padding.RIGHT, AbsoluteLengthValue.create(ua, 16),
					CSSStyle.MODE_IMPORTANT);
			style.set(Padding.BOTTOM, thin, CSSStyle.MODE_IMPORTANT);
			style.set(Padding.LEFT, thin, CSSStyle.MODE_IMPORTANT);
		}
			break;
		// SMALL/SPAN/STRIKE/STRONG/STYLE/SUB/SUP: 既定値はUAデフォルトスタイルシート
		// (html-ua.css)に移行(2026-07-19)。SPANは属性駆動のロジックがなく既定値も無いため
		// Javaケース自体が不要だった
		case HTMLCodes.TABLE: {
			// <TABLE background> のみ残る。
			//
			// **cellspacing・cellpadding・border・bordercolor・frame・rules・
			// width・height・hspace・vspace・bgcolor・align は html-ua.css へ
			// 移送済み**(2026-08-03)。値をセルへ配る内部プロパティ
			// (-cssj-html-table-border / -cssj-html-cell-padding)はCSSから
			// 書けるようにし、attr()を計算値の段階で解くようにした。
			//
			// font-size は互換モードでのみ設定するのでここに残る。
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
			// font-weight/text-alignはhtml-ua.cssに移行(2026-08-02)
			HTMLStyle.applyTableCell("TH", style);
		}
			break;
		case HTMLCodes.TR: {
			// <TR bordercolor background bgcolor align valign height
			// -charoff,-bordercolordark,-bordercolorlight>
			// align/bgcolor/height/rules=rowsはhtml-ua.cssへ移送済み
			// (2026-08-04)。backgroundだけは資源の解決が要るので残る
			HTMLStyleUtils.applyBackground("TR", style);
		}
			break;
		case HTMLCodes.TT: {
			// <TT>
			// font-familyのCSS化はFontValueUtils.toFontFamily()のフォールバック追加と
			// 非対称になるため見送り(html-ua.cssのコメント参照)。Java側に残す。
			style.set(CSSFontFamily.INFO, FontFamilyValue.MONOSPACE);
		}
			break;
		case HTMLCodes.TEXTAREA:
			// <TEXTAREA cols rows disabled wrap> は html-ua.css へ移送済み
			// (2026-08-03)
			break;
		case HTMLCodes.UL:
			// <UL type> は html-ua.css へ移送済み(2026-08-03、先頭1文字判定は
			// 前方一致の属性セレクタで同値)
			break;
		case HTMLCodes.VIDEO: {
			// <VIDEO width height poster>(display:inline-blockはhtml-ua.cssへ移行)
			HTMLStyleUtils.applyWidthHeight("VIDEO", style);
			String poster = ce.atts.getValue("poster");
			if (poster != null) {
				HTMLStyle.applyImage(style, poster, null, "");
			}
		}
			break;
		// WBR: 属性駆動のロジックがなく既定値も無いため、Javaケース自体が不要
		case HTMLCodes.XMP: {
			// <XMP>
			// display/white-space/text-alignはUAデフォルトスタイルシート(html-ua.css)に
			// 移行済み(2026-07-19)。font-familyのみFontValueUtils.toFontFamily()の
			// フォールバック追加との非対称性を避けてJava側に残す。
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

		// @popover(Popover API、2026-08-07)。UA既定は
		// `[popover]:not(:popover-open){display:none}`——:popover-openは
		// JSでshowPopover()が呼ばれて初めて成立する状態で、静的なHTML
		// (このエンジンの入力)には反映されないため、popover属性がある
		// 要素は常にdisplay:noneが正しい既定値になる(実地: vercel.comの
		// ロゴクリックメニュー・製品メガメニューがpage内容に重なって
		// 描かれていた——popover属性の既定非表示が未実装だった)。
		{
			String popover = ce.atts.getValue("popover");
			if (popover != null) {
				style.set(Display.INFO, DisplayValue.NONE_VALUE);
			}
		}
	}
}
