package net.zamasoft.foliojet.css.style;

import java.awt.geom.AffineTransform;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.MarginBoxName;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.StringFunctionValue;
import net.zamasoft.foliojet.css.value.ElementFunctionValue;
import net.zamasoft.foliojet.ua.PageAssignmentState;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.box.VerticalAlign;
import net.zamasoft.foliojet.css.impl.property.content.Content;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.LetterSpacing;
import net.zamasoft.foliojet.css.impl.property.font.LineHeight;
import net.zamasoft.foliojet.css.impl.property.text.TextAlign;
import net.zamasoft.foliojet.css.impl.property.text.TextAlignLast;
import net.zamasoft.foliojet.css.impl.property.text.TextFillColor;
import net.zamasoft.foliojet.css.impl.property.text.UnicodeBidi;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.impl.property.text.WordSpacing;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.CellAlign;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.sizing.MeasuredIntrinsics;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.css.style.running.RunningRenderer;

/**
 * ページマージンボックス(css-page-3 §7)の組版と描画です。
 *
 * <p>
 * ページ確定(drawPage)時に呼ばれます。ライブの StyleBuilder /
 * DocumentBuilder / LayoutSource には一切触れず、ボックスごとに新品の
 * DocumentBuilder による隔離ミニレイアウトで組みます(M6b v3 の
 * SourceReplayer と同じ隔離原則)。旧 -cssj-page-content(drawPage 時に
 * ライブ StyleBuilder へ再入)の後継です。
 * </p>
 *
 * <p>
 * 現段階の対応: content の文字列・counter()/counters()(ページレベルの
 * カウンタ=page/pages と @page の counter-* によるもの)・
 * string()(GCPM、PageAssignmentStateを読む)、フォント・色・text-align・
 * vertical-align(top/middle/bottom)、margin/border/padding/background。
 * 縦書き(writing-mode)は 2026-09-06 から: vertical-align は天地、行の束は帯の中央、
 * 背景は領域全体(Vivliostyle 実測に一致)。未対応(FINE ログ): url() 画像・引用符・
 * attr()・page-ref・width/height。幅配分は css-page-3 §7.3 の基本形(センター優先、なければ
 * max-content 比例)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
final class MarginBoxes {
	private static final Logger LOG = Logger.getLogger(MarginBoxes.class.getName());

	/** max-content 測定に使う「十分に広い」寸法です。 */
	private static final double INFINITE = 1e6;

	private MarginBoxes() {
		// pure functions
	}

	/**
	 * ページのマージンボックスを組んで描画します。
	 *
	 * @param ua           ユーザーエージェント
	 * @param styleContext スタイル文脈(マージンボックス宣言の取得)
	 * @param pageElement  現在のページ擬似要素(:left/:right/:first)
	 * @param pageBox      確定したページ
	 * @param drawer       描画先(座標系はページ内容領域原点)
	 * @param visitor      ビジタ
	 */
	static void draw(final UserAgent ua, final StyleContext styleContext, final CSSElement pageElement,
			final String pageName, final PageBox pageBox, final Drawer drawer, final Visitor visitor,
			final RunningRenderer running) {
		final Map<MarginBoxName, Declaration> declarations = styleContext.pageMarginBoxes(pageElement, pageName);
		if (declarations.isEmpty()) {
			return;
		}
		final Map<MarginBoxName, Box> boxes = new EnumMap<MarginBoxName, Box>(MarginBoxName.class);
		for (final Map.Entry<MarginBoxName, Declaration> e : declarations.entrySet()) {
			final Box box = Box.create(ua, e.getKey(), e.getValue(), running);
			if (box != null) {
				boxes.put(e.getKey(), box);
			}
		}
		if (boxes.isEmpty()) {
			return;
		}

		// drawer 座標系はページ内容領域原点(用紙原点は (-margin.left, -margin.top))。
		// PageBox の width/height は用紙全体なのでマージンを控除する
		final AbsoluteInsets margin = pageBox.getFrame().margin;
		final double contentW = pageBox.getWidth() - margin.left - margin.right;
		final double contentH = pageBox.getHeight() - margin.top - margin.bottom;

		// 上下バンド(コーナー間の内容領域幅)
		band(ua, boxes, MarginBoxName.TOP_LEFT, MarginBoxName.TOP_CENTER, MarginBoxName.TOP_RIGHT, 0, -margin.top,
				contentW, margin.top, drawer, visitor);
		band(ua, boxes, MarginBoxName.BOTTOM_LEFT, MarginBoxName.BOTTOM_CENTER, MarginBoxName.BOTTOM_RIGHT, 0,
				contentH, contentW, margin.bottom, drawer, visitor);

		// コーナー
		place(ua, boxes.get(MarginBoxName.TOP_LEFT_CORNER), -margin.left, -margin.top, margin.left, margin.top,
				drawer, visitor);
		place(ua, boxes.get(MarginBoxName.TOP_RIGHT_CORNER), contentW, -margin.top, margin.right, margin.top, drawer,
				visitor);
		place(ua, boxes.get(MarginBoxName.BOTTOM_LEFT_CORNER), -margin.left, contentH, margin.left, margin.bottom,
				drawer, visitor);
		place(ua, boxes.get(MarginBoxName.BOTTOM_RIGHT_CORNER), contentW, contentH, margin.right, margin.bottom,
				drawer, visitor);

		// 側面カラム(コーナー間の内容領域高さ)
		column(ua, boxes, MarginBoxName.LEFT_TOP, MarginBoxName.LEFT_MIDDLE, MarginBoxName.LEFT_BOTTOM, -margin.left,
				0, margin.left, contentH, drawer, visitor);
		column(ua, boxes, MarginBoxName.RIGHT_TOP, MarginBoxName.RIGHT_MIDDLE, MarginBoxName.RIGHT_BOTTOM, contentW,
				0, margin.right, contentH, drawer, visitor);
	}

	/**
	 * 上下バンドの3ボックスの幅を配分して置きます(css-page-3 §7.3 の基本形:
	 * センターがあれば中央固定で両側均等、なければ max-content 比例)。
	 */
	private static void band(final UserAgent ua, final Map<MarginBoxName, Box> boxes, final MarginBoxName leftName,
			final MarginBoxName centerName, final MarginBoxName rightName, final double x, final double y,
			final double w, final double h, final Drawer drawer, final Visitor visitor) {
		final Box left = boxes.get(leftName);
		final Box center = boxes.get(centerName);
		final Box right = boxes.get(rightName);
		if (left == null && center == null && right == null) {
			return;
		}
		if (center != null) {
			final double cw = Math.min(center.preferredWidth(ua), w);
			final double side = (w - cw) / 2;
			place(ua, left, x, y, side, h, drawer, visitor);
			place(ua, center, x + side, y, cw, h, drawer, visitor);
			place(ua, right, x + w - side, y, side, h, drawer, visitor);
		} else if (left != null && right != null) {
			final double pl = left.preferredWidth(ua);
			final double pr = right.preferredWidth(ua);
			final double lw = (pl + pr) <= 0 ? w / 2 : Math.min(w * pl / (pl + pr), w);
			place(ua, left, x, y, lw, h, drawer, visitor);
			place(ua, right, x + lw, y, w - lw, h, drawer, visitor);
		} else if (left != null) {
			place(ua, left, x, y, w, h, drawer, visitor);
		} else {
			place(ua, right, x, y, w, h, drawer, visitor);
		}
	}

	/**
	 * 側面カラムの3ボックスの高さを配分して置きます(バンドの縦版)。
	 */
	private static void column(final UserAgent ua, final Map<MarginBoxName, Box> boxes, final MarginBoxName topName,
			final MarginBoxName middleName, final MarginBoxName bottomName, final double x, final double y,
			final double w, final double h, final Drawer drawer, final Visitor visitor) {
		final Box top = boxes.get(topName);
		final Box middle = boxes.get(middleName);
		final Box bottom = boxes.get(bottomName);
		if (top == null && middle == null && bottom == null) {
			return;
		}
		if (middle != null) {
			final double ch = Math.min(middle.preferredHeight(ua, w, h), h);
			final double side = (h - ch) / 2;
			place(ua, top, x, y, w, side, drawer, visitor);
			place(ua, middle, x, y + side, w, ch, drawer, visitor);
			place(ua, bottom, x, y + h - side, w, side, drawer, visitor);
		} else if (top != null && bottom != null) {
			final double pt = top.preferredHeight(ua, w, h);
			final double pb = bottom.preferredHeight(ua, w, h);
			final double th = (pt + pb) <= 0 ? h / 2 : Math.min(h * pt / (pt + pb), h);
			place(ua, top, x, y, w, th, drawer, visitor);
			place(ua, bottom, x, y + th, w, h - th, drawer, visitor);
		} else if (top != null) {
			place(ua, top, x, y, w, h, drawer, visitor);
		} else {
			place(ua, bottom, x, y, w, h, drawer, visitor);
		}
	}

	/**
	 * ボックスを矩形に組んで描画します(縦位置は vertical-align)。
	 */
	private static void place(final UserAgent ua, final Box box, final double x, final double y, final double w,
			final double h, final Drawer drawer, final Visitor visitor) {
		if (box == null || w <= 0 || h <= 0) {
			return;
		}
		final boolean text = box.running == null;
		final PageBox mini = box.layout(ua, w, h, text);
		if (mini == null) {
			return;
		}
		final boolean vertical = box.params.flow.isVertical();
		// 寄せは物理軸で行う(2026-09-06、利用者申し送り §4、Vivliostyle 実測):
		// vertical-align は横書き・縦書きとも天地(y)の寄せ。縦書きでは行が x に
		// 積まれるので、行の束は帯の中央に置く(text-align は行方向の寄せとして
		// 縦書きでは使わない。Box.create で start に固定してある)。padding・margin は
		// 左右方向の padding・margin は内容箱を狭めるだけで折り返しには影響しない
		// (天地方向のものは行長を削る)。
		// 縦書きのミニページは行長=領域高で 1 回だけ組む(百分率 padding の基準が
		// 配置と同じになる)。枠(背景・罫線)は領域全体に置いたまま、内容だけを
		// dy でずらす(Vivliostyle と同じ: マージンボックスの背景は領域いっぱい)。
		// 行頭が物理下端(sideways-lr、縦書き rtl)なら内容は既に地側にあるので
		// dy は余りの分だけ天側へ。running テンプレートは自身の text-align で
		// 行方向に寄るので dy は付けない(内部の寄せと二重にならないように)
		// 文字列ボックスは箱を領域に固定してあるので、寄せは padding の内側(内容箱)で
		// 内側の container の実寸から決める。running テンプレートは従来どおり
		// (箱は内容の大きさ、ミニページの外寸で寄せる)
		final RectFrame frame = box.params.frame;
		final Container inner = text ? innerContainer(mini) : null;
		final double innerW = text ? Math.max(0, w - frame.margin.getLeft() - frame.margin.getRight()
				- frame.border.getFrameWidth() - frame.padding.getLeft() - frame.padding.getRight()) : w;
		final double innerH = text ? Math.max(0, h - frame.margin.getTop() - frame.margin.getBottom()
				- frame.border.getFrameHeight() - frame.padding.getTop() - frame.padding.getBottom()) : h;
		final double blockSize = inner != null ? inner.getContentSize() : mini.getContainer().getContentSize();
		final double extent = !vertical ? blockSize
				: box.running != null ? h : MeasuredIntrinsics.usedLineExtent(inner, box.params.flow);
		final double slack = Math.max(0, innerH - extent);
		double dy;
		switch (box.verticalAlign) {
		case START:
			dy = 0;
			break;
		case END:
			dy = slack;
			break;
		default:
			// MIDDLE / BASELINE(マージンボックスでは middle 扱い)
			dy = slack / 2;
			break;
		}
		if (vertical && inlineStartsAtBottom(box.params)) {
			dy -= slack;
		}
		final double dx = vertical ? Math.max(0, (innerW - blockSize) / 2) : 0;
		// frames が背景・ボーダー層、draw が内容層(PageBox.drawFlow と同じ二層)。
		// 縦書き RL のミニページは右端から行を積むので x は左へ寄せる
		final double drawX = x + (box.params.flow == WritingMode.RL ? -dx : dx);
		final double drawY = y + dy;
		if (box.running != null) {
			RunningRenderer.draw(mini, drawer, drawX, drawY);
		} else {
			// 枠(背景・罫線)は領域に固定した箱をそのまま、内容だけを寄せて描く
			mini.frames(mini, drawer, null, new AffineTransform(), x, y);
			mini.draw(mini, drawer, visitor, null, new AffineTransform(), drawX, drawY, drawX, drawY);
		}
	}

	/** ミニページの最初のブロック箱(マージンボックス本体)の内側 container。 */
	private static Container innerContainer(final PageBox mini) {
		final AbstractContainerBox[] found = { null };
		mini.getContainer().eachFlowBox(b -> {
			if (found[0] == null && b instanceof AbstractContainerBox c) {
				found[0] = c;
			}
		});
		return found[0] == null ? mini.getContainer() : found[0].getContainer();
	}

	/**
	 * 縦書きのミニページで行頭が物理下端に来るか(内容が既に地側に寄っているか)。
	 * 条件は行ボックスの実際の反転と同じにする: sideways は
	 * {@code LayoutUtils.inlineToPhysical} が進行方向 BOTTOM_TO_TOP で無条件に反転、
	 * 通常の縦書き rtl は {@code AbstractLineBox} が段落 bidi 有効時だけ start/end を
	 * 交換する(codex レビュー 3 回目、2026-09-06)。
	 */
	private static boolean inlineStartsAtBottom(final BlockParams params) {
		if (TypesettingMode.usesSidewaysInlineAxis(params.flow, params.writingModeVariant)) {
			return TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
					params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
		}
		return params.paragraphBidi
				&& params.direction == net.zamasoft.foliojet.layout.box.params.AbstractTextParams.DIRECTION_RTL;
	}

	/**
	 * 1個のマージンボックスの合成結果(スタイル+内容テキスト)です。
	 */
	private static final class Box {
		final BlockParams params;

		final String text;
		final RunningRenderer.Content running;

		final CellAlign verticalAlign;

		private double preferredWidth = -1;

		private Box(BlockParams params, String text, CellAlign verticalAlign, RunningRenderer.Content running) {
			this.params = params;
			this.text = text;
			this.verticalAlign = verticalAlign;
			this.running = running;
		}

		/**
		 * 宣言からボックスを合成します。内容が生成されない場合は null。
		 */
		static Box create(final UserAgent ua, final MarginBoxName name, final Declaration declaration,
				final RunningRenderer renderer) {
			final CSSStyle style = CSSStyle.getCSSStyle(ua, null, CSSElement.BEFORE);
			// ボックス位置ごとの UA 既定(css-page-3 Appendix A 相当)。
			// 宣言が上書きできるよう applyProperties より先に設定する
			style.set(TextAlign.INFO, defaultTextAlign(name));
			style.set(VerticalAlign.INFO, defaultVerticalAlign(name));
			declaration.applyProperties(style);

			final Value[] contents = Content.get(style);
			if (contents == null) {
				return null;
			}
			final StringBuilder text = new StringBuilder();
			RunningRenderer.Content running = null;
			for (final Value v : contents) {
				switch (v) {
				case StringValue str -> text.append(str.getString());
				case CounterValue counter -> text
						.append(CounterStyles.of(ua).format(counterValue(ua, counter.getName()), counter.getStyle()));
				case CountersValue counters -> text.append(
						CounterStyles.of(ua).format(counterValue(ua, counters.getName()), counters.getStyle()));
				case StringFunctionValue sf -> {
					final PageAssignmentState.Resolution<String> result = ua.getPassContext().getStringState()
							.resolve(sf.getName(), sf.getMode());
					if (result.presence() == PageAssignmentState.Presence.VALUE) {
						text.append(result.value());
					}
				}
				case ElementFunctionValue element -> {
					running = renderer.prepare(element, style);
					if (running == null) {
						return null;
					}
				}
				default -> LOG.log(Level.FINE, "マージンボックスで未対応のcontent値: {0}", v);
				}
			}

			final BlockParams params = new BlockParams();
			params.element = style.getCSSElement();
			params.frame = RectFrame.create(
					BoxValueUtils.toInsets(Margin.get(style, Side.TOP), Margin.get(style, Side.RIGHT),
							Margin.get(style, Side.BOTTOM), Margin.get(style, Side.LEFT)),
					BoxStyleMapper.createRectBorder(style), BoxStyleMapper.createBackground(style),
					BoxValueUtils.toInsets(Padding.get(style, Side.TOP), Padding.get(style, Side.RIGHT),
							Padding.get(style, Side.BOTTOM), Padding.get(style, Side.LEFT)));
			params.color = TextFillColor.get(style);
			params.fontStyle = style.getFontStyle();
			params.fontManager = ua.getFontManager();
			params.lineBreakRules = LanguageProfileBundle.getLanguageProfile(style.getCSSElement().lang)
					.getTextBreakingRules(style);
			params.direction = Direction.get(style);
			params.unicodeBidi = UnicodeBidi.get(style);
			params.paragraphBidi = UAProps.LAYOUT_BIDI_PARAGRAPH.getBoolean(ua);
			params.bidiSemanticAlias = UAProps.OUTPUT_PDF_BIDI_ACTUAL_TEXT.getBoolean(ua);
			params.flow = net.zamasoft.foliojet.css.impl.property.text.BlockFlow.get(style);
			params.writingModeVariant = net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.get(style);
			if (params.flow.isVertical()) {
				// 縦書きの行方向(天地)の寄せは place() が vertical-align で行う。
				// ミニページの行長は帯の高さそのものなので、ここで center に
				// すると vertical-align: top の柱が天地中央へ行ってしまう
				params.textAlign = net.zamasoft.foliojet.layout.box.params.AbstractLineParams.TEXT_ALIGN_START;
				params.textAlignLast = net.zamasoft.foliojet.layout.box.params.AbstractLineParams.TEXT_ALIGN_START;
			} else {
				params.textAlign = TextAlign.get(style);
				params.textAlignLast = TextAlignLast.get(style);
			}
			params.lineHeight = LineHeight.get(style);
			params.whiteSpace = WhiteSpace.get(style);
			params.letterSpacing = LetterSpacing.get(style);
			params.wordSpacing = WordSpacing.get(style);
			return new Box(params, text.toString(), VerticalAlign.getForTableCell(style), running);
		}

		/**
		 * max-content 幅を実レイアウトで測ります(結果はキャッシュ)。
		 */
		double preferredWidth(final UserAgent ua) {
			if (this.preferredWidth < 0) {
				final PageBox wide = this.layout(ua, INFINITE, INFINITE);
				this.preferredWidth = wide == null ? 0 : this.params.flow.isVertical()
						? wide.getContainer().getContentSize()
						: MeasuredIntrinsics.usedLineExtent(wide.getContainer(), this.params.flow);
			}
			return this.preferredWidth;
		}

		/**
		 * 与えられた幅で組んだときの内容高さを測ります。縦書きは行長=利用可能な高さで
		 * 組む(百分率 padding の基準を配置時と揃える。無限高だと padding だけで
		 * 領域を占有する——codex レビュー 3 回目)。横書きは高さ無限で自然な高さ
		 */
		double preferredHeight(final UserAgent ua, final double width, final double availableHeight) {
			final PageBox mini = this.layout(ua, width, this.params.flow.isVertical() ? availableHeight : INFINITE);
			return mini == null ? 0 : this.params.flow.isVertical()
					? MeasuredIntrinsics.usedLineExtent(mini.getContainer(), this.params.flow)
					: mini.getContainer().getContentSize();
		}

		/**
		 * 隔離ミニレイアウトで内容を組みます。
		 */
		PageBox layout(final UserAgent ua, final double width, final double height) {
			return this.layout(ua, width, height, false);
		}

		/**
		 * 隔離ミニレイアウトで内容を組みます。{@code fill} なら箱を領域(width×height、
		 * margin を除く)の大きさに固定する——背景・罫線が領域全体に描かれ、内容の
		 * 寄せは {@link MarginBoxes#place} が行う(Vivliostyle 実測 2026-09-06: マージン
		 * ボックスの背景は縦横とも割り当て領域いっぱい)。測定(preferredWidth/Height)は
		 * 自然な寸法が要るので固定しない。
		 */
		PageBox layout(final UserAgent ua, final double width, final double height, final boolean fill) {
			if (this.running != null) {
				return this.running.layout(this.params, width, height);
			}
			if (fill) {
				final Insets margin = this.params.frame.margin;
				this.params.boxSizing = BoxSizingMode.BORDER_BOX;
				this.params.size = Dimension.create(Math.max(0, width - margin.getLeft() - margin.getRight()),
						Math.max(0, height - margin.getTop() - margin.getBottom()), LengthType.ABSOLUTE,
						LengthType.ABSOLUTE);
			} else {
				this.params.boxSizing = BoxSizingMode.CONTENT_BOX;
				this.params.size = Dimension.AUTO_DIMENSION;
			}
			final MeasurePageGenerator pg = new MeasurePageGenerator(ua, this.params, width, height, null, false);
			final DocumentBuilder doc = new DocumentBuilder(pg);
			doc.setPageMode(DocumentBuilder.PAGE_MODE_NO_BREAK);
			doc.startBox(new FlowBlockBox(this.params, new FlowPos()));
			if (!this.text.isEmpty()) {
				final char[] ch = this.text.toCharArray();
				doc.characters(-1, ch, 0, ch.length, true);
			}
			doc.endBox();
			doc.end();
			return pg.getLastPage();
		}

		private static int counterValue(final UserAgent ua, final String name) {
			// マージンボックスはページレベル(level 0)のカウンタを見る
			// (page / pages / @page の counter-*)。文書ツリー内のカウンタは
			// 改ページ位置のスナップショットが必要なため未対応(FINE ログなし:
			// 未定義カウンタの 0 は仕様通り)
			final CounterScope scope = ua.getPassContext().getCounterScope(0, false);
			if (scope != null && scope.defined(name)) {
				return scope.get(name);
			}
			return 0;
		}

		private static Value defaultTextAlign(final MarginBoxName name) {
			return switch (name) {
			case TOP_LEFT_CORNER, BOTTOM_LEFT_CORNER -> TextAlignValue.RIGHT_VALUE;
			case TOP_RIGHT_CORNER, BOTTOM_RIGHT_CORNER -> TextAlignValue.LEFT_VALUE;
			case TOP_LEFT, BOTTOM_LEFT -> TextAlignValue.LEFT_VALUE;
			case TOP_CENTER, BOTTOM_CENTER -> TextAlignValue.CENTER_VALUE;
			case TOP_RIGHT, BOTTOM_RIGHT -> TextAlignValue.RIGHT_VALUE;
			// 側面ボックスは中央
			default -> TextAlignValue.CENTER_VALUE;
			};
		}

		private static Value defaultVerticalAlign(final MarginBoxName name) {
			return switch (name) {
			case LEFT_TOP, RIGHT_TOP -> VerticalAlignValue.TOP_VALUE;
			case LEFT_BOTTOM, RIGHT_BOTTOM -> VerticalAlignValue.BOTTOM_VALUE;
			default -> VerticalAlignValue.MIDDLE_VALUE;
			};
		}
	}
}
