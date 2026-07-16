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
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.CounterValue;
import net.zamasoft.foliojet.css.value.CountersValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.VerticalAlignValue;
import net.zamasoft.foliojet.css.value.ext.CSSJFirstHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJLastHeadingValue;
import net.zamasoft.foliojet.css.value.ext.CSSJTitleValue;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.impl.css.property.box.Margin;
import net.zamasoft.foliojet.impl.css.property.box.Padding;
import net.zamasoft.foliojet.impl.css.property.box.Side;
import net.zamasoft.foliojet.impl.css.property.box.VerticalAlign;
import net.zamasoft.foliojet.impl.css.property.content.Content;
import net.zamasoft.foliojet.impl.css.property.text.Direction;
import net.zamasoft.foliojet.impl.css.property.text.LetterSpacing;
import net.zamasoft.foliojet.impl.css.property.font.LineHeight;
import net.zamasoft.foliojet.impl.css.property.text.TextAlign;
import net.zamasoft.foliojet.impl.css.property.text.TextAlignLast;
import net.zamasoft.foliojet.impl.css.property.text.TextFillColor;
import net.zamasoft.foliojet.impl.css.property.text.WhiteSpace;
import net.zamasoft.foliojet.impl.css.property.text.WordSpacing;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.CellAlign;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.sizing.MeasuredIntrinsics;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.UserAgent;

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
 * -cssj-first/last-heading・-cssj-title、フォント・色・text-align・
 * vertical-align(top/middle/bottom)、margin/border/padding/background。
 * 未対応(FINE ログ): url() 画像・引用符・attr()・page-ref、縦書きの側面
 * ボックス。幅配分は css-page-3 §7.3 の基本形(センター優先、なければ
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
			final PageBox pageBox, final Drawer drawer, final Visitor visitor) {
		final Map<MarginBoxName, Declaration> declarations = styleContext.pageMarginBoxes(pageElement);
		if (declarations.isEmpty()) {
			return;
		}
		final Map<MarginBoxName, Box> boxes = new EnumMap<MarginBoxName, Box>(MarginBoxName.class);
		for (final Map.Entry<MarginBoxName, Declaration> e : declarations.entrySet()) {
			final Box box = Box.create(ua, e.getKey(), e.getValue());
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
			final double ch = Math.min(middle.preferredHeight(ua, w), h);
			final double side = (h - ch) / 2;
			place(ua, top, x, y, w, side, drawer, visitor);
			place(ua, middle, x, y + side, w, ch, drawer, visitor);
			place(ua, bottom, x, y + h - side, w, side, drawer, visitor);
		} else if (top != null && bottom != null) {
			final double pt = top.preferredHeight(ua, w);
			final double pb = bottom.preferredHeight(ua, w);
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
		final PageBox mini = box.layout(ua, w, h);
		if (mini == null) {
			return;
		}
		final double contentH = mini.getContainer().getContentSize();
		final double dy;
		switch (box.verticalAlign) {
		case START:
			dy = 0;
			break;
		case END:
			dy = Math.max(0, h - contentH);
			break;
		default:
			// MIDDLE / BASELINE(マージンボックスでは middle 扱い)
			dy = Math.max(0, (h - contentH) / 2);
			break;
		}
		// frames が背景・ボーダー層、draw が内容層(PageBox.drawFlow と同じ二層)
		mini.frames(mini, drawer, null, new AffineTransform(), x, y + dy);
		mini.draw(mini, drawer, visitor, null, new AffineTransform(), x, y + dy, x, y + dy);
	}

	/**
	 * 1個のマージンボックスの合成結果(スタイル+内容テキスト)です。
	 */
	private static final class Box {
		final BlockParams params;

		final String text;

		final CellAlign verticalAlign;

		private double preferredWidth = -1;

		private Box(BlockParams params, String text, CellAlign verticalAlign) {
			this.params = params;
			this.text = text;
			this.verticalAlign = verticalAlign;
		}

		/**
		 * 宣言からボックスを合成します。内容が生成されない場合は null。
		 */
		static Box create(final UserAgent ua, final MarginBoxName name, final Declaration declaration) {
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
			for (final Value v : contents) {
				switch (v) {
				case StringValue str -> text.append(str.getString());
				case CounterValue counter -> text
						.append(GeneratedValueUtils.format(counterValue(ua, counter.getName()), counter.getStyle()));
				case CountersValue counters -> text.append(
						GeneratedValueUtils.format(counterValue(ua, counters.getName()), counters.getStyle()));
				case CSSJTitleValue title -> {
					final String str = ua.getPassContext().getSectionState().title;
					if (str != null) {
						text.append(str);
					}
				}
				case CSSJFirstHeadingValue heading -> {
					final String[] sections = ua.getPassContext().getSectionState().firstSections;
					final int level = Math.min(heading.getLevel() - 1, sections.length - 1);
					if (sections[level] != null) {
						text.append(sections[level]);
					}
				}
				case CSSJLastHeadingValue heading -> {
					final String[] sections = ua.getPassContext().getSectionState().lastSections;
					final int level = Math.min(heading.getLevel() - 1, sections.length - 1);
					if (sections[level] != null) {
						text.append(sections[level]);
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
					StyleBuilder.createRectBorder(style), StyleBuilder.createBackground(style),
					BoxValueUtils.toInsets(Padding.get(style, Side.TOP), Padding.get(style, Side.RIGHT),
							Padding.get(style, Side.BOTTOM), Padding.get(style, Side.LEFT)));
			params.color = TextFillColor.get(style);
			params.fontStyle = style.getFontStyle();
			params.fontManager = ua.getFontManager();
			params.lineBreakRules = LanguageProfileBundle.getLanguageProfile(style.getCSSElement().lang)
					.getLineBreakRules(style);
			params.direction = Direction.get(style);
			params.flow = WritingMode.TB;
			params.textAlign = TextAlign.get(style);
			params.textAlignLast = TextAlignLast.get(style);
			params.lineHeight = LineHeight.get(style);
			params.whiteSpace = WhiteSpace.get(style);
			params.letterSpacing = LetterSpacing.get(style);
			params.wordSpacing = WordSpacing.get(style);
			return new Box(params, text.toString(), VerticalAlign.getForTableCell(style));
		}

		/**
		 * max-content 幅を実レイアウトで測ります(結果はキャッシュ)。
		 */
		double preferredWidth(final UserAgent ua) {
			if (this.preferredWidth < 0) {
				final PageBox wide = this.layout(ua, INFINITE, INFINITE);
				this.preferredWidth = wide == null ? 0
						: MeasuredIntrinsics.usedLineExtent(wide.getContainer(), this.params.flow);
			}
			return this.preferredWidth;
		}

		/**
		 * 与えられた幅で組んだときの内容高さを測ります。
		 */
		double preferredHeight(final UserAgent ua, final double width) {
			final PageBox mini = this.layout(ua, width, INFINITE);
			return mini == null ? 0 : mini.getContainer().getContentSize();
		}

		/**
		 * 隔離ミニレイアウトで内容を組みます。
		 */
		PageBox layout(final UserAgent ua, final double width, final double height) {
			final MeasurePageGenerator pg = new MeasurePageGenerator(ua, this.params, width, height);
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
