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
 * @author MIYABE Tatsuhiko
 */
public class StyleBuilder implements PageGenerator, StyleBuildContext {
	private static final boolean DEBUG = false;

	private static final Logger LOG = Logger.getLogger(StyleBuilder.class.getName());

	/**
	 * 総ページ数カウンタ名。css-page-3 §6.1相当のUA予約カウンタとして扱い、
	 * 著者の{@code counter-reset}/{@code counter-increment}からは保護する
	 * ({@link #isReservedCounterName(String)}参照)。
	 */
	private static final String PAGES_COUNTER_NAME = "pages";


	static boolean isReservedCounterName(String name) {
		return PAGES_COUNTER_NAME.equalsIgnoreCase(name);
	}


	private final UserAgent ua;

	private final DocumentBuilder doc;
	private final Imposition imposition;
	private StyleContext styleContext;
	private CSSStyle currentStyle;

	private FlowBlockBox htmlRootBlock = null;
	private boolean rightSide = false;
	private boolean inBody = false;
	private boolean inTextBlock = false;




	/**
	 * 本流のスタイルイベント窓です(M6a)。改ページ再開をボックス再生から
	 * セグメント再駆動へ置き換えるための記録で、現段階では記録のみ
	 * (消費者なし)。疑似要素(::before/::after/::first-letter 等)の
	 * 合成イベントは再生時に再合成されるため記録しません(ソース純度)。
	 * 窓はページ境界で開いている要素の Start だけに刈り込まれます。
	 */
	private final Segment segment = new Segment();

	/**
	 * M6b v3 のレイアウトソースプロトコルtee(記録+docへの引き渡し)。
	 * 記録の契約・{@code LayoutSource}の寿命は{@link RecordingLayoutSink}参照
	 * (StyleBuilder解体・増分1で抽出、2026-07-30)。
	 */
	private final RecordingLayoutSink sink;

	/**
	 * ページのライフサイクル(作成・@pageカウンタ・白紙判定と
	 * 巻き戻し・描画・面付け終了)。StyleBuilder解体・増分2で抽出
	 * (2026-07-30)。PageGeneratorの実装は引き続きStyleBuilderで、
	 * ページ系のメソッドはここへ委譲する。
	 */
	private final PageSequence pageSequence;

	/**
	 * CSS計算値→Params/Pos/RectFrameの写像群。StyleBuilder解体・
	 * 増分3で抽出(2026-07-30)。
	 */
	private final BoxStyleMapper mapper;

	/**
	 * displayによるボックスdispatchと匿名表補完。StyleBuilder解体・
	 * 増分4aで抽出(2026-07-30。逐語移動——匿名表の再帰は残存し、
	 * 反復化は増分4b。状態は{@link StyleBuildContext}経由で共有)。
	 */
	private final StyleBoxEmitter emitter;

	/**
	 * スタイルイベントの状態機械(カウンタ・string-set・マーカー・quotes・
	 * generated content・::first-letter)。StyleBuilder解体・増分5で抽出
	 * (2026-07-30、逐語移動)。
	 */
	private final StyleEventMachine eventMachine;

	/**
	 * レイアウトソースログを返します(M6b v3)。
	 */
	public LayoutSource getLayoutSource() {
		return this.sink.source();
	}

	public int getDeliveredCharEnd() {
		return this.doc.getDeliveredCharEnd();
	}

	public void compactLayoutSource(final long watermark) {
		this.sink.compact(watermark);
	}

	/**
	 * 本流のスタイルイベント窓を返します(M6a)。
	 */
	public Segment getSegment() {
		return this.segment;
	}


	/**
	 * レイアウトソースのspillストア(一時ファイル)を閉じます
	 * (E-6増分3b-2)。変換の終了経路——成功・例外を問わずformatterの
	 * finallyから{@code CSSProcessor.dispose()}経由で呼ばれる。冪等。
	 */
	public void closeLayoutSource() {
		this.sink.close();
	}

	public StyleBuilder(StyleContext styleContext, UserAgent ua, Imposition imposition) {
		this.styleContext = styleContext;
		this.ua = ua;
		this.imposition = imposition;
		this.doc = new DocumentBuilder(this);
		// E-6増分3b-2: text payloadのspill予算(bytes)はsinkが注入する
		this.sink = new RecordingLayoutSink(this.doc, UAProps.PROCESSING_TEXT_SPILL_BUDGET.getLong(ua));

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

		// ページ幅・高さ・マージン・最大ページ数の初期化は
		// PageSequenceのコンストラクタへ移動(増分2、2026-07-30。
		// 警告メッセージの順序も従来と同一)
		this.pageSequence = new PageSequence(ua, styleContext, imposition, this.doc, this.segment,
				this::warnReservedCounter);
		this.mapper = new BoxStyleMapper(ua, styleContext);
		this.emitter = new StyleBoxEmitter(this, this.sink, this.mapper, this.pageSequence, ua, imposition);
		this.eventMachine = new StyleEventMachine(this, this.segment, this.sink, this.mapper, this.emitter,
				this.pageSequence, ua, styleContext);
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public CSSElement getPageElement() {
		return this.pageSequence.getPageElement();
	}




	public CSSStyle getCurrentStyle() {
		return this.currentStyle;
	}

	public void startStyle(final CSSStyle style) {
		this.eventMachine.startStyle(style);
	}

	public void characters(final int charOffset, final char[] ch, final int off, final int len) {
		this.eventMachine.characters(charOffset, ch, off, len);
	}

	public void endStyle() {
		this.eventMachine.endStyle();
	}

	@Override
	public void checkMarker() {
		this.eventMachine.checkMarker();
	}

	/** PageSequenceの予約カウンタ警告の委譲先(実体は増分5で機械側へ)。 */
	void warnReservedCounter(final String name) {
		this.eventMachine.warnReservedCounter(name);
	}



	public PageBreakMode getPageSide() {
		return this.pageSequence.getPageSide();
	}

	public PageBox nextPage() {
		return this.pageSequence.nextPage();
	}

	@Override
	public String getPageName() {
		return this.pageSequence.getPageName();
	}

	@Override
	public void setPageName(String pageName) {
		this.pageSequence.setPageName(pageName);
	}

	public boolean drawPage(final PageBox pageBox, final boolean lastPage, final boolean closedByForcedBreak)
			throws GraphicsException {
		return this.pageSequence.drawPage(pageBox, lastPage, closedByForcedBreak);
	}

	public void finish() throws GraphicsException {
		this.doc.end();
		this.pageSequence.finish();
		// E-6増分3b-2: 最終ページ確定後はソース再生が発生しないため、
		// spillストアの一時ファイルをここで早期解放する(例外経路は
		// formatterのfinally→CSSProcessor.dispose→closeLayoutSourceが清算)
		this.sink.close();
	}

	// ---- StyleBuildContext(増分4a)——状態の物理置き場は当面ここのまま ----

	// getCurrentStyle()は既存のpublicメソッドを流用(StyleBuildContext実装)

	@Override
	public void setCurrentStyle(final CSSStyle style) {
		this.currentStyle = style;
	}

	@Override
	public FlowBlockBox getHtmlRootBlock() {
		return this.htmlRootBlock;
	}

	@Override
	public void setHtmlRootBlock(final FlowBlockBox box) {
		this.htmlRootBlock = box;
	}

	@Override
	public boolean isInBody() {
		return this.inBody;
	}

	@Override
	public void setInBody(final boolean inBody) {
		this.inBody = inBody;
	}

	@Override
	public boolean isInTextBlock() {
		return this.inTextBlock;
	}

	@Override
	public void setInTextBlock(final boolean inTextBlock) {
		this.inTextBlock = inTextBlock;
	}

	@Override
	public boolean isRightSide() {
		return this.rightSide;
	}

	@Override
	public void setRightSide(final boolean rightSide) {
		this.rightSide = rightSide;
	}
}
