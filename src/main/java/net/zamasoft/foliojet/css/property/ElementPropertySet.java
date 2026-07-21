package net.zamasoft.foliojet.css.property;


import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.css.impl.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.css.impl.property.table.BorderCollapse;
import net.zamasoft.foliojet.css.impl.property.table.BorderSpacing;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.box.CSSFloat;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontStyle;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.table.CaptionSide;
import net.zamasoft.foliojet.css.impl.property.box.Clear;
import net.zamasoft.foliojet.css.impl.property.box.Clip;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.content.CounterIncrement;
import net.zamasoft.foliojet.css.impl.property.content.CounterReset;
import net.zamasoft.foliojet.css.impl.property.content.StringSet;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.table.EmptyCells;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.font.FontVariant;
import net.zamasoft.foliojet.css.impl.property.font.FontWeight;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.box.InlineSize;
import net.zamasoft.foliojet.css.impl.property.box.BlockSize;
import net.zamasoft.foliojet.css.impl.property.box.MinInlineSize;
import net.zamasoft.foliojet.css.impl.property.box.MaxInlineSize;
import net.zamasoft.foliojet.css.impl.property.box.MinBlockSize;
import net.zamasoft.foliojet.css.impl.property.box.MaxBlockSize;
import net.zamasoft.foliojet.css.impl.property.text.Hyphens;
import net.zamasoft.foliojet.css.impl.property.text.LetterSpacing;
import net.zamasoft.foliojet.css.impl.property.font.LineHeight;
import net.zamasoft.foliojet.css.impl.property.content.ListStyleImage;
import net.zamasoft.foliojet.css.impl.property.content.ListStylePosition;
import net.zamasoft.foliojet.css.impl.property.content.ListStyleType;
import net.zamasoft.foliojet.css.impl.property.box.MaxHeight;
import net.zamasoft.foliojet.css.impl.property.box.MaxWidth;
import net.zamasoft.foliojet.css.impl.property.box.MinHeight;
import net.zamasoft.foliojet.css.impl.property.box.MinWidth;
import net.zamasoft.foliojet.css.impl.property.page.Orphans;
import net.zamasoft.foliojet.css.impl.property.box.Overflow;
import net.zamasoft.foliojet.css.impl.property.page.PageBreakAfter;
import net.zamasoft.foliojet.css.impl.property.page.PageBreakBefore;
import net.zamasoft.foliojet.css.impl.property.page.PageBreakInside;
import net.zamasoft.foliojet.css.impl.property.content.Quotes;
import net.zamasoft.foliojet.css.impl.property.table.TableLayout;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.impl.property.text.TextDecoration;
import net.zamasoft.foliojet.css.impl.property.text.TextIndent;
import net.zamasoft.foliojet.css.impl.property.text.TextTransform;
import net.zamasoft.foliojet.css.impl.property.text.UnicodeBidi;
import net.zamasoft.foliojet.css.impl.property.box.VerticalAlign;
import net.zamasoft.foliojet.css.impl.property.box.Visibility;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.impl.property.page.Widows;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.impl.property.text.WordSpacing;
import net.zamasoft.foliojet.css.impl.property.box.ZIndex;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundClip;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundSize;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderRadiusShorthand;
import net.zamasoft.foliojet.css.impl.property.box.BoxSizing;
import net.zamasoft.foliojet.css.impl.property.font.CSSUnicodeRange;
import net.zamasoft.foliojet.css.impl.property.column.ColumnCount;
import net.zamasoft.foliojet.css.impl.property.column.ColumnFill;
import net.zamasoft.foliojet.css.impl.property.column.ColumnGap;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleColor;
import net.zamasoft.foliojet.css.impl.property.shorthand.ColumnRuleShorthand;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleStyle;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleWidth;
import net.zamasoft.foliojet.css.impl.property.column.ColumnSpan;
import net.zamasoft.foliojet.css.impl.property.column.ColumnWidth;
import net.zamasoft.foliojet.css.impl.property.shorthand.ColumnsShorthand;
import net.zamasoft.foliojet.css.impl.property.box.Opacity;
import net.zamasoft.foliojet.css.impl.property.font.Src;
import net.zamasoft.foliojet.css.impl.property.text.TextAlignLast;
import net.zamasoft.foliojet.css.impl.property.shorthand.TextCombineShorthand;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisColor;
import net.zamasoft.foliojet.css.impl.property.shorthand.TextEmphasisShorthand;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisStyle;
import net.zamasoft.foliojet.css.impl.property.text.TextFillColor;
import net.zamasoft.foliojet.css.impl.property.text.TextShadow;
import net.zamasoft.foliojet.css.impl.property.text.TextStrokeColor;
import net.zamasoft.foliojet.css.impl.property.shorthand.TextStrokeShorthand;
import net.zamasoft.foliojet.css.impl.property.text.TextStrokeWidth;
import net.zamasoft.foliojet.css.impl.property.box.Transform;
import net.zamasoft.foliojet.css.impl.property.box.TransformOrigin;
import net.zamasoft.foliojet.css.impl.property.text.WordBreak;
import net.zamasoft.foliojet.css.impl.property.text.WordWrap;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJBreakCharacters;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJFontPolicy;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJNoBreakCharacters;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJRuby;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJAutoWidth;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlCellPadding;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlTableBorder;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalLink;
import net.zamasoft.foliojet.css.impl.property.shorthand.BackgroundShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderBottomShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderColorShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderLeftShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderRightShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderStyleShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderTopShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderWidthShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.FontShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.ListStyleShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.MarginShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.PaddingShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.WritingModeShorthand;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.border.BorderRadius;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.border.Corner;
import net.zamasoft.foliojet.css.impl.property.box.Side;

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
		regCode(info);
	}

	/**
	 * 複合特性の2つ目以降の構成要素を登録します。
	 * 名前は代表(最初の構成要素)のみに紐づけ、カスケード用のコードだけを割り当てます。
	 */
	private void regCode(PrimitivePropertyInfo info) {
		CODES.put(info, (short) CODE_SIZE++);
	}

	private ElementPropertySet() {
		reg(Display.INFO);
		reg(CSSPosition.INFO);
		reg(CSSFloat.INFO);
		reg(Clear.INFO);
		reg(CSSColor.INFO);
		reg(Inset.TOP);
		reg(Inset.LEFT);
		reg(Inset.BOTTOM);
		reg(Inset.RIGHT);
		reg(Inset.BLOCK_START);
		reg(Inset.BLOCK_END);
		reg(Inset.INLINE_START);
		reg(Inset.INLINE_END);
		reg(Width.INFO);
		reg(Height.INFO);
		reg(InlineSize.INFO);
		reg(BlockSize.INFO);
		reg(LineHeight.INFO);
		reg(MinWidth.INFO);
		reg(MaxWidth.INFO);
		reg(MinHeight.INFO);
		reg(MaxHeight.INFO);
		reg(MinInlineSize.INFO);
		reg(MaxInlineSize.INFO);
		reg(MinBlockSize.INFO);
		reg(MaxBlockSize.INFO);
		reg(BorderColor.TOP);
		reg(BorderColor.LEFT);
		reg(BorderColor.RIGHT);
		reg(BorderColor.BOTTOM);
		reg(BorderStyle.TOP);
		reg(BorderStyle.LEFT);
		reg(BorderStyle.RIGHT);
		reg(BorderStyle.BOTTOM);
		reg(BorderWidth.TOP);
		reg(BorderWidth.LEFT);
		reg(BorderWidth.RIGHT);
		reg(BorderWidth.BOTTOM);
		reg(Margin.TOP);
		reg(Margin.LEFT);
		reg(Margin.RIGHT);
		reg(Margin.BOTTOM);
		reg(Margin.BLOCK_START);
		reg(Margin.BLOCK_END);
		reg(Margin.INLINE_START);
		reg(Margin.INLINE_END);
		reg(Padding.TOP);
		reg(Padding.LEFT);
		reg(Padding.RIGHT);
		reg(Padding.BOTTOM);
		reg(Padding.BLOCK_START);
		reg(Padding.BLOCK_END);
		reg(Padding.INLINE_START);
		reg(Padding.INLINE_END);
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
		regCode(BackgroundPosition.INFO_Y);
		reg(Content.INFO);
		reg(Quotes.INFO);
		reg(CounterReset.INFO);
		reg(CounterIncrement.INFO);
		reg(StringSet.INFO);
		reg(ListStyleType.INFO);
		reg(ListStylePosition.INFO);
		reg(ListStyleImage.INFO);
		reg(CaptionSide.INFO);
		reg(TableLayout.INFO);
		reg(BorderCollapse.INFO);
		reg(EmptyCells.INFO);
		reg(BorderSpacing.INFO_H);
		regCode(BorderSpacing.INFO_V);
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
		regCode(BackgroundSize.INFO_HEIGHT);
		reg(BlockFlow.INFO);
		reg(BoxSizing.INFO);
		reg(TextAlignLast.INFO);
		put(WritingModeShorthand.INFO);
		reg(WordWrap.INFO);
		reg(WordBreak.INFO);
		reg(Hyphens.INFO);
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
		reg(BorderRadius.BOTTOM_RIGHT);
		reg(BorderRadius.TOP_LEFT);
		reg(BorderRadius.TOP_RIGHT);
		reg(BorderRadius.BOTTOM_LEFT);
		put(BorderRadiusShorthand.INFO);
		put(TextEmphasisShorthand.INFO);
		put(TextCombineShorthand.INFO);
		reg(Transform.INFO);
		reg(TransformOrigin.INFO_X);
		regCode(TransformOrigin.INFO_Y);
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