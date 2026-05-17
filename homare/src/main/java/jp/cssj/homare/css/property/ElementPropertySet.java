package jp.cssj.homare.css.property;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.homare.impl.css.property.BackgroundAttachment;
import jp.cssj.homare.impl.css.property.BackgroundColor;
import jp.cssj.homare.impl.css.property.BackgroundImage;
import jp.cssj.homare.impl.css.property.BackgroundPosition;
import jp.cssj.homare.impl.css.property.BackgroundRepeat;
import jp.cssj.homare.impl.css.property.BorderBottomColor;
import jp.cssj.homare.impl.css.property.BorderBottomStyle;
import jp.cssj.homare.impl.css.property.BorderBottomWidth;
import jp.cssj.homare.impl.css.property.BorderCollapse;
import jp.cssj.homare.impl.css.property.BorderLeftColor;
import jp.cssj.homare.impl.css.property.BorderLeftStyle;
import jp.cssj.homare.impl.css.property.BorderLeftWidth;
import jp.cssj.homare.impl.css.property.BorderRightColor;
import jp.cssj.homare.impl.css.property.BorderRightStyle;
import jp.cssj.homare.impl.css.property.BorderRightWidth;
import jp.cssj.homare.impl.css.property.BorderSpacing;
import jp.cssj.homare.impl.css.property.BorderTopColor;
import jp.cssj.homare.impl.css.property.BorderTopStyle;
import jp.cssj.homare.impl.css.property.BorderTopWidth;
import jp.cssj.homare.impl.css.property.Bottom;
import jp.cssj.homare.impl.css.property.CSSColor;
import jp.cssj.homare.impl.css.property.CSSFloat;
import jp.cssj.homare.impl.css.property.CSSFontFamily;
import jp.cssj.homare.impl.css.property.CSSFontStyle;
import jp.cssj.homare.impl.css.property.CSSPosition;
import jp.cssj.homare.impl.css.property.CaptionSide;
import jp.cssj.homare.impl.css.property.Clear;
import jp.cssj.homare.impl.css.property.Clip;
import jp.cssj.homare.impl.css.property.Content;
import jp.cssj.homare.impl.css.property.CounterIncrement;
import jp.cssj.homare.impl.css.property.CounterReset;
import jp.cssj.homare.impl.css.property.Direction;
import jp.cssj.homare.impl.css.property.Display;
import jp.cssj.homare.impl.css.property.EmptyCells;
import jp.cssj.homare.impl.css.property.FontSize;
import jp.cssj.homare.impl.css.property.FontVariant;
import jp.cssj.homare.impl.css.property.FontWeight;
import jp.cssj.homare.impl.css.property.Height;
import jp.cssj.homare.impl.css.property.Left;
import jp.cssj.homare.impl.css.property.LetterSpacing;
import jp.cssj.homare.impl.css.property.LineHeight;
import jp.cssj.homare.impl.css.property.ListStyleImage;
import jp.cssj.homare.impl.css.property.ListStylePosition;
import jp.cssj.homare.impl.css.property.ListStyleType;
import jp.cssj.homare.impl.css.property.MarginBottom;
import jp.cssj.homare.impl.css.property.MarginLeft;
import jp.cssj.homare.impl.css.property.MarginRight;
import jp.cssj.homare.impl.css.property.MarginTop;
import jp.cssj.homare.impl.css.property.MaxHeight;
import jp.cssj.homare.impl.css.property.MaxWidth;
import jp.cssj.homare.impl.css.property.MinHeight;
import jp.cssj.homare.impl.css.property.MinWidth;
import jp.cssj.homare.impl.css.property.Orphans;
import jp.cssj.homare.impl.css.property.Overflow;
import jp.cssj.homare.impl.css.property.PaddingBottom;
import jp.cssj.homare.impl.css.property.PaddingLeft;
import jp.cssj.homare.impl.css.property.PaddingRight;
import jp.cssj.homare.impl.css.property.PaddingTop;
import jp.cssj.homare.impl.css.property.PageBreakAfter;
import jp.cssj.homare.impl.css.property.PageBreakBefore;
import jp.cssj.homare.impl.css.property.PageBreakInside;
import jp.cssj.homare.impl.css.property.Quotes;
import jp.cssj.homare.impl.css.property.Right;
import jp.cssj.homare.impl.css.property.TableLayout;
import jp.cssj.homare.impl.css.property.TextAlign;
import jp.cssj.homare.impl.css.property.TextDecoration;
import jp.cssj.homare.impl.css.property.TextIndent;
import jp.cssj.homare.impl.css.property.TextTransform;
import jp.cssj.homare.impl.css.property.Top;
import jp.cssj.homare.impl.css.property.UnicodeBidi;
import jp.cssj.homare.impl.css.property.VerticalAlign;
import jp.cssj.homare.impl.css.property.Visibility;
import jp.cssj.homare.impl.css.property.WhiteSpace;
import jp.cssj.homare.impl.css.property.Widows;
import jp.cssj.homare.impl.css.property.Width;
import jp.cssj.homare.impl.css.property.WordSpacing;
import jp.cssj.homare.impl.css.property.ZIndex;
import jp.cssj.homare.impl.css.property.css3.BackgroundClip;
import jp.cssj.homare.impl.css.property.css3.BackgroundSize;
import jp.cssj.homare.impl.css.property.css3.BlockFlow;
import jp.cssj.homare.impl.css.property.css3.BorderBottomLeftRadius;
import jp.cssj.homare.impl.css.property.css3.BorderBottomRightRadius;
import jp.cssj.homare.impl.css.property.css3.BorderRadiusShorthand;
import jp.cssj.homare.impl.css.property.css3.BorderTopLeftRadius;
import jp.cssj.homare.impl.css.property.css3.BorderTopRightRadius;
import jp.cssj.homare.impl.css.property.css3.BoxSizing;
import jp.cssj.homare.impl.css.property.css3.CSSUnicodeRange;
import jp.cssj.homare.impl.css.property.css3.ColumnCount;
import jp.cssj.homare.impl.css.property.css3.ColumnFill;
import jp.cssj.homare.impl.css.property.css3.ColumnGap;
import jp.cssj.homare.impl.css.property.css3.ColumnRuleColor;
import jp.cssj.homare.impl.css.property.css3.ColumnRuleShorthand;
import jp.cssj.homare.impl.css.property.css3.ColumnRuleStyle;
import jp.cssj.homare.impl.css.property.css3.ColumnRuleWidth;
import jp.cssj.homare.impl.css.property.css3.ColumnSpan;
import jp.cssj.homare.impl.css.property.css3.ColumnWidth;
import jp.cssj.homare.impl.css.property.css3.ColumnsShorthand;
import jp.cssj.homare.impl.css.property.css3.Opacity;
import jp.cssj.homare.impl.css.property.css3.Src;
import jp.cssj.homare.impl.css.property.css3.TextAlignLast;
import jp.cssj.homare.impl.css.property.css3.TextCombineShorthand;
import jp.cssj.homare.impl.css.property.css3.TextEmphasisColor;
import jp.cssj.homare.impl.css.property.css3.TextEmphasisShorthand;
import jp.cssj.homare.impl.css.property.css3.TextEmphasisStyle;
import jp.cssj.homare.impl.css.property.css3.TextFillColor;
import jp.cssj.homare.impl.css.property.css3.TextShadow;
import jp.cssj.homare.impl.css.property.css3.TextStrokeColor;
import jp.cssj.homare.impl.css.property.css3.TextStrokeShorthand;
import jp.cssj.homare.impl.css.property.css3.TextStrokeWidth;
import jp.cssj.homare.impl.css.property.css3.Transform;
import jp.cssj.homare.impl.css.property.css3.TransformOrigin;
import jp.cssj.homare.impl.css.property.css3.WordBreak;
import jp.cssj.homare.impl.css.property.css3.WordWrap;
import jp.cssj.homare.impl.css.property.ext.CSSJBreakCharacters;
import jp.cssj.homare.impl.css.property.ext.CSSJDirectionMode;
import jp.cssj.homare.impl.css.property.ext.CSSJFontPolicy;
import jp.cssj.homare.impl.css.property.ext.CSSJNoBreakCharacters;
import jp.cssj.homare.impl.css.property.ext.CSSJPageContent;
import jp.cssj.homare.impl.css.property.ext.CSSJPageContentClear;
import jp.cssj.homare.impl.css.property.ext.CSSJRegeneratable;
import jp.cssj.homare.impl.css.property.ext.CSSJRuby;
import jp.cssj.homare.impl.css.property.internal.CSSJAutoWidth;
import jp.cssj.homare.impl.css.property.internal.CSSJHtmlAlign;
import jp.cssj.homare.impl.css.property.internal.CSSJHtmlCellPadding;
import jp.cssj.homare.impl.css.property.internal.CSSJHtmlTableBorder;
import jp.cssj.homare.impl.css.property.internal.CSSJInternalImage;
import jp.cssj.homare.impl.css.property.internal.CSSJInternalLink;
import jp.cssj.homare.impl.css.property.shorthand.BackgroundShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderBottomShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderColorShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderLeftShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderRightShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderStyleShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderTopShorthand;
import jp.cssj.homare.impl.css.property.shorthand.BorderWidthShorthand;
import jp.cssj.homare.impl.css.property.shorthand.FontShorthand;
import jp.cssj.homare.impl.css.property.shorthand.ListStyleShorthand;
import jp.cssj.homare.impl.css.property.shorthand.MarginShorthand;
import jp.cssj.homare.impl.css.property.shorthand.PaddingShorthand;
import jp.cssj.homare.impl.css.property.shorthand.WritingModeShorthand;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ElementPropertySet.java 1635 2023-04-03 08:16:41Z miyabe $
 */
public final class ElementPropertySet extends PropertySet {
	private Map<String, PropertyInfo> nameToInfo;
	private static Map<PrimitivePropertyInfo, Short> CODES;
	private static short CODE_SIZE = 0;

	public static int getCodeSize() {
		return CODE_SIZE;
	}

	public static short getCode(PrimitivePropertyInfo info) {
		Short s = (Short) CODES.get(info);
		if (s == null) {
			return -1;
		}
		return s.shortValue();
	}

	private void put(PrimitivePropertyInfo info) {
		this.nameToInfo.put(info.getName(), info);
		CODES.put(info, NumberUtils.shortValue(CODE_SIZE++));
	}

	private void putShorthand(ShorthandPropertyInfo info) {
		this.nameToInfo.put(info.getName(), info);
	}

	{
		this.nameToInfo = new HashMap<String, PropertyInfo>();
		CODES = new HashMap<PrimitivePropertyInfo, Short>();
		this.put(Display.INFO);
		this.put(CSSPosition.INFO);
		this.put(CSSFloat.INFO);
		this.put(Clear.INFO);
		this.put(CSSColor.INFO);
		this.put(Top.INFO);
		this.put(Left.INFO);
		this.put(Bottom.INFO);
		this.put(Right.INFO);
		this.put(Width.INFO);
		this.put(Height.INFO);
		this.put(LineHeight.INFO);
		this.put(MinWidth.INFO);
		this.put(MaxWidth.INFO);
		this.put(MinHeight.INFO);
		this.put(MaxHeight.INFO);
		this.put(BorderTopColor.INFO);
		this.put(BorderLeftColor.INFO);
		this.put(BorderRightColor.INFO);
		this.put(BorderBottomColor.INFO);
		this.put(BorderTopStyle.INFO);
		this.put(BorderLeftStyle.INFO);
		this.put(BorderRightStyle.INFO);
		this.put(BorderBottomStyle.INFO);
		this.put(BorderTopWidth.INFO);
		this.put(BorderLeftWidth.INFO);
		this.put(BorderRightWidth.INFO);
		this.put(BorderBottomWidth.INFO);
		this.put(MarginTop.INFO);
		this.put(MarginLeft.INFO);
		this.put(MarginRight.INFO);
		this.put(MarginBottom.INFO);
		this.put(PaddingTop.INFO);
		this.put(PaddingLeft.INFO);
		this.put(PaddingRight.INFO);
		this.put(PaddingBottom.INFO);
		this.put(VerticalAlign.INFO);
		this.put(ZIndex.INFO);
		this.put(Visibility.INFO);
		this.put(Overflow.INFO);
		this.put(Clip.INFO);
		this.put(CSSFontFamily.INFO);
		this.put(CSSFontStyle.INFO);
		this.put(FontVariant.INFO);
		this.put(FontWeight.INFO);
		this.put(FontSize.INFO);
		this.put(TextIndent.INFO);
		this.put(TextAlign.INFO);
		this.put(TextDecoration.INFO);
		this.put(LetterSpacing.INFO);
		this.put(WordSpacing.INFO);
		this.put(TextTransform.INFO);
		this.put(UnicodeBidi.INFO);
		this.put(WhiteSpace.INFO);
		this.put(Direction.INFO);
		this.put(BackgroundColor.INFO);
		this.put(BackgroundImage.INFO);
		this.put(BackgroundRepeat.INFO);
		this.put(BackgroundAttachment.INFO);
		this.put(BackgroundPosition.INFO_X);
		this.put(BackgroundPosition.INFO_Y);
		this.put(Content.INFO);
		this.put(Quotes.INFO);
		this.put(CounterReset.INFO);
		this.put(CounterIncrement.INFO);
		this.put(ListStyleType.INFO);
		this.put(ListStylePosition.INFO);
		this.put(ListStyleImage.INFO);
		this.put(CaptionSide.INFO);
		this.put(TableLayout.INFO);
		this.put(BorderCollapse.INFO);
		this.put(EmptyCells.INFO);
		this.put(BorderSpacing.INFO_H);
		this.put(BorderSpacing.INFO_V);
		this.put(PageBreakBefore.INFO);
		this.put(PageBreakAfter.INFO);
		this.put(PageBreakInside.INFO);
		this.put(Orphans.INFO);
		this.put(Widows.INFO);

		// font-face
		this.put(Src.INFO);
		this.put(CSSUnicodeRange.INFO);

		// shorthand
		this.putShorthand(BorderShorthand.INFO);
		this.putShorthand(BorderTopShorthand.INFO);
		this.putShorthand(BorderLeftShorthand.INFO);
		this.putShorthand(BorderRightShorthand.INFO);
		this.putShorthand(BorderBottomShorthand.INFO);
		this.putShorthand(BorderColorShorthand.INFO);
		this.putShorthand(BorderStyleShorthand.INFO);
		this.putShorthand(BorderWidthShorthand.INFO);
		this.putShorthand(MarginShorthand.INFO);
		this.putShorthand(PaddingShorthand.INFO);
		this.putShorthand(FontShorthand.INFO);
		this.putShorthand(BackgroundShorthand.INFO);
		this.putShorthand(ListStyleShorthand.INFO);

		// 互換性
		this.nameToInfo.put("windows", Widows.INFO);

		// CSS3
		this.put(BackgroundClip.INFO);
		this.put(BackgroundSize.INFO_WIDTH);
		this.put(BackgroundSize.INFO_HEIGHT);
		this.put(BlockFlow.INFO);
		this.put(BoxSizing.INFO);
		this.put(TextAlignLast.INFO);
		this.putShorthand(WritingModeShorthand.INFO);
		this.put(WordWrap.INFO);
		this.put(WordBreak.INFO);
		this.put(ColumnCount.INFO);
		this.put(ColumnWidth.INFO);
		this.put(ColumnGap.INFO);
		this.put(ColumnRuleStyle.INFO);
		this.put(ColumnRuleColor.INFO);
		this.put(ColumnRuleWidth.INFO);
		this.put(ColumnFill.INFO);
		this.put(ColumnSpan.INFO);
		this.putShorthand(ColumnRuleShorthand.INFO);
		this.putShorthand(ColumnsShorthand.INFO);
		this.put(TextEmphasisStyle.INFO);
		this.put(TextEmphasisColor.INFO);
		this.put(Opacity.INFO);
		this.put(BorderBottomRightRadius.INFO);
		this.put(BorderTopLeftRadius.INFO);
		this.put(BorderTopRightRadius.INFO);
		this.put(BorderBottomLeftRadius.INFO);
		this.putShorthand(BorderRadiusShorthand.INFO);
		this.putShorthand(TextEmphasisShorthand.INFO);
		this.putShorthand(TextCombineShorthand.INFO);
		this.put(Transform.INFO);
		this.put(TransformOrigin.INFO_X);
		this.put(TransformOrigin.INFO_Y);
		this.put(TextStrokeWidth.INFO);
		this.put(TextStrokeColor.INFO);
		this.put(TextFillColor.INFO);
		this.putShorthand(TextStrokeShorthand.INFO);
		this.put(TextShadow.INFO);

		this.nameToInfo.put("-webkit-transform", Transform.INFO);
		this.nameToInfo.put("-webkit-transform-origin", TransformOrigin.INFO_X);
		this.nameToInfo.put("-moz-transform", Transform.INFO);
		this.nameToInfo.put("-moz-transform-origin", TransformOrigin.INFO_X);

		this.nameToInfo.put("-webkit-text-stroke-width", TextStrokeWidth.INFO);
		this.nameToInfo.put("-webkit-text-stroke-color", TextStrokeColor.INFO);
		this.nameToInfo.put("-webkit-text-fill-color", TextFillColor.INFO);
		this.nameToInfo.put("-webkit-text-stroke", TextStrokeShorthand.INFO);
		this.nameToInfo.put("-webkit-background-clip", BackgroundClip.INFO);

		this.nameToInfo.put("oeb-column-number", ColumnCount.INFO);
		this.nameToInfo.put("-epub-writing-mode", WritingModeShorthand.INFO);
		this.nameToInfo.put("-epub-text-align-last", TextAlignLast.INFO);
		this.nameToInfo.put("-epub-text-emphasis-style", TextEmphasisStyle.INFO);
		this.nameToInfo.put("-epub-text-emphasis-color", TextEmphasisColor.INFO);
		this.nameToInfo.put("-epub-text-emphasis", TextEmphasisShorthand.INFO);
		this.nameToInfo.put("-epub-text-combine", TextCombineShorthand.INFO);
		this.nameToInfo.put("-epub-column-count", ColumnCount.INFO);
		this.nameToInfo.put("-epub-column-width", ColumnWidth.INFO);
		this.nameToInfo.put("-epub-column-gap", ColumnGap.INFO);
		this.nameToInfo.put("-epub-column-rule-style", ColumnRuleStyle.INFO);
		this.nameToInfo.put("-epub-column-rule-color", ColumnRuleColor.INFO);
		this.nameToInfo.put("-epub-column-rule-width", ColumnRuleWidth.INFO);
		this.nameToInfo.put("-epub-column-fill", ColumnFill.INFO);
		this.nameToInfo.put("-epub-column-span", ColumnSpan.INFO);
		this.nameToInfo.put("-epub-column-rule", ColumnRuleShorthand.INFO);
		this.nameToInfo.put("-epub-columns", ColumnsShorthand.INFO);

		this.nameToInfo.put("transform", Transform.INFO);
		this.nameToInfo.put("transform-origin", TransformOrigin.INFO_X);
		this.nameToInfo.put("background-size", BackgroundSize.INFO_WIDTH);
		this.nameToInfo.put("block-flow", BlockFlow.INFO);
		this.nameToInfo.put("text-align-last", TextAlignLast.INFO);
		this.nameToInfo.put("writing-mode", WritingModeShorthand.INFO);
		this.nameToInfo.put("word-wrap", WordWrap.INFO);
		this.nameToInfo.put("column-count", ColumnCount.INFO);
		this.nameToInfo.put("column-width", ColumnWidth.INFO);
		this.nameToInfo.put("column-gap", ColumnGap.INFO);
		this.nameToInfo.put("column-rule-style", ColumnRuleStyle.INFO);
		this.nameToInfo.put("column-rule-color", ColumnRuleColor.INFO);
		this.nameToInfo.put("column-rule-width", ColumnRuleWidth.INFO);
		this.nameToInfo.put("column-fill", ColumnFill.INFO);
		this.nameToInfo.put("column-span", ColumnSpan.INFO);
		this.nameToInfo.put("column-rule", ColumnRuleShorthand.INFO);
		this.nameToInfo.put("columns", ColumnsShorthand.INFO);

		// Extensions
		this.put(CSSJFontPolicy.INFO);
		this.put(CSSJPageContent.INFO_NAME);
		this.put(CSSJPageContent.INFO_PAGE);
		this.put(CSSJRegeneratable.INFO);
		this.put(CSSJPageContentClear.INFO);
		this.put(CSSJDirectionMode.INFO);
		this.put(CSSJRuby.INFO);
		this.put(CSSJBreakCharacters.INFO);
		this.put(CSSJNoBreakCharacters.INFO);

		this.put(CSSJAutoWidth.INFO);
		this.put(CSSJHtmlAlign.INFO);
		this.put(CSSJHtmlCellPadding.INFO);
		this.put(CSSJHtmlTableBorder.INFO);
		this.put(CSSJInternalImage.INFO);
		this.put(CSSJInternalLink.INFO);

		this.nameToInfo = Collections.unmodifiableMap(this.nameToInfo);
		CODES = Collections.unmodifiableMap(CODES);
	}

	private static final PropertySet INSTANCE = new ElementPropertySet();

	private ElementPropertySet() {
		// ignore
	}

	protected PropertyInfo getPropertyParser(String name) {
		PropertyInfo info = (PropertyInfo) this.nameToInfo.get(name);
		return info;
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}