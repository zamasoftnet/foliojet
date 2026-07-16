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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.CSSStyleSheet;
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
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.css.value.VisibilityValue;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.css.value.ext.CSSJFirstHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJLastHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJPageRefValue;
import net.zamasoft.foliojet.css.value.ext.CSSJRubyValue;
import net.zamasoft.foliojet.css.value.ext.CSSJTitleValue;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundColor;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundImage;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundPosition;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.impl.css.property.table.BorderCollapse;
import net.zamasoft.foliojet.impl.css.property.table.BorderSpacing;
import net.zamasoft.foliojet.impl.css.property.text.CSSColor;
import net.zamasoft.foliojet.impl.css.property.box.CSSFloat;
import net.zamasoft.foliojet.impl.css.property.box.CSSPosition;
import net.zamasoft.foliojet.impl.css.property.table.CaptionSide;
import net.zamasoft.foliojet.impl.css.property.box.Clear;
import net.zamasoft.foliojet.impl.css.property.content.Content;
import net.zamasoft.foliojet.impl.css.property.content.CounterIncrement;
import net.zamasoft.foliojet.impl.css.property.content.CounterReset;
import net.zamasoft.foliojet.impl.css.property.text.Direction;
import net.zamasoft.foliojet.impl.css.property.box.Display;
import net.zamasoft.foliojet.impl.css.property.table.EmptyCells;
import net.zamasoft.foliojet.impl.css.property.font.FontSize;
import net.zamasoft.foliojet.impl.css.property.box.Height;
import net.zamasoft.foliojet.impl.css.property.text.Hyphens;
import net.zamasoft.foliojet.impl.css.property.text.LetterSpacing;
import net.zamasoft.foliojet.impl.css.property.font.LineHeight;
import net.zamasoft.foliojet.impl.css.property.content.ListStyleImage;
import net.zamasoft.foliojet.impl.css.property.content.ListStylePosition;
import net.zamasoft.foliojet.impl.css.property.content.ListStyleType;
import net.zamasoft.foliojet.impl.css.property.box.MaxHeight;
import net.zamasoft.foliojet.impl.css.property.box.MaxWidth;
import net.zamasoft.foliojet.impl.css.property.box.MinHeight;
import net.zamasoft.foliojet.impl.css.property.box.MinWidth;
import net.zamasoft.foliojet.impl.css.property.page.Orphans;
import net.zamasoft.foliojet.impl.css.property.box.Overflow;
import net.zamasoft.foliojet.impl.css.property.page.PageBreakAfter;
import net.zamasoft.foliojet.impl.css.property.page.PageBreakBefore;
import net.zamasoft.foliojet.impl.css.property.page.PageBreakInside;
import net.zamasoft.foliojet.impl.css.property.content.Quotes;
import net.zamasoft.foliojet.impl.css.property.table.TableLayout;
import net.zamasoft.foliojet.impl.css.property.text.TextAlign;
import net.zamasoft.foliojet.impl.css.property.text.TextDecoration;
import net.zamasoft.foliojet.impl.css.property.text.TextIndent;
import net.zamasoft.foliojet.impl.css.property.text.TextTransform;
import net.zamasoft.foliojet.impl.css.property.box.VerticalAlign;
import net.zamasoft.foliojet.impl.css.property.box.Visibility;
import net.zamasoft.foliojet.impl.css.property.text.WhiteSpace;
import net.zamasoft.foliojet.impl.css.property.page.Widows;
import net.zamasoft.foliojet.impl.css.property.box.Width;
import net.zamasoft.foliojet.impl.css.property.text.WordSpacing;
import net.zamasoft.foliojet.impl.css.property.box.ZIndex;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundClip;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundSize;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.box.BoxSizing;
import net.zamasoft.foliojet.impl.css.property.column.ColumnCount;
import net.zamasoft.foliojet.impl.css.property.column.ColumnFill;
import net.zamasoft.foliojet.impl.css.property.column.ColumnGap;
import net.zamasoft.foliojet.impl.css.property.column.ColumnRuleColor;
import net.zamasoft.foliojet.impl.css.property.column.ColumnRuleStyle;
import net.zamasoft.foliojet.impl.css.property.column.ColumnRuleWidth;
import net.zamasoft.foliojet.impl.css.property.column.ColumnSpan;
import net.zamasoft.foliojet.impl.css.property.column.ColumnWidth;
import net.zamasoft.foliojet.impl.css.property.box.Opacity;
import net.zamasoft.foliojet.impl.css.property.text.TextAlignLast;
import net.zamasoft.foliojet.impl.css.property.text.TextEmphasisColor;
import net.zamasoft.foliojet.impl.css.property.text.TextEmphasisStyle;
import net.zamasoft.foliojet.impl.css.property.text.TextFillColor;
import net.zamasoft.foliojet.impl.css.property.text.TextShadow;
import net.zamasoft.foliojet.impl.css.property.text.TextStrokeColor;
import net.zamasoft.foliojet.impl.css.property.text.TextStrokeWidth;
import net.zamasoft.foliojet.impl.css.property.box.Transform;
import net.zamasoft.foliojet.impl.css.property.box.TransformOrigin;
import net.zamasoft.foliojet.impl.css.property.text.WordWrap;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJPageContent;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJPageContentClear;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJRegeneratable;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJRuby;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJHtmlAlign;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
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
import net.zamasoft.foliojet.layout.box.impl.RubyBodyBox;
import net.zamasoft.foliojet.layout.box.impl.RubyBox;
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
import net.zamasoft.foliojet.layout.box.params.Pos;
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
import net.zamasoft.foliojet.layout.visitor.VisitorWrapper;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.PageRef.Fragment;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.SectionState;
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
import net.zamasoft.foliojet.impl.css.property.border.BorderWidth;
import net.zamasoft.foliojet.impl.css.property.border.BorderStyle;
import net.zamasoft.foliojet.impl.css.property.border.BorderRadius;
import net.zamasoft.foliojet.impl.css.property.box.Padding;
import net.zamasoft.foliojet.impl.css.property.box.Margin;
import net.zamasoft.foliojet.impl.css.property.border.BorderColor;
import net.zamasoft.foliojet.impl.css.property.box.Inset;
import net.zamasoft.foliojet.impl.css.property.border.Corner;
import net.zamasoft.foliojet.impl.css.property.box.Side;
import net.zamasoft.foliojet.ua.AbsoluteFontSize;
import net.zamasoft.foliojet.ua.BoundSide;

/**
 * @author MIYABE Tatsuhiko
 */
public class StyleBuilder implements PageGenerator {
	private static final boolean DEBUG = false;
	private static final Logger LOG = Logger.getLogger(StyleBuilder.class.getName());

	private static final ValueListValue LF = new ValueListValue(new Value[] { new StringValue("\n") });

	private static final RelativeLengthValue EM_1_618 = RelativeLengthValue.em(1.618);
	private static final RelativeLengthValue EM_1_414 = RelativeLengthValue.em(1.414);
	private static final RelativeLengthValue EM_1_4 = RelativeLengthValue.em(1.4);

	private final UserAgent ua;
	private final DocumentBuilder doc;
	private final Imposition imposition;
	private final AbsoluteLengthValue[] margins;
	private StyleContext styleContext;
	private CSSStyle currentStyle;
	private CSSElement pageElement = null;
	private int pageNumber = 0;
	private int maxPageNumber = Integer.MAX_VALUE;

	private FlowBlockBox htmlRootBlock = null;
	private Background background = null;
	private WritingMode progression = WritingMode.TB;
	private boolean rightSide = false;
	private boolean inBody = false;
	private boolean inTextBlock = false;
	private boolean firstLetter = false;

	private int depth = 0;
	private int quoteLevel = 0;

	/** リストアイテム用のカウンタ。要素は int[]{深さ, 値} 。 */
	private final List<int[]> listCounterStack = new ArrayList<int[]>();

	private Marker marker = null;

	private final Map<CSSElement, String[]> toPageContentClear = new HashMap<CSSElement, String[]>();
	private final Map<CSSElement, PageContent> toPageContent = new HashMap<CSSElement, PageContent>();
	private final Map<String, PageContent> pageContents = new HashMap<String, PageContent>();
	private final List<PageContent> pageContentStack = new ArrayList<PageContent>();

	private Segment runIn = null;

	/**
	 * 本流のスタイルイベント窓です(M6a)。改ページ再開をボックス再生から
	 * セグメント再駆動へ置き換えるための記録で、現段階では記録のみ
	 * (消費者なし)。疑似要素(::before/::after/::first-letter 等)の
	 * 合成イベントは再生時に再合成されるため記録しません(ソース純度)。
	 * 窓はページ境界で開いている要素の Start だけに刈り込まれます。
	 */
	private final Segment segment = new Segment();

	/**
	 * 本流のスタイルイベント窓を返します(M6a)。
	 */
	public Segment getSegment() {
		return this.segment;
	}

	/**
	 * ソースアンカーの窓内整合を検査します(M6b診断)。匿名・合成
	 * ボックスは直前の記録イベントを指すため要素一致は強制せず、
	 * 窓外参照(窓とアンカーの同期破壊)のみを検出します。
	 * 切替(segment-restyle)時に厳密化します。
	 */
	public boolean verifySourceAnchor(final int sourceEpoch, final int sourceIndex,
			final net.zamasoft.foliojet.css.CSSElement element) {
		if (sourceEpoch != this.segment.getEpoch()) {
			// 旧世代のアンカー(複数ページに跨るボックス)は未接続扱い
			return true;
		}
		return sourceIndex < this.segment.size();
	}

	public boolean replaySubtree(final int sourceEpoch, final int sourceIndex,
			final net.zamasoft.foliojet.css.CSSElement element) {
		if (sourceEpoch != this.segment.getEpoch() || !this.segment.isStartOf(sourceIndex, element)) {
			// 旧世代・不整合アンカーはボックス再生へフォールバック
			return false;
		}
		final int end = this.segment.endOf(sourceIndex);
		if (end < 0) {
			// 窓内で閉じていない部分木(切断された要素)は対象外
			return false;
		}
		this.segment.replaySubtree(this, sourceIndex, end);
		return true;
	}

	/**
	 * ボックスの開始を本流セグメント窓の現在位置(ソースアンカー)を
	 * 刻印してから doc に渡します(M6b)。合成ボックスは直前の記録
	 * イベントの位置を継承し、再生時に同じ位置で再合成されます。
	 */
	private void startBox(final net.zamasoft.foliojet.layout.box.INonReplacedBox box) {
		box.getParams().sourceIndex = this.segment.size() - 1;
		box.getParams().sourceEpoch = this.segment.getEpoch();
		this.doc.startBox(box);
	}

	private static final byte STATE_RESTYLE_RUN_IN = 1;

	private byte state = 0;

	public StyleBuilder(StyleContext styleContext, UserAgent ua, Imposition imposition) {
		this.styleContext = styleContext;
		this.ua = ua;
		this.pageNumber = ua.getPassContext().getPageNumber();
		this.imposition = imposition;
		this.doc = new DocumentBuilder(this);

		byte pageMode = 0;
		// 自動高さ
		if (UAProps.OUTPUT_AUTO_HEIGHT.getBoolean(ua)) {
			pageMode |= DocumentBuilder.PAGE_MODE_CONTINUOUS;
		}

		// 改ページ禁止
		if (UAProps.OUTPUT_NO_PAGE_BREAK.getBoolean(ua)) {
			pageMode |= DocumentBuilder.PAGE_MODE_NO_BREAK;
		}
		this.doc.setPageMode(pageMode);

		// ページ幅
		{
			String s = UAProps.OUTPUT_PAGE_WIDTH.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false, s);
			if (length != null) {
				double l = length.getLength();
				this.imposition.setPageWidth(l);
				if (this.imposition.getNote() != null) {
					this.imposition.setNote(this.imposition.getNote() + " / width " + s);
				}
			} else {
				this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_WIDTH.name, s);
			}
		}

		// ページ高さ
		{
			String s = UAProps.OUTPUT_PAGE_HEIGHT.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false, s);
			if (length != null) {
				double l = length.getLength();
				this.imposition.setPageHeight(l);
				if (this.imposition.getNote() != null) {
					this.imposition.setNote(this.imposition.getNote() + " / height " + s);
				}
			} else {
				this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_HEIGHT.name, s);
			}
		}
		LayoutUtils.setupImposition(this.ua, this.imposition);

		// マージン
		{
			AbsoluteLengthValue[] margins;
			String s = UAProps.OUTPUT_PAGE_MARGINS.getString(ua);
			if (s != null) {
				String[] values = s.split("[\\s]+");
				if (values.length <= 0 || values.length > 4) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_MARGINS.name, s);
					margins = null;
				} else {
					margins = new AbsoluteLengthValue[values.length];
					for (int i = 0; i < values.length; ++i) {
						AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, values[i]);
						if (length != null) {
							margins[i] = length;
						} else {
							ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_MARGINS.name, s);
							margins = null;
							break;
						}
					}
				}
			} else {
				margins = null;
			}
			this.margins = margins;
		}

		// 最大ページ数
		this.maxPageNumber = UAProps.OUTPUT_PAGE_LIMIT.getInteger(ua);

		// ページごとに生成される内容
		for (CSSStyleSheet.PageContent cpc : this.styleContext.getPageContents()) {
			CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, CSSElement.BEFORE);
			style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
			style.set(CSSJPageContent.INFO_NAME, KeywordValue.NONE, CSSStyle.MODE_IMPORTANT);
			style.set(CSSJRegeneratable.INFO, KeywordValue.NONE, CSSStyle.MODE_IMPORTANT);
			style.set(CSSPosition.INFO, PositionValue._CSSJ_CURRENT_PAGE_VALUE, CSSStyle.MODE_IMPORTANT);
			byte[] pages = null;
			if (cpc.pseudoPage != null) {
				String ident = cpc.pseudoPage;
				if (ident.equals("first")) {
					pages = new byte[] { CSSElement.PC_FIRST };
				} else if (ident.equals("right")) {
					pages = new byte[] { CSSElement.PC_RIGHT };
				} else if (ident.equals("left")) {
					pages = new byte[] { CSSElement.PC_LEFT };
				} else if (ident.equals("single")) {
					pages = new byte[] { (byte) 0 };
				}
			}
			cpc.decleration.applyProperties(style);
			PageContent pc = new PageContent(this.styleContext, pages, cpc.name);
			pc.startStyle(style);
			pc.endStyle(style);
			this.pageContents.put(pc.name, pc);
		}
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public CSSElement getPageElement() {
		return this.pageElement;
	}

	/**
	 * 相対配置可能な配置の設定します。
	 * 
	 * @param pos
	 * @param style
	 */
	private void setupStaticPos(final AbstractStaticPos pos, final CSSStyle style) {
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
	private void setupInlinePos(InlinePos pos, CSSStyle style) {
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
	private void setupAbsolutePos(AbsolutePos pos, CSSStyle style) {
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
		case PositionValue._CSSJ_CURRENT_PAGE:
			pos.fiducial = Fiducial.CURRENT_PAGE;
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
	private void setupFlowPos(FlowPos pos, CSSStyle style) {
		this.setupStaticPos(pos, style);
		pos.clear = Clear.get(style);
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), style);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), style);
		pos.columnSpan = ColumnSpan.get(style);
	}

	/**
	 * 浮動配置の設定をします。
	 * 
	 * @param pos
	 * @param style
	 */
	private void setupFloatPos(FloatPos pos, CSSStyle style) {
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
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), style);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), style);
	}

	/**
	 * ボックスの基本パラメータを設定します。
	 * 
	 * @param params
	 * @param style
	 */
	private void setupParams(Params params, CSSStyle style) {
		params.element = style.getCSSElement();
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
	private void setupTextParams(AbstractTextParams params, CSSStyle style) {
		this.setupParams(params, style);
		params.whiteSpace = WhiteSpace.get(style);
		params.wordWrap = WordWrap.get(style);
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
	}

	/**
	 * 置換可能ボックスのパラメータを設定します。
	 * 
	 * @param src
	 * @param params
	 * @param style
	 */
	private void setupReplacedParams(Image image, ReplacedParams params, CSSStyle style) {
		this.setupTextParams(params, style);
		params.image = image;

		params.size = BoxValueUtils.toDimension(Width.get(style), Height.get(style));
		params.minSize = BoxValueUtils.toDimension(MinWidth.get(style), MinHeight.get(style));
		params.maxSize = BoxValueUtils.toDimension(MaxWidth.get(style), MaxHeight.get(style));
		params.boxSizing = BoxSizing.get(style);

		params.frame = this.createRectFrame(style);
		params.color = CSSColor.get(style);
		params.lineHeight = LineHeight.get(style);
	}

	/**
	 * 行ボックスのパラメータを設定します。
	 * 
	 * @param params
	 * @param style
	 */
	private void setupAbstractLineParams(AbstractLineParams params, CSSStyle style) {
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
	private void setupLineParams(FirstLineParams params, CSSStyle style) {
		this.setupAbstractLineParams(params, style);
		if (style.getCSSElement() == CSSElement.FIRST_LINE) {
			params.background = this.createBackground(style);
		}
	}

	private void setupBlockParams(BlockParams params, CSSStyle style) {
		this.setupAbstractLineParams(params, style);
		params.pageBreakInside = PageBreakInside.get(style);
		params.orphans = (byte) Math.min(Byte.MAX_VALUE, Orphans.get(style));
		params.widows = (byte) Math.min(Byte.MAX_VALUE, Widows.get(style));

		// :first-line
		this.styleContext.startElement(CSSElement.FIRST_LINE);
		final Declaration declaration = this.styleContext.merge(null);
		this.styleContext.endElement();
		if (declaration != null) {
			CSSStyle firstLineStyle = CSSStyle.getCSSStyle(this.ua, this.currentStyle, CSSElement.FIRST_LINE);
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
		params.frame = this.createRectFrame(style);

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
	private void setupInlineParams(InlineParams params, CSSStyle style) {
		this.setupTextParams(params, style);
		params.frame = this.createRectFrame(style);
	}

	/**
	 * テーブルボックスのパラメータを設定します。
	 * 
	 * @param params
	 * @param style
	 */
	private void setupTableParams(TableParams params, CSSStyle style) {
		this.setupBlockParams(params, style);
		params.borderSpacingH = BorderSpacing.getHorizontal(style);
		params.borderSpacingV = BorderSpacing.getVertical(style);
		params.borderCollapse = BorderCollapse.get(style);
		params.layout = TableLayout.get(style);
	}

	private void setupInnerTableParams(InnerTableParams params, CSSStyle style) {
		this.setupParams(params, style);
		params.background = this.createBackground(style);
		params.border = this.createRectBorder(style);
		params.pageBreakInside = PageBreakInside.get(style);
	}

	/**
	 * テーブルキャプション配置の設定をします。
	 * 
	 * @param pos
	 * @param style
	 */
	private void setupTableCaptionPos(TableCaptionPos pos, CSSStyle style) {
		this.setupFlowPos(pos, style);
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

	private void setupTableRowGroup(InnerTableParams params, TableRowGroupPos pos, CSSStyle style, RowGroupType rowGroupType) {
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
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), style);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), style);
	}

	private void setupTableColumn(InnerTableParams params, TableColumnPos pos, CSSStyle style) {
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

	private void setupTableRow(InnerTableParams params, TableRowPos pos, CSSStyle style) {
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
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), style);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), style);
	}

	private void setupTableCellPos(TableCellPos pos, CSSStyle style) {
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
					}
				} catch (NumberFormatException e) {
					pos.rowspan = 1;
				}
			}
		}
		pos.pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), style);
		pos.pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), style);
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
	private Background createBackground(CSSStyle style) {
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
	private RectBorder createRectBorder(CSSStyle style) {
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
	private RectFrame createRectFrame(CSSStyle style) {
		RectBorder border = this.createRectBorder(style);
		Background background = this.createBackground(style);

		// HTML/BODYタグ
		if (!this.inBody) {
			CSSElement ce = style.getCSSElement();
			if (XHTML.HTML_ELEM.equalsElement(ce) || XHTML.BODY_ELEM.equalsElement(ce)) {
				// 背景の扱い
				// これはIE, Opera, FirefoxよりもKHTMLに近いものです。
				if (this.background == null && background != Background.NULL_BACKGROUND) {
					this.background = background;
					background = Background.NULL_BACKGROUND;
				}
				this.progression = BlockFlow.get(style);
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
	private Offset createRelativeOffset(CSSStyle style) {
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

	private void requireRoot(byte direction, WritingMode progression) {
		// 保留されたHTMLのルートを出力する
		if (!this.inBody) {
			this.inBody = true;
			if (this.htmlRootBlock != null) {
				// ページの描画方法
				final BlockParams params = this.htmlRootBlock.getBlockParams();
				params.direction = direction;
				params.flow = progression;
			}
			this.progression = progression;
			// 右とじ
			boolean right;
			OutputPrintMode printMode = UAProps.OUTPUT_PRINT_MODE.get(this.ua);
			if (printMode == OutputPrintMode.LEFT_SIDE) {
				right = false;
			} else if (printMode == OutputPrintMode.RIGHT_SIDE) {
				right = true;
			} else {
				right = direction == AbstractTextParams.DIRECTION_RTL || progression == WritingMode.RL;
			}
			if (right) {
				this.imposition.setBoundSide(BoundSide.RIGHT);
				this.rightSide = true;
			}
			if (this.htmlRootBlock != null) {
				this.startBox(this.htmlRootBlock);
				this.htmlRootBlock = null;
			}
		}
	}

	private AbstractBlockBox createBlockBox(CSSStyle style, BlockParams params, byte position, byte display,
			byte floating) {
		final AbstractBlockBox blockBox;
		if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED
				|| position == PositionValue._CSSJ_CURRENT_PAGE) {
			final AbsolutePos pos = new AbsolutePos();
			this.setupAbsolutePos(pos, style);
			blockBox = new AbsoluteBlockBox(params, pos);
		} else if (display == DisplayValue.INLINE_BLOCK || display == DisplayValue.INLINE_TABLE) {
			final InlinePos pos = new InlinePos();
			this.setupInlinePos(pos, style);
			if (CSSJRuby.get(style) == CSSJRubyValue.RUBY) {
				blockBox = new RubyBox(params, pos);
			} else {
				blockBox = new InlineBlockBox(params, pos);
			}
		} else if (floating != CSSFloatValue.NONE) {
			final FloatPos pos = new FloatPos();
			this.setupFloatPos(pos, style);
			blockBox = new FloatBlockBox(params, pos);
		} else {
			final FlowPos pos = new FlowPos();
			this.setupFlowPos(pos, style);
			final CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null) {
				pos.align = CSSJHtmlAlign.get(parentStyle);
			}
			if (CSSJRuby.get(style) == CSSJRubyValue.RB) {
				blockBox = new RubyBodyBox(params, pos);
			} else {
				blockBox = new FlowBlockBox(params, pos);
			}
		}
		return blockBox;
	}

	public CSSStyle getCurrentStyle() {
		return this.currentStyle;
	}

	public void startStyle(CSSStyle style) {
		if (DEBUG) {
			System.err.println(style.path());
		}
		final CSSElement ce = style.getCSSElement();

		// ページごとに生成する内容
		boolean regenerate = false;
		String pageContentName = CSSJPageContent.getName(style);
		if (pageContentName == null) {
			pageContentName = CSSJRegeneratable.get(style);
			if (pageContentName != null) {
				regenerate = true;
			}
		}
		if (pageContentName != null) {
			style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
		}

		short explDisplay = Display.get(style);

		// run-inの中
		boolean inRunIn = false;
		if (this.runIn != null) {
			if (this.runIn.getDepth() == 0) {
				switch (explDisplay) {
				case DisplayValue.TABLE:
				case DisplayValue.TABLE_COLUMN:
				case DisplayValue.TABLE_COLUMN_GROUP:
				case DisplayValue.TABLE_ROW_GROUP:
				case DisplayValue.TABLE_HEADER_GROUP:
				case DisplayValue.TABLE_FOOTER_GROUP:
				case DisplayValue.TABLE_ROW:
				case DisplayValue.TABLE_CELL:
					// テーブルがあるrun-inはブロックとして処理
					Segment buff = this.runIn;
					this.runIn = null;
					this.state = STATE_RESTYLE_RUN_IN;
					buff.restyle(this);
					this.state = 0;
					break;

				case DisplayValue.RUN_IN:
					// run-inの中のrun-inはinlineとして処理
					style.set(Display.INFO, DisplayValue.INLINE_VALUE, CSSStyle.MODE_IMPORTANT);
					explDisplay = DisplayValue.INLINE;
				}
			} else {
				switch (explDisplay) {
				case DisplayValue.BLOCK:
				case DisplayValue.LIST_ITEM:
				case DisplayValue.TABLE:
					// テーブルやブロックがあるrun-inはブロックとして処理
					Segment buff = this.runIn;
					this.runIn = null;
					this.state = STATE_RESTYLE_RUN_IN;
					buff.restyle(this);
					this.state = 0;
					break;

				case DisplayValue.RUN_IN:
					// run-inの中のrun-inはinlineとして処理
					style.set(Display.INFO, DisplayValue.INLINE_VALUE, CSSStyle.MODE_IMPORTANT);
				default:
					this.runIn.startStyle(style);
					this.currentStyle = style;
					inRunIn = true;
				}
			}
		}

		if (!inRunIn) {
			// ページごとに生成される内容
			String[] pageContentClearNames = CSSJPageContentClear.get(style);
			if (pageContentClearNames != null) {
				this.toPageContentClear.put(ce, pageContentClearNames);
			}

			final LanguageProfile lang = LanguageProfileBundle
					.getLanguageProfile(style.getCSSElement().lang);
			
			if (pageContentName != null) {
				final PageContent pageContent;
				if (regenerate) {
					pageContent = new Regeneratable(this.styleContext.copy(1));
				} else {
					style.set(CSSJPageContent.INFO_NAME, KeywordValue.NONE, CSSStyle.MODE_IMPORTANT);
					style.set(CSSJRegeneratable.INFO, KeywordValue.NONE, CSSStyle.MODE_IMPORTANT);
					style.set(CSSPosition.INFO, PositionValue._CSSJ_CURRENT_PAGE_VALUE, CSSStyle.MODE_IMPORTANT);
					pageContent = new PageContent(this.styleContext.copy(1), CSSJPageContent.getPages(style),
							pageContentName);
				}
				final InlinePos pos = new InlinePos();
				final InlineParams params = new InlineParams();
				params.element = ce;
				params.fontStyle = style.getFontStyle();
				params.fontManager = this.ua.getFontManager();
				params.lineBreakRules = lang.getLineBreakRules(style);
				final InlineBox inlineBox = new InlineBox(params, pos);
				this.startBox(inlineBox);
				this.doc.endBox();
				this.toPageContent.put(ce, pageContent);
				this.pageContentStack.add(pageContent);
			}

			if (!this.pageContentStack.isEmpty()) {
				// ページごとに生成される内容の記録
				PageContent pageContent = (PageContent) this.pageContentStack.get(this.pageContentStack.size() - 1);
				pageContent.startStyle(style);
				this.currentStyle = style;
			} else {
				if (explDisplay == DisplayValue.RUN_IN) {
					// run-inの開始
					style.set(Display.INFO, DisplayValue.INLINE_VALUE, CSSStyle.MODE_IMPORTANT);
					this.runIn = new Segment();
					this.runIn.startStyle(style);
					this.currentStyle = style;
				} else {
					if (!ce.isPseudoElement()) {
						// 本流のセグメント記録(M6a)。カウンタ状態も再開用に保持(M6b)
						this.segment.startStyle(style, this.ua.getPassContext().snapshotNonPageCounters());
					}
					if (this.currentStyle != null) {
						WHILE: while (this.currentStyle.isAnonStyle()) {
							// 匿名スタイルの終了

							// 静的要素のみに適用
							final byte pos = CSSPosition.get(style);
							if (pos != PositionValue.STATIC && pos != PositionValue.RELATIVE) {
								break WHILE;
							}

							final byte anonRuby = CSSJRuby.get(this.currentStyle);
							if (anonRuby == CSSJRubyValue.RB) {
								// ルビ関係
								final byte ruby = CSSJRuby.get(style);
								if (ruby != CSSJRubyValue.RT) {
									break WHILE;
								}
							} else {
								// テーブル関係
								final short anonDisplay = Display.get(this.currentStyle);
								switch (explDisplay) {
								case DisplayValue.TABLE_HEADER_GROUP:
								case DisplayValue.TABLE_FOOTER_GROUP:
								case DisplayValue.TABLE_ROW_GROUP:
									switch (anonDisplay) {
									case DisplayValue.TABLE_ROW:
									case DisplayValue.TABLE_ROW_GROUP:
										break;
									default:
										break WHILE;
									}
									break;
								case DisplayValue.TABLE_CELL:
									switch (anonDisplay) {
									case DisplayValue.TABLE_ROW_GROUP:
									case DisplayValue.TABLE:
									case DisplayValue.INLINE_TABLE:
										break;
									default:
										break WHILE;
									}
									break;
								case DisplayValue.INLINE:
								case DisplayValue.BLOCK:
								case DisplayValue.LIST_ITEM:
								case DisplayValue.INLINE_BLOCK:
								case DisplayValue.TABLE:
								case DisplayValue.INLINE_TABLE:
									switch (anonDisplay) {
									case DisplayValue.TABLE_ROW:
										CSSStyle parent = this.currentStyle.getParentStyle();
										if (!parent.isAnonStyle() || !parent.getParentStyle().isAnonStyle()) {
											break WHILE;
										}
									case DisplayValue.TABLE_ROW_GROUP:
									case DisplayValue.TABLE:
									case DisplayValue.INLINE_TABLE:
										break;
									default:
										break WHILE;
									}
									break;
								default:
									break WHILE;
								}
							}
							if (style.getParentStyle() == this.currentStyle) {
								style.removeAnonStyle();
							}
							this._endStyle();
						}
					}

					// BR
					if (XHTML.BR_ELEM.equalsElement(ce)) {
						// クリアランス、強制改ページは後にブロックを生成する
						ClearMode clear = Clear.get(style);
						PageBreakMode pageBreakBefore = this.toPageBreak(PageBreakBefore.get(style), style);
						PageBreakMode pageBreakAfter = this.toPageBreak(PageBreakAfter.get(style), style);
						if (clear != ClearMode.NONE || pageBreakBefore != PageBreakMode.AUTO
								|| pageBreakAfter != PageBreakMode.AUTO) {
							// クリアランス等の実行
							final FlowPos pos = new FlowPos();
							pos.clear = clear;
							pos.pageBreakBefore = pageBreakBefore;
							pos.pageBreakAfter = pageBreakAfter;
							BlockParams params = new BlockParams();
							params.fontStyle = style.getFontStyle();
							params.fontManager = this.ua.getFontManager();
							params.lineBreakRules = lang.getLineBreakRules(style);
							params.direction = Direction.get(style);
							params.flow = BlockFlow.get(style);
							params.element = ce;
							final Insets margin = Insets.create(0, 0, -LineHeight.get(style), 0, LengthType.ABSOLUTE,
									LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
							params.frame = RectFrame.create(margin, RectBorder.NONE_RECT_BORDER,
									Background.NULL_BACKGROUND, Insets.NULL_INSETS);
							// テーブル内で問題が起こるので、匿名ボックスの処理をした後で挿入する
							FlowBlockBox flowBox = new FlowBlockBox(params, pos);
							this.startBox(flowBox);
							this.doc.endBox();
						}
					}

					this._startStyle(style);

					this.firstLetter = true;
					if (!ce.isPseudoElement()) {
						++this.depth;
					}
					int depth = this.depth;

					// カウンターリセット
					Value[] resets = CounterReset.get(style);
					if (resets != null) {
						final PassContext pc = this.ua.getPassContext();
						for (int i = 0; i < resets.length; ++i) {
							CounterSetValue counterSet = (CounterSetValue) resets[i];
							String name = counterSet.getName();
							int value = counterSet.getValue();
							CounterScope scope = pc.getCounterScope(0, false);
							if (scope != null && scope.defined(name)) {
								scope.reset(name, value);
								continue;
							}
							pc.getCounterScope(depth, true).reset(name, value);
						}
					}

					// カウンター加算
					final Value[] increments = CounterIncrement.get(style);
					if (increments != null) {
						final PassContext pc = this.ua.getPassContext();
						for (int i = 0; i < increments.length; ++i) {
							CounterSetValue counterSet = (CounterSetValue) increments[i];
							String name = counterSet.getName();
							int delta = counterSet.getValue();
							int level = depth;
							for (; level > 0; --level) {
								CounterScope scope = pc.getCounterScope(level, false);
								if (scope != null && scope.defined(name)) {
									break;
								}
							}
							pc.getCounterScope(level, true).increment(name, delta);
						}
					}

					// マーカー
					if (explDisplay == DisplayValue.LIST_ITEM) {
						int[] counter = null;
						if (!this.listCounterStack.isEmpty()) {
							counter = (int[]) this.listCounterStack.get(this.listCounterStack.size() - 1);
							if (counter[0] == depth) {
								++counter[1];
							} else {
								counter = null;
							}
						}
						if (counter == null) {
							int start = 1;
							CSSStyle parentStyle = style;
							for (parentStyle = parentStyle
									.getParentStyle(); parentStyle != null; parentStyle = parentStyle
											.getParentStyle()) {
								CSSElement parentCe = parentStyle.getCSSElement();
								if (parentCe == null) {
									continue;
								}
								if (XHTML.UL_ELEM.equalsElement(parentCe)) {
									break;
								}
								if (XHTML.OL_ELEM.equalsElement(parentCe)) {
									String str = parentCe.atts.getValue("start");
									if (str != null) {
										try {
											start = Integer.parseInt(str);
										} catch (NumberFormatException e) {
											ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "OL", "start" + str);
										}
									}
									break;
								}
							}
							counter = new int[] { depth, start };
							this.listCounterStack.add(counter);
						}
						if (style.getCSSElement() != null && XHTML.LI_ELEM.equalsElement(style.getCSSElement())) {
							String value = style.getCSSElement().atts.getValue("value");
							if (value != null) {
								try {
									counter[1] = Integer.parseInt(value);
								} catch (NumberFormatException e) {
									ua.message(MessageCodes.WARN_BAD_HTML_ATTRIBUTE, "LI", "value" + value);
								}
							}
						}

						int number = counter[1];
						InlinePos pos = new InlinePos();
						BlockParams params = new BlockParams();
						this.setupBlockParams(params, style);
						this.setupInlinePos(pos, style);
						params.frame = RectFrame.NULL_FRAME;
						short listStyleType = ListStyleType.get(style);
						Image image = ListStyleImage.get(style);
						if (image == null) {
							image = GeneratedValueUtils.format(listStyleType, params.color, params.fontStyle);
						}
						this.marker = null;
						Marker marker = null;
						if (image == null) {
							String str = GeneratedValueUtils.format(number, listStyleType);
							if (str != null) {
								marker = new Marker();
								String dot = GeneratedValueUtils.period(listStyleType);
								marker.text = (str + dot + ' ').toCharArray();
							}
						} else {
							marker = new Marker();
							ReplacedParams rparams = new ReplacedParams();
							this.setupParams(rparams, style);
							rparams.image = image;
							marker.imageBox = new InlineReplacedBox(rparams, pos);
						}
						if (marker != null) {
							switch (ListStylePosition.get(style)) {
							case ListStylePositionValue.INSIDE:
								// 内部マーカー
								marker.box = new InlineBlockBox(params, pos);
								this.marker(marker);
								break;
							case ListStylePositionValue.OUTSIDE:
								// 外部マーカー
								marker.box = new OutsideMarkerBox(params, pos);
								this.marker = marker;
								break;
							default:
								throw new IllegalStateException();
							}
						}
					}

					// コンテンツ生成
					if (ce == CSSElement.AFTER || ce == CSSElement.BEFORE) {
						final Value[] contents = Content.get(style);
						if (contents != null) {
							for (int i = 0; i < contents.length; ++i) {
								final Value v = contents[i];
								switch (v) {
								case StringValue stringValue: {
									// 文字列
									String str = stringValue.getString();
									if (str.length() > 0) {
										char[] ch = str.toCharArray();
										this.checkMarker();
										this.doc.characters(-1, ch, 0, ch.length, true);
									}
								}
									break;
								case URIValue uriValue: {
									// 画像
									URI uri = uriValue.getURI();
									try {
										Source source = this.ua.resolve(uri);
										try {
											Image image = this.ua.getImage(source);
											ReplacedParams rparams = new ReplacedParams();
											this.setupParams(rparams, style);
											rparams.image = image;
											AbstractReplacedBox replaced = new InlineReplacedBox(rparams,
													new InlinePos());
											this.checkMarker();
											this.doc.addReplacedBox(replaced);
										} finally {
											this.ua.release(source);
										}
									} catch (Exception e) {
										LOG.log(Level.FINE, "Missing image", e);
										this.ua.message(MessageCodes.WARN_MISSING_IMAGE, uri.toString());
									}
								}
									break;

								case CounterValue counter: {
									// カウンタ
									final String name = counter.getName();
									final short counterStyle = counter.getStyle();
									int number = 0;
									final PassContext pc = this.ua.getPassContext();
									for (int level = depth; level >= 0; --level) {
										CounterScope scope = pc.getCounterScope(level, false);
										if (scope != null && scope.defined(name)) {
											number = scope.get(name);
											break;
										}
									}
									this.counter(number, counterStyle, style);
								}
									break;

								case CountersValue counters: {
									// カウンタ
									final String name = counters.getName();
									final String delim = counters.getDelimiter();
									final short counterStyle = counters.getStyle();
									boolean first = true;
									final PassContext pc = this.ua.getPassContext();
									for (int level = 0; level <= depth; ++level) {
										CounterScope scope = pc.getCounterScope(level, false);
										if (scope != null && scope.defined(name)) {
											if (!first && delim != null && delim.length() > 0) {
												char[] ch = delim.toCharArray();
												this.checkMarker();
												this.doc.characters(-1, ch, 0, ch.length, true);
											}
											first = false;
											final int number = scope.get(name);
											this.counter(number, counterStyle, style);
										}
									}
								}
									break;

								case QuoteValue quote: {
									// 引用符
									Value[] quotesList = Quotes.get(style);

									switch (quote.getQuote()) {
									case QuoteValue.OPEN_QUOTE: {
										if (quotesList != null) {
											String str = ((QuotesValue) quotesList[Math.min(this.quoteLevel,
													quotesList.length - 1)]).getOpen();
											if (str.length() > 0) {
												char[] ch = str.toCharArray();
												this.checkMarker();
												this.doc.characters(-1, ch, 0, ch.length, true);
											}
										}
										++this.quoteLevel;
									}
										break;

									case QuoteValue.CLOSE_QUOTE: {
										if (this.quoteLevel > 0) {
											--this.quoteLevel;
											if (quotesList != null) {
												String str = ((QuotesValue) quotesList[Math.min(this.quoteLevel,
														quotesList.length - 1)]).getClose();
												if (str.length() > 0) {
													char[] ch = str.toCharArray();
													this.checkMarker();
													this.doc.characters(-1, ch, 0, ch.length, true);
												}
											}
										}
									}
										break;

									case QuoteValue.NO_OPEN_QUOTE: {
										++this.quoteLevel;
									}
										break;

									case QuoteValue.NO_CLOSE_QUOTE: {
										if (this.quoteLevel > 0) {
											--this.quoteLevel;
										}
									}
										break;

									default:
										throw new IllegalStateException();
									}
								}
									break;
								case AttrValue attr: {
									// 属性
									CSSElement parentCe = style.getParentStyle().getCSSElement();
									if (parentCe.atts != null) {
										String str = parentCe.atts.getValue(attr.getName());
										if (str != null && str.length() > 0) {
											char[] ch = str.toCharArray();
											this.checkMarker();
											this.doc.characters(-1, ch, 0, ch.length, true);
										}
									}
								}
									break;
								case CSSJLastHeadingValue header: {
									// ヘッダ
									SectionState state = this.ua.getPassContext().getSectionState();
									int level = header.getLevel() - 1;
									String str = state.lastSections[level >= state.lastSections.length
											? state.lastSections.length - 1
											: level];
									if (str != null && str.length() > 0) {
										char[] ch = str.toCharArray();
										this.checkMarker();
										this.doc.characters(-1, ch, 0, ch.length, true);
									}
								}
									break;
								case CSSJFirstHeadingValue header: {
									// ヘッダ
									SectionState state = this.ua.getPassContext().getSectionState();
									int level = header.getLevel() - 1;
									String str = state.firstSections[level >= state.firstSections.length
											? state.firstSections.length - 1
											: level];
									if (str != null && str.length() > 0) {
										char[] ch = str.toCharArray();
										this.checkMarker();
										this.doc.characters(-1, ch, 0, ch.length, true);
									}
								}
									break;
								case CSSJTitleValue title: {
									// タイトル
									SectionState state = this.ua.getPassContext().getSectionState();
									String str = state.title;
									if (str != null && str.length() > 0) {
										char[] ch = str.toCharArray();
										this.checkMarker();
										this.doc.characters(-1, ch, 0, ch.length, true);
									}
								}
									break;
								case CSSJPageRefValue pageRefFunc: {
									// ページ番号
									switch (pageRefFunc.getType()) {
									case CSSJPageRefValue.ATTR: {
										// 属性から
										CSSElement parentCe = style.getParentStyle().getCSSElement();
										if (parentCe.atts != null) {
											String attr = pageRefFunc.getRef();
											String str = parentCe.atts.getValue(attr);
											if (str != null) {
												if (!attr.equals("href") && str.indexOf("#") == -1) {
													// 互換性のため
													str = "#" + str;
												}
												this.pageRef(pageRefFunc, str);
											}
										}
									}
										break;
									case CSSJPageRefValue.REF: {
										// ID指定
										String id = pageRefFunc.getRef();
										if (id.indexOf("#") == -1) {
											// 互換性のため
											id = "#" + id;
										}
										this.pageRef(pageRefFunc, id);
									}
										break;
									default:
										throw new IllegalStateException();
									}
								}
									break;
								default:
									throw new IllegalStateException(String.valueOf(v));
								}
							}
						}
					}

					// run-in生成
					if (this.runIn != null) {
						Segment buff = this.runIn;
						this.runIn = null;
						style = this.currentStyle;
						this.state = STATE_RESTYLE_RUN_IN;
						buff.restyle(this);
						this.state = 0;
						this.currentStyle = style;
					}
				}
			}
		}

		// before
		if (this.state != STATE_RESTYLE_RUN_IN && ce != CSSElement.AFTER && ce != CSSElement.BEFORE
				&& CSSJInternalImage.getImage(style) == null) {
			// :before
			CSSElement beforeCe = CSSElement.BEFORE;
			this.styleContext.startElement(beforeCe);
			final Declaration beforeDeclaration = this.styleContext.merge(null);
			if (beforeDeclaration != null || HTMLStyle.hasBeforeContent(ce)) {
				CSSStyle beforeStyle = CSSStyle.getCSSStyle(this.ua, style, beforeCe);
				HTMLStyle.applyBeforeStyle(beforeStyle);
				if (beforeDeclaration != null) {
					beforeDeclaration.applyProperties(beforeStyle);
				}
				if (Content.get(beforeStyle) != null && Display.get(beforeStyle) != DisplayValue.NONE) {
					this.startStyle(beforeStyle);
					this.endStyle();
				}
			}
			this.styleContext.endElement();
		}
	}

	private void counter(int number, short counterStyle, CSSStyle style) {
		final String str = GeneratedValueUtils.format(number, counterStyle);
		if (str != null) {
			char[] ch = str.toCharArray();
			this.checkMarker();
			// カウンタ
			this.doc.characters(-1, ch, 0, ch.length, true);
		} else {
			final ReplacedParams rparams = new ReplacedParams();
			this.setupParams(rparams, style);
			rparams.image = GeneratedValueUtils.format(counterStyle, CSSColor.get(style), style.getFontStyle());
			if (rparams.image != null) {
				final AbstractReplacedBox replaced = new InlineReplacedBox(rparams, new InlinePos());
				this.checkMarker();
				this.doc.addReplacedBox(replaced);
			}
		}
	}

	private void pageRef(CSSJPageRefValue pageRefFunc, String ref) {
		PageRef pageRef = this.ua.getUAContext().getPageRef();
		if (pageRef == null) {
			return;
		}

		try {
			URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(),
					this.ua.getDocumentContext().getBaseURI(), ref);
			String sep = pageRefFunc.getSeparator();
			String counter = pageRefFunc.getCounter();
			char[] ch;
			if (sep == null) {
				Fragment frag = pageRef.getFragment(uri);
				if (frag == null) {
					return;
				}
				int count = frag.getCounterValue(counter);
				String str = GeneratedValueUtils.format(count, pageRefFunc.getNumberStyleType());
				if (str == null) {
					return;
				}
				ch = str.toCharArray();
			} else {
				Collection<?> frags = pageRef.getFragments(uri);
				if (frags == null || frags.isEmpty()) {
					return;
				}
				IntList counts = new IntList();
				for (Iterator<?> j = frags.iterator(); j.hasNext();) {
					Fragment fragment = (Fragment) j.next();
					int count = fragment.getCounterValue(counter);
					if (!counts.contains(count)) {
						counts.add(count);
					}
				}
				StringBuilder buff = new StringBuilder();
				for (int j = 0; j < counts.size(); ++j) {
					if (buff.length() > 0) {
						buff.append(sep);
					}
					String str = GeneratedValueUtils.format(counts.get(j), pageRefFunc.getNumberStyleType());
					if (str != null) {
						buff.append(str);
					}
				}
				if (buff.length() <= 0) {
					return;
				}
				ch = buff.toString().toCharArray();
			}
			this.checkMarker();
			// ページ参照
			this.doc.characters(-1, ch, 0, ch.length, true);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
		}
	}

	private CSSStyle startColumns(CSSStyle style, AbstractContainerBox box) {
		int c = LayoutUtils.getColumnCount(box);
		if (c > 1) {
			final BlockParams params = box.getBlockParams();
			final BlockParams mcParams = new BlockParams();
			final FlowPos mcPos = new FlowPos();
			final CSSStyle mc = style.inheritAnonStyle(CSSElement.ANON);
			this.setupBlockParams(mcParams, mc);
			this.setupFlowPos(mcPos, mc);
			mcParams.columns = params.columns;
			if (params.size.getWidthType() != LengthType.AUTO) {
				if (params.size.getHeightType() != LengthType.AUTO) {
					mcParams.size = Dimension.create(1, 1, LengthType.RELATIVE, LengthType.RELATIVE);
				} else {
					mcParams.size = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.AUTO);
				}
			} else if (params.size.getHeightType() != LengthType.AUTO) {
				mcParams.size = Dimension.create(0, 1, LengthType.AUTO, LengthType.RELATIVE);
			}
			final MulticolumnBlockBox mcBox = new MulticolumnBlockBox(mcParams, mcPos);
			this.startBox(mcBox);
			style = mc;
		}
		return style;
	}

	private void startRB(CSSStyle style) {
		style.set(Display.INFO, DisplayValue.BLOCK_VALUE);
		style.set(CSSJRuby.INFO, CSSJRubyValue.RB_VALUE);
		style.set(LineHeight.INFO, PercentageValue.FULL);
		style.set(TextAlign.INFO, TextAlignValue.X_JUSTIFY_CENTER_VALUE);
		final CSSStyle pStyle = style.getParentStyle();
		if (pStyle != null && ((CSSJDirectionMode.get(pStyle) == CSSJDirectionModeValue.PHYSICAL
				&& BlockFlow.get(pStyle).isVertical())
				|| CSSJDirectionMode.get(pStyle) == CSSJDirectionModeValue.VERTICAL_RL)) {
			// 縦書き
			style.set(Width.INFO, AbsoluteLengthValue.ZERO);
		} else {
			// 横書き
			style.set(Height.INFO, AbsoluteLengthValue.ZERO);
		}
		this._startStyle(style);
	}

	private void _startStyle(CSSStyle style) {
		// System.err.println("_"+style.path());
		// ルートのHTMLタグはblockに固定する
		boolean htmlRoot = false;
		if (!this.inBody && this.htmlRootBlock == null) {
			final CSSElement ce = style.getCSSElement();
			if (ce.isPseudoClass(CSSElement.PC_ROOT)) {
				htmlRoot = true;
			}
			style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
			style.set(CSSFloat.INFO, CSSFloatValue.NONE_VALUE, CSSStyle.MODE_IMPORTANT);
			final byte position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
				style.set(CSSPosition.INFO, PositionValue.STATIC_VALUE, CSSStyle.MODE_IMPORTANT);
			}
		}

		// SPEC CSS 2.1 9.7の計算はDisplayクラスで実装済み
		final byte display = Display.get(style);
		final byte position = CSSPosition.get(style);
		if (position == PositionValue.STATIC || position == PositionValue.RELATIVE) {
			// タグの補完
			final CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null) {
				final byte ruby = CSSJRuby.get(style);
				if (ruby != CSSJRubyValue.RT && ruby != CSSJRubyValue.RB) {
					// ruby内のrt以外の要素の上にrbを挿入
					final byte parentRuby = CSSJRuby.get(parentStyle);
					if (parentRuby == CSSJRubyValue.RUBY) {
						this.startRB(style.insertAnonStyle(CSSElement.ANON_RB));
					}
				}

				final short parentDisplay = Display.get(parentStyle);
				switch (display) {
				case DisplayValue.TABLE_CELL: {
					// CSS 2.1 17.2.1 #1
					// テーブルセルの上にテーブル行を挿入
					if (parentDisplay != DisplayValue.TABLE_ROW) {
						final CSSStyle row = style.insertAnonStyle(CSSElement.ANON_TR);
						row.set(Display.INFO, DisplayValue.TABLE_ROW_VALUE);
						this._startStyle(row);
					}
				}
					break;

				case DisplayValue.TABLE_ROW: {
					// CSS 2.1 17.2.1 #2
					// テーブル行の上にテーブル行グループを挿入
					if (parentDisplay != DisplayValue.TABLE_ROW_GROUP
							&& parentDisplay != DisplayValue.TABLE_HEADER_GROUP
							&& parentDisplay != DisplayValue.TABLE_FOOTER_GROUP) {
						CSSStyle rowGroup = style.insertAnonStyle(CSSElement.ANON_TBODY);
						rowGroup.set(Display.INFO, DisplayValue.TABLE_ROW_GROUP_VALUE);
						this._startStyle(rowGroup);
					}
				}
					break;

				case DisplayValue.TABLE_COLUMN_GROUP:
					if (parentDisplay == DisplayValue.TABLE_COLUMN_GROUP
							|| parentDisplay == DisplayValue.TABLE_COLUMN) {
						break;
					}
				case DisplayValue.TABLE_ROW_GROUP:
				case DisplayValue.TABLE_HEADER_GROUP:
				case DisplayValue.TABLE_FOOTER_GROUP: {
					// CSS 2.1 17.2.1 #2
					// テーブルカラムグループ、行グループの上にテーブルを挿入
					if (parentDisplay != DisplayValue.TABLE && parentDisplay != DisplayValue.INLINE_TABLE) {
						CSSStyle table = style.insertAnonStyle(CSSElement.ANON_TBODY);
						if (parentDisplay == DisplayValue.INLINE) {
							table.set(Display.INFO, DisplayValue.INLINE_TABLE_VALUE);
						} else {
							table.set(Display.INFO, DisplayValue.TABLE_VALUE);
						}
						this._startStyle(table);
					}
				}
					break;

				case DisplayValue.TABLE_COLUMN: {
					// テーブルカラムの上にテーブルを挿入
					if (parentDisplay != DisplayValue.TABLE && parentDisplay != DisplayValue.INLINE_TABLE
							&& parentDisplay != DisplayValue.TABLE_COLUMN_GROUP) {
						CSSStyle table = style.insertAnonStyle(CSSElement.ANON_TABLE);
						if (parentDisplay == DisplayValue.INLINE) {
							table.set(Display.INFO, DisplayValue.INLINE_TABLE_VALUE);
						} else {
							table.set(Display.INFO, DisplayValue.TABLE_VALUE);
						}
						this._startStyle(table);
					}
				}
					break;

				case DisplayValue.TABLE_CAPTION:
					switch (parentDisplay) {
					case DisplayValue.INLINE_TABLE:
					case DisplayValue.TABLE:
					case DisplayValue.TABLE_ROW_GROUP:
					case DisplayValue.TABLE_HEADER_GROUP:
					case DisplayValue.TABLE_FOOTER_GROUP:
					case DisplayValue.TABLE_ROW:
						break;
					default:
						// テーブルキャプションをブロックに変換
						style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
						break;
					}
					break;

				case DisplayValue.TABLE:
				case DisplayValue.BLOCK:
				case DisplayValue.LIST_ITEM:
				case DisplayValue.INLINE_TABLE:
				case DisplayValue.INLINE:
				case DisplayValue.INLINE_BLOCK:
					// テーブル内のテーブル、ブロック、インラインの上にセルを挿入
					switch (parentDisplay) {
					case DisplayValue.INLINE_TABLE:
					case DisplayValue.TABLE:
					case DisplayValue.TABLE_ROW_GROUP:
					case DisplayValue.TABLE_HEADER_GROUP:
					case DisplayValue.TABLE_FOOTER_GROUP:
					case DisplayValue.TABLE_ROW:
						CSSStyle anon = style.insertAnonStyle(CSSElement.ANON_TD);
						anon.set(Display.INFO, DisplayValue.TABLE_CELL_VALUE);
						this._startStyle(anon);
					}
					break;

				default:
					throw new IllegalStateException();
				}
			}
		}

		// 配置の設定
		byte floating = CSSFloat.get(style);

		// ボックスの種類ごとの処理
		switch (display) {
		case DisplayValue.BLOCK:
		case DisplayValue.INLINE_BLOCK: {
			// ブロック
			final Image image = CSSJInternalImage.getImage(style);
			if (image != null) {
				// 画像
				final AbstractReplacedBox replacedBox;
				boolean inline = false;
				ReplacedParams params;
				if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED
						|| position == PositionValue._CSSJ_CURRENT_PAGE) {
					final AbsolutePos pos = new AbsolutePos();
					params = new ReplacedParams();
					this.setupReplacedParams(image, params, style);
					this.setupAbsolutePos(pos, style);
					replacedBox = new AbsoluteReplacedBox(params, pos);
				} else if (display == DisplayValue.INLINE_BLOCK) {
					final InlinePos pos = new InlinePos();
					params = new ReplacedParams();
					this.setupReplacedParams(image, params, style);
					this.setupInlinePos(pos, style);
					inline = true;
					replacedBox = new InlineReplacedBox(params, pos);
				} else if (floating != CSSFloatValue.NONE) {
					final FloatPos pos = new FloatPos();
					params = new ReplacedParams();
					this.setupReplacedParams(image, params, style);
					this.setupFloatPos(pos, style);
					replacedBox = new FloatReplacedBox(params, pos);
				} else {
					final FlowPos pos = new FlowPos();
					params = new ReplacedParams();
					this.setupReplacedParams(image, params, style);
					this.setupFlowPos(pos, style);
					final CSSStyle parentStyle = style.getParentStyle();
					if (parentStyle != null) {
						pos.align = CSSJHtmlAlign.get(parentStyle);
					}
					replacedBox = new FlowReplacedBox(params, pos);
				}
				this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB);
				if (inline) {
					this.checkMarker();
				}
				this.doc.addReplacedBox(replacedBox);
			} else {
				// ブロックボックス
				final BlockParams params = new BlockParams();
				this.setupBlockParams(params, style);
				final AbstractBlockBox blockBox = this.createBlockBox(style, params, position, display, floating);
				// HTMLのルートは出力を保留する
				if (blockBox.getPos().getType() == PosType.FLOW && htmlRoot) {
					this.htmlRootBlock = (FlowBlockBox) blockBox;
					break;
				}
				this.requireRoot(params.direction, params.flow);
				if (blockBox.getPos().getType() == PosType.INLINE) {
					this.checkMarker();
				}
				this.startBox(blockBox);

				// 段組みの開始
				style = this.startColumns(style, blockBox);
			}
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.INLINE: {
			Image image = CSSJInternalImage.getImage(style);
			InlinePos pos = new InlinePos();
			if (image != null) {
				// インラインの画像
				ReplacedParams params = new ReplacedParams();
				this.setupReplacedParams(image, params, style);
				this.setupInlinePos(pos, style);
				AbstractReplacedBox replaced = new InlineReplacedBox(params, pos);
				this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB);
				this.checkMarker();
				this.doc.addReplacedBox(replaced);
			} else {
				// インラインボックス
				InlineParams params = new InlineParams();
				this.setupInlineParams(params, style);
				this.setupInlinePos(pos, style);
				InlineBox inline = new InlineBox(params, pos);
				this.requireRoot(params.direction, params.flow);
				this.startBox(inline);
			}
		}
			break;
		case DisplayValue.LIST_ITEM: {
			// リストアイテム
			final BlockParams params = new BlockParams();
			this.setupBlockParams(params, style);
			final AbstractBlockBox listItem = this.createBlockBox(style, params, position, display, floating);
			this.requireRoot(params.direction, params.flow);
			this.startBox(listItem);
		}
			break;

		case DisplayValue.TABLE:
		case DisplayValue.INLINE_TABLE: {
			// テーブル
			final TableParams params = new TableParams();
			this.setupTableParams(params, style);
			final AbstractBlockBox blockBox = this.createBlockBox(style, params, position, display, floating);
			if (blockBox.getPos().getType() == PosType.FLOW) {
				if (CSSJHtmlAlign.get(style) == Align.CENTER) {
					((FlowPos) blockBox.getPos()).align = Align.CENTER;
				}
			}
			TableBox table = new TableBox(params, blockBox);
			this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB);
			this.startBox(table);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_CAPTION: {
			// テーブルキャプション
			final TableCaptionPos pos = new TableCaptionPos();
			final BlockParams params = new BlockParams();
			this.setupTableCaptionPos(pos, style);
			this.setupBlockParams(params, style);
			params.pageBreakInside = PageBreakMode.AVOID;
			switch (pos.captionSide) {
			case CaptionSideMode.BEFORE:
				pos.pageBreakAfter = PageBreakMode.AVOID;
				break;
			case CaptionSideMode.AFTER:
				pos.pageBreakBefore = PageBreakMode.AVOID;
				break;
			default:
				throw new IllegalStateException();
			}
			final FlowBlockBox caption = new FlowBlockBox(params, pos);
			this.requireRoot(params.direction, params.flow);
			this.startBox(caption);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_COLUMN_GROUP: {
			// テーブル列グループ
			final TableColumnPos pos = new TableColumnPos();
			final InnerTableParams params = new InnerTableParams();
			this.setupTableColumn(params, pos, style);
			final TableColumnGroupBox columnGroup = new TableColumnGroupBox(params, pos);
			this.startBox(columnGroup);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_COLUMN: {
			// テーブル列
			TableColumnPos pos = new TableColumnPos();
			InnerTableParams params = new InnerTableParams();
			this.setupTableColumn(params, pos, style);
			TableColumnBox column = new TableColumnBox(params, pos);
			this.startBox(column);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_HEADER_GROUP: {
			// テーブルヘッダグループ
			final TableRowGroupPos pos = new TableRowGroupPos();
			final InnerTableParams params = new InnerTableParams();
			this.setupTableRowGroup(params, pos, style, RowGroupType.HEADER);
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.startBox(rowGroup);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_ROW_GROUP: {
			// テーブル行グループ
			final TableRowGroupPos pos = new TableRowGroupPos();
			final InnerTableParams params = new InnerTableParams();
			this.setupTableRowGroup(params, pos, style, RowGroupType.BODY);
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.startBox(rowGroup);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_FOOTER_GROUP: {
			// テーブルフッタグループ
			TableRowGroupPos pos = new TableRowGroupPos();
			InnerTableParams params = new InnerTableParams();
			this.setupTableRowGroup(params, pos, style, RowGroupType.FOOTER);
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.startBox(rowGroup);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_ROW: {
			// テーブル行
			TableRowPos pos = new TableRowPos();
			InnerTableParams params = new InnerTableParams();
			this.setupTableRow(params, pos, style);
			TableRowBox row = new TableRowBox(params, pos);
			this.startBox(row);
			this.inTextBlock = false;
		}
			break;

		case DisplayValue.TABLE_CELL: {
			// テーブルセル
			final TableCellPos pos = new TableCellPos();
			final BlockParams params = new BlockParams();
			this.setupTableCellPos(pos, style);
			this.setupBlockParams(params, style);
			final TableCellBox cell = new TableCellBox(params, pos, new FlowContainer());
			this.startBox(cell);
			this.inTextBlock = false;

			// 段組みの開始
			style = this.startColumns(style, cell);
		}
			break;

		default:
			throw new IllegalStateException();
		}

		this.currentStyle = style;
	}

	public void characters(int charOffset, char[] ch, int off, int len) {
		assert len > 0;
		// run-inの中
		if (this.runIn != null) {
			this.runIn.characters(charOffset, ch, off, len);
			return;
		}

		if (!this.pageContentStack.isEmpty()) {
			PageContent pageContent = (PageContent) this.pageContentStack.get(this.pageContentStack.size() - 1);
			pageContent.characters(charOffset, ch, off, len);
			return;
		}
		if (this.htmlRootBlock == null && this.currentStyle != null) {
			// 本文の中
			this.segment.characters(charOffset, ch, off, len); // 本流のセグメント記録(M6a)
			if (!this.inTextBlock) {
				// ブロック補完のためにテキストブロックの開始をチェック
				// net.zamasoft.foliojet.layoutパッケージを直接利用する場合のために、
				// StyledTextUnitizerでも同じ処理をしています。
				final CSSStyle style = this.currentStyle;
				TEXTBLOCK: switch (WhiteSpace.get(style)) {
				case AbstractTextParams.WHITE_SPACE_NORMAL:
				case AbstractTextParams.WHITE_SPACE_NOWRAP:
					// 空白か制御コード以外の文字が必要
					for (int i = 0; i < len; ++i) {
						char c = ch[i + off];
						if (!TextUtils.isWhiteSpace(c)) {
							break TEXTBLOCK;
						}
					}
					return;

				case AbstractTextParams.WHITE_SPACE_PRE_LINE:
					// 改行コードか空白か制御コード以外の文字が必要
					for (int i = 0; i < len; ++i) {
						char c = ch[i + off];
						if (!TextUtils.isWhiteSpace(c) || c == '\n') {
							break TEXTBLOCK;
						}
					}
					return;
				case AbstractTextParams.WHITE_SPACE_PRE:
				case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
					break;
				default:
					throw new IllegalStateException();
				}
				this.inTextBlock = true;
			}

			// テキストの中
			final byte parentRuby = CSSJRuby.get(this.currentStyle);
			if (parentRuby == CSSJRubyValue.RUBY) {
				this.startRB(this.currentStyle.inheritAnonStyle(CSSElement.ANON_RB));
			}

			if (this.firstLetter) {
				this.firstLetter = false;

				// :first-letter
				this.styleContext.startElement(CSSElement.FIRST_LETTER);
				final Declaration declaration = this.styleContext.merge(null);
				this.styleContext.endElement();
				if (declaration != null) {
					final CSSStyle firstLetterStyle = CSSStyle.getCSSStyle(this.ua, this.currentStyle,
							CSSElement.FIRST_LETTER);
					declaration.applyProperties(firstLetterStyle);
					if (Display.get(firstLetterStyle) != DisplayValue.NONE) {
						this.startStyle(firstLetterStyle);
						final LanguageProfile lang = LanguageProfileBundle
								.getLanguageProfile(this.currentStyle.getCSSElement().lang);
						int first = lang.countFirstLetter(ch, off, len);
						this.checkMarker();
						this.doc.characters(charOffset, ch, off, first, false);
						len -= first;
						off += first;
						charOffset += first;
						this.endStyle();
					}
					if (len == 0) {
						return;
					}
				}
			}
			this.checkMarker();

			if (this.currentStyle != null) {
				WHILE: while (this.currentStyle.isAnonStyle()) {
					// 匿名スタイルの終了
					final short anonDisplay = Display.get(this.currentStyle);
					switch (anonDisplay) {
					case DisplayValue.TABLE_ROW:
						CSSStyle parent = this.currentStyle.getParentStyle();
						if (!parent.isAnonStyle() || !parent.getParentStyle().isAnonStyle()) {
							break WHILE;
						}
					case DisplayValue.TABLE_ROW_GROUP:
					case DisplayValue.TABLE:
					case DisplayValue.INLINE_TABLE:
						break;
					default:
						break WHILE;
					}
					this._endStyle();
				}
			}

			String em = TextEmphasisStyle.get(this.currentStyle);
			if (em == null || em.length() == 0) {
				this.doc.characters(charOffset, ch, off, len, false);
			} else {
				// 圏点
				final char[] emc = em.toCharArray();
				final boolean vert = BlockFlow.get(this.currentStyle).isVertical();
				final boolean logVert = (CSSJDirectionMode.get(this.currentStyle) == CSSJDirectionModeValue.PHYSICAL
						&& vert) || CSSJDirectionMode.get(this.currentStyle) == CSSJDirectionModeValue.VERTICAL_RL;
				Value color = this.currentStyle.get(TextEmphasisColor.INFO);
				if (color == KeywordValue.DEFAULT) {
					color = this.currentStyle.get(CSSColor.INFO);
				}
				for (int i = 0; i < len; ++i) {
					final CSSStyle eb = this.currentStyle.inheritAnonStyle(CSSElement.ANON);
					eb.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
					eb.set(CSSPosition.INFO, PositionValue.RELATIVE_VALUE);
					eb.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
					if (vert) {
						eb.set(LineHeight.INFO, EM_1_618);
					} else {
						eb.set(LineHeight.INFO, EM_1_414);
					}
					this._startStyle(eb);
					final CSSStyle et = eb.inheritAnonStyle(CSSElement.ANON);
					et.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
					et.set(CSSPosition.INFO, PositionValue.ABSOLUTE_VALUE);
					et.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
					et.set(CSSColor.INFO, color);
					et.set(FontSize.INFO, PercentageValue.HALF);
					if (logVert) {
						et.set(Height.INFO, PercentageValue.FULL);
						et.set(Inset.LEFT, EM_1_4);
					} else {
						et.set(Width.INFO, PercentageValue.FULL);
						et.set(Inset.BOTTOM, EM_1_4);
					}
					et.set(TextAlign.INFO, TextAlignValue.CENTER_VALUE);
					this._startStyle(et);
					this.doc.characters(-1, emc, 0, 1, false);
					this._endStyle();
					this.doc.characters(charOffset, ch, i + off, 1, false);
					this._endStyle();
				}
			}
		}
	}

	private PageBreakMode toPageBreak(byte pageBreak, CSSStyle style) {
		switch (pageBreak) {
		case PageBreakValue.PAGE_BREAK_AUTO:
			return PageBreakMode.AUTO;
		case PageBreakValue.PAGE_BREAK_AVOID:
			return PageBreakMode.AVOID;
		case PageBreakValue.PAGE_BREAK_ALWAYS:
			return PageBreakMode.PAGE;
		case PageBreakValue.PAGE_BREAK_LEFT:
			if ((this.rightSide && CSSJDirectionMode.get(style) == CSSJDirectionModeValue.PHYSICAL)
					|| CSSJDirectionMode.get(style) == CSSJDirectionModeValue.VERTICAL_RL) {
				return PageBreakMode.RECTO;
			}
			return PageBreakMode.VERSO;
		case PageBreakValue.PAGE_BREAK_RIGHT:
			if ((this.rightSide && CSSJDirectionMode.get(style) == CSSJDirectionModeValue.PHYSICAL)
					|| CSSJDirectionMode.get(style) == CSSJDirectionModeValue.VERTICAL_RL) {
				return PageBreakMode.VERSO;
			}
			return PageBreakMode.RECTO;
		case PageBreakValue.PAGE_BREAK_IF_LEFT:
			if ((this.rightSide && CSSJDirectionMode.get(style) == CSSJDirectionModeValue.PHYSICAL)
					|| CSSJDirectionMode.get(style) == CSSJDirectionModeValue.VERTICAL_RL) {
				return PageBreakMode.IF_RECTO;
			}
			return PageBreakMode.IF_VERSO;
		case PageBreakValue.PAGE_BREAK_IF_RIGHT:
			if ((this.rightSide && CSSJDirectionMode.get(style) == CSSJDirectionModeValue.PHYSICAL)
					|| CSSJDirectionMode.get(style) == CSSJDirectionModeValue.VERTICAL_RL) {
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

	private void checkMarker() {
		if (this.marker == null) {
			return;
		}
		// 外部マーカー
		Marker marker = this.marker;
		this.marker = null;
		this.marker(marker);
	}

	private void marker(Marker marker) {
		this.startBox(marker.box);
		if (marker.text != null) {
			// マーカーのテキスト
			this.doc.characters(-1, marker.text, 0, marker.text.length, false);
		} else if (marker.imageBox != null) {
			this.doc.addReplacedBox(marker.imageBox);
		}
		this.doc.endBox();
	}

	private void _endStyle() {
		final CSSStyle style = this.currentStyle;
		// System.out.println("/" + style.path());
		if (!this.inBody) {
			this.inBody = true;
			this._startStyle(style);
		}
		if (CSSJInternalImage.getImage(style) == null) {
			this.doc.endBox();
		}
		switch (Display.get(style)) {
		case DisplayValue.TABLE:
		case DisplayValue.INLINE_TABLE:
		case DisplayValue.BLOCK:
		case DisplayValue.LIST_ITEM:
		case DisplayValue.TABLE_CAPTION:
		case DisplayValue.TABLE_COLUMN_GROUP:
		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_CELL:
			this.inTextBlock = false;
			break;

		case DisplayValue.INLINE_BLOCK:
			this.inTextBlock = true;
			break;

		case DisplayValue.INLINE:
			break;

		default:
			throw new IllegalStateException();
		}

		this.currentStyle = style.getParentStyle();
	}

	public void endStyle() {
		CSSStyle style = this.currentStyle;
		if (DEBUG) {
			System.err.println("/" + style.path());
		}

		final CSSElement ce = style.getCSSElement();
		if (this.state != STATE_RESTYLE_RUN_IN && ce != CSSElement.AFTER && ce != CSSElement.BEFORE
				&& CSSJInternalImage.getImage(style) == null) {
			// :after
			boolean br = XHTML.BR_ELEM.equalsElement(ce);
			CSSElement afterCe = CSSElement.AFTER;
			this.styleContext.startElement(afterCe);
			final Declaration afterDeclaration = this.styleContext.merge(null);
			if (afterDeclaration != null || br || HTMLStyle.hasAfterContent(ce)) {
				CSSStyle afterStyle = CSSStyle.getCSSStyle(this.ua, style, afterCe);
				HTMLStyle.applyAfterStyle(afterStyle);
				if (br) {
					afterStyle.set(Content.INFO, LF);
					afterStyle.set(Clear.INFO, KeywordValue.INHERIT);
				}
				if (afterDeclaration != null) {
					afterDeclaration.applyProperties(afterStyle);
				}
				if (br && Display.get(afterStyle) == DisplayValue.INLINE) {
					PageBreakMode pageBreakBefore = this.toPageBreak(PageBreakBefore.get(afterStyle), afterStyle);
					PageBreakMode pageBreakAfter = this.toPageBreak(PageBreakAfter.get(afterStyle), afterStyle);
					if ((pageBreakBefore != PageBreakMode.AUTO
							&& pageBreakBefore != PageBreakMode.AVOID)
							|| (pageBreakAfter != PageBreakMode.AUTO
									&& pageBreakAfter != PageBreakMode.AVOID)) {
						afterStyle.set(Display.INFO, DisplayValue.BLOCK_VALUE);
					}
				}
				if (Content.get(afterStyle) != null && Display.get(afterStyle) != DisplayValue.NONE) {
					this.startStyle(afterStyle);
					this.endStyle();
				}
			}
			this.styleContext.endElement();
		}

		if (this.runIn != null) {
			// run-inの中
			this.runIn.endStyle(this.currentStyle);
			this.currentStyle = this.currentStyle.getParentStyle();
			return;
		}

		if (!this.pageContentStack.isEmpty()) {
			// ページことに生成される内容
			PageContent pageContent = (PageContent) this.pageContentStack.get(this.pageContentStack.size() - 1);
			pageContent.endStyle(this.currentStyle);
			if (pageContent.getDepth() == 0) {
				this.pageContentStack.remove(this.pageContentStack.size() - 1);
			}
			this.currentStyle = this.currentStyle.getParentStyle();
			return;
		}

		// 匿名スタイルを終了
		while (this.currentStyle.isAnonStyle()) {
			this._endStyle();
		}

		// 明示されたスタイルを終了
		style = this.currentStyle;
		if (!style.getCSSElement().isPseudoElement()) {
			// 本流のセグメント記録(M6a)
			this.segment.endStyle(style);
		}
		this._endStyle();
		if (this.currentStyle != null) {
			short explDisplay = Display.get(style);
			WHILE: while (this.currentStyle.isInsertedAnonStyle()) {
				// 匿名スタイルの終了
				final short anonDisplay = Display.get(this.currentStyle);
				switch (explDisplay) {
				case DisplayValue.TABLE_CELL:
					switch (anonDisplay) {
					case DisplayValue.TABLE_ROW:
						// セルを終わるときは行で止める
						break WHILE;
					}
					break;
				case DisplayValue.TABLE_ROW:
					switch (anonDisplay) {
					// 行を終わるときは行グループで止める
					case DisplayValue.TABLE_ROW_GROUP:
						break WHILE;
					}
					break;
				case DisplayValue.INLINE:
				case DisplayValue.BLOCK:
				case DisplayValue.LIST_ITEM:
				case DisplayValue.INLINE_BLOCK:
				case DisplayValue.TABLE:
				case DisplayValue.INLINE_TABLE:
					switch (anonDisplay) {
					// 匿名セルが生成されている場合は行で止める
					case DisplayValue.TABLE_ROW:
						break WHILE;
					}
					// 匿名RBが生成されている場合はRBで止める
					if (CSSJRuby.get(this.currentStyle) == CSSJRubyValue.RB) {
						break WHILE;
					}
					break;
				}
				if (style.getParentStyle() == this.currentStyle) {
					style.removeAnonStyle();
				}
				this._endStyle();
			}
		}

		if (!style.getCSSElement().isPseudoElement()) {
			// リスト用カウンタのクリア
			if (!this.listCounterStack.isEmpty()) {
				int[] counter = (int[]) this.listCounterStack.get(this.listCounterStack.size() - 1);
				if (counter[0] > this.depth) {
					this.listCounterStack.remove(this.listCounterStack.size() - 1);
				}
			}
			--this.depth;
		}
		this.firstLetter = false;
	}

	public PageBreakMode getPageSide() {
		if (this.pageElement.isPseudoClass(CSSElement.PC_EVEN)) {
			return PageBreakMode.VERSO;
		}
		if (this.pageElement.isPseudoClass(CSSElement.PC_ODD)) {
			return PageBreakMode.RECTO;
		}
		return PageBreakMode.AUTO;
	}

	public PageBox nextPage() {
		// セグメント窓の刈り込み: 開いている要素だけ残す(M6a)
		this.segment.trimToOpenElements();
		// ページスタイル
		this.pageElement = this.imposition.nextPageSide();
		Declaration declaration = this.styleContext.nextPage(this.pageElement);
		CSSStyle pageStyle = CSSStyle.getCSSStyle(this.ua, null, this.pageElement);

		// デフォルトのマージン
		if (this.margins != null) {
			switch (this.margins.length) {
			case 1:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[0]);
				pageStyle.set(Margin.BOTTOM, this.margins[0]);
				pageStyle.set(Margin.LEFT, this.margins[0]);
				break;
			case 2:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[1]);
				pageStyle.set(Margin.BOTTOM, this.margins[0]);
				pageStyle.set(Margin.LEFT, this.margins[1]);
				break;
			case 3:
				pageStyle.set(Margin.TOP, this.margins[1]);
				pageStyle.set(Margin.RIGHT, this.margins[2]);
				pageStyle.set(Margin.BOTTOM, this.margins[3]);
				pageStyle.set(Margin.LEFT, this.margins[2]);
				break;
			case 4:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[1]);
				pageStyle.set(Margin.BOTTOM, this.margins[2]);
				pageStyle.set(Margin.LEFT, this.margins[3]);
				break;
			}
		}

		declaration.applyProperties(pageStyle);

		// ページカウンターリセット
		Value[] resets = CounterReset.get(pageStyle);
		if (resets != null) {
			for (int i = 0; i < resets.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) resets[i];
				String name = counterSet.getName();
				int value = counterSet.getValue();
				this.ua.getPassContext().getCounterScope(0, true).reset(name, value);
			}
		}

		// ページカウンター加算
		Value[] increments = CounterIncrement.get(pageStyle);
		if (increments != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < increments.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) increments[i];
				String name = counterSet.getName();
				int delta = counterSet.getValue();
				pc.getCounterScope(0, true).increment(name, delta);
			}
		}

		// ルートのスタイルを適用
		if (this.background == null) {
			this.background = Background.NULL_BACKGROUND;
		}

		final BlockParams params = new BlockParams();
		params.flow = this.progression;
		params.fontStyle = pageStyle.getFontStyle();
		params.fontManager = this.ua.getFontManager();
		final LanguageProfile lang = LanguageProfileBundle
				.getLanguageProfile(pageStyle.getCSSElement().lang);
		params.lineBreakRules = lang.getLineBreakRules(pageStyle);

		// ページのサイズ
		double width = this.imposition.getPageWidth();
		double height = this.imposition.getPageHeight();

		if ((this.doc.getPageMode() & DocumentBuilder.PAGE_MODE_CONTINUOUS) != 0) {
			if (this.imposition.getBoundSide() == BoundSide.LEFT) {
				// 横書き
				params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.AUTO);
			} else {
				// 縦書き
				params.size = Dimension.create(width, height, LengthType.AUTO, LengthType.ABSOLUTE);
			}
		} else {
			params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		}
		params.overflow = OverflowMode.VISIBLE;

		// マージン
		Value marginTop = Margin.get(pageStyle, Side.TOP);
		Value marginRight = Margin.get(pageStyle, Side.RIGHT);
		Value marginBottom = Margin.get(pageStyle, Side.BOTTOM);
		Value marginLeft = Margin.get(pageStyle, Side.LEFT);
		Insets margin = BoxValueUtils.toInsets(marginTop, marginRight, marginBottom, marginLeft);

		params.frame = RectFrame.create(margin, RectBorder.NONE_RECT_BORDER, this.background, Insets.NULL_INSETS);

		this.pageNumber++;
		if (this.maxPageNumber != -1 && this.pageNumber > this.maxPageNumber) {
			short code = MessageCodes.ERROR_OUT_OF_PAGE_LIMIT;
			String[] args = new String[] { String.valueOf(this.maxPageNumber) };
			ua.message(code, args);
			if (UAProps.OUTPUT_PAGE_LIMIT_ABORT.get(ua) == OutputPageLimitAbort.NORMAL) {
				throw new AbortException(AbortException.ABORT_NORMAL);
			}
			throw new AbortException(AbortException.ABORT_FORCE);
		}
		this.ua.message(MessageCodes.INFO_PAGE_NUMBER, String.valueOf(this.pageNumber));
		return new PageBox(params, this.ua);
	}

	public void drawPage(final PageBox pageBox) throws GraphicsException {
		// ページサイズ決定
		if (UAProps.OUTPUT_EXPAND_WITH_CONTENT.getBoolean(ua)) {
			this.imposition.setPageWidth(pageBox.getVisualWidth());
			this.imposition.setPageHeight(pageBox.getVisualHeight());
		} else {
			this.imposition.setPageWidth(pageBox.getWidth());
			this.imposition.setPageHeight(pageBox.getHeight());
		}
		if (UAProps.OUTPUT_PAPER_WIDTH.getString(ua) == null) {
			this.imposition.fitPaperWidth();
		}
		if (UAProps.OUTPUT_PAPER_HEIGHT.getString(ua) == null) {
			this.imposition.fitPaperHeight();
		}

		if ((this.doc.getPageMode() & DocumentBuilder.PAGE_MODE_CONTINUOUS) != 0) {
			// 自動高さの場合、高さを通知する
			this.ua.message(MessageCodes.INFO_PAGE_HEIGHT, String.valueOf(pageBox.getHeight()));
		}

		// 描画
		final GC gc = this.imposition.nextPage();

		if (UAProps.OUTPUT_EXPAND_WITH_CONTENT.getBoolean(ua)) {
			if (pageBox.getVisualWidth() > pageBox.getWidth()) {
				gc.transform(AffineTransform.getTranslateInstance(pageBox.getVisualWidth() - pageBox.getWidth(), 0));
			}
			this.imposition.setPageWidth(pageBox.getWidth());
			this.imposition.setPageHeight(pageBox.getHeight());
			if (UAProps.OUTPUT_PAPER_WIDTH.getString(ua) == null) {
				this.imposition.fitPaperWidth();
			}
			if (UAProps.OUTPUT_PAPER_HEIGHT.getString(ua) == null) {
				this.imposition.fitPaperHeight();
			}
		}

		final AffineTransform marginT;
		GC.State marginState = null;
		if (gc != null) {
			AbsoluteInsets margin = pageBox.getFrame().margin;
			double xoff = margin.left;
			double yoff = margin.top;
			if (xoff != 0 || yoff != 0) {
				marginT = AffineTransform.getTranslateInstance(xoff, yoff);
			} else {
				marginT = null;
			}
			if (marginT != null) {
				marginState = gc.begin();
				gc.transform(marginT);
			}
		} else {
			marginT = null;
		}

		Visitor visitor = this.ua.getVisitor(gc);
		visitor = new VisitorWrapper(visitor) {
			public void visitBox(AffineTransform transform, IBox box, Drawer drawer, double x, double y) {
				super.visitBox(transform, box, drawer, x, y);
				Object key = box.getParams().element;
				String[] pageContentClearNames = (String[]) StyleBuilder.this.toPageContentClear.remove(key);
				if (pageContentClearNames != null) {
					for (int i = 0; i < pageContentClearNames.length; ++i) {
						StyleBuilder.this.pageContents.remove(pageContentClearNames[i]);
					}
				}

				PageContent pageContent = (PageContent) StyleBuilder.this.toPageContent.remove(key);
				if (pageContent != null) {
					StyleBuilder.this.pageContents.put(pageContent.name, pageContent);
				}
			}
		};
		visitor.startPage();

		final Drawer drawer = new Drawer(0);

		// フロー
		pageBox.drawFlow(drawer, visitor);

		if (gc != null) {
			// 固定
			pageBox.drawFixed(drawer, visitor);

			// ページごとに生成される内容
			if (!this.pageContents.isEmpty()) {
				for (Iterator<PageContent> i = this.pageContents.values().iterator(); i.hasNext();) {
					PageContent pageContent = (PageContent) i.next();
					boolean apply;
					if (pageContent.pages != null) {
						// System.out.println(Arrays.asList(pageContent.pages));
						// System.out.println(this.pageElement);
						apply = false;
						for (int j = 0; j < pageContent.pages.length; ++j) {
							byte page = pageContent.pages[j];
							if (page == 0 && (this.pageElement == CSSElement.PAGE_SINGLE
									|| this.pageElement == CSSElement.PAGE_SINGLE_FIRST)) {
								apply = true;
								break;
							}
							if (this.pageElement.isPseudoClass(page)) {
								apply = true;
								break;
							}
						}
					} else {
						apply = true;
					}
					if (apply) {
						CSSStyle style = this.currentStyle;
						StyleContext styleContext = this.styleContext;
						this.styleContext = pageContent.styleContext;
						pageContent.restyle(this);
						this.styleContext = styleContext;
						this.currentStyle = style;
					}
				}
			}

			pageBox.drawPageContents(drawer, visitor);
			visitor.endPage();

		}

		// 描画処理を非同期で実行
		// PDFでは描画処理は非常に早く終わる
		if (gc != null) {
			DisplayListDumper.dumpPage(drawer, this.pageNumber);
			drawer.draw(gc);
			if (marginState != null) {
				marginState.close();
			}
		}
		StyleBuilder.this.imposition.closePage();
	}

	public void finish() throws GraphicsException {
		this.doc.end();
		this.imposition.finish();
		this.ua.getPassContext().setPageNumber(this.pageNumber);
	}
}
