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
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
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
 * display値によるボックスのdispatchと匿名表補完です(StyleBuilder解体・
 * 増分4a、2026-07-30。各メソッドの本体はStyleBuilderから逐語移動——
 * 挙動不変。状態は{@link StyleBuildContext}経由)。
 *
 * <p>
 * 匿名表補完は増分4b(2026-07-30)で明示スタック({@code OpenStep}の
 * chain)へ反復化済み——補正・挿入は内→外、送出は外→内という旧再帰と
 * 同一の順序を保存している({@link #_startStyle}のコメント参照)。
 * </p>
 */
final class StyleBoxEmitter {
	private final StyleBuildContext context;
	private final RecordingLayoutSink sink;
	private final BoxStyleMapper mapper;
	private final PageSequence pageSequence;
	private final UserAgent ua;
	private final Imposition imposition;

	StyleBoxEmitter(final StyleBuildContext context, final RecordingLayoutSink sink, final BoxStyleMapper mapper,
			final PageSequence pageSequence, final UserAgent ua, final Imposition imposition) {
		this.context = context;
		this.sink = sink;
		this.mapper = mapper;
		this.pageSequence = pageSequence;
		this.ua = ua;
		this.imposition = imposition;
	}

	void requireRoot(byte direction, WritingMode progression) {
		// 保留されたHTMLのルートを出力する
		if (!this.context.isInBody()) {
			this.context.setInBody(true);
			if (this.context.getHtmlRootBlock() != null) {
				// ページの描画方法
				final BlockParams params = this.context.getHtmlRootBlock().getBlockParams();
				params.direction = direction;
				params.flow = progression;
			}
			this.pageSequence.setProgression(progression);
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
				this.context.setRightSide(true);
			}
			if (this.context.getHtmlRootBlock() != null) {
				this.sink.start(this.context.getHtmlRootBlock());
				this.context.setHtmlRootBlock(null);
			}
		}
	}

	AbstractBlockBox createBlockBox(CSSStyle style, BlockParams params, byte position, byte display,
			byte floating) {
		final AbstractBlockBox blockBox;
		if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
			final AbsolutePos pos = new AbsolutePos();
			this.mapper.setupAbsolutePos(pos, style);
			blockBox = new AbsoluteBlockBox(params, pos);
		} else if (display == DisplayValue.INLINE_BLOCK || display == DisplayValue.INLINE_TABLE) {
			final InlinePos pos = new InlinePos();
			this.mapper.setupInlinePos(pos, style);
			blockBox = new InlineBlockBox(params, pos);
		} else if (floating == CSSFloatValue.FOOTNOTE && !params.flow.isVertical()) {
			// 脚注F2(2026-07-31): FootnotePos(PosType=FLOAT)で分離builderの
			// ライフサイクルへ流す。左右floatと違いFloatSide/clear等は使わず、
			// 終了時に親へaddBoundされずページ脚注台帳へ渡る。縦書きは
			// 初期サブセット外(下のelseで通常フロー=F1挙動へ)
			final FootnotePos pos = new FootnotePos();
			this.mapper.setupStaticPos(pos, style);
			blockBox = new FloatBlockBox(params, pos);
		} else if (floating != CSSFloatValue.NONE && floating != CSSFloatValue.FOOTNOTE) {
			final FloatPos pos = new FloatPos();
			this.mapper.setupFloatPos(pos, style, this.context.isRightSide());
			blockBox = new FloatBlockBox(params, pos);
		} else {
			final FlowPos pos = new FlowPos();
			this.mapper.setupFlowPos(pos, style, this.context.isRightSide());
			final CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null) {
				pos.align = CSSJHtmlAlign.get(parentStyle);
			}
			blockBox = new FlowBlockBox(params, pos);
		}
		return blockBox;
	}

	CSSStyle startColumns(CSSStyle style, AbstractContainerBox box) {
		int c = LayoutUtils.getColumnCount(box);
		if (c > 1) {
			final BlockParams params = box.getBlockParams();
			final BlockParams mcParams = new BlockParams();
			final FlowPos mcPos = new FlowPos();
			final CSSStyle mc = style.inheritAnonStyle(CSSElement.ANON);
			this.mapper.setupBlockParams(mcParams, mc, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			this.mapper.setupFlowPos(mcPos, mc, this.context.isRightSide());
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
			this.sink.start(mcBox);
			style = mc;
		}
		return style;
	}

	void _startStyle(final CSSStyle startStyle) {
		// 匿名表補完の自己再帰を明示スタックへ(増分4b、2026-07-30)。
		// 不変: 頭の補正と匿名親の挿入は内→外(発見順)、ボックスの送出は
		// 外→内——旧再帰(fix(style)→fix(anon)→…→emit(anonN)→…→
		// emit(style))と同一の順序。display/position/htmlRootは補正時点の
		// 値を捕捉する(例: キャプションのBLOCK変換後も旧ローカル同様に
		// TABLE_CAPTIONとしてdispatchされる)
		final java.util.ArrayDeque<OpenStep> chain = new java.util.ArrayDeque<>();
		CSSStyle style = startStyle;
		while (true) {
			CSSStyle inserted = null;

			// System.err.println("_"+style.path());
			if (CSSJRuby.get(style) != CSSJRubyValue.NONE) {
				// ルビ関連要素(ruby/rb/rt)は箱を作らず通常のINLINEとして流す
				// (注釈付きテキスト方式、2026-07-25仕様裁定)。単位の組み立ては
				// 文字処理層(StyledTextUnitizer)がrubyRoleマーカーを手掛かりに
				// 行う。
				style.set(Display.INFO, DisplayValue.INLINE_VALUE, CSSStyle.MODE_IMPORTANT);
			}
			// ルートのHTMLタグはblockに固定する
			boolean htmlRoot = false;
			if (!this.context.isInBody() && this.context.getHtmlRootBlock() == null) {
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
					final short parentDisplay = Display.get(parentStyle);
					switch (display) {
					case DisplayValue.TABLE_CELL: {
						// CSS 2.1 17.2.1 #1
						// テーブルセルの上にテーブル行を挿入
						if (parentDisplay != DisplayValue.TABLE_ROW) {
							final CSSStyle row = style.insertAnonStyle(CSSElement.ANON_TR);
							row.set(Display.INFO, DisplayValue.TABLE_ROW_VALUE);
							inserted = row;
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
							inserted = rowGroup;
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
							inserted = table;
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
							inserted = table;
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
							inserted = anon;
						}
						break;

					default:
						throw new IllegalStateException();
					}
				}
			}


			chain.push(new OpenStep(style, htmlRoot, display, position));
			if (inserted == null) {
				break;
			}
			style = inserted;
		}
		while (!chain.isEmpty()) {
			this.openBox(chain.pop());
		}
	}

	/** 開く1レベル分の捕捉(補正時点のdisplay/position/htmlRoot)。 */
	private record OpenStep(CSSStyle style, boolean htmlRoot, byte display, byte position) {
	}

	/** 1レベル分のボックス送出(旧_startStyleのdispatch部を逐語移動)。 */
	private void openBox(final OpenStep step) {
		// startColumns(段組ラッパー)がstyleを差し替えるため非final(旧コードと同じ)
		CSSStyle style = step.style();
		final boolean htmlRoot = step.htmlRoot();
		final byte display = step.display();
		final byte position = step.position();
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
				if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
					final AbsolutePos pos = new AbsolutePos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupAbsolutePos(pos, style);
					replacedBox = new AbsoluteReplacedBox(params, pos);
				} else if (display == DisplayValue.INLINE_BLOCK) {
					final InlinePos pos = new InlinePos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupInlinePos(pos, style);
					inline = true;
					replacedBox = new InlineReplacedBox(params, pos);
				} else if (floating != CSSFloatValue.NONE && floating != CSSFloatValue.FOOTNOTE) {
					// FOOTNOTEはcreateBlockBoxと同じ理由で通常フローへ(F3まで)
					final FloatPos pos = new FloatPos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupFloatPos(pos, style, this.context.isRightSide());
					replacedBox = new FloatReplacedBox(params, pos);
				} else {
					final FlowPos pos = new FlowPos();
					params = new ReplacedParams();
					this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
					this.mapper.setupFlowPos(pos, style, this.context.isRightSide());
					final CSSStyle parentStyle = style.getParentStyle();
					if (parentStyle != null) {
						pos.align = CSSJHtmlAlign.get(parentStyle);
					}
					replacedBox = new FlowReplacedBox(params, pos);
				}
				this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB);
				if (inline) {
					this.context.checkMarker();
				}
				this.sink.replaced(replacedBox);
			} else {
				// ブロックボックス
				final BlockParams params = new BlockParams();
				this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
				final AbstractBlockBox blockBox = this.createBlockBox(style, params, position, display, floating);
				// HTMLのルートは出力を保留する
				if (blockBox.getPos().getType() == PosType.FLOW && htmlRoot) {
					this.context.setHtmlRootBlock((FlowBlockBox) blockBox);
					break;
				}
				this.requireRoot(params.direction, params.flow);
				if (blockBox.getPos().getType() == PosType.INLINE) {
					this.context.checkMarker();
				}
				this.sink.start(blockBox);

				// 段組みの開始
				style = this.startColumns(style, blockBox);
			}
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.INLINE: {
			Image image = CSSJInternalImage.getImage(style);
			InlinePos pos = new InlinePos();
			if (image != null) {
				// インラインの画像
				ReplacedParams params = new ReplacedParams();
				this.mapper.setupReplacedParams(image, params, style, this.context.isInBody(), this.pageSequence);
				this.mapper.setupInlinePos(pos, style);
				AbstractReplacedBox replaced = new InlineReplacedBox(params, pos);
				this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB);
				this.context.checkMarker();
				this.sink.replaced(replaced);
			} else {
				// インラインボックス
				InlineParams params = new InlineParams();
				this.mapper.setupInlineParams(params, style, this.context.isInBody(), this.pageSequence);
				this.mapper.setupInlinePos(pos, style);
				InlineBox inline = new InlineBox(params, pos);
				this.requireRoot(params.direction, params.flow);
				this.sink.start(inline);
			}
		}
			break;
		case DisplayValue.LIST_ITEM: {
			// リストアイテム
			final BlockParams params = new BlockParams();
			this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			final AbstractBlockBox listItem = this.createBlockBox(style, params, position, display, floating);
			this.requireRoot(params.direction, params.flow);
			this.sink.start(listItem);
		}
			break;

		case DisplayValue.TABLE:
		case DisplayValue.INLINE_TABLE: {
			// テーブル
			final TableParams params = new TableParams();
			this.mapper.setupTableParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			final AbstractBlockBox blockBox = this.createBlockBox(style, params, position, display, floating);
			if (blockBox.getPos().getType() == PosType.FLOW) {
				if (CSSJHtmlAlign.get(style) == Align.CENTER) {
					((FlowPos) blockBox.getPos()).align = Align.CENTER;
				}
			}
			TableBox table = new TableBox(params, blockBox);
			this.requireRoot(AbstractTextParams.DIRECTION_LTR, WritingMode.TB);
			this.sink.start(table);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_CAPTION: {
			// テーブルキャプション
			final TableCaptionPos pos = new TableCaptionPos();
			final BlockParams params = new BlockParams();
			this.mapper.setupTableCaptionPos(pos, style, this.context.isRightSide());
			this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
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
			this.sink.start(caption);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_COLUMN_GROUP: {
			// テーブル列グループ
			final TableColumnPos pos = new TableColumnPos();
			final InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableColumn(params, pos, style);
			final TableColumnGroupBox columnGroup = new TableColumnGroupBox(params, pos);
			this.sink.start(columnGroup);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_COLUMN: {
			// テーブル列
			TableColumnPos pos = new TableColumnPos();
			InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableColumn(params, pos, style);
			TableColumnBox column = new TableColumnBox(params, pos);
			this.sink.start(column);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_HEADER_GROUP: {
			// テーブルヘッダグループ
			final TableRowGroupPos pos = new TableRowGroupPos();
			final InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRowGroup(params, pos, style, RowGroupType.HEADER, this.context.isRightSide());
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.sink.start(rowGroup);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_ROW_GROUP: {
			// テーブル行グループ
			final TableRowGroupPos pos = new TableRowGroupPos();
			final InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRowGroup(params, pos, style, RowGroupType.BODY, this.context.isRightSide());
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.sink.start(rowGroup);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_FOOTER_GROUP: {
			// テーブルフッタグループ
			TableRowGroupPos pos = new TableRowGroupPos();
			InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRowGroup(params, pos, style, RowGroupType.FOOTER, this.context.isRightSide());
			TableRowGroupBox rowGroup = new TableRowGroupBox(params, pos);
			this.sink.start(rowGroup);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_ROW: {
			// テーブル行
			TableRowPos pos = new TableRowPos();
			InnerTableParams params = new InnerTableParams();
			this.mapper.setupTableRow(params, pos, style, this.context.isRightSide());
			TableRowBox row = new TableRowBox(params, pos);
			this.sink.start(row);
			this.context.setInTextBlock(false);
		}
			break;

		case DisplayValue.TABLE_CELL: {
			// テーブルセル
			final TableCellPos pos = new TableCellPos();
			final BlockParams params = new BlockParams();
			this.mapper.setupTableCellPos(pos, style, this.context.isRightSide());
			this.mapper.setupBlockParams(params, style, this.context.getCurrentStyle(), this.context.isInBody(), this.pageSequence);
			final TableCellBox cell = new TableCellBox(params, pos, new FlowContainer());
			this.sink.start(cell);
			this.context.setInTextBlock(false);

			// 段組みの開始
			style = this.startColumns(style, cell);
		}
			break;

		default:
			throw new IllegalStateException();
		}

		this.context.setCurrentStyle(style);
	}


	void _endStyle() {
		final CSSStyle style = this.context.getCurrentStyle();
		// System.out.println("/" + style.path());
		if (!this.context.isInBody()) {
			this.context.setInBody(true);
			this._startStyle(style);
		}
		if (CSSJInternalImage.getImage(style) == null) {
			this.sink.end();
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
			this.context.setInTextBlock(false);
			break;

		case DisplayValue.INLINE_BLOCK:
			this.context.setInTextBlock(true);
			break;

		case DisplayValue.INLINE:
			break;

		default:
			throw new IllegalStateException();
		}

		this.context.setCurrentStyle(style.getParentStyle());
	}

}
