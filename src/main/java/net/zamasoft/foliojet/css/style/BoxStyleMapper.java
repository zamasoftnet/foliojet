package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;

import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.layout.box.params.FloatSide;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.params.ClearMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.geom.AffineTransform;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.foliojet.css.lang.LanguageProfile;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.css.lang.WordHyphenatorBundle;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AttrValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.CaptionSideValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.ContentFunctionValue;
import net.zamasoft.foliojet.css.value.CounterSetValue;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.css3.WordBreakValue;
import net.zamasoft.foliojet.css.value.ListStylePositionValue;
import net.zamasoft.foliojet.css.value.PageBreakValue;
import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.QuoteValue;
import net.zamasoft.foliojet.css.value.QuotesValue;
import net.zamasoft.foliojet.css.value.StringFunctionValue;
import net.zamasoft.foliojet.css.value.StringSetEntryValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TargetCounterValue;
import net.zamasoft.foliojet.css.value.TargetTextValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.value.VisibilityValue;
import net.zamasoft.foliojet.css.value.ext.CSSJRubyValue;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.css.impl.property.table.BorderCollapse;
import net.zamasoft.foliojet.css.impl.property.table.BorderSpacing;
import net.zamasoft.foliojet.css.impl.property.text.CSSColor;
import net.zamasoft.foliojet.css.impl.property.box.CSSFloat;
import net.zamasoft.foliojet.css.impl.property.box.CSSPosition;
import net.zamasoft.foliojet.css.impl.property.table.CaptionSide;
import net.zamasoft.foliojet.css.impl.property.box.Clear;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.content.CounterIncrement;
import net.zamasoft.foliojet.css.impl.property.content.CounterReset;
import net.zamasoft.foliojet.css.impl.property.content.StringSet;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.table.EmptyCells;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.box.BlockSize;
import net.zamasoft.foliojet.css.impl.property.box.Height;
import net.zamasoft.foliojet.css.impl.property.text.HyphenateCharacter;
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
import net.zamasoft.foliojet.css.impl.property.box.MaskImage;
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
import net.zamasoft.foliojet.css.impl.property.box.VerticalAlign;
import net.zamasoft.foliojet.css.impl.property.box.Visibility;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.impl.property.page.Widows;
import net.zamasoft.foliojet.css.impl.property.box.Width;
import net.zamasoft.foliojet.css.impl.property.text.WordSpacing;
import net.zamasoft.foliojet.css.impl.property.box.ZIndex;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundBlendMode;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundClip;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundOrigin;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundSize;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.box.BoxSizing;
import net.zamasoft.foliojet.css.impl.property.box.ObjectFit;
import net.zamasoft.foliojet.css.impl.property.box.ObjectPosition;
import net.zamasoft.foliojet.css.impl.property.column.ColumnCount;
import net.zamasoft.foliojet.css.impl.property.column.ColumnFill;
import net.zamasoft.foliojet.css.impl.property.column.ColumnGap;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleColor;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleStyle;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleWidth;
import net.zamasoft.foliojet.css.impl.property.column.ColumnSpan;
import net.zamasoft.foliojet.css.impl.property.column.ColumnWidth;
import net.zamasoft.foliojet.css.impl.property.box.Opacity;
import net.zamasoft.foliojet.css.impl.property.text.PaintOrder;
import net.zamasoft.foliojet.css.impl.property.text.TextAlignLast;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisColor;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisStyle;
import net.zamasoft.foliojet.css.impl.property.text.TextFillColor;
import net.zamasoft.foliojet.css.impl.property.text.TextShadow;
import net.zamasoft.foliojet.css.impl.property.text.TextStrokeColor;
import net.zamasoft.foliojet.css.impl.property.text.TextStrokeWidth;
import net.zamasoft.foliojet.css.impl.property.box.Transform;
import net.zamasoft.foliojet.css.impl.property.box.TransformOrigin;
import net.zamasoft.foliojet.css.impl.property.text.TextWrapStyle;
import net.zamasoft.foliojet.css.impl.property.text.WordWrap;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJRuby;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.LayoutFontStyle;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BackgroundFit;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FirstLineParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectBorder.Radius;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;

import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.TextUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.PageRef.Fragment;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.PendingStringSet;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPageLimitAbort;
import net.zamasoft.foliojet.ua.props.OutputPrintMode;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.vocab.XHTML;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.border.BorderRadius;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.border.Corner;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;
import net.zamasoft.foliojet.ua.BoundSide;

/**
 * Maps computed CSS values to Params / Pos / RectFrame / Background / RectBorder
 * (extracted in increment 3 of the StyleBuilder decomposition, 2026-07-30.
 * Method bodies moved verbatim from StyleBuilder; behavior is unchanged).
 *
 * <p>
 * Holds no state (only ua/styleContext references). Receives the call-time context
 * (currentStyle/rightSide/inBody) as arguments.
 * </p>
 */
final class BoxStyleMapper {
	/**
	 * Upper bound for {@code colspan}, as defined by the HTML Standard (matching browsers).
	 *
	 * <p>
	 * Without a bound, one cell with {@code colspan="2147483647"} makes
	 * {@code IncrementalTableBuilder} add about 2.1 billion entries, exhausting memory
	 * before completion (found in an independent review, 2026-07-25).
	 * Negative, zero, and nonnumeric values were already normalized, but <b>huge positive
	 * values alone passed through</b>. Align the bound with the HTML Standard under the
	 * criterion of whether an equivalent exists in worldwide standards.
	 * </p>
	 */
	private static final int MAX_COLSPAN = 1000;

	/**
	 * Upper bound for {@code rowspan}, as defined by the HTML Standard (matching browsers).
	 *
	 * <p>
	 * Without a bound, {@code rowspan="2147483647"} makes
	 * {@code borderRow + rowspan - 1} in {@code CollapsedBorderRules.streamSpacing}
	 * <b>overflow int to a negative value</b>, causing {@code IndexOutOfBoundsException}
	 * in {@code List.get(negative value)} (found in an independent review, 2026-07-25).
	 * </p>
	 */
	private static final int MAX_ROWSPAN = 65534;

	private final UserAgent ua;
	private final StyleContext styleContext;

	BoxStyleMapper(final UserAgent ua, final StyleContext styleContext) {
		this.ua = ua;
		this.styleContext = styleContext;
	}

	/**
	 * Sets up positioning that permits relative positioning.
	 *
	 * @param pos
	 * @param style
	 */
	void setupStaticPos(final AbstractStaticPos pos, final CSSStyle style) {
		final byte position = CSSPosition.get(style);
		if (position == PositionValue.STICKY) {
			pos.offset = Offset.ZERO_OFFSET;
		} else if (position != PositionValue.STATIC) {
			pos.offset = this.createRelativeOffset(style);
		}
		// Named pages N1b: carry the used value of page (nearest non-auto ancestor)
		// to block-level positioning (input for boundary detection=N2).
		if (pos instanceof net.zamasoft.foliojet.layout.box.params.AbstractBlockLevelPos blockLevel) {
			blockLevel.pageName = net.zamasoft.foliojet.css.impl.property.page.PageProperty.getUsed(style);
		}
	}

	/**
	 * Sets up inline positioning.
	 *
	 * @param pos
	 * @param style
	 */
	void setupInlinePos(InlinePos pos, CSSStyle style) {
		this.setupStaticPos(pos, style);
		pos.verticalAlign = VerticalAlign.getForInline(style);
		pos.lineHeight = LineHeight.get(style);
	}

	/**
	 * Sets up absolute positioning.
	 *
	 * @param pos
	 * @param style
	 */
	void setupAbsolutePos(AbsolutePos pos, CSSStyle style) {
		Value top = Inset.get(style, Side.TOP);
		Value right = Inset.get(style, Side.RIGHT);
		Value bottom = Inset.get(style, Side.BOTTOM);
		Value left = Inset.get(style, Side.LEFT);
		pos.location = BoxValueUtils.toInsets(top, right, bottom, left);

		switch (CSSPosition.get(style)) {
		case PositionValue.ABSOLUTE:
			pos.fiducial = Fiducial.CONTEXT;
			switch (Display.get(style)) {
			case DisplayValue.INLINE_BLOCK:
			case DisplayValue.INLINE_TABLE:
				pos.autoPosition = AutoPosition.INLINE;
				break;

			default:
				// The display of an absolutely positioned element is blockified (CSS Display 3
				// §2.7). All values except inline-block/inline-table reach here, and the static
				// position is determined as a block.
				// **Enumerating known values and throwing for the rest caused crashes whenever
				// a display value was added later** (discovered on 2026-08-02 in a real document
				// with position:absolute + display:flex).
				// Replace enumeration with the blockification rule itself.
				pos.autoPosition = AutoPosition.BLOCK;
				break;
			}
			break;
		case PositionValue.FIXED:
			pos.fiducial = Fiducial.ALL_PAGE;
			pos.autoPosition = AutoPosition.BLOCK;
			break;
		}
	}

	/**
	 * Sets up normal flow positioning.
	 *
	 * @param pos
	 * @param style
	 */
	void setupFlowPos(FlowPos pos, CSSStyle style, boolean rightSide) {
		this.setupStaticPos(pos, style);
		pos.clear = Clear.get(style);
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), rightSide);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), rightSide);
		pos.columnSpan = ColumnSpan.get(style);
		// Grid G4a/G5a: four explicit-placement longhands + two self-alignment values (read
		// only when becoming a direct Grid item; share a singleton when all are auto).
		pos.gridItem = net.zamasoft.foliojet.layout.box.params.GridItemSpec.of(
				net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.COLUMN_START),
				net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.COLUMN_END),
				net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.ROW_START),
				net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridPlacement.ROW_END),
				toBoxAlignment(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.JUSTIFY_SELF)),
				toBoxAlignment(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_SELF)));
		// Flex F1a: three flex values + whether min is declared, to restore automatic minimum
		// size (read only when becoming a direct Flex item; share a singleton for all defaults.
		// alignSelf is parsed in F3c, order in F5a).
		pos.flexItem = net.zamasoft.foliojet.layout.box.params.FlexItemSpec.of(
				net.zamasoft.foliojet.css.impl.property.flex.FlexFactor.get(style,
						net.zamasoft.foliojet.css.impl.property.flex.FlexFactor.GROW),
				net.zamasoft.foliojet.css.impl.property.flex.FlexFactor.get(style,
						net.zamasoft.foliojet.css.impl.property.flex.FlexFactor.SHRINK),
				net.zamasoft.foliojet.css.impl.property.flex.FlexBasisProperty.get(style),
				toFlexItemAlignment(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_SELF),
						net.zamasoft.foliojet.layout.box.params.BoxAlignment.AUTO),
				net.zamasoft.foliojet.css.impl.property.flex.OrderProperty.get(style),
				!minSizeDeclared(style, false), !minSizeDeclared(style, true));
	}

	/**
	 * Returns whether the author declares min-width (vertical=false) or min-height
	 * (vertical=true) (Flex F1a: input to automatic minimum size §4.5 checks).
	 * As in {@code MinWidth.get}/{@code MinHeight.get}, physical properties take precedence,
	 * and logical properties are mapped according to the writing direction.
	 */
	private static boolean minSizeDeclared(final CSSStyle style, final boolean height) {
		if (style.isDeclared(height ? net.zamasoft.foliojet.css.impl.property.box.MinHeight.INFO
				: net.zamasoft.foliojet.css.impl.property.box.MinWidth.INFO)) {
			return true;
		}
		final boolean vertical = net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style).isVertical();
		return style.isDeclared(height == vertical ? net.zamasoft.foliojet.css.impl.property.box.MinInlineSize.INFO
				: net.zamasoft.foliojet.css.impl.property.box.MinBlockSize.INFO);
	}

	/**
	 * Sets up float positioning.
	 *
	 * @param pos
	 * @param style
	 */
	void setupFloatPos(FloatPos pos, CSSStyle style, boolean rightSide) {
		this.setupStaticPos(pos, style);
		byte floating = CSSFloat.get(style);
		switch (floating) {
		case CSSFloatValue.LEFT:
		case CSSFloatValue.START:
			pos.floating = FloatSide.START;
			break;

		case CSSFloatValue.RIGHT:
		case CSSFloatValue.END:
			pos.floating = FloatSide.END;
			break;

		default:
			throw new IllegalStateException();
		}
		pos.clear = Clear.get(style);
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), rightSide);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), rightSide);
		// shape-outside (css-shapes-1, 2026-08-29). Applies only to left/right floats
		// (footnotes and page floats use other Pos types and do not reach here).
		pos.shapeOutside = net.zamasoft.foliojet.css.impl.property.box.ShapeOutside.toParams(style);
	}

	/**
	 * Sets up Grid container parameters (Grid G0).
	 */
	void setupGridParams(net.zamasoft.foliojet.layout.box.params.GridParams params, CSSStyle style,
			CSSStyle parentStyle, boolean inBody, PageSequence pageSequence) {
		this.setupBlockParams(params, style, parentStyle, inBody, pageSequence);
		final net.zamasoft.foliojet.css.value.GridTrackListValue columns = net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks
				.getColumns(style);
		final net.zamasoft.foliojet.css.value.GridTrackListValue rows = net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks
				.getRows(style);
		params.templateColumns = columns.getTracks();
		params.templateRows = rows.getTracks();
		// 2026-08-29: line names, areas, implicit tracks, auto-flow
		params.columnLineNames = columns.getLineNames();
		params.rowLineNames = rows.getLineNames();
		// subgrid (css-grid-2, 2026-08-29): tracks are empty; carry only the line-name sequence
		params.columnsSubgrid = columns.isSubgrid();
		params.rowsSubgrid = rows.isSubgrid();
		params.templateAreas = net.zamasoft.foliojet.css.impl.property.grid.GridTemplateAreas.get(style);
		params.autoColumns = net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks
				.get(style, net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.AUTO_COLUMNS).getTracks();
		params.autoRows = net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks
				.get(style, net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.AUTO_ROWS).getTracks();
		final net.zamasoft.foliojet.css.value.GridAutoFlowValue autoFlow = net.zamasoft.foliojet.css.impl.property.grid.GridAutoFlow
				.get(style);
		params.autoFlowColumn = autoFlow.isColumn();
		params.autoFlowDense = autoFlow.isDense();
		params.rowGap = net.zamasoft.foliojet.css.impl.property.grid.RowGap.get(style);
		params.rowGapNormal = net.zamasoft.foliojet.css.impl.property.grid.RowGap.isNormal(style);
		params.columnGap = net.zamasoft.foliojet.css.impl.property.column.ColumnGap.getForGrid(style);
		params.columnGapNormal = net.zamasoft.foliojet.css.impl.property.column.ColumnGap.isNormal(style);
		// G5a: four container alignment values (resolve used values at bind time for replay determinism)
		params.justifyItems = toBoxAlignment(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty
				.get(style, net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.JUSTIFY_ITEMS));
		params.alignItems = toBoxAlignment(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty
				.get(style, net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_ITEMS));
		params.justifyContent = toBoxAlignment(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty
				.get(style, net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.JUSTIFY_CONTENT));
		params.alignContent = toBoxAlignment(net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty
				.get(style, net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_CONTENT));
	}

	void setupFlexParams(net.zamasoft.foliojet.layout.box.params.FlexParams params, CSSStyle style,
			CSSStyle parentStyle, boolean inBody, PageSequence pageSequence) {
		this.setupBlockParams(params, style, parentStyle, inBody, pageSequence);
		// Flex F1a: direction/wrap (alignment in F3a, gap in F2c)
		params.flexDirection = switch (net.zamasoft.foliojet.css.impl.property.flex.FlexDirectionProperty.get(style)) {
		case ROW -> net.zamasoft.foliojet.layout.box.params.FlexDirection.ROW;
		case ROW_REVERSE -> net.zamasoft.foliojet.layout.box.params.FlexDirection.ROW_REVERSE;
		case COLUMN -> net.zamasoft.foliojet.layout.box.params.FlexDirection.COLUMN;
		case COLUMN_REVERSE -> net.zamasoft.foliojet.layout.box.params.FlexDirection.COLUMN_REVERSE;
		};
		params.flexWrap = switch (net.zamasoft.foliojet.css.impl.property.flex.FlexWrapProperty.get(style)) {
		case NOWRAP -> net.zamasoft.foliojet.layout.box.params.FlexWrap.NOWRAP;
		case WRAP -> net.zamasoft.foliojet.layout.box.params.FlexWrap.WRAP;
		case WRAP_REVERSE -> net.zamasoft.foliojet.layout.box.params.FlexWrap.WRAP_REVERSE;
		};
		// F2c: share row-gap/column-gap with Grid (including the gap shorthand)
		params.rowGap = net.zamasoft.foliojet.css.impl.property.grid.RowGap.get(style);
		params.columnGap = net.zamasoft.foliojet.css.impl.property.column.ColumnGap.getForGrid(style);
		// F3a: alignment (properties shared with Grid; Flex defaults: align-items=stretch,
		// content properties=normal)
		params.justifyContent = toFlexJustify(
				net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.JUSTIFY_CONTENT),
				params.flexDirection.isReverse());
		params.alignItems = toFlexItemAlignment(
				net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_ITEMS),
				net.zamasoft.foliojet.layout.box.params.BoxAlignment.STRETCH);
		params.alignContent = toFlexContentAlignment(
				net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_CONTENT));
	}

	/**
	 * Flex mapping for justify-content (F5b). On a reversed main axis,
	 * flex-start/flex-end/normal/stretch (all equivalent to flex-start) map to physical END.
	 * Plain start/end follow the writing direction and are not reversed (F5b recommendation:
	 * "start and flex-start cannot be treated as equivalent").
	 */
	private static net.zamasoft.foliojet.layout.box.params.FlexContentAlignment toFlexJustify(
			final net.zamasoft.foliojet.css.value.BoxAlignmentValue value, final boolean reversed) {
		if (reversed) {
			switch (value) {
			case AUTO:
			case NORMAL:
			case STRETCH:
			case FLEX_START:
				return net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.END;
			case FLEX_END:
				return net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.START;
			default:
				break;
			}
		}
		return toFlexContentAlignment(value);
	}

	/**
	 * Flex mapping for content-distribution values (for align-content; flex-* cross-axis
	 * reversal is handled by wrap-reverse in F5c).
	 */
	private static net.zamasoft.foliojet.layout.box.params.FlexContentAlignment toFlexContentAlignment(
			final net.zamasoft.foliojet.css.value.BoxAlignmentValue value) {
		return switch (value) {
		case AUTO, NORMAL -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.NORMAL;
		case FLEX_START, START -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.START;
		case FLEX_END, END -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.END;
		case CENTER -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.CENTER;
		case STRETCH -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.STRETCH;
		case SPACE_BETWEEN -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.SPACE_BETWEEN;
		case SPACE_AROUND -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.SPACE_AROUND;
		case SPACE_EVENLY -> net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.SPACE_EVENLY;
		};
	}

	/** Flex mapping for self-alignment values (normal to the contextual default, flex-start/end to start/end). */
	private static net.zamasoft.foliojet.layout.box.params.BoxAlignment toFlexItemAlignment(
			final net.zamasoft.foliojet.css.value.BoxAlignmentValue value,
			final net.zamasoft.foliojet.layout.box.params.BoxAlignment normal) {
		return switch (value) {
		case AUTO -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.AUTO;
		case NORMAL -> normal;
		case FLEX_START, START -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.START;
		case FLEX_END, END -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.END;
		case CENTER -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.CENTER;
		case STRETCH -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.STRETCH;
		default -> normal;
		};
	}

	/** CSS values to layout values (matching names). */
	private static net.zamasoft.foliojet.layout.box.params.BoxAlignment toBoxAlignment(
			final net.zamasoft.foliojet.css.value.BoxAlignmentValue value) {
		// Grid fallback for values added in Flex F3a: flex-start/end map to start/end;
		// space-* falls back to NORMAL because Grid does not support it (risk handling from
		// the F3a recommendation: CSS properties are display-independent and also reach Grid).
		return switch (value) {
		case FLEX_START -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.START;
		case FLEX_END -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.END;
		case SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY ->
			net.zamasoft.foliojet.layout.box.params.BoxAlignment.NORMAL;
		default -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.valueOf(value.name());
		};
	}

	/**
	 * Computed values of {@code bookmark-level} and {@code bookmark-label} (2026-10-04).
	 * Null if both are defaults. Resolves {@code attr()} from element attributes here;
	 * leaves {@code content()} as a null component (element text at bookmark creation time).
	 */
	private static net.zamasoft.foliojet.layout.box.params.BookmarkSpec bookmark(final CSSStyle style) {
		final CSSElement ce = style.getCSSElement();
		if (ce == null || ce.isPseudoElement()) {
			return null;
		}
		final int level = net.zamasoft.foliojet.css.impl.property.content.BookmarkLevel.get(style);
		final Value[] parts = net.zamasoft.foliojet.css.impl.property.content.BookmarkLabel.get(style);
		if (level == net.zamasoft.foliojet.layout.box.params.BookmarkSpec.LEVEL_AUTO && parts == null) {
			return null;
		}
		String[] label = null;
		if (parts != null) {
			label = new String[parts.length];
			for (int i = 0; i < parts.length; ++i) {
				final Value part = parts[i];
				if (part instanceof net.zamasoft.foliojet.css.value.StringValue str) {
					label[i] = str.getString();
				} else if (part instanceof net.zamasoft.foliojet.css.value.AttrValue attr) {
					final String value = ce.atts == null ? null : ce.atts.getValue(attr.getName());
					label[i] = value == null ? "" : value;
				}
				// content(): leave null
			}
		}
		return new net.zamasoft.foliojet.layout.box.params.BookmarkSpec(level, label);
	}

	void setupParams(Params params, CSSStyle style) {
		params.element = style.getCSSElement();
		params.footnoteId = style.footnoteId;
		params.bookmark = bookmark(style);
		if (Visibility.get(style) == VisibilityValue.VISIBLE) {
			params.opacity = Opacity.get(style);
		} else {
			params.opacity = 0f;
		}
		params.blendMode = net.zamasoft.foliojet.css.impl.property.box.MixBlendMode.get(style);
		params.filter = net.zamasoft.foliojet.css.impl.property.box.Filter.get(style);
		this.setupTransform(params, style);
		params.transformOrigin = TransformOrigin.get(style);
		params.zoom = net.zamasoft.foliojet.css.impl.property.box.Zoom.get(style);
		params.zIndexType = ZIndex.getType(style);
		if (params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			params.zIndexValue = ZIndex.getValue(style);
		} else if (params.filter.own().needsGroup()) {
			// filter-effects-1 §3: a filter other than none creates a stacking context.
			// Continue handling opacity() alone through the drawing element's transparency layer.
			params.zIndexType = Params.Z_INDEX_SPECIFIED;
			params.zIndexValue = 0;
		}
	}

	/**
	 * Composes individual transform properties and {@code transform} (css-transforms-2
	 * §7.3, 2026-08-29). Order: translate → rotate → scale → transform,
	 * i.e. matrix P·M (P=T·R·S; M is {@code transform}).
	 *
	 * <p>
	 * The percentage components of {@code transform} (four W/H coefficients,
	 * {@code TransformValue}) are a translation added outside M, so premultiplication by P
	 * maps them through P_lin (P·(M+v) = P·M + P_lin·v).
	 * T is a pure translation, so P_lin = R·S. The percentages in {@code translate}
	 * itself come first (preceded by identity), so add W→x and H→y directly.
	 * </p>
	 */
	private void setupTransform(final Params params, final CSSStyle style) {
		final AffineTransform ct = Transform.get(style);
		double txRatio = Transform.getTxRatio(style);
		double tyRatio = Transform.getTyRatio(style);
		double txRatioH = Transform.getTxRatioH(style);
		double tyRatioW = Transform.getTyRatioW(style);
		final net.zamasoft.foliojet.css.value.css3.TransformValue translate = net.zamasoft.foliojet.css.impl.property.box.Translate
				.get(style);
		final net.zamasoft.foliojet.css.value.css3.TransformValue rotate = net.zamasoft.foliojet.css.impl.property.box.Rotate
				.get(style);
		final net.zamasoft.foliojet.css.value.css3.TransformValue scale = net.zamasoft.foliojet.css.impl.property.box.Scale
				.get(style);
		if (translate == net.zamasoft.foliojet.css.value.css3.TransformValue.IDENTITY_TRANSFORM_VALUE
				&& rotate == net.zamasoft.foliojet.css.value.css3.TransformValue.IDENTITY_TRANSFORM_VALUE
				&& scale == net.zamasoft.foliojet.css.value.css3.TransformValue.IDENTITY_TRANSFORM_VALUE) {
			params.transform = ct;
			params.transformTxRatio = txRatio;
			params.transformTyRatio = tyRatio;
			params.transformTxRatioH = txRatioH;
			params.transformTyRatioW = tyRatioW;
			return;
		}
		final AffineTransform pre = new AffineTransform(translate.getTransform());
		pre.concatenate(rotate.getTransform());
		pre.concatenate(scale.getTransform());
		// Map transform coefficient vectors (W→(x,y), H→(x,y)) through P_lin
		final double m00 = pre.getScaleX(), m10 = pre.getShearY(), m01 = pre.getShearX(), m11 = pre.getScaleY();
		final double wx = m00 * txRatio + m01 * tyRatioW;
		final double wy = m10 * txRatio + m11 * tyRatioW;
		final double hx = m00 * txRatioH + m01 * tyRatio;
		final double hy = m10 * txRatioH + m11 * tyRatio;
		txRatio = wx + translate.getTxRatio();
		tyRatioW = wy;
		txRatioH = hx;
		tyRatio = hy + translate.getTyRatio();
		pre.concatenate(ct);
		params.transform = pre;
		params.transformTxRatio = txRatio;
		params.transformTyRatio = tyRatio;
		params.transformTxRatioH = txRatioH;
		params.transformTyRatioW = tyRatioW;
	}

	/**
	 * Sets up text box parameters.
	 *
	 * @param params
	 * @param style
	 */
	void setupTextParams(AbstractTextParams params, CSSStyle style) {
		this.setupParams(params, style);
		params.whiteSpace = WhiteSpace.get(style);
		params.wordWrap = WordWrap.get(style);
		if (net.zamasoft.foliojet.css.impl.property.text.WordBreak.get(style) == WordBreakValue.BREAK_WORD) {
			// css-text-3 §5.2: break-word = normal + overflow-wrap:anywhere (2026-08-29)
			params.wordWrap = AbstractTextParams.WORD_WRAP_BREAK_WORD;
		}
		params.textWrapStyle = TextWrapStyle.get(style);
		params.textJustify = net.zamasoft.foliojet.css.impl.property.text.TextJustify.get(style);
		params.strictLineBox = this.ua.getDocumentContext()
				.getCompatibleMode() == net.zamasoft.foliojet.ua.CompatibleMode.STRICT;
		// tab-size (css-text-3, 2026-08-29). Multiply a factor by the space width (TextBuilder).
		params.tabSize = net.zamasoft.foliojet.css.impl.property.text.TabSize.get(style);
		params.tabSizeIsMultiple = net.zamasoft.foliojet.css.impl.property.text.TabSize.isMultiple(style);
		params.color = TextFillColor.get(style);
		params.decoration = TextDecoration.get(style);
		params.decorationThickness = 1.0 / style.getUserAgent().getFontSize(AbsoluteFontSize.MEDIUM) / 2.0;
		params.decorationColor = net.zamasoft.foliojet.css.impl.property.text.TextDecorationColor.get(style);
		// Line style, thickness, and underline position (wired to drawing on 2026-08-29; treat from-font as auto)
		params.decorationStyle = net.zamasoft.foliojet.css.impl.property.text.TextDecorationAux.getStyle(style);
		params.decorationThicknessLength = net.zamasoft.foliojet.css.impl.property.text.TextDecorationAux
				.getThickness(style);
		params.underlineOffset = net.zamasoft.foliojet.css.impl.property.text.TextDecorationAux
				.getUnderlineOffset(style);
		params.underlinePosition = net.zamasoft.foliojet.css.impl.property.text.TextUnderlinePosition.get(style);
		params.textStrokeWidth = TextStrokeWidth.get(style);
		params.textStrokeColor = TextStrokeColor.get(style);
		final net.zamasoft.foliojet.css.value.PaintOrderValue paintOrder = PaintOrder.get(style);
		params.strokeBeforeFill = paintOrder.isStrokeBeforeFill();
		params.textShadows = TextShadow.get(style);
		params.letterSpacing = LetterSpacing.get(style);
		params.wordSpacing = WordSpacing.get(style);
		params.textTransform = TextTransform.get(style);
		// Japanese text spacing A1: effective flags (geometry wired in A2)
		params.textAutospace = net.zamasoft.foliojet.css.impl.property.text.TextAutospace.getFlags(style);
		// Japanese text spacing T1b: space-all disables punctuation trimming; trim-start removes line-start spacing
		params.textSpacingTrimOff = net.zamasoft.foliojet.css.impl.property.text.TextSpacingTrim.isSpaceAll(style);
		params.textSpacingTrimStart = net.zamasoft.foliojet.css.impl.property.text.TextSpacingTrim
				.trimsLineStart(style);
		params.textSpacingTrimEnd = net.zamasoft.foliojet.css.impl.property.text.TextSpacingTrim.trimsLineEnd(style);
		params.textSpacingSpaceFirst = net.zamasoft.foliojet.css.impl.property.text.TextSpacingTrim
				.spacesFirstLine(style);
		// Tate-chu-yoko type (only all is compressed to 1em width; 2026-08-11)
		params.textCombine = net.zamasoft.foliojet.css.impl.property.text.TextCombineMode.get(style);
		// Japanese text spacing H1: hanging punctuation at line ends
		params.hangingPunctuationEnd = net.zamasoft.foliojet.css.impl.property.text.HangingPunctuation
				.isAllowEnd(style);
		params.hangingPunctuationFirst = net.zamasoft.foliojet.css.impl.property.text.HangingPunctuation
				.hangsFirst(style);
		params.hangingPunctuationForceEnd = net.zamasoft.foliojet.css.impl.property.text.HangingPunctuation
				.isForceEnd(style);
		params.rubyAlign = net.zamasoft.foliojet.css.impl.property.text.RubyAlign.get(style);
		params.rubyMerge = net.zamasoft.foliojet.css.impl.property.text.RubyMerge.get(style);
		params.rubyOverhang = net.zamasoft.foliojet.css.impl.property.text.RubyOverhang
				.get(style) == net.zamasoft.foliojet.css.value.RubyOverhangValue.AUTO;
		params.rubyPosition = net.zamasoft.foliojet.css.impl.property.text.RubyPosition.get(style);
		params.warichu = net.zamasoft.foliojet.css.impl.property.ext.CSSJWarichu.isEnabled(style);
		params.fontStyle = LayoutFontStyle.withPaintOrder(style.getFontStyle(),
				paintOrder.isNormal() ? null : paintOrder.toString());
		params.fontManager = this.ua.getFontManager();
		final LanguageProfile lang = LanguageProfileBundle
				.getLanguageProfile(style.getLang());
		params.lineBreakRules = lang.getTextBreakingRules(style);
		params.hyphens = Hyphens.get(style);
		params.hyphenateCharacter = HyphenateCharacter.get(style);
		if (params.hyphens == AbstractTextParams.HYPHENS_AUTO) {
			params.hyphenator = WordHyphenatorBundle.getHyphenator(style.getLang());
		}
		params.direction = Direction.get(style);
		params.unicodeBidi = net.zamasoft.foliojet.css.impl.property.text.UnicodeBidi.get(style);
		params.bidiSemanticAlias = UAProps.OUTPUT_PDF_BIDI_ACTUAL_TEXT.getBoolean(this.ua);
		params.flow = BlockFlow.get(style);
		params.writingModeVariant = net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.get(style);
		// Carry the ruby role marker in params to the text processing layer
		// (StyledTextUnitizer; annotated-text approach, specification decision on 2026-07-25).
		switch (CSSJRuby.get(style)) {
		case CSSJRubyValue.RUBY:
			params.rubyRole = AbstractTextParams.RUBY_CONTAINER;
			break;
		case CSSJRubyValue.RB:
			params.rubyRole = AbstractTextParams.RUBY_BASE;
			break;
		case CSSJRubyValue.RT:
			params.rubyRole = AbstractTextParams.RUBY_TEXT;
			break;
		case CSSJRubyValue.RTC:
			params.rubyRole = AbstractTextParams.RUBY_TEXT_CONTAINER;
			break;
		default:
			break;
		}
	}

	/**
	 * Sets up replaced box parameters.
	 *
	 * @param src
	 * @param params
	 * @param style
	 */
	void setupReplacedParams(Image image, ReplacedParams params, CSSStyle style, boolean inBody, PageSequence pageSequence) {
		this.setupTextParams(params, style);
		params.image = image;

		params.size = BoxValueUtils.toDimension(Width.get(style), Height.get(style));
		params.minSize = BoxValueUtils.toMinDimension(MinWidth.get(style), MinHeight.get(style));
		params.maxSize = BoxValueUtils.toDimension(MaxWidth.get(style), MaxHeight.get(style));
		params.boxSizing = BoxSizing.get(style);
		params.objectFit = ObjectFit.get(style);
		params.objectPosition = ObjectPosition.get(style);
		// clip-path (2026-08-29). Pass the same shapes used for blocks to replaced elements.
		// Previously only BlockParams held them, so they were silently ignored on <img>.
		params.clipPath = net.zamasoft.foliojet.css.impl.property.box.ClipPath
				.toShape(net.zamasoft.foliojet.css.impl.property.box.ClipPath.get(style));
		// aspect-ratio (2026-08-29). With auto, prefer the intrinsic ratio (AbstractReplacedBox).
		final net.zamasoft.foliojet.css.value.AspectRatioValue aspectRatio = net.zamasoft.foliojet.css.impl.property.box.AspectRatio
				.get(style);
		params.aspectRatio = aspectRatio.getRatio();
		params.aspectRatioAuto = aspectRatio.isAuto();

		params.frame = this.createRectFrame(style, inBody, pageSequence);
		params.color = CSSColor.get(style);
		params.lineHeight = LineHeight.get(style);
	}

	/**
	 * Sets up line box parameters.
	 *
	 * @param params
	 * @param style
	 */
	void setupAbstractLineParams(AbstractLineParams params, CSSStyle style) {
		this.setupTextParams(params, style);
		params.textIndent = TextIndent.get(style);
		params.textAlign = TextAlign.get(style);
		params.textAlignLast = TextAlignLast.get(style);
		params.lineHeight = LineHeight.get(style);
	}

	/**
	 * Sets up line box parameters.
	 *
	 * @param params
	 * @param style
	 */
	void setupLineParams(FirstLineParams params, CSSStyle style) {
		this.setupAbstractLineParams(params, style);
		if (style.getCSSElement() == CSSElement.FIRST_LINE) {
			params.background = createBackground(style);
		}
	}

	void setupBlockParams(BlockParams params, CSSStyle style, CSSStyle currentStyle, boolean inBody, PageSequence pageSequence) {
		this.setupAbstractLineParams(params, style);
		params.pageBreakInside = PageBreakInside.get(style);
		params.orphans = (byte) Math.min(Byte.MAX_VALUE, Orphans.get(style));
		params.widows = (byte) Math.min(Byte.MAX_VALUE, Widows.get(style));

		// :first-line
		this.styleContext.startElement(CSSElement.FIRST_LINE);
		final Declaration declaration = this.styleContext.merge(null);
		this.styleContext.endElement();
		if (declaration != null) {
			CSSStyle firstLineStyle = CSSStyle.getCSSStyle(this.ua, currentStyle, CSSElement.FIRST_LINE);
			declaration.applyProperties(firstLineStyle);
			if (Display.get(firstLineStyle) != DisplayValue.NONE) {
				params.firstLineStyle = new FirstLineParams();
				this.setupLineParams(params.firstLineStyle, firstLineStyle);
			}
		}

		params.size = BoxValueUtils.toDimension(Width.get(style), Height.get(style));
		params.minSize = BoxValueUtils.toMinDimension(MinWidth.get(style), MinHeight.get(style));
		params.maxSize = BoxValueUtils.toDimension(MaxWidth.get(style), MaxHeight.get(style));
		{
			// Carry intrinsic size keywords (2026-08-29) separately only for the inline axis.
			// On the block axis, max-content/min-content/fit-content mean content height (=auto),
			// or 0/none for min/max, respectively, as specified. Converting to AUTO
			// on the Dimension side (BoxValueUtils.lengthType) is therefore sufficient.
			final boolean vertical = params.flow.isVertical();
			params.intrinsicLine = BoxValueUtils.toIntrinsicSize(vertical ? Height.get(style) : Width.get(style));
			params.intrinsicMinLine = BoxValueUtils
					.toIntrinsicSize(vertical ? MinHeight.get(style) : MinWidth.get(style));
			params.intrinsicMaxLine = BoxValueUtils
					.toIntrinsicSize(vertical ? MaxHeight.get(style) : MaxWidth.get(style));
		}
		params.boxSizing = BoxSizing.get(style);
		// aspect-ratio (2026-08-29). Specifying auto alongside it has no meaning for non-replaced boxes.
		params.aspectRatio = net.zamasoft.foliojet.css.impl.property.box.AspectRatio.get(style).getRatio();

		params.overflow = Overflow.get(style);
		params.flowRoot = style.get(Display.INFO) == DisplayValue.FLOW_ROOT_VALUE;
		// -webkit-line-clamp: N means cut off after N lines and hide the rest (2026-08-29).
		// Carry the count in BlockParams.lineClamp; TextBuilder discards lines after the Nth
		// and adds an ellipsis (true line-clamp, that evening). Keep the height limit
		// N×line-height and overflow:hidden as safeguards against leaking subsequent content
		// that does not count as lines (replaced blocks, tables, floats, etc.).
		// Used to truncate excerpts and headings on real sites; discarding it exposes
		// the entire body text, overlapping subsequent content.
		final int lineClamp = net.zamasoft.foliojet.css.impl.property.box.LineClamp.get(style);
		if (lineClamp > 0) {
			params.lineClamp = lineClamp;
			final double lineHeight = net.zamasoft.foliojet.css.impl.property.font.LineHeight.get(style);
			final double clampHeight = lineHeight * lineClamp;
			final net.zamasoft.foliojet.layout.box.params.Dimension max = params.maxSize;
			final boolean vertical = params.flow.isVertical();
			final double current = vertical ? max.getWidth() : max.getHeight();
			final net.zamasoft.foliojet.layout.box.params.LengthType currentType = vertical ? max.getWidthType()
					: max.getHeightType();
			if (currentType != net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE || current > clampHeight) {
				params.maxSize = vertical
						? net.zamasoft.foliojet.layout.box.params.Dimension.create(clampHeight, max.getHeight(),
								net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE, max.getHeightType())
						: net.zamasoft.foliojet.layout.box.params.Dimension.create(max.getWidth(), clampHeight,
								max.getWidthType(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE);
			}
			if (params.overflow == net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
				params.overflow = net.zamasoft.foliojet.layout.box.params.OverflowMode.HIDDEN;
			}
		}
		params.textOverflow = net.zamasoft.foliojet.css.impl.property.text.TextOverflow.get(style);
		params.blockAlignContent = toBlockContentAlignment(
				net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.get(style,
						net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty.ALIGN_CONTENT));
		params.paintClip = MaskImage.isClip(style);
		params.clipPath = net.zamasoft.foliojet.css.impl.property.box.ClipPath
				.toShape(net.zamasoft.foliojet.css.impl.property.box.ClipPath.get(style));
		params.frame = this.createRectFrame(style, inBody, pageSequence);

		byte columnCount = (byte) Math.min(Byte.MAX_VALUE, ColumnCount.get(style));
		double columnWidth = ColumnWidth.get(style);
		if (columnCount >= 2 || !LayoutUtils.isNone(columnWidth)) {
			params.columns = new Columns(columnCount, columnWidth, ColumnGap.get(style),
					Border.create(ColumnRuleStyle.get(style), ColumnRuleWidth.get(style), ColumnRuleColor.get(style)),
					ColumnFill.get(style));
		}
	}

	/**
	 * The align-content used value for a normal block. CSS Align §5.1.1 treats all content
	 * as a single alignment subject, so content-distribution values reduce to their
	 * respective fallbacks.
	 */
	private static net.zamasoft.foliojet.layout.box.params.BoxAlignment toBlockContentAlignment(
			final net.zamasoft.foliojet.css.value.BoxAlignmentValue value) {
		return switch (value) {
		case CENTER, SPACE_AROUND, SPACE_EVENLY ->
			net.zamasoft.foliojet.layout.box.params.BoxAlignment.CENTER;
		case END, FLEX_END -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.END;
		case START, FLEX_START -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.START;
		case AUTO, NORMAL -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.NORMAL;
		case STRETCH, SPACE_BETWEEN -> net.zamasoft.foliojet.layout.box.params.BoxAlignment.START;
		};
	}

	/**
	 * Sets up inline box parameters.
	 *
	 * @param params
	 * @param style
	 */
	void setupInlineParams(InlineParams params, CSSStyle style, boolean inBody, PageSequence pageSequence) {
		this.setupTextParams(params, style);
		params.frame = this.createRectFrame(style, inBody, pageSequence);
	}

	/**
	 * Sets up table box parameters.
	 *
	 * @param params
	 * @param style
	 */
	void setupTableParams(TableParams params, CSSStyle style, CSSStyle currentStyle, boolean inBody, PageSequence pageSequence) {
		this.setupBlockParams(params, style, currentStyle, inBody, pageSequence);
		params.borderSpacingH = BorderSpacing.getHorizontal(style);
		params.borderSpacingV = BorderSpacing.getVertical(style);
		params.borderCollapse = BorderCollapse.get(style);
		params.layout = TableLayout.get(style);
	}

	void setupInnerTableParams(InnerTableParams params, CSSStyle style) {
		this.setupParams(params, style);
		params.background = createBackground(style);
		params.border = createRectBorder(style);
		params.pageBreakInside = PageBreakInside.get(style);
	}

	/**
	 * Sets up table caption positioning.
	 *
	 * @param pos
	 * @param style
	 */
	void setupTableCaptionPos(TableCaptionPos pos, CSSStyle style, boolean rightSide) {
		this.setupFlowPos(pos, style, rightSide);
		switch (CaptionSide.get(style)) {
		case CaptionSideValue.CAPTION_SIDE_TOP:
		case CaptionSideValue.CAPTION_SIDE_BEFORE:
			pos.captionSide = CaptionSideMode.BEFORE;
			break;
		case CaptionSideValue.CAPTION_SIDE_BOTTOM:
		case CaptionSideValue.CAPTION_SIDE_AFTER:
			pos.captionSide = CaptionSideMode.AFTER;
			break;
		default:
			throw new IllegalStateException();
		}
	}

	void setupTableRowGroup(InnerTableParams params, TableRowGroupPos pos, CSSStyle style, RowGroupType rowGroupType, boolean rightSide) {
		this.setupInnerTableParams(params, style);
		if (BlockFlow.get(style.getParentStyle()).isVertical()) {
			params.size = Width.getLength(style);
			params.minSize = MinWidth.getLength(style);
			params.maxSize = MaxWidth.getLength(style);
		} else {
			params.size = Height.getLength(style);
			params.minSize = MinHeight.getLength(style);
			params.maxSize = MaxHeight.getLength(style);
		}
		pos.rowGroupType = rowGroupType;
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), rightSide);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), rightSide);
	}

	void setupTableColumn(InnerTableParams params, TableColumnPos pos, CSSStyle style) {
		this.setupInnerTableParams(params, style);
		if (BlockFlow.get(style.getParentStyle()).isVertical()) {
			params.size = Height.getLength(style);
			params.minSize = MinHeight.getLength(style);
			params.maxSize = MaxHeight.getLength(style);
		} else {
			params.size = Width.getLength(style);
			params.minSize = MinWidth.getLength(style);
			params.maxSize = MaxWidth.getLength(style);
		}

		CSSElement ce = style.getCSSElement();
		if (ce.atts == null) {
			return;
		}
		String span = ce.atts.getValue(XHTML.SPAN_ATTR.lName);
		if (span == null) {
			return;
		}
		try {
			pos.span = Integer.parseInt(span);
			if (pos.span <= 0) {
				pos.span = 1;
			}
		} catch (NumberFormatException e) {
			pos.span = 1;
		}
	}

	void setupTableRow(InnerTableParams params, TableRowPos pos, CSSStyle style, boolean rightSide) {
		this.setupInnerTableParams(params, style);
		if (BlockFlow.get(style.getParentStyle()).isVertical()) {
			params.size = Width.getLength(style);
			params.minSize = MinWidth.getLength(style);
			params.maxSize = MaxWidth.getLength(style);
		} else {
			params.size = Height.getLength(style);
			params.minSize = MinHeight.getLength(style);
			params.maxSize = MaxHeight.getLength(style);
		}
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), rightSide);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), rightSide);
	}

	void setupTableCellPos(TableCellPos pos, CSSStyle style, boolean rightSide) {
		this.setupStaticPos(pos, style);
		pos.emptyCells = EmptyCells.get(style);
		pos.verticalAlign = VerticalAlign.getForTableCell(style);
		// Detect author opt-out from avoid-like behavior between rows spanned by rowspan (manual 4550)
		// (see TableCellPos.breakInsideDeclaredAuto)
		pos.breakInsideDeclaredAuto = style.isDeclared(PageBreakInside.INFO)
				&& PageBreakInside.get(style) == net.zamasoft.foliojet.layout.box.params.PageBreakMode.AUTO;

		CSSElement ce = style.getCSSElement();
		if (ce.atts != null) {
			String colspan = ce.atts.getValue(XHTML.COLSPAN_ATTR.lName);
			if (colspan != null) {
				try {
					pos.colspan = Integer.parseInt(colspan);
					if (pos.colspan <= 0) {
						pos.colspan = 1;
					} else if (pos.colspan > MAX_COLSPAN) {
						pos.colspan = MAX_COLSPAN;
					}
				} catch (NumberFormatException e) {
					pos.colspan = 1;
				}
			}
			String rowspan = ce.atts.getValue(XHTML.ROWSPAN_ATTR.lName);
			if (rowspan != null) {
				try {
					pos.rowspan = Integer.parseInt(rowspan);
					if (pos.rowspan <= 0) {
						pos.rowspan = 1;
					} else if (pos.rowspan > MAX_ROWSPAN) {
						pos.rowspan = MAX_ROWSPAN;
					}
				} catch (NumberFormatException e) {
					pos.rowspan = 1;
				}
			}
		}
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), rightSide);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), rightSide);
		final byte position = CSSPosition.get(style);
		if (position == PositionValue.STICKY) {
			pos.offset = Offset.ZERO_OFFSET;
		} else if (position != PositionValue.STATIC) {
			pos.offset = this.createRelativeOffset(style);
		}
	}

	/**
	 * Builds the background.
	 *
	 * @param style
	 * @return
	 */
	static Background createBackground(CSSStyle style) {
		final Image maskImage = MaskImage.getImage(style);
		PaintValue backgroundPaint = BackgroundColor.get(style);
		// background-image layers (multilayer support added on 2026-08-29). First is frontmost.
		// Stack gradients as paint (PaintLayer) and url() as images over the background color
		// from last to first; colors/images below show through translucent gradients.
		final net.zamasoft.foliojet.css.value.Value[] values = BackgroundImage.getLayers(style);
		final java.util.List<Background.Layer> layers = new java.util.ArrayList<Background.Layer>(values.length);
		final java.util.List<Integer> sourceLayerIndexes = new java.util.ArrayList<Integer>(values.length);
		for (int layerIndex = 0; layerIndex < values.length; ++layerIndex) {
			final net.zamasoft.foliojet.css.value.Value value = values[layerIndex];
			if (value instanceof PaintValue paint) {
				layers.add(new Background.PaintLayer(paint));
				sourceLayerIndexes.add(layerIndex);
			} else if (value instanceof net.zamasoft.foliojet.css.value.URIValue uri) {
				final Image image = BackgroundImage.load(style, uri);
				if (image != null) {
					layers.add(net.zamasoft.foliojet.layout.box.params.BackgroundImage.create(image,
							BackgroundRepeat.get(style), BackgroundAttachment.get(style), BackgroundPosition.get(style),
							BackgroundSize.get(style, image), BackgroundSize.getFit(style, image)));
					sourceLayerIndexes.add(layerIndex);
				}
			}
		}
		net.zamasoft.foliojet.layout.box.params.BackgroundImage backgroundImage;
		if (!layers.isEmpty()) {
			final byte[] declaredOrigins = BackgroundOrigin.get(style);
			final net.zamasoft.pdfg2d.gc.paint.BlendMode[] declaredBlendModes = BackgroundBlendMode.get(style);
			final byte[] origins = new byte[layers.size()];
			final net.zamasoft.pdfg2d.gc.paint.BlendMode[] blendModes = new net.zamasoft.pdfg2d.gc.paint.BlendMode[layers
					.size()];
			for (int i = 0; i < sourceLayerIndexes.size(); ++i) {
				final int source = sourceLayerIndexes.get(i);
				origins[i] = declaredOrigins[source % declaredOrigins.length];
				blendModes[i] = declaredBlendModes[source % declaredBlendModes.length];
			}
			return Background.create(backgroundPaint, layers.toArray(new Background.Layer[layers.size()]),
					BackgroundClip.get(style), origins, blendModes);
		} else if (maskImage != null) {
			// For a single-color SVG URL mask, bake the background color into SVG currentColor
			// and draw directly. Do not retain a background-color rectangle.
			// Honor mask-size/position/repeat when specified
			// (2026-08-29). Otherwise, continue filling the box without repetition,
			// which fits icon cutout examples better than the specified defaults (intrinsic size,
			// repeat); an approximation verified on MDN and elsewhere.
			final boolean declared = !net.zamasoft.foliojet.css.impl.property.box.MaskSize.isDefault(style)
					|| !net.zamasoft.foliojet.css.impl.property.box.MaskPosition.isDefault(style)
					|| !net.zamasoft.foliojet.css.impl.property.box.MaskRepeat.isDefault(style);
			if (declared) {
				backgroundImage = net.zamasoft.foliojet.layout.box.params.BackgroundImage.create(maskImage,
						net.zamasoft.foliojet.css.impl.property.box.MaskRepeat.get(style),
						net.zamasoft.foliojet.layout.box.params.BackgroundImage.ATTACHMENT_SCROLL,
						net.zamasoft.foliojet.css.impl.property.box.MaskPosition.get(style),
						net.zamasoft.foliojet.css.impl.property.box.MaskSize.get(style, maskImage),
						net.zamasoft.foliojet.css.impl.property.box.MaskSize.getFit(style, maskImage));
			} else {
				backgroundImage = net.zamasoft.foliojet.layout.box.params.BackgroundImage.create(maskImage,
						net.zamasoft.foliojet.layout.box.params.BackgroundImage.REPEAT_NO,
						net.zamasoft.foliojet.layout.box.params.BackgroundImage.ATTACHMENT_SCROLL,
						BackgroundPosition.get(style), BackgroundSize.get(style, maskImage), BackgroundFit.COVER);
			}
			backgroundPaint = null;
		} else {
			backgroundImage = null;
		}
		if (maskImage != null) {
			byte clip = BackgroundClip.get(style);
			final net.zamasoft.foliojet.css.impl.property.box.MaskClip.ClipValue maskClip = net.zamasoft.foliojet.css.impl.property.box.MaskClip
					.get(style);
			if (maskClip != net.zamasoft.foliojet.css.impl.property.box.MaskClip.ClipValue.BORDER_BOX
					&& maskClip.isPaintSupported()) {
				clip = maskClip.getBackgroundClip();
			}
			final net.zamasoft.foliojet.css.impl.property.box.MaskOrigin.OriginValue maskOrigin = net.zamasoft.foliojet.css.impl.property.box.MaskOrigin
					.get(style);
			if (maskOrigin != net.zamasoft.foliojet.css.impl.property.box.MaskOrigin.OriginValue.BORDER_BOX
					&& maskOrigin.isPaintSupported()) {
				return Background.create(backgroundPaint, backgroundImage, clip,
						new byte[] { maskOrigin.getBackgroundOrigin() });
			}
			// For the initial mask-origin value, retain the previous positioning path and preserve existing output.
			return Background.create(backgroundPaint, backgroundImage, clip);
		}
		return Background.create(backgroundPaint, backgroundImage, BackgroundClip.get(style));
	}

	/**
	 * Builds the rectangular border.
	 *
	 * @param style
	 * @return
	 */
	static RectBorder createRectBorder(CSSStyle style) {
		final Border top = Border.create(BorderStyle.get(style, Side.TOP), BorderWidth.get(style, Side.TOP),
				BorderColor.get(style, Side.TOP));
		final Border right = Border.create(BorderStyle.get(style, Side.RIGHT), BorderWidth.get(style, Side.RIGHT),
				BorderColor.get(style, Side.RIGHT));
		final Border bottom = Border.create(BorderStyle.get(style, Side.BOTTOM), BorderWidth.get(style, Side.BOTTOM),
				BorderColor.get(style, Side.BOTTOM));
		final Border left = Border.create(BorderStyle.get(style, Side.LEFT), BorderWidth.get(style, Side.LEFT),
				BorderColor.get(style, Side.LEFT));

		final Radius topLeft = BorderRadius.get(style, Corner.TOP_LEFT);
		final Radius topRight = BorderRadius.get(style, Corner.TOP_RIGHT);
		final Radius bottomLeft = BorderRadius.get(style, Corner.BOTTOM_LEFT);
		final Radius bottomRight = BorderRadius.get(style, Corner.BOTTOM_RIGHT);

		final net.zamasoft.foliojet.layout.box.params.BorderImage borderImage = createBorderImage(style);
		final RectBorder border = RectBorder.create(top, right, bottom, left, topLeft, topRight, bottomLeft,
				bottomRight, borderImage);
		return border;
	}

	/** Maps computed border-image values to parameters with units for the drawing layer. */
	private static net.zamasoft.foliojet.layout.box.params.BorderImage createBorderImage(CSSStyle style) {
		final Value declaredSource = net.zamasoft.foliojet.css.impl.property.border.BorderImageSource.get(style);
		final net.zamasoft.foliojet.layout.box.params.BorderImage.Source source;
		if (declaredSource instanceof URIValue uri) {
			final Image image = BackgroundImage.load(style, uri);
			if (image == null) {
				return null;
			}
			source = new net.zamasoft.foliojet.layout.box.params.BorderImage.ImageSource(image);
		} else if (declaredSource instanceof PaintValue paint) {
			source = new net.zamasoft.foliojet.layout.box.params.BorderImage.PaintSource(paint);
		} else {
			// none. Keeping this null leaves the existing border parameters and drawing path
			// completely unchanged.
			return null;
		}

		final net.zamasoft.foliojet.css.value.BorderImageSliceValue slice = net.zamasoft.foliojet.css.impl.property.border.BorderImageSlice
				.get(style);
		final net.zamasoft.foliojet.css.value.BorderImageWidthValue width = net.zamasoft.foliojet.css.impl.property.border.BorderImageWidth
				.get(style);
		final net.zamasoft.foliojet.css.value.BorderImageOutsetValue outset = net.zamasoft.foliojet.css.impl.property.border.BorderImageOutset
				.get(style);
		final net.zamasoft.foliojet.css.value.BorderImageRepeatValue repeat = net.zamasoft.foliojet.css.impl.property.border.BorderImageRepeat
				.get(style);
		return new net.zamasoft.foliojet.layout.box.params.BorderImage(source,
				borderImageSliceQuad(style, slice.top(), slice.right(), slice.bottom(), slice.left()), slice.fill(),
				borderImageQuad(width.top(), width.right(), width.bottom(), width.left()),
				borderImageQuad(outset.top(), outset.right(), outset.bottom(), outset.left()),
				borderImageRepeat(repeat.horizontal()), borderImageRepeat(repeat.vertical()));
	}

	/**
	 * The four {@code border-image-slice} values.
	 *
	 * <p>
	 * Numbers are <b>image pixel counts</b>, so convert them to pt here: the drawing layer
	 * can only compare against {@code Image#getWidth()} (pt) and does not know the UA resolution.
	 * After conversion, treat them uniformly with percentages as positions in image coordinates.
	 */
	private static net.zamasoft.foliojet.layout.box.params.BorderImage.Quad borderImageSliceQuad(CSSStyle style,
			Value top, Value right, Value bottom, Value left) {
		return new net.zamasoft.foliojet.layout.box.params.BorderImage.Quad(borderImageSlice(style, top),
				borderImageSlice(style, right), borderImageSlice(style, bottom), borderImageSlice(style, left));
	}

	private static net.zamasoft.foliojet.layout.box.params.BorderImage.Component borderImageSlice(CSSStyle style,
			Value value) {
		if (value instanceof net.zamasoft.foliojet.css.value.RealValue number) {
			return net.zamasoft.foliojet.layout.box.params.BorderImage.Component
					.absolute(net.zamasoft.foliojet.css.util.LengthUtils.convert(style.getUserAgent(),
							number.getReal(), net.zamasoft.foliojet.css.token.Unit.PX,
							net.zamasoft.foliojet.css.token.Unit.PT));
		}
		return borderImageComponent(value);
	}

	private static net.zamasoft.foliojet.layout.box.params.BorderImage.Quad borderImageQuad(Value top, Value right,
			Value bottom, Value left) {
		return new net.zamasoft.foliojet.layout.box.params.BorderImage.Quad(borderImageComponent(top),
				borderImageComponent(right), borderImageComponent(bottom), borderImageComponent(left));
	}

	private static net.zamasoft.foliojet.layout.box.params.BorderImage.Component borderImageComponent(Value value) {
		if (value instanceof net.zamasoft.foliojet.css.value.RealValue number) {
			return net.zamasoft.foliojet.layout.box.params.BorderImage.Component.number(number.getReal());
		}
		if (value instanceof AbsoluteLengthValue length) {
			return net.zamasoft.foliojet.layout.box.params.BorderImage.Component.absolute(length.getLength());
		}
		if (value instanceof PercentageValue percentage) {
			return net.zamasoft.foliojet.layout.box.params.BorderImage.Component.relative(percentage.getRatio());
		}
		if (value instanceof CalcLengthValue calc) {
			return net.zamasoft.foliojet.layout.box.params.BorderImage.Component.mixed(calc.getAbsolute(),
					calc.getRatio());
		}
		if (value == KeywordValue.AUTO) {
			return net.zamasoft.foliojet.layout.box.params.BorderImage.Component.auto();
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	private static net.zamasoft.foliojet.layout.box.params.BorderImage.Repeat borderImageRepeat(
			net.zamasoft.foliojet.css.value.BorderImageRepeatValue.Mode repeat) {
		return switch (repeat) {
		case STRETCH -> net.zamasoft.foliojet.layout.box.params.BorderImage.Repeat.STRETCH;
		case REPEAT -> net.zamasoft.foliojet.layout.box.params.BorderImage.Repeat.REPEAT;
		case ROUND -> net.zamasoft.foliojet.layout.box.params.BorderImage.Repeat.ROUND;
		case SPACE -> net.zamasoft.foliojet.layout.box.params.BorderImage.Repeat.SPACE;
		};
	}

	/**
	 * Builds the rectangular frame.
	 *
	 * @param style
	 * @return
	 */
	RectFrame createRectFrame(CSSStyle style, boolean inBody, PageSequence pageSequence) {
		RectBorder border = createRectBorder(style);
		Background background = createBackground(style);

		// HTML/BODY tags
		if (!inBody) {
			CSSElement ce = style.getCSSElement();
			if (XHTML.HTML_ELEM.equalsElement(ce) || XHTML.BODY_ELEM.equalsElement(ce)) {
				// Background handling
				// This is closer to KHTML than to IE, Opera, or Firefox.
				if (pageSequence.promoteRootBackground(background)) {
					background = Background.NULL_BACKGROUND;
				}
				pageSequence.setProgression(BlockFlow.get(style));
			}
		}

		// Margins
		final Insets margin;
		{
			Value top = Margin.get(style, Side.TOP);
			Value right = Margin.get(style, Side.RIGHT);
			Value bottom = Margin.get(style, Side.BOTTOM);
			Value left = Margin.get(style, Side.LEFT);
			margin = BoxValueUtils.toInsets(top, right, bottom, left);
		}

		// Padding
		final Insets padding;
		{
			Value top = Padding.get(style, Side.TOP);
			Value right = Padding.get(style, Side.RIGHT);
			Value bottom = Padding.get(style, Side.BOTTOM);
			Value left = Padding.get(style, Side.LEFT);
			padding = BoxValueUtils.toInsets(top, right, bottom, left);
		}
		RectFrame frame = RectFrame.create(margin, border, background, padding,
				net.zamasoft.foliojet.css.impl.property.box.BoxShadow.get(style), createOutline(style));
		return frame;
	}

	/**
	 * Builds the outline (2026-08-29). Null if invisible.
	 */
	static net.zamasoft.foliojet.layout.box.params.Outline createOutline(CSSStyle style) {
		return net.zamasoft.foliojet.layout.box.params.Outline.create(
				net.zamasoft.foliojet.css.impl.property.border.OutlineStyle.get(style),
				net.zamasoft.foliojet.css.impl.property.border.OutlineWidth.get(style),
				net.zamasoft.foliojet.css.impl.property.border.OutlineColor.get(style),
				net.zamasoft.foliojet.css.impl.property.border.OutlineOffset.get(style));
	}

	/**
	 * Builds the relative position.
	 *
	 * @param style
	 * @return
	 */
	Offset createRelativeOffset(CSSStyle style) {
		Value top = Inset.get(style, Side.TOP);
		Value right = Inset.get(style, Side.RIGHT);
		Value bottom = Inset.get(style, Side.BOTTOM);
		Value left = Inset.get(style, Side.LEFT);

		final double x, xRatio, y, yRatio;
		final LengthType xType, yType;

		if (top instanceof AbsoluteLengthValue length) {
			yType = LengthType.ABSOLUTE;
			y = length.getLength();
			yRatio = 0;
		} else if (top instanceof PercentageValue percentage) {
			yType = LengthType.RELATIVE;
			y = percentage.getRatio();
			yRatio = 0;
		} else if (top instanceof CalcLengthValue calc) {
			yType = LengthType.MIXED;
			y = calc.getAbsolute();
			yRatio = calc.getRatio();
		} else if (top == KeywordValue.AUTO) {
			if (bottom instanceof AbsoluteLengthValue length) {
				yType = LengthType.ABSOLUTE;
				y = -length.getLength();
				yRatio = 0;
			} else if (bottom instanceof PercentageValue percentage) {
				yType = LengthType.RELATIVE;
				y = -percentage.getRatio();
				yRatio = 0;
			} else if (bottom instanceof CalcLengthValue calc) {
				yType = LengthType.MIXED;
				y = -calc.getAbsolute();
				yRatio = -calc.getRatio();
			} else if (bottom == KeywordValue.AUTO) {
				yType = LengthType.AUTO;
				y = 0;
				yRatio = 0;
			} else {
				throw new IllegalStateException(String.valueOf(bottom));
			}
		} else {
			throw new IllegalStateException(String.valueOf(top));
		}

		if (left instanceof AbsoluteLengthValue length) {
			xType = LengthType.ABSOLUTE;
			x = length.getLength();
			xRatio = 0;
		} else if (left instanceof PercentageValue percentage) {
			xType = LengthType.RELATIVE;
			x = percentage.getRatio();
			xRatio = 0;
		} else if (left instanceof CalcLengthValue calc) {
			xType = LengthType.MIXED;
			x = calc.getAbsolute();
			xRatio = calc.getRatio();
		} else if (left == KeywordValue.AUTO) {
			if (right instanceof AbsoluteLengthValue length) {
				xType = LengthType.ABSOLUTE;
				x = -length.getLength();
				xRatio = 0;
			} else if (right instanceof PercentageValue percentage) {
				xType = LengthType.RELATIVE;
				x = -percentage.getRatio();
				xRatio = 0;
			} else if (right instanceof CalcLengthValue calc) {
				xType = LengthType.MIXED;
				x = -calc.getAbsolute();
				xRatio = -calc.getRatio();
			} else if (right == KeywordValue.AUTO) {
				xType = LengthType.AUTO;
				x = 0;
				xRatio = 0;
			} else {
				throw new IllegalStateException(String.valueOf(right));
			}
		} else {
			throw new IllegalStateException(String.valueOf(left));
		}

		return Offset.create(x, xRatio, y, yRatio, xType, yType);
	}

	PageBreakMode toPageBreak(byte pageBreak, boolean rightSide) {
		switch (pageBreak) {
		case PageBreakValue.PAGE_BREAK_AUTO:
			return PageBreakMode.AUTO;
		case PageBreakValue.PAGE_BREAK_AVOID:
			return PageBreakMode.AVOID;
		case PageBreakValue.PAGE_BREAK_ALWAYS:
			return PageBreakMode.PAGE;
		case PageBreakValue.PAGE_BREAK_LEFT:
			// 2026-07-20: use only rightSide after removal of -cssj-direction-mode
			if (rightSide) {
				return PageBreakMode.RECTO;
			}
			return PageBreakMode.VERSO;
		case PageBreakValue.PAGE_BREAK_RIGHT:
			if (rightSide) {
				return PageBreakMode.VERSO;
			}
			return PageBreakMode.RECTO;
		case PageBreakValue.PAGE_BREAK_IF_LEFT:
			if (rightSide) {
				return PageBreakMode.IF_RECTO;
			}
			return PageBreakMode.IF_VERSO;
		case PageBreakValue.PAGE_BREAK_IF_RIGHT:
			if (rightSide) {
				return PageBreakMode.IF_VERSO;
			}
			return PageBreakMode.IF_RECTO;
		case PageBreakValue.PAGE_BREAK_PAGE:
			return PageBreakMode.PAGE;
		case PageBreakValue.PAGE_BREAK_COLUMN:
			return PageBreakMode.COLUMN;
		// case PageBreakValue.PAGE_BREAK_AVOID_PAGE:
		// return Types.PAGE_BREAK_AVOID_PAGE;
		// case PageBreakValue.PAGE_BREAK_AVOID_COLUMN:
		// return Types.PAGE_BREAK_AVOID_COLUMN;
		case PageBreakValue.PAGE_BREAK_VERSO:
			return PageBreakMode.VERSO;
		case PageBreakValue.PAGE_BREAK_RECTO:
			return PageBreakMode.RECTO;
		case PageBreakValue.PAGE_BREAK_IF_VERSO:
			return PageBreakMode.IF_VERSO;
		case PageBreakValue.PAGE_BREAK_IF_RECTO:
			return PageBreakMode.IF_RECTO;
		default:
			throw new IllegalStateException();
		}

	}

}
