package net.zamasoft.foliojet.css.property;

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

/**
 * @author MIYABE Tatsuhiko
 */
public final class ElementPropertySet extends PropertySet {
	private static final Map<PrimitivePropertyInfo, Short> CODES = new HashMap<PrimitivePropertyInfo, Short>();

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

	private void reg(PrimitivePropertyInfo info) {
		put(info);
		CODES.put(info, (short) CODE_SIZE++);
	}

	private ElementPropertySet() {
		reg(Display.INFO);
		reg(CSSPosition.INFO);
		reg(CSSFloat.INFO);
		reg(Clear.INFO);
		reg(CSSColor.INFO);
		reg(Top.INFO);
		reg(Left.INFO);
		reg(Bottom.INFO);
		reg(Right.INFO);
		reg(Width.INFO);
		reg(Height.INFO);
		reg(LineHeight.INFO);
		reg(MinWidth.INFO);
		reg(MaxWidth.INFO);
		reg(MinHeight.INFO);
		reg(MaxHeight.INFO);
		reg(BorderTopColor.INFO);
		reg(BorderLeftColor.INFO);
		reg(BorderRightColor.INFO);
		reg(BorderBottomColor.INFO);
		reg(BorderTopStyle.INFO);
		reg(BorderLeftStyle.INFO);
		reg(BorderRightStyle.INFO);
		reg(BorderBottomStyle.INFO);
		reg(BorderTopWidth.INFO);
		reg(BorderLeftWidth.INFO);
		reg(BorderRightWidth.INFO);
		reg(BorderBottomWidth.INFO);
		reg(MarginTop.INFO);
		reg(MarginLeft.INFO);
		reg(MarginRight.INFO);
		reg(MarginBottom.INFO);
		reg(PaddingTop.INFO);
		reg(PaddingLeft.INFO);
		reg(PaddingRight.INFO);
		reg(PaddingBottom.INFO);
		reg(VerticalAlign.INFO);
		reg(ZIndex.INFO);
		reg(Visibility.INFO);
		reg(Overflow.INFO);
		reg(Clip.INFO);
		reg(CSSFontFamily.INFO);
		reg(CSSFontStyle.INFO);
		reg(FontVariant.INFO);
		reg(FontWeight.INFO);
		reg(FontSize.INFO);
		reg(TextIndent.INFO);
		reg(TextAlign.INFO);
		reg(TextDecoration.INFO);
		reg(LetterSpacing.INFO);
		reg(WordSpacing.INFO);
		reg(TextTransform.INFO);
		reg(UnicodeBidi.INFO);
		reg(WhiteSpace.INFO);
		reg(Direction.INFO);
		reg(BackgroundColor.INFO);
		reg(BackgroundImage.INFO);
		reg(BackgroundRepeat.INFO);
		reg(BackgroundAttachment.INFO);
		reg(BackgroundPosition.INFO_X);
		reg(BackgroundPosition.INFO_Y);
		reg(Content.INFO);
		reg(Quotes.INFO);
		reg(CounterReset.INFO);
		reg(CounterIncrement.INFO);
		reg(ListStyleType.INFO);
		reg(ListStylePosition.INFO);
		reg(ListStyleImage.INFO);
		reg(CaptionSide.INFO);
		reg(TableLayout.INFO);
		reg(BorderCollapse.INFO);
		reg(EmptyCells.INFO);
		reg(BorderSpacing.INFO_H);
		reg(BorderSpacing.INFO_V);
		reg(PageBreakBefore.INFO);
		reg(PageBreakAfter.INFO);
		reg(PageBreakInside.INFO);
		reg(Orphans.INFO);
		reg(Widows.INFO);

		// font-face
		reg(Src.INFO);
		reg(CSSUnicodeRange.INFO);

		// shorthand
		put(BorderShorthand.INFO);
		put(BorderTopShorthand.INFO);
		put(BorderLeftShorthand.INFO);
		put(BorderRightShorthand.INFO);
		put(BorderBottomShorthand.INFO);
		put(BorderColorShorthand.INFO);
		put(BorderStyleShorthand.INFO);
		put(BorderWidthShorthand.INFO);
		put(MarginShorthand.INFO);
		put(PaddingShorthand.INFO);
		put(FontShorthand.INFO);
		put(BackgroundShorthand.INFO);
		put(ListStyleShorthand.INFO);

		// 互換性
		alias("windows", Widows.INFO);

		// CSS3
		reg(BackgroundClip.INFO);
		reg(BackgroundSize.INFO_WIDTH);
		reg(BackgroundSize.INFO_HEIGHT);
		reg(BlockFlow.INFO);
		reg(BoxSizing.INFO);
		reg(TextAlignLast.INFO);
		put(WritingModeShorthand.INFO);
		reg(WordWrap.INFO);
		reg(WordBreak.INFO);
		reg(ColumnCount.INFO);
		reg(ColumnWidth.INFO);
		reg(ColumnGap.INFO);
		reg(ColumnRuleStyle.INFO);
		reg(ColumnRuleColor.INFO);
		reg(ColumnRuleWidth.INFO);
		reg(ColumnFill.INFO);
		reg(ColumnSpan.INFO);
		put(ColumnRuleShorthand.INFO);
		put(ColumnsShorthand.INFO);
		reg(TextEmphasisStyle.INFO);
		reg(TextEmphasisColor.INFO);
		reg(Opacity.INFO);
		reg(BorderBottomRightRadius.INFO);
		reg(BorderTopLeftRadius.INFO);
		reg(BorderTopRightRadius.INFO);
		reg(BorderBottomLeftRadius.INFO);
		put(BorderRadiusShorthand.INFO);
		put(TextEmphasisShorthand.INFO);
		put(TextCombineShorthand.INFO);
		reg(Transform.INFO);
		reg(TransformOrigin.INFO_X);
		reg(TransformOrigin.INFO_Y);
		reg(TextStrokeWidth.INFO);
		reg(TextStrokeColor.INFO);
		reg(TextFillColor.INFO);
		put(TextStrokeShorthand.INFO);
		reg(TextShadow.INFO);

		alias("-webkit-transform", Transform.INFO);
		alias("-webkit-transform-origin", TransformOrigin.INFO_X);
		alias("-moz-transform", Transform.INFO);
		alias("-moz-transform-origin", TransformOrigin.INFO_X);

		alias("-webkit-text-stroke-width", TextStrokeWidth.INFO);
		alias("-webkit-text-stroke-color", TextStrokeColor.INFO);
		alias("-webkit-text-fill-color", TextFillColor.INFO);
		alias("-webkit-text-stroke", TextStrokeShorthand.INFO);
		alias("-webkit-background-clip", BackgroundClip.INFO);

		alias("oeb-column-number", ColumnCount.INFO);
		alias("-epub-writing-mode", WritingModeShorthand.INFO);
		alias("-epub-text-align-last", TextAlignLast.INFO);
		alias("-epub-text-emphasis-style", TextEmphasisStyle.INFO);
		alias("-epub-text-emphasis-color", TextEmphasisColor.INFO);
		alias("-epub-text-emphasis", TextEmphasisShorthand.INFO);
		alias("-epub-text-combine", TextCombineShorthand.INFO);
		alias("-epub-column-count", ColumnCount.INFO);
		alias("-epub-column-width", ColumnWidth.INFO);
		alias("-epub-column-gap", ColumnGap.INFO);
		alias("-epub-column-rule-style", ColumnRuleStyle.INFO);
		alias("-epub-column-rule-color", ColumnRuleColor.INFO);
		alias("-epub-column-rule-width", ColumnRuleWidth.INFO);
		alias("-epub-column-fill", ColumnFill.INFO);
		alias("-epub-column-span", ColumnSpan.INFO);
		alias("-epub-column-rule", ColumnRuleShorthand.INFO);
		alias("-epub-columns", ColumnsShorthand.INFO);

		alias("transform", Transform.INFO);
		alias("transform-origin", TransformOrigin.INFO_X);
		alias("background-size", BackgroundSize.INFO_WIDTH);
		alias("block-flow", BlockFlow.INFO);
		alias("text-align-last", TextAlignLast.INFO);
		alias("writing-mode", WritingModeShorthand.INFO);
		alias("word-wrap", WordWrap.INFO);
		alias("column-count", ColumnCount.INFO);
		alias("column-width", ColumnWidth.INFO);
		alias("column-gap", ColumnGap.INFO);
		alias("column-rule-style", ColumnRuleStyle.INFO);
		alias("column-rule-color", ColumnRuleColor.INFO);
		alias("column-rule-width", ColumnRuleWidth.INFO);
		alias("column-fill", ColumnFill.INFO);
		alias("column-span", ColumnSpan.INFO);
		alias("column-rule", ColumnRuleShorthand.INFO);
		alias("columns", ColumnsShorthand.INFO);

		// Extensions
		reg(CSSJFontPolicy.INFO);
		reg(CSSJPageContent.INFO_NAME);
		reg(CSSJPageContent.INFO_PAGE);
		reg(CSSJRegeneratable.INFO);
		reg(CSSJPageContentClear.INFO);
		reg(CSSJDirectionMode.INFO);
		reg(CSSJRuby.INFO);
		reg(CSSJBreakCharacters.INFO);
		reg(CSSJNoBreakCharacters.INFO);

		reg(CSSJAutoWidth.INFO);
		reg(CSSJHtmlAlign.INFO);
		reg(CSSJHtmlCellPadding.INFO);
		reg(CSSJHtmlTableBorder.INFO);
		reg(CSSJInternalImage.INFO);
		reg(CSSJInternalLink.INFO);
	}

	private static final PropertySet INSTANCE = new ElementPropertySet();

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}