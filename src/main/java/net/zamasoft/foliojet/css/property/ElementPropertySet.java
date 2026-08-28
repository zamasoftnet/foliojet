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
import net.zamasoft.foliojet.css.impl.property.content.CounterSet;
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
import net.zamasoft.foliojet.css.impl.property.text.TextOrientation;
import net.zamasoft.foliojet.css.impl.property.text.UnicodeBidi;
import net.zamasoft.foliojet.css.impl.property.box.VerticalAlign;
import net.zamasoft.foliojet.css.impl.property.box.ContentVisibility;
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
import net.zamasoft.foliojet.css.impl.property.container.ContainerName;
import net.zamasoft.foliojet.css.impl.property.container.ContainerType;
import net.zamasoft.foliojet.css.impl.property.shorthand.ContainerShorthand;
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
import net.zamasoft.foliojet.css.impl.property.text.TextWrapStyle;
import net.zamasoft.foliojet.css.impl.property.shorthand.TextWrapShorthand;
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
import net.zamasoft.foliojet.css.impl.property.shorthand.InsetShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.MarginShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.OverflowShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.PaddingShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.WritingModeShorthand;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.border.BorderRadius;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.MaskImage;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.border.Corner;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.page.PageBleed;
import net.zamasoft.foliojet.css.impl.property.page.PageMarks;
import net.zamasoft.foliojet.css.impl.property.page.PageSize;

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
		reg(ContentVisibility.INFO);
		reg(Overflow.INFO_X);
		reg(Overflow.INFO_Y);
		reg(Clip.INFO);
		reg(MaskImage.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.box.ClipPath.INFO);
		reg(CSSFontFamily.INFO);
		reg(CSSFontStyle.INFO);
		reg(FontVariant.INFO);
		reg(FontWeight.INFO);
		reg(FontSize.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.font.FontFeatureSettings.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.font.FontVariantEastAsian.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.font.FontVariantNumeric.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.font.FontSynthesisWeight.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.font.FontSynthesisStyle.INFO);
		// Grid G0(consult-codex-2026-07-31-grid.txt)
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.COLUMNS);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.ROWS);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.COLUMN_START);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.COLUMN_END);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.ROW_START);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.ROW_END);
		reg(net.zamasoft.foliojet.css.impl.property.grid.RowGap.INFO);
		// Grid G5a(consult-codex-2026-07-31-grid-g5.txt)
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.JUSTIFY_ITEMS);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_ITEMS);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.JUSTIFY_SELF);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_SELF);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.JUSTIFY_CONTENT);
		reg(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_CONTENT);
		// Flex F1a(consult-codex-2026-08-02-flexbox.txt)
		reg(net.zamasoft.foliojet.css.impl.property.flex.FlexDirectionProperty.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.flex.FlexWrapProperty.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.flex.FlexFactor.GROW);
		reg(net.zamasoft.foliojet.css.impl.property.flex.FlexFactor.SHRINK);
		reg(net.zamasoft.foliojet.css.impl.property.flex.FlexBasisProperty.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.flex.OrderProperty.INFO);
		reg(TextIndent.INFO);
		reg(TextAlign.INFO);
		reg(TextDecoration.INFO);
		reg(LetterSpacing.INFO);
		// 和文詰めA1/T1b(consult-codex-2026-07-31-text-spacing.txt)
		reg(net.zamasoft.foliojet.css.impl.property.text.TextAutospace.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.text.TextSpacingTrim.INFO);
		// 縦中横の種別(内部——TextCombineShorthandが設定する)
		reg(net.zamasoft.foliojet.css.impl.property.text.TextCombineMode.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.text.HangingPunctuation.INFO);
		// 名前付きページN1b(consult-codex-2026-07-31-named-pages.txt)
		reg(net.zamasoft.foliojet.css.impl.property.page.PageProperty.INFO);
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
		reg(CounterSet.INFO);
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
		put(net.zamasoft.foliojet.css.impl.property.shorthand.FontSynthesisShorthand.INFO);
		put(BorderTopShorthand.INFO);
		put(BorderLeftShorthand.INFO);
		put(BorderRightShorthand.INFO);
		put(BorderBottomShorthand.INFO);
		put(BorderColorShorthand.INFO);
		put(BorderStyleShorthand.INFO);
		put(BorderWidthShorthand.INFO);
		put(MarginShorthand.INFO);
		put(InsetShorthand.INFO);
		put(PaddingShorthand.INFO);
		put(OverflowShorthand.INFO);
		put(FontShorthand.INFO);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.GridLineShorthand.COLUMN);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.GridLineShorthand.ROW);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.FlexShorthand.INFO);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.FlexFlowShorthand.INFO);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.GapShorthand.INFO);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.PlaceShorthand.ITEMS);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.PlaceShorthand.SELF);
		put(net.zamasoft.foliojet.css.impl.property.shorthand.PlaceShorthand.CONTENT);
		put(BackgroundShorthand.INFO);
		put(ListStyleShorthand.INFO);

		// 互換性
		alias("windows", Widows.INFO);
		// css-break-3の正式名(page-break-*は旧css2名)。値パーサは
		// page/column/recto/verso等のLevel 3値を既に受ける(2026-08-22)
		alias("break-before", PageBreakBefore.INFO);
		alias("break-after", PageBreakAfter.INFO);
		alias("break-inside", PageBreakInside.INFO);

		// CSS3
		reg(BackgroundClip.INFO);
		reg(BackgroundSize.INFO_WIDTH);
		regCode(BackgroundSize.INFO_HEIGHT);
		reg(BlockFlow.INFO);
		reg(TextOrientation.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.text.RubyAlign.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.text.RubyMerge.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.text.RubyOverhang.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.text.RubyPosition.INFO);
		reg(BoxSizing.INFO);
		// CSS Images 3: 置換要素の内容の収め方(2026-08-27)
		reg(net.zamasoft.foliojet.css.impl.property.box.ObjectFit.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.box.ObjectPosition.INFO_X);
		regCode(net.zamasoft.foliojet.css.impl.property.box.ObjectPosition.INFO_Y);
		reg(TextAlignLast.INFO);
		put(WritingModeShorthand.INFO);
		reg(WordWrap.INFO);
		reg(WordBreak.INFO);
		reg(TextWrapStyle.INFO);
		put(TextWrapShorthand.INFO);
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
		// @container G2(2026-08-15段2、docs/history/2026-08-15-container-queries-design.md)
		reg(ContainerType.INFO);
		reg(ContainerName.INFO);
		put(ContainerShorthand.INFO);
		reg(BorderRadius.BOTTOM_RIGHT);
		reg(BorderRadius.TOP_LEFT);
		reg(BorderRadius.TOP_RIGHT);
		reg(BorderRadius.BOTTOM_LEFT);
		put(BorderRadiusShorthand.INFO);
		put(TextEmphasisShorthand.INFO);
		alias("text-emphasis", TextEmphasisShorthand.INFO);
		alias("text-emphasis-style", TextEmphasisStyle.INFO);
		alias("text-emphasis-color", TextEmphasisColor.INFO);
		put(TextCombineShorthand.INFO);
		reg(Transform.INFO);
		reg(TransformOrigin.INFO_X);
		regCode(TransformOrigin.INFO_Y);
		reg(TextStrokeWidth.INFO);
		reg(TextStrokeColor.INFO);
		reg(TextFillColor.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.text.InitialLetter.INFO);
		// font-variation-settings: 適用は@font-faceディスクリプタのみ
		// (FontVariationSettingsのjavadoc)。要素側はカスケード用コードの
		// 割当のために登録する(CSSStyle.setがコード無しだと黙って落ちる罠)
		reg(net.zamasoft.foliojet.css.impl.property.font.FontVariationSettings.INFO);
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
		alias("-webkit-mask-image", MaskImage.INFO);
		alias("-webkit-clip-path", net.zamasoft.foliojet.css.impl.property.box.ClipPath.INFO);

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
		alias("overflow-wrap", WordWrap.INFO);
		alias("text-combine-upright", TextCombineShorthand.INFO);
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

		// 論理境界プロパティ(2026-08-03)。border-block-start-* ほか12個
		for (net.zamasoft.foliojet.css.impl.property.border.LogicalBorder info : //
				net.zamasoft.foliojet.css.impl.property.border.LogicalBorder.all()) {
			reg(info);
		}

		// Extensions
		reg(CSSJFontPolicy.INFO);
		reg(CSSJRuby.INFO);
		reg(net.zamasoft.foliojet.css.impl.property.ext.CSSJWarichu.INFO);
		reg(CSSJBreakCharacters.INFO);
		reg(CSSJNoBreakCharacters.INFO);

		reg(CSSJAutoWidth.INFO);
		reg(CSSJHtmlAlign.INFO);
		reg(CSSJHtmlCellPadding.INFO);
		reg(CSSJHtmlTableBorder.INFO);
		reg(CSSJInternalImage.INFO);
		reg(CSSJInternalLink.INFO);

		// @page専用特性: カスケード用コードのみ割り当てる(名前解決は
		// PagePropertySetに限定し、要素へのsize指定は受け付けない)
		regCode(PageSize.INFO);
		regCode(PageMarks.INFO);
		regCode(PageBleed.INFO);
	}

	private static final PropertySet INSTANCE = new ElementPropertySet();

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}
