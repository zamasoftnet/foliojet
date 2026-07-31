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
import net.zamasoft.foliojet.css.value.ContentFunctionValue;
import net.zamasoft.foliojet.css.value.CounterSetValue;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.ListStylePositionValue;
import net.zamasoft.foliojet.css.value.PageBreakValue;
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
import net.zamasoft.foliojet.css.impl.property.box.BoxSizing;
import net.zamasoft.foliojet.css.impl.property.column.ColumnCount;
import net.zamasoft.foliojet.css.impl.property.column.ColumnFill;
import net.zamasoft.foliojet.css.impl.property.column.ColumnGap;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleColor;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleStyle;
import net.zamasoft.foliojet.css.impl.property.column.ColumnRuleWidth;
import net.zamasoft.foliojet.css.impl.property.column.ColumnSpan;
import net.zamasoft.foliojet.css.impl.property.column.ColumnWidth;
import net.zamasoft.foliojet.css.impl.property.box.Opacity;
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
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Background;
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
import net.zamasoft.foliojet.layout.util.IntList;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.TextUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.NamedStringState;
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
 * CSSの計算値を Params / Pos / RectFrame / Background / RectBorder へ
 * 写像する群です(StyleBuilder解体・増分3で抽出、2026-07-30。
 * 各メソッドの本体はStyleBuilderから逐語移動——挙動不変)。
 *
 * <p>
 * 状態を持たない(ua/styleContextの参照のみ)。呼び出し時点の
 * 文脈(currentStyle/rightSide/inBody)は引数で受け取る。
 * </p>
 */
final class BoxStyleMapper {
	/**
	 * {@code colspan}の上限です。HTML Standardが定める値(実ブラウザと同じ)。
	 *
	 * <p>
	 * 上限がないと、{@code colspan="2147483647"}のセル1個で
	 * {@code IncrementalTableBuilder}が約21億回の要素追加を行い、停止前に
	 * メモリを使い尽くします(2026-07-25、独立レビューで発見)。
	 * 負・0・非数値の正規化は以前からあったが、<b>巨大な正数だけが素通り</b>
	 * していた。上限値は「世界の標準動向に対応物があるか」の基準で
	 * HTML Standardに揃える。
	 * </p>
	 */
	private static final int MAX_COLSPAN = 1000;

	/**
	 * {@code rowspan}の上限です。HTML Standardが定める値(実ブラウザと同じ)。
	 *
	 * <p>
	 * 上限がないと、{@code rowspan="2147483647"}で
	 * {@code CollapsedBorderRules.streamSpacing}の
	 * {@code borderRow + rowspan - 1}が<b>intオーバーフローで負値</b>になり、
	 * {@code List.get(負値)}で{@code IndexOutOfBoundsException}になります
	 * (2026-07-25、独立レビューで発見)。
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
	 * 相対配置可能な配置の設定します。
	 * 
	 * @param pos
	 * @param style
	 */
	void setupStaticPos(final AbstractStaticPos pos, final CSSStyle style) {
		if (CSSPosition.get(style) != PositionValue.STATIC) {
			pos.offset = this.createRelativeOffset(style);
		}
	}

	/**
	 * インライン配置の設定をします。
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
	 * 絶対配置の設定をします。
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

			case DisplayValue.BLOCK:
			case DisplayValue.TABLE:
			case DisplayValue.LIST_ITEM:
				pos.autoPosition = AutoPosition.BLOCK;
				break;
			default:
				throw new IllegalStateException(style.get(Display.INFO).toString());
			}
			break;
		case PositionValue.FIXED:
			pos.fiducial = Fiducial.ALL_PAGE;
			pos.autoPosition = AutoPosition.BLOCK;
			break;
		}
	}

	/**
	 * 通常のフロー配置の設定をします。
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
	}

	/**
	 * 浮動配置の設定をします。
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
	}

	/**
	 * ボックスの基本パラメータを設定します。
	 * 
	 * @param params
	 * @param style
	 */
	/**
	 * Gridコンテナのパラメータを設定します(Grid G0)。
	 */
	void setupGridParams(net.zamasoft.foliojet.layout.box.params.GridParams params, CSSStyle style,
			CSSStyle parentStyle, boolean inBody, PageSequence pageSequence) {
		this.setupBlockParams(params, style, parentStyle, inBody, pageSequence);
		params.templateColumns = net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.getColumns(style)
				.getTracks();
		params.templateRows = net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.getRows(style)
				.getTracks();
		params.rowGap = net.zamasoft.foliojet.css.impl.property.grid.RowGap.get(style);
		params.columnGap = net.zamasoft.foliojet.css.impl.property.column.ColumnGap.getForGrid(style);
	}

	void setupParams(Params params, CSSStyle style) {
		params.element = style.getCSSElement();
		params.footnoteId = style.footnoteId;
		if (Visibility.get(style) == VisibilityValue.VISIBLE) {
			params.opacity = Opacity.get(style);
		} else {
			params.opacity = 0f;
		}
		params.transform = Transform.get(style);
		params.transformOrigin = TransformOrigin.get(style);
		params.zIndexType = ZIndex.getType(style);
		if (params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			params.zIndexValue = ZIndex.getValue(style);
		}
	}

	/**
	 * テキストボックスのパラメータを設定します。
	 * 
	 * @param params
	 * @param style
	 */
	void setupTextParams(AbstractTextParams params, CSSStyle style) {
		this.setupParams(params, style);
		params.whiteSpace = WhiteSpace.get(style);
		params.wordWrap = WordWrap.get(style);
		params.textWrapStyle = TextWrapStyle.get(style);
		params.color = TextFillColor.get(style);
		params.decoration = TextDecoration.get(style);
		params.decorationThickness = 1.0 / style.getUserAgent().getFontSize(AbsoluteFontSize.MEDIUM) / 2.0;
		params.textStrokeWidth = TextStrokeWidth.get(style);
		params.textStrokeColor = TextStrokeColor.get(style);
		params.textShadows = TextShadow.get(style);
		params.letterSpacing = LetterSpacing.get(style);
		params.wordSpacing = WordSpacing.get(style);
		params.textTransform = TextTransform.get(style);
		params.fontStyle = style.getFontStyle();
		params.fontManager = this.ua.getFontManager();
		final LanguageProfile lang = LanguageProfileBundle
				.getLanguageProfile(style.getCSSElement().lang);
		params.lineBreakRules = lang.getLineBreakRules(style);
		params.hyphens = Hyphens.get(style);
		if (params.hyphens == AbstractTextParams.HYPHENS_AUTO) {
			params.hyphenator = WordHyphenatorBundle.getHyphenator(style.getCSSElement().lang);
		}
		params.direction = Direction.get(style);
		params.flow = BlockFlow.get(style);
		// ルビ役割マーカーをparamsへ載せ、文字処理層(StyledTextUnitizer)へ
		// 配達する(注釈付きテキスト方式、2026-07-25仕様裁定)。
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
		default:
			break;
		}
	}

	/**
	 * 置換可能ボックスのパラメータを設定します。
	 * 
	 * @param src
	 * @param params
	 * @param style
	 */
	void setupReplacedParams(Image image, ReplacedParams params, CSSStyle style, boolean inBody, PageSequence pageSequence) {
		this.setupTextParams(params, style);
		params.image = image;

		params.size = BoxValueUtils.toDimension(Width.get(style), Height.get(style));
		params.minSize = BoxValueUtils.toDimension(MinWidth.get(style), MinHeight.get(style));
		params.maxSize = BoxValueUtils.toDimension(MaxWidth.get(style), MaxHeight.get(style));
		params.boxSizing = BoxSizing.get(style);

		params.frame = this.createRectFrame(style, inBody, pageSequence);
		params.color = CSSColor.get(style);
		params.lineHeight = LineHeight.get(style);
	}

	/**
	 * 行ボックスのパラメータを設定します。
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
	 * 行ボックスのパラメータを設定します。
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
		params.minSize = BoxValueUtils.toDimension(MinWidth.get(style), MinHeight.get(style));
		params.maxSize = BoxValueUtils.toDimension(MaxWidth.get(style), MaxHeight.get(style));
		params.boxSizing = BoxSizing.get(style);

		params.overflow = Overflow.get(style);
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
	 * インラインボックスのパラメータを設定します。
	 * 
	 * @param params
	 * @param style
	 */
	void setupInlineParams(InlineParams params, CSSStyle style, boolean inBody, PageSequence pageSequence) {
		this.setupTextParams(params, style);
		params.frame = this.createRectFrame(style, inBody, pageSequence);
	}

	/**
	 * テーブルボックスのパラメータを設定します。
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
	 * テーブルキャプション配置の設定をします。
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
		if (CSSPosition.get(style) != PositionValue.STATIC) {
			pos.offset = this.createRelativeOffset(style);
		}
	}

	/**
	 * 背景を構築します。
	 * 
	 * @param style
	 * @return
	 */
	static Background createBackground(CSSStyle style) {
		Image image = BackgroundImage.get(style);
		net.zamasoft.foliojet.layout.box.params.BackgroundImage backgroundImage;
		if (image != null) {
			backgroundImage = net.zamasoft.foliojet.layout.box.params.BackgroundImage.create(image, BackgroundRepeat.get(style),
					BackgroundAttachment.get(style), BackgroundPosition.get(style), BackgroundSize.get(style, image));
		} else {
			backgroundImage = null;
		}
		Background background = Background.create(BackgroundColor.get(style), backgroundImage, BackgroundClip.get(style));
		return background;
	}

	/**
	 * 矩形境界を構築します。
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

		final RectBorder border = RectBorder.create(top, right, bottom, left, topLeft, topRight, bottomLeft,
				bottomRight);
		return border;
	}

	/**
	 * 矩形枠を構築します。
	 * 
	 * @param style
	 * @return
	 */
	RectFrame createRectFrame(CSSStyle style, boolean inBody, PageSequence pageSequence) {
		RectBorder border = createRectBorder(style);
		Background background = createBackground(style);

		// HTML/BODYタグ
		if (!inBody) {
			CSSElement ce = style.getCSSElement();
			if (XHTML.HTML_ELEM.equalsElement(ce) || XHTML.BODY_ELEM.equalsElement(ce)) {
				// 背景の扱い
				// これはIE, Opera, FirefoxよりもKHTMLに近いものです。
				if (pageSequence.promoteRootBackground(background)) {
					background = Background.NULL_BACKGROUND;
				}
				pageSequence.setProgression(BlockFlow.get(style));
			}
		}

		// マージン
		final Insets margin;
		{
			Value top = Margin.get(style, Side.TOP);
			Value right = Margin.get(style, Side.RIGHT);
			Value bottom = Margin.get(style, Side.BOTTOM);
			Value left = Margin.get(style, Side.LEFT);
			margin = BoxValueUtils.toInsets(top, right, bottom, left);
		}

		// パディング
		final Insets padding;
		{
			Value top = Padding.get(style, Side.TOP);
			Value right = Padding.get(style, Side.RIGHT);
			Value bottom = Padding.get(style, Side.BOTTOM);
			Value left = Padding.get(style, Side.LEFT);
			padding = BoxValueUtils.toInsets(top, right, bottom, left);
		}
		RectFrame frame = RectFrame.create(margin, border, background, padding);
		return frame;
	}

	/**
	 * 相対位置を構築します。
	 * 
	 * @param style
	 * @return
	 */
	Offset createRelativeOffset(CSSStyle style) {
		Value top = Inset.get(style, Side.TOP);
		Value right = Inset.get(style, Side.RIGHT);
		Value bottom = Inset.get(style, Side.BOTTOM);
		Value left = Inset.get(style, Side.LEFT);

		final double x, y;
		final LengthType xType, yType;

		if (top instanceof AbsoluteLengthValue length) {
			yType = LengthType.ABSOLUTE;
			y = length.getLength();
		} else if (top instanceof PercentageValue percentage) {
			yType = LengthType.RELATIVE;
			y = percentage.getRatio();
		} else if (top == KeywordValue.AUTO) {
			if (bottom instanceof AbsoluteLengthValue length) {
				yType = LengthType.ABSOLUTE;
				y = -length.getLength();
			} else if (bottom instanceof PercentageValue percentage) {
				yType = LengthType.RELATIVE;
				y = -percentage.getRatio();
			} else if (bottom == KeywordValue.AUTO) {
				yType = LengthType.AUTO;
				y = 0;
			} else {
				throw new IllegalStateException(String.valueOf(bottom));
			}
		} else {
			throw new IllegalStateException(String.valueOf(top));
		}

		if (left instanceof AbsoluteLengthValue length) {
			xType = LengthType.ABSOLUTE;
			x = length.getLength();
		} else if (left instanceof PercentageValue percentage) {
			xType = LengthType.RELATIVE;
			x = percentage.getRatio();
		} else if (left == KeywordValue.AUTO) {
			if (right instanceof AbsoluteLengthValue length) {
				xType = LengthType.ABSOLUTE;
				x = -length.getLength();
			} else if (right instanceof PercentageValue percentage) {
				xType = LengthType.RELATIVE;
				x = -percentage.getRatio();
			} else if (right == KeywordValue.AUTO) {
				xType = LengthType.AUTO;
				x = 0;
			} else {
				throw new IllegalStateException(String.valueOf(right));
			}
		} else {
			throw new IllegalStateException(String.valueOf(left));
		}

		return Offset.create(x, y, xType, yType);
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
			// 2026-07-20、-cssj-direction-mode廃止によりrightSideのみで判定
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
