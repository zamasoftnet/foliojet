package net.zamasoft.foliojet.css.property;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.impl.css.property.BackgroundAttachment;
import net.zamasoft.foliojet.impl.css.property.BackgroundColor;
import net.zamasoft.foliojet.impl.css.property.BackgroundImage;
import net.zamasoft.foliojet.impl.css.property.BackgroundPosition;
import net.zamasoft.foliojet.impl.css.property.BackgroundRepeat;
import net.zamasoft.foliojet.impl.css.property.BorderBottomColor;
import net.zamasoft.foliojet.impl.css.property.BorderBottomStyle;
import net.zamasoft.foliojet.impl.css.property.BorderBottomWidth;
import net.zamasoft.foliojet.impl.css.property.BorderCollapse;
import net.zamasoft.foliojet.impl.css.property.BorderLeftColor;
import net.zamasoft.foliojet.impl.css.property.BorderLeftStyle;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.impl.css.property.BorderRightColor;
import net.zamasoft.foliojet.impl.css.property.BorderRightStyle;
import net.zamasoft.foliojet.impl.css.property.BorderRightWidth;
import net.zamasoft.foliojet.impl.css.property.BorderSpacing;
import net.zamasoft.foliojet.impl.css.property.BorderTopColor;
import net.zamasoft.foliojet.impl.css.property.BorderTopStyle;
import net.zamasoft.foliojet.impl.css.property.BorderTopWidth;
import net.zamasoft.foliojet.impl.css.property.Bottom;
import net.zamasoft.foliojet.impl.css.property.CSSColor;
import net.zamasoft.foliojet.impl.css.property.CSSFloat;
import net.zamasoft.foliojet.impl.css.property.CSSFontFamily;
import net.zamasoft.foliojet.impl.css.property.CSSFontStyle;
import net.zamasoft.foliojet.impl.css.property.CSSPosition;
import net.zamasoft.foliojet.impl.css.property.CaptionSide;
import net.zamasoft.foliojet.impl.css.property.Clear;
import net.zamasoft.foliojet.impl.css.property.Clip;
import net.zamasoft.foliojet.impl.css.property.Content;
import net.zamasoft.foliojet.impl.css.property.CounterIncrement;
import net.zamasoft.foliojet.impl.css.property.CounterReset;
import net.zamasoft.foliojet.impl.css.property.Direction;
import net.zamasoft.foliojet.impl.css.property.Display;
import net.zamasoft.foliojet.impl.css.property.EmptyCells;
import net.zamasoft.foliojet.impl.css.property.FontSize;
import net.zamasoft.foliojet.impl.css.property.FontVariant;
import net.zamasoft.foliojet.impl.css.property.FontWeight;
import net.zamasoft.foliojet.impl.css.property.Height;
import net.zamasoft.foliojet.impl.css.property.Left;
import net.zamasoft.foliojet.impl.css.property.LetterSpacing;
import net.zamasoft.foliojet.impl.css.property.LineHeight;
import net.zamasoft.foliojet.impl.css.property.ListStyleImage;
import net.zamasoft.foliojet.impl.css.property.ListStylePosition;
import net.zamasoft.foliojet.impl.css.property.ListStyleType;
import net.zamasoft.foliojet.impl.css.property.MarginBottom;
import net.zamasoft.foliojet.impl.css.property.MarginLeft;
import net.zamasoft.foliojet.impl.css.property.MarginRight;
import net.zamasoft.foliojet.impl.css.property.MarginTop;
import net.zamasoft.foliojet.impl.css.property.MaxHeight;
import net.zamasoft.foliojet.impl.css.property.MaxWidth;
import net.zamasoft.foliojet.impl.css.property.MinHeight;
import net.zamasoft.foliojet.impl.css.property.MinWidth;
import net.zamasoft.foliojet.impl.css.property.Orphans;
import net.zamasoft.foliojet.impl.css.property.Overflow;
import net.zamasoft.foliojet.impl.css.property.PaddingBottom;
import net.zamasoft.foliojet.impl.css.property.PaddingLeft;
import net.zamasoft.foliojet.impl.css.property.PaddingRight;
import net.zamasoft.foliojet.impl.css.property.PaddingTop;
import net.zamasoft.foliojet.impl.css.property.PageBreakAfter;
import net.zamasoft.foliojet.impl.css.property.PageBreakBefore;
import net.zamasoft.foliojet.impl.css.property.PageBreakInside;
import net.zamasoft.foliojet.impl.css.property.Quotes;
import net.zamasoft.foliojet.impl.css.property.Right;
import net.zamasoft.foliojet.impl.css.property.TableLayout;
import net.zamasoft.foliojet.impl.css.property.TextAlign;
import net.zamasoft.foliojet.impl.css.property.TextDecoration;
import net.zamasoft.foliojet.impl.css.property.TextIndent;
import net.zamasoft.foliojet.impl.css.property.TextTransform;
import net.zamasoft.foliojet.impl.css.property.Top;
import net.zamasoft.foliojet.impl.css.property.UnicodeBidi;
import net.zamasoft.foliojet.impl.css.property.VerticalAlign;
import net.zamasoft.foliojet.impl.css.property.Visibility;
import net.zamasoft.foliojet.impl.css.property.WhiteSpace;
import net.zamasoft.foliojet.impl.css.property.Widows;
import net.zamasoft.foliojet.impl.css.property.Width;
import net.zamasoft.foliojet.impl.css.property.WordSpacing;
import net.zamasoft.foliojet.impl.css.property.ZIndex;
import net.zamasoft.foliojet.impl.css.property.css3.BackgroundClip;
import net.zamasoft.foliojet.impl.css.property.css3.BackgroundSize;
import net.zamasoft.foliojet.impl.css.property.css3.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.css3.BorderBottomLeftRadius;
import net.zamasoft.foliojet.impl.css.property.css3.BorderBottomRightRadius;
import net.zamasoft.foliojet.impl.css.property.css3.BorderRadiusShorthand;
import net.zamasoft.foliojet.impl.css.property.css3.BorderTopLeftRadius;
import net.zamasoft.foliojet.impl.css.property.css3.BorderTopRightRadius;
import net.zamasoft.foliojet.impl.css.property.css3.BoxSizing;
import net.zamasoft.foliojet.impl.css.property.css3.CSSUnicodeRange;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnCount;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnFill;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnGap;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnRuleColor;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnRuleShorthand;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnRuleStyle;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnRuleWidth;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnSpan;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnWidth;
import net.zamasoft.foliojet.impl.css.property.css3.ColumnsShorthand;
import net.zamasoft.foliojet.impl.css.property.css3.Opacity;
import net.zamasoft.foliojet.impl.css.property.css3.Src;
import net.zamasoft.foliojet.impl.css.property.css3.TextAlignLast;
import net.zamasoft.foliojet.impl.css.property.css3.TextCombineShorthand;
import net.zamasoft.foliojet.impl.css.property.css3.TextEmphasisColor;
import net.zamasoft.foliojet.impl.css.property.css3.TextEmphasisShorthand;
import net.zamasoft.foliojet.impl.css.property.css3.TextEmphasisStyle;
import net.zamasoft.foliojet.impl.css.property.css3.TextFillColor;
import net.zamasoft.foliojet.impl.css.property.css3.TextShadow;
import net.zamasoft.foliojet.impl.css.property.css3.TextStrokeColor;
import net.zamasoft.foliojet.impl.css.property.css3.TextStrokeShorthand;
import net.zamasoft.foliojet.impl.css.property.css3.TextStrokeWidth;
import net.zamasoft.foliojet.impl.css.property.css3.Transform;
import net.zamasoft.foliojet.impl.css.property.css3.TransformOrigin;
import net.zamasoft.foliojet.impl.css.property.css3.WordBreak;
import net.zamasoft.foliojet.impl.css.property.css3.WordWrap;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJBreakCharacters;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJFontPolicy;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJNoBreakCharacters;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJPageContent;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJPageContentClear;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJRegeneratable;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJRuby;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJAutoWidth;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJHtmlCellPadding;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJHtmlTableBorder;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJInternalLink;
import net.zamasoft.foliojet.impl.css.property.shorthand.BackgroundShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderBottomShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderColorShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderLeftShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderRightShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderStyleShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderTopShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderWidthShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.FontShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.ListStyleShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.MarginShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.PaddingShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.WritingModeShorthand;
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