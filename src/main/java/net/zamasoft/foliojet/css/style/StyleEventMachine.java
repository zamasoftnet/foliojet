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
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
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
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
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
import net.zamasoft.foliojet.css.impl.property.content.CounterSet;
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
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisPosition;
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
import net.zamasoft.pdfg2d.util.IntList;
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
 * スタイルイベント(startStyle/characters/endStyle)の状態機械です
 * (StyleBuilder解体・増分5、2026-07-30。本体はStyleBuilderから逐語移動
 * ——挙動不変)。カウンタ・named string・target参照・リストマーカー・
 * quotes・generated content・::first-letterの状態を所有する。
 *
 * <p>
 * ::before/::after/::first-letterの合成イベントは自己の
 * {@code startStyle}/{@code endStyle}への再入(深さは疑似要素の
 * 入れ子で有界)。M6a Segmentの記録点は旧コードの位置のまま。
 * </p>
 */
final class StyleEventMachine {
	private static final Logger LOG = Logger.getLogger(StyleEventMachine.class.getName());

	private static final boolean DEBUG = false;

	private static final ValueListValue LF = new ValueListValue(new Value[] { new StringValue("\n") });

	private static final RelativeLengthValue EM_1_618 = RelativeLengthValue.em(1.618);
	private static final RelativeLengthValue EM_1_414 = RelativeLengthValue.em(1.414);
	private static final RelativeLengthValue EM_1_4 = RelativeLengthValue.em(1.4);

	private final StyleBuildContext context;
	private final Segment segment;
	private final RecordingLayoutSink sink;
	private final BoxStyleMapper mapper;
	private final StyleBoxEmitter emitter;
	private final PageSequence pageSequence;
	private final UserAgent ua;

	/** 生成コンテンツの参照解決(string-set/target-*系、増分14で分離)。 */
	private final GeneratedContentResolver generated;
	private final StyleContext styleContext;

	private boolean warnedReservedCounter = false;
	private int depth = 0;
	private int quoteLevel = 0;
	/** リストアイテム用のカウンタ。要素は int[]{深さ, 値} 。 */
	private final List<int[]> listCounterStack = new ArrayList<int[]>();
	private Marker marker = null;
	private boolean firstLetter = false;

	StyleEventMachine(final StyleBuildContext context, final Segment segment, final RecordingLayoutSink sink,
			final BoxStyleMapper mapper, final StyleBoxEmitter emitter, final PageSequence pageSequence,
			final UserAgent ua, final StyleContext styleContext) {
		this.context = context;
		this.segment = segment;
		this.sink = sink;
		this.mapper = mapper;
		this.emitter = emitter;
		this.pageSequence = pageSequence;
		this.ua = ua;
		this.generated = new GeneratedContentResolver(ua);
		this.styleContext = styleContext;
	}

	void startStyle(CSSStyle style) {
		if (DEBUG) {
			System.err.println(style.path());
		}
		final CSSElement ce = style.getCSSElement();

		short explDisplay = Display.get(style);

		// @container G4(2026-08-15段4、docs/history/2026-08-15-container-queries-design.md §2):
		// container-type: inline-sizeの要素は、この時点(スタイル確定・
		// レイアウトより前)で「クエリコンテナである」ことと名前を記録する。
		// 実測inline-sizeはレイアウト確定後(AbstractVisitor.visitBox)で
		// 別途書き込む。擬似要素はelementKeyが安定しない(-1)ため対象外
		if (!ce.isPseudoElement() && ce.elementKey >= 0
				&& net.zamasoft.foliojet.css.impl.property.container.ContainerType.get(style) //
						== net.zamasoft.foliojet.css.value.ContainerTypeValue.INLINE_SIZE) {
			this.ua.getUAContext().getContainerFacts().setInlineSizeContainer(ce.elementKey,
					net.zamasoft.foliojet.css.impl.property.container.ContainerName.get(style));
		}

		if (!ce.isPseudoElement()) {
			// 本流のセグメント記録(M6a)
			this.segment.startStyle(style);
		}
		if (this.context.getCurrentStyle() != null) {
			WHILE: while (this.context.getCurrentStyle().isAnonStyle()) {
				// 匿名スタイルの終了

				// 静的要素のみに適用
				final byte pos = CSSPosition.get(style);
				if (pos != PositionValue.STATIC && pos != PositionValue.RELATIVE && pos != PositionValue.STICKY) {
					break WHILE;
				}

				{
					// テーブル関係
					final short anonDisplay = Display.get(this.context.getCurrentStyle());
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
					case DisplayValue.GRID:
					case DisplayValue.FLEX:
					case DisplayValue.LIST_ITEM:
					case DisplayValue.INLINE_BLOCK:
					case DisplayValue.TABLE:
					case DisplayValue.INLINE_TABLE:
						switch (anonDisplay) {
						case DisplayValue.TABLE_ROW:
							CSSStyle parent = this.context.getCurrentStyle().getParentStyle();
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
				if (style.getParentStyle() == this.context.getCurrentStyle()) {
					style.removeAnonStyle();
				}
				this.emitter._endStyle();
			}
		}

		// BR
		if (XHTML.BR_ELEM.equalsElement(ce)) {
			// クリアランス、強制改ページは後にブロックを生成する
			ClearMode clear = Clear.get(style);
			PageBreakMode pageBreakBefore = this.mapper.toPageBreak(PageBreakBefore.get(style), this.context.isRightSide());
			PageBreakMode pageBreakAfter = this.mapper.toPageBreak(PageBreakAfter.get(style), this.context.isRightSide());
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
				params.lineBreakRules = LanguageProfileBundle
						.getLanguageProfile(style.getCSSElement().lang).getTextBreakingRules(style);
				params.direction = Direction.get(style);
				params.flow = BlockFlow.get(style);
				params.element = ce;
				final Insets margin = Insets.create(0, 0, -LineHeight.get(style), 0, LengthType.ABSOLUTE,
						LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
				params.frame = RectFrame.create(margin, RectBorder.NONE_RECT_BORDER,
						Background.NULL_BACKGROUND, Insets.NULL_INSETS);
				// テーブル内で問題が起こるので、匿名ボックスの処理をした後で挿入する
				FlowBlockBox flowBox = new FlowBlockBox(params, pos);
				this.sink.start(flowBox);
				this.sink.end();
			}
		}

		// 脚注F1(2026-07-31、consult-codex-2026-07-31-footnote.txt §3):
		// float:footnoteの要素は開始時に脚注番号(engine-ownedの文書通番、
		// globalスコープの"footnote"カウンタ)を進め、呼び出し位置=親の
		// インライン流へ::footnote-callを合成する。ページごとのリセットは
		// ページローカル再生増分(F5)まで保留。本文のページ下端への移動は
		// F3で配線——それまで本文はその場に描かれる
		// display:contentsは箱を作らないのでfloatも適用されない(CSS Display 3)
		boolean footnote = !ce.isPseudoElement() && explDisplay != DisplayValue.NONE
				&& explDisplay != DisplayValue.CONTENTS
				&& CSSFloat.get(style) == CSSFloatValue.FOOTNOTE;
		if (footnote && inTableStructure(style)) {
			// **表の構造の内側(行・行グループ・列)では脚注にしない**
			// (2026-08-02、掃過で発覚)。呼び出し(::footnote-call)は
			// インラインとして親へ合成されるが、表の構造の直下は
			// インラインを置けず、TableBuilderを要求する箱の構築に
			// 落ちて変換が失敗していた。セルの中は従来どおり脚注になる。
			// 表の構造の直下のインラインを匿名セルへ包む機構へ載せるのが
			// 本筋(PLANの脚注残)——それまでは通常のfloat扱いへ縮退する
			if (!this.warnedFootnoteInTableStructure) {
				this.warnedFootnoteInTableStructure = true;
				LOG.warning("float: footnote inside a table structure (row/row-group/column)"
						+ " is not supported; treated as a normal float");
			}
			footnote = false;
		}
		if (footnote) {
			// F7: 段組祖先内の脚注は段の高さが不揃いになり得る(予約が
			// ページ容量を縮めても組済みの段は再配分されない)。型付き失敗に
			// せず警告して続行(クラッシュ排除方針。脚注領域自体はページ
			// 全幅で置かれる——consult-codex-2026-07-31-footnote-f6f7.txt §4)
			for (CSSStyle ancestor = style.getParentStyle(); ancestor != null; ancestor = ancestor
					.getParentStyle()) {
				if (ColumnCount.get(ancestor) > 1) {
					java.util.logging.Logger.getLogger(StyleEventMachine.class.getName())
							.warning("footnote inside a multi-column ancestor: column heights may become uneven");
					break;
				}
			}
			// F4: 論理ID(表示番号とは独立)を元要素と::footnote-callの両方へ。
			// ページ確定時の「callがこのページに残ったか」の集合判定に使う
			style.footnoteId = this.nextFootnoteId++;
			this.ua.getPassContext().getCounterScope(0, true).increment("footnote", 1);
			this.footnotePseudo(style, CSSElement.FOOTNOTE_CALL);
		}

		// 外置きリストマーカーは通常、最初の文字が作る行へ遅延して置く。
		// ただし最初の子が表なら、その文字は最初のセルの中で初めて現れる。
		// そこまで遅延するとマーカーがセル内容に混入し、行分割時に
		// 「マーカーだけ前断片、セル本文は後断片」となって隣のセルより
		// 本文が後のページへ逆転する(seed 455)。表を開く前、まだ
		// list-item の直下にいる時点でマーカーを確定させる。
		if (this.marker != null
				&& (explDisplay == DisplayValue.TABLE || explDisplay == DisplayValue.INLINE_TABLE)) {
			if (this.marker.box instanceof OutsideMarkerBox outsideMarker) {
				outsideMarker.setOverlaysFollowingBlock(true);
			}
			this.checkMarker();
		}

		this.emitter._startStyle(style);

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
				if (StyleBuilder.isReservedCounterName(name)) {
					this.warnReservedCounter(name);
					continue;
				}
				int value = counterSet.getValue();
				CounterScope scope = pc.getCounterScope(0, false);
				if (scope != null && scope.defined(name)) {
					scope.reset(name, value);
					continue;
				}
				pc.getCounterScope(depth, true).reset(name, value);
			}
		}

		// カウンターの設定(counter-set、CSS Lists 3——2026-08-02)。
		// 新しい入れ子は作らず、一番内側の既存カウンタへ代入する
		// (探索はcounter-incrementと同じ。無ければこの要素に作る)
		final Value[] sets = CounterSet.get(style);
		if (sets != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < sets.length; ++i) {
				final CounterSetValue counterSet = (CounterSetValue) sets[i];
				final String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.warnReservedCounter(name);
					continue;
				}
				int level = depth;
				for (; level > 0; --level) {
					final CounterScope scope = pc.getCounterScope(level, false);
					if (scope != null && scope.defined(name)) {
						break;
					}
				}
				if (level == 0) {
					final CounterScope root = pc.getCounterScope(0, false);
					if (root == null || !root.defined(name)) {
						// どこにも無い——この要素に作る
						level = depth;
					}
				}
				pc.getCounterScope(level, true).reset(name, counterSet.getValue());
			}
		}

		// カウンター加算
		final Value[] increments = CounterIncrement.get(style);
		if (increments != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < increments.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) increments[i];
				String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.warnReservedCounter(name);
					continue;
				}
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

		// string-set(GCPM)。counter()/attr()/文字列は文書順=build時に確定させる
		// (呼び出しタイミングではなくelementKeyで先後を判定するNamedStringStateの
		// 契約を守るため)。content()を含むエントリのみ、要素のボックスが確定する
		// draw時(AbstractVisitor.visitBox)まで解決を保留する。
		final Value[] stringSets = StringSet.get(style);
		if (stringSets != null) {
			final long elementKey = ce.elementKey;
			for (int i = 0; i < stringSets.length; ++i) {
				final StringSetEntryValue entry = (StringSetEntryValue) stringSets[i];
				final Value[] parts = entry.getParts();
				final List<Object> resolvedParts = new ArrayList<Object>(parts.length);
				boolean needsContent = false;
				for (int j = 0; j < parts.length; ++j) {
					final Value part = parts[j];
					if (part instanceof ContentFunctionValue) {
						resolvedParts.add(PendingStringSet.CONTENT);
						needsContent = true;
					} else {
						resolvedParts.add(this.generated.stringSetPart(part, ce, depth));
					}
				}
				final String name = entry.getName();
				if (!needsContent) {
					final StringBuilder buff = new StringBuilder();
					for (int j = 0; j < resolvedParts.size(); ++j) {
						buff.append((String) resolvedParts.get(j));
					}
					this.ua.getPassContext().getNamedStringState().set(name, buff.toString(), elementKey);
				} else {
					List<PendingStringSet> pending = this.ua.getPassContext().getPendingStringSets().get(elementKey);
					if (pending == null) {
						pending = new ArrayList<PendingStringSet>();
						this.ua.getPassContext().getPendingStringSets().put(elementKey, pending);
					}
					pending.add(new PendingStringSet(name, resolvedParts));
				}
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
			// 2026-07-21新設: ::marker(CSS Lists)。BEFORE/AFTERと同じ
			// 仕組みでCSSElement.MARKERをカスケード解決し、限定的な
			// プロパティ(color/font-*等)だけliの実スタイルへ上書きする。
			// list-style-type/list-style-position等は::markerの対象
			// プロパティではないため、常にliの実スタイル(style)から
			// 読む(仕様どおり)。
			this.styleContext.startElement(CSSElement.MARKER);
			final Declaration markerDeclaration = this.styleContext.merge(null);
			CSSStyle markerStyle = style;
			if (markerDeclaration != null) {
				markerStyle = CSSStyle.getCSSStyle(this.ua, style, CSSElement.MARKER);
				markerDeclaration.applyProperties(markerStyle);
			}
			this.styleContext.endElement();
			BlockParams params = new BlockParams();
			this.mapper.setupBlockParams(params, markerStyle, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			this.mapper.setupInlinePos(pos, markerStyle);
			params.frame = RectFrame.NULL_FRAME;
			short listStyleType = ListStyleType.get(style);
			Image image = ListStyleImage.get(style);
			if (image == null) {
				image = GeneratedValueUtils.format(listStyleType, params.color, params.fontStyle);
			}
			this.marker = null;
			Marker marker = null;
			if (image == null) {
				final CounterStyles counterStyles = CounterStyles.of(this.ua);
				String str = counterStyles.format(number, listStyleType);
				if (str != null) {
					marker = new Marker();
					// 前後の記号は組み込みなら従来の句点、著者定義なら
					// prefix/suffix記述子(既定は".")を使う
					marker.text = (counterStyles.prefix(listStyleType) + str
							+ counterStyles.suffix(listStyleType) + ' ').toCharArray();
				}
			} else {
				marker = new Marker();
				ReplacedParams rparams = new ReplacedParams();
				this.mapper.setupParams(rparams, markerStyle);
				rparams.image = image;
				marker.imageBox = new InlineReplacedBox(rparams, pos);
			}
			if (marker != null) {
				switch (ListStylePosition.get(style)) {
				case ListStylePositionValue.INSIDE:
					// 内部マーカー
					marker.box = new net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox(params, pos);
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

		// コンテンツ生成(脚注のcall/markerはF5でfootnotePseudo側の
		// ラベルコンパイルへ移った——番号を文字として焼き込まないため)
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
							this.sink.characters(-1, ch, 0, ch.length, true);
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
								this.mapper.setupParams(rparams, style);
								rparams.image = image;
								AbstractReplacedBox replaced = new InlineReplacedBox(rparams,
										new InlinePos());
								this.checkMarker();
								this.sink.replaced(replaced);
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
									this.sink.characters(-1, ch, 0, ch.length, true);
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
									this.sink.characters(-1, ch, 0, ch.length, true);
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
										this.sink.characters(-1, ch, 0, ch.length, true);
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
								this.sink.characters(-1, ch, 0, ch.length, true);
							}
						}
					}
						break;
					case StringFunctionValue sf: {
						// string()(GCPM)
						String str = this.ua.getPassContext().getNamedStringState().get(sf.getName(), sf.getMode());
						if (str != null && str.length() > 0) {
							char[] ch = str.toCharArray();
							this.checkMarker();
							this.sink.characters(-1, ch, 0, ch.length, true);
						}
					}
						break;
					case TargetCounterValue pageRefFunc: {
						// ページ番号
						String ref = GeneratedContentResolver.targetRef(pageRefFunc.getType(), pageRefFunc.getRef(), style);
						if (ref != null) {
							this.pageRef(pageRefFunc, ref);
						}
					}
						break;
					case TargetTextValue targetText: {
						// ターゲットのテキスト
						String ref = GeneratedContentResolver.targetRef(targetText.getType(), targetText.getRef(), style);
						if (ref != null) {
							this.targetText(targetText, ref);
						}
					}
						break;
					case net.zamasoft.foliojet.css.value.LeaderValue leader: {
						// leader() L1: 正規化済みパターンをそのまま搬送する
						// (shape・幅の割り付けはレイアウト側)
						this.checkMarker();
						this.sink.leader(leader.getPattern());
					}
						break;
					default:
						throw new IllegalStateException(String.valueOf(v));
					}
				}
			}
		}

		// 脚注F1: 本文先頭へ::footnote-marker(番号)を合成する。
		// リストマーカー→footnote-marker→::beforeの順で本文頭に並ぶ
		if (footnote) {
			this.footnotePseudo(style, CSSElement.FOOTNOTE_MARKER);
		}

		// before(合成擬似要素自身には::before/::afterを作らない)
		if (!ce.isPseudoElement()
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

	/**
	 * {@code string-set}の値リストの1要素(build時に確定できるもの、
	 * {@link ContentFunctionValue}は呼び出し側で個別に扱う)を文字列へ
	 * 解決する。画像ベースの{@code list-style-type}は文字列として意味を
	 * 持たないため空文字列として扱う。
	 */

	private void counter(int number, short counterStyle, CSSStyle style) {
		final String str = CounterStyles.of(this.ua).format(number, counterStyle);
		if (str != null) {
			char[] ch = str.toCharArray();
			this.checkMarker();
			// カウンタ
			this.sink.characters(-1, ch, 0, ch.length, true);
		} else {
			final ReplacedParams rparams = new ReplacedParams();
			this.mapper.setupParams(rparams, style);
			rparams.image = GeneratedValueUtils.format(counterStyle, CSSColor.get(style), style.getFontStyle());
			if (rparams.image != null) {
				final AbstractReplacedBox replaced = new InlineReplacedBox(rparams, new InlinePos());
				this.checkMarker();
				this.sink.replaced(replaced);
			}
		}
	}

	/**
	 * {@code counter-reset}/{@code counter-increment}で予約カウンタ名
	 * ({@code pages})が指定された場合の警告(1文書につき1回のみ)。
	 * css-page-3 §6.1の{@code pages}はUA予約であり、著者が明示しても
	 * 無視して継続する(警告+縮退、例外にはしない方針)。
	 */
	void warnReservedCounter(String name) {
		if (!this.warnedReservedCounter) {
			this.warnedReservedCounter = true;
			LOG.warning("counter '" + name + "' is reserved by the UA (total page count) and cannot be "
					+ "reset/incremented by author style; ignoring.");
		}
	}

	/**
	 * {@code target-counter()}系/{@code target-text()}のtarget参照
	 * (ATTR/REF)を、実際に{@code PageRef}へ問い合わせるための
	 * {@code "#id"}文字列(またはhref)へ解決する。属性値が無い場合は
	 * {@code null}。
	 */

	
	private void targetText(TargetTextValue targetText, String ref) {
		PageRef pageRef = this.ua.getUAContext().getPageRef();
		if (pageRef == null) {
			return;
		}
		try {
			URI uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(),
					this.ua.getDocumentContext().getBaseURI(), ref);
			Fragment frag = pageRef.getFragment(uri);
			if (frag == null) {
				return;
			}
			this.generated.checkConverged(pageRef, frag);
			if (frag.text == null || frag.text.length() == 0) {
				return;
			}
			char[] ch = frag.text.toCharArray();
			this.checkMarker();
			// ターゲットテキスト
			this.sink.characters(-1, ch, 0, ch.length, true);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
		}
	}

	private void pageRef(TargetCounterValue pageRefFunc, String ref) {
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
				this.generated.checkConverged(pageRef, frag);
				int count = frag.getCounterValue(counter);
				String str = CounterStyles.of(this.ua).format(count, pageRefFunc.getNumberStyleType());
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
					this.generated.checkConverged(pageRef, fragment);
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
					String str = CounterStyles.of(this.ua).format(counts.get(j), pageRefFunc.getNumberStyleType());
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
			this.sink.characters(-1, ch, 0, ch.length, true);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_BAD_LINK_URI, e.getMessage());
		}
	}



	void characters(int charOffset, char[] ch, int off, int len) {
		assert len > 0;
		if (this.context.getHtmlRootBlock() == null && this.context.getCurrentStyle() != null) {
			// 本文の中
			this.segment.characters(charOffset, ch, off, len); // 本流のセグメント記録(M6a)
			if (!this.context.isInTextBlock()) {
				// ブロック補完のためにテキストブロックの開始をチェック
				// net.zamasoft.foliojet.layoutパッケージを直接利用する場合のために、
				// StyledTextUnitizerでも同じ処理をしています。
				final CSSStyle style = this.context.getCurrentStyle();
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
				this.context.setInTextBlock(true);
			}

			if (this.firstLetter) {
				this.firstLetter = false;

				// :first-letter
				this.styleContext.startElement(CSSElement.FIRST_LETTER);
				final Declaration declaration = this.styleContext.merge(null);
				this.styleContext.endElement();
				if (declaration != null) {
					final CSSStyle firstLetterStyle = CSSStyle.getCSSStyle(this.ua, this.context.getCurrentStyle(),
							CSSElement.FIRST_LETTER);
					declaration.applyProperties(firstLetterStyle);
					// initial-letter(css-inline-3)はここでfloat+文字寸法へ
					// 脱糖して既存機構に載せる(2026-08-20)
					net.zamasoft.foliojet.css.impl.property.text.InitialLetter.desugar(firstLetterStyle,
							this.context.getCurrentStyle());
					if (Display.get(firstLetterStyle) != DisplayValue.NONE) {
						this.startStyle(firstLetterStyle);
						final LanguageProfile lang = LanguageProfileBundle
								.getLanguageProfile(this.context.getCurrentStyle().getCSSElement().lang);
						int first = lang.countFirstLetter(ch, off, len);
						this.checkMarker();
						this.sink.characters(charOffset, ch, off, first, false);
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

			if (this.context.getCurrentStyle() != null) {
				WHILE: while (this.context.getCurrentStyle().isAnonStyle()) {
					// 匿名スタイルの終了
					final short anonDisplay = Display.get(this.context.getCurrentStyle());
					switch (anonDisplay) {
					case DisplayValue.TABLE_ROW:
						CSSStyle parent = this.context.getCurrentStyle().getParentStyle();
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
					this.emitter._endStyle();
				}
			}

			// display:contentsの直下のテキストは、contents要素のスタイルを
			// 継承する匿名インラインで包む(2026-08-07)。contentsは箱を
			// 作らないため、素通しにするとテキストが外側の箱のパラメータ
			// (=contentsより上の祖先のスタイル)で組まれ、contents要素に
			// 書かれたcolor/font等の継承が失われる
			final boolean inContents = Display.get(this.context.getCurrentStyle()) == DisplayValue.CONTENTS;
			if (inContents) {
				final CSSStyle contentsInline = this.context.getCurrentStyle().inheritAnonStyle(CSSElement.ANON);
				contentsInline.set(Display.INFO, DisplayValue.INLINE_VALUE);
				this.emitter._startStyle(contentsInline);
			}
			String em = TextEmphasisStyle.get(this.context.getCurrentStyle());
			if (em == null || em.length() == 0) {
				this.sink.characters(charOffset, ch, off, len, false);
			} else {
				// 圏点
				final char[] emc = em.toCharArray();
				final boolean vert = BlockFlow.get(this.context.getCurrentStyle()).isVertical();
				final var emPosition = TextEmphasisPosition.get(this.context.getCurrentStyle());
				Value color = this.context.getCurrentStyle().get(TextEmphasisColor.INFO);
				if (color == KeywordValue.DEFAULT) {
					color = this.context.getCurrentStyle().get(CSSColor.INFO);
				}
				for (int i = 0; i < len; ++i) {
					final CSSStyle eb = this.context.getCurrentStyle().inheritAnonStyle(CSSElement.ANON);
					eb.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
					eb.set(CSSPosition.INFO, PositionValue.RELATIVE_VALUE);
					eb.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
					if (vert) {
						eb.set(LineHeight.INFO, EM_1_618);
					} else {
						eb.set(LineHeight.INFO, EM_1_414);
					}
					this.emitter._startStyle(eb);
					final CSSStyle et = eb.inheritAnonStyle(CSSElement.ANON);
					et.set(Display.INFO, DisplayValue.INLINE_BLOCK_VALUE);
					et.set(CSSPosition.INFO, PositionValue.ABSOLUTE_VALUE);
					et.set(TextIndent.INFO, AbsoluteLengthValue.ZERO);
					et.set(CSSColor.INFO, color);
					et.set(FontSize.INFO, PercentageValue.HALF);
					if (vert) {
						et.set(Height.INFO, PercentageValue.FULL);
						et.set(emPosition.isLeft() ? Inset.RIGHT : Inset.LEFT, EM_1_4);
					} else {
						et.set(Width.INFO, PercentageValue.FULL);
						et.set(emPosition.isUnder() ? Inset.TOP : Inset.BOTTOM, EM_1_4);
					}
					et.set(TextAlign.INFO, TextAlignValue.CENTER_VALUE);
					this.emitter._startStyle(et);
					this.sink.characters(-1, emc, 0, 1, false);
					this.emitter._endStyle();
					this.sink.characters(charOffset, ch, i + off, 1, false);
					this.emitter._endStyle();
				}
			}
			if (inContents) {
				// contents直下テキストの匿名インラインを閉じる
				this.emitter._endStyle();
			}
		}
	}


	void checkMarker() {
		if (this.marker == null) {
			return;
		}
		// 外部マーカー
		Marker marker = this.marker;
		this.marker = null;
		this.marker(marker);
	}

	private void marker(Marker marker) {
		this.sink.start(marker.box);
		if (marker.text != null) {
			// マーカーのテキスト
			this.sink.characters(-1, marker.text, 0, marker.text.length, false);
		} else if (marker.imageBox != null) {
			this.sink.replaced(marker.imageBox);
		}
		this.sink.end();
	}

	/**
	 * {@code ::footnote-call}/{@code ::footnote-marker}を合成します(脚注F1、
	 * 2026-07-31——consult-codex-2026-07-31-footnote.txt §3)。利用者の同名
	 * 擬似要素規則をカスケードし、{@code content}指定があればそれを
	 * ({@link #startStyle}の生成機構で)、無ければUA既定=脚注番号
	 * (globalスコープの"footnote"カウンタ、markerは区切り付き)を発行する。
	 * callのUA既定は上付きの小さな番号(利用者規則が後から上書きする)。
	 */
	/** 脚注の論理ID採番(F4。表示番号のcounter "footnote"とは独立)。 */
	private long nextFootnoteId = 0;

	/**
	 * インラインを直接置けない表の構造(表・行・行グループ・列)の中か。
	 * 間に{@code display:inline}が挟まることがあるので、<b>インラインを
	 * 置ける祖先</b>(ブロック・セル・flex等)に当たるまで遡る。
	 */
	private static boolean inTableStructure(final CSSStyle style) {
		for (CSSStyle parent = style.getParentStyle(); parent != null; parent = parent.getParentStyle()) {
			switch (Display.get(parent)) {
			case DisplayValue.INLINE:
			case DisplayValue.INLINE_BLOCK:
			case DisplayValue.CONTENTS:
				continue;
			default:
				return isTableStructure(Display.get(parent));
			}
		}
		return false;
	}

	/** インラインを直接置けない表の構造か。 */
	private static boolean isTableStructure(final byte display) {
		switch (display) {
		case DisplayValue.TABLE:
		case DisplayValue.INLINE_TABLE:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_COLUMN_GROUP:
			return true;
		default:
			return false;
		}
	}

	/** 表構造内の脚注の警告は1文書に1回。 */
	private boolean warnedFootnoteInTableStructure = false;

	private void footnotePseudo(final CSSStyle style, final CSSElement pseudoCe) {
		this.styleContext.startElement(pseudoCe);
		final Declaration declaration = this.styleContext.merge(null);
		final CSSStyle pseudoStyle = CSSStyle.getCSSStyle(this.ua, style, pseudoCe);
		pseudoStyle.footnoteId = style.footnoteId;
		if (pseudoCe == CSSElement.FOOTNOTE_CALL) {
			pseudoStyle.set(VerticalAlign.INFO, VerticalAlignValue.SUPER_VALUE);
			pseudoStyle.set(FontSize.INFO, PercentageValue.create(83));
		}
		if (declaration != null) {
			declaration.applyProperties(pseudoStyle);
		}
		if (pseudoCe == CSSElement.FOOTNOTE_CALL) {
			// F4: ::footnote-callは**常にinline**へ強制する(意図的仕様逸脱)。
			// callのインラインボックスはページ確定時の所属判定の唯一の事実で、
			// 消すと脚注の配置先が決められない
			// (consult-codex-2026-07-31-footnote-f4.txt)。
			// **2026-08-02に「display:noneのときだけ」から拡張した**——
			// displayの計算値は親に依存し(表の匿名整形)、
			// `display:table`の要素にfloat:footnoteを付けると、この擬似要素が
			// table-cellへ計算されてTableBuilderを要求し、変換が失敗していた。
			// callは親のフローに置かれるインライン原子なので、脚注要素の
			// 表整形を継がせてはならない
			pseudoStyle.set(Display.INFO, DisplayValue.INLINE_VALUE, CSSStyle.MODE_IMPORTANT);
		} else if (Display.get(pseudoStyle) == DisplayValue.NONE) {
			// markerは消してよい
			this.styleContext.endElement();
			return;
		}
		this.startStyle(pseudoStyle);
		// F5(2026-07-31、consult-codex-2026-07-31-footnote-f5.txt): 番号を
		// 文字として焼き込まず、footnoteId付きの未解決ラベル原子
		// (FootnoteLabelImageを持つInlineReplacedBox)として発行する。
		// ページ確定時にRootBuilderが「callが残ったページ」ごとに1から
		// 採番して解決する。欄幅は桁数に依存しない固定欄(意図的仕様逸脱)
		final boolean isMarker = pseudoCe == CSSElement.FOOTNOTE_MARKER;
		String prefix = "";
		String suffix = isMarker ? ". " : "";
		final Value[] labelContents = Content.get(pseudoStyle);
		if (labelContents != null) {
			// 受け付けるのは literal* counter(footnote,decimal) literal* のみ。
			// それ以外は型付きunsupported——黙って文書通番を焼き込まない
			final StringBuilder pre = new StringBuilder();
			final StringBuilder post = new StringBuilder();
			boolean seenCounter = false;
			for (final Value v : labelContents) {
				if (v instanceof StringValue sv) {
					(seenCounter ? post : pre).append(sv.getString());
				} else if (v instanceof CounterValue cv && !seenCounter && cv.getName().equals("footnote")
						&& cv.getStyle() == net.zamasoft.foliojet.css.value.ListStyleTypeValue.DECIMAL) {
					seenCounter = true;
				} else {
					throw new net.zamasoft.foliojet.layout.builder.impl.FootnoteOverflowException(
							"unsupported footnote label content (only literals and counter(footnote) are supported): "
									+ v);
				}
			}
			if (!seenCounter) {
				// 番号を含まないliteralのみのラベルは通常の生成内容として発行
				// (ページ採番の対象にしない——記号脚注等)
				final String text = pre.toString();
				if (!text.isEmpty()) {
					final char[] chars = text.toCharArray();
					this.checkMarker();
					this.sink.characters(-1, chars, 0, chars.length, true);
				}
				this.endStyle();
				this.styleContext.endElement();
				return;
			}
			prefix = pre.toString();
			suffix = post.toString();
		}
		final ReplacedParams rparams = new ReplacedParams();
		this.mapper.setupParams(rparams, pseudoStyle);
		rparams.image = new net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage(pseudoStyle.footnoteId,
				isMarker, prefix, suffix, pseudoStyle.getFontStyle(), this.ua.getFontManager());
		final InlinePos labelPos = new InlinePos();
		this.mapper.setupInlinePos(labelPos, pseudoStyle);
		final AbstractReplacedBox labelBox = new InlineReplacedBox(rparams, labelPos);
		this.checkMarker();
		this.sink.replaced(labelBox);
		this.endStyle();
		this.styleContext.endElement();
	}


	void endStyle() {
		CSSStyle style = this.context.getCurrentStyle();
		if (DEBUG) {
			System.err.println("/" + style.path());
		}

		final CSSElement ce = style.getCSSElement();
		if (!ce.isPseudoElement()
				&& CSSJInternalImage.getImage(style) == null) {
			// :after(合成擬似要素自身には作らない——脚注F1でce判定を
			// AFTER/BEFORE個別からisPseudoElementへ一般化)
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
					PageBreakMode pageBreakBefore = this.mapper.toPageBreak(PageBreakBefore.get(afterStyle), this.context.isRightSide());
					PageBreakMode pageBreakAfter = this.mapper.toPageBreak(PageBreakAfter.get(afterStyle), this.context.isRightSide());
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

		// 匿名スタイルを終了
		while (this.context.getCurrentStyle().isAnonStyle()) {
			this.emitter._endStyle();
		}

		// 明示されたスタイルを終了
		style = this.context.getCurrentStyle();
		if (!style.getCSSElement().isPseudoElement()) {
			// 本流のセグメント記録(M6a)
			this.segment.endStyle(style);
		}
		this.emitter._endStyle();
		if (this.context.getCurrentStyle() != null) {
			short explDisplay = Display.get(style);
			WHILE: while (this.context.getCurrentStyle().isInsertedAnonStyle()) {
				// 匿名スタイルの終了
				final short anonDisplay = Display.get(this.context.getCurrentStyle());
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
					break;
				}
				if (style.getParentStyle() == this.context.getCurrentStyle()) {
					style.removeAnonStyle();
				}
				this.emitter._endStyle();
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

}
