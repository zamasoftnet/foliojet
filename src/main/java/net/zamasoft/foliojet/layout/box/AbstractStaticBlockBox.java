package net.zamasoft.foliojet.layout.box;

import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.sizing.Sizing;
import net.zamasoft.foliojet.layout.sizing.SizingContext;
import net.zamasoft.foliojet.layout.sizing.SizingMode;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.IntrinsicSize;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractStaticBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractStaticBlockBox extends AbstractBlockBox {
	protected boolean specifiedPageAxis = false;

	public AbstractStaticBlockBox(final BlockParams params) {
		super(params);
	}

	protected AbstractStaticBlockBox(final BlockParams params, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, final Container container) {
		super(params, size, minSize, frame, container);
	}

	public abstract AbstractStaticPos getStaticPos();

	/**
	 * 縦中横({@code text-combine-upright: all})の水平圧縮率です。
	 * 1なら圧縮なし。{@link #compressTextCombine}が設定する。
	 */
	private double textCombineScaleX = 1;

	/** 圧縮後のセル内で内容を中央へ寄せる物理Xのずれです。 */
	private double textCombineOffsetX = 0;

	protected final double internalScaleX() {
		return this.textCombineScaleX;
	}

	protected final double internalOffsetX() {
		return this.textCombineOffsetX;
	}

	/**
	 * 縦中横の内容を1emのセルへ収めます(css-writing-modes-4 §9.1.3、
	 * 2026-08-11)。
	 *
	 * <p>
	 * 自然幅{@code W}で組み終えた<b>あと</b>に呼ぶこと。箱の幅を
	 * {@code cellExtent}(=1em)へ差し替え、内容には
	 * {@code min(1, cellExtent/W)}の水平アフィンを掛ける。先に幅を1emにして
	 * 組むと数字が折り返してしまうため、この順序でなければならない。
	 * 2〜4文字では字形化前に{@code hwid/twid/qwid}を要求しており、ここで
	 * 見る自然幅はその再計量結果です。featureが無いフォントだけが
	 * アフィン圧縮へフォールバックする。
	 * 自然幅が1emより狭いときは等倍のままセル内で中央へ寄せる。
	 * </p>
	 *
	 * <p>
	 * 二度呼ばれても壊れないよう、圧縮済み(scaleX≠1)なら何もしない
	 * ——2パス構成では同じ箱が再度行へ積まれることがある。
	 * </p>
	 *
	 * @param cellExtent セルの幅(通常は1em)
	 * @param inkBounds  圧縮前のローカル座標における字面の輪郭。取得できない場合はnull
	 */
	public final void compressTextCombine(final double cellExtent, final Rectangle2D inkBounds) {
		if (this.textCombineScaleX != 1 || cellExtent <= 0) {
			return;
		}
		final double natural = this.width;
		if (natural <= 0) {
			return;
		}
		if (natural > cellExtent) {
			this.textCombineScaleX = cellExtent / natural;
		}
		if (inkBounds != null && !inkBounds.isEmpty()) {
			// 送り幅ではなく実際の墨の中心を1emセルの中心へ置く。
			// 数字は同じadvanceでも左右サイドベアリングが字形ごとに異なるため、
			// 左端基準で縮小すると二桁ページ番号が数字ごとに横へ揺れる
			// (2026-08-13、実書籍の目次で41/43/45を1200dpi実測)。
			this.textCombineOffsetX = cellExtent / 2.0
					- this.textCombineScaleX * inkBounds.getCenterX();
		} else if (natural <= cellExtent) {
			this.textCombineOffsetX = (cellExtent - natural) / 2;
		}
		this.width = cellExtent;
	}

	public final boolean isSpecifiedPageSize() {
		return this.specifiedPageAxis;
	}

	/**
	 * {@code aspect-ratio}から求めたページ方向のcontent-box寸法です
	 * (2026-08-29、css-sizing-4 §5)。比率は物理の幅/高さで、
	 * {@code box-sizing}の箱(border-boxならpadding+border込み)に掛かる。
	 *
	 * @param lineExtent 行方向のcontent-box寸法
	 * @return ページ方向のcontent-box寸法(比率指定が無ければNONE)
	 */
	protected final double aspectRatioPageExtent(final double lineExtent) {
		final double ratio = this.params.aspectRatio;
		if (!(ratio > 0)) {
			return LayoutUtils.NONE;
		}
		final boolean borderBox = this.params.boxSizing == BoxSizingMode.BORDER_BOX;
		final double lineFrame = borderBox ? this.frame.getBorderLineExtent(this.params.flow) : 0;
		final double pageFrame = borderBox ? this.frame.getBorderPageExtent(this.params.flow) : 0;
		final double outerLine = Math.max(0, lineExtent) + lineFrame;
		// 横書き: 行軸=幅→高さ=幅/比率。縦書き: 行軸=高さ→幅=高さ×比率
		final double outerPage = this.params.flow.isVertical() ? outerLine * ratio : outerLine / ratio;
		return Math.max(0, outerPage - pageFrame);
	}

	/**
	 * {@code aspect-ratio}から求めた行方向のcontent-box寸法です
	 * ({@link #aspectRatioPageExtent}の逆——ページ方向だけが確定している
	 * ときに使う。2026-08-29)。
	 */
	protected final double aspectRatioLineExtent(final double pageExtent) {
		final double ratio = this.params.aspectRatio;
		if (!(ratio > 0)) {
			return LayoutUtils.NONE;
		}
		final boolean borderBox = this.params.boxSizing == BoxSizingMode.BORDER_BOX;
		final double lineFrame = borderBox ? this.frame.getBorderLineExtent(this.params.flow) : 0;
		final double pageFrame = borderBox ? this.frame.getBorderPageExtent(this.params.flow) : 0;
		final double outerPage = Math.max(0, pageExtent) + pageFrame;
		final double outerLine = this.params.flow.isVertical() ? outerPage / ratio : outerPage * ratio;
		return Math.max(0, outerLine - lineFrame);
	}

	public final boolean isContextBox() {
		return this.getStaticPos().offset != null;
	}

	public void shrinkToFit(LayoutStack layoutStack, IntrinsicSizes sizes, boolean table) {
		final double minLineAxis = sizes.minContent(), maxLineAxis = sizes.maxContent();
		final AbstractContainerBox containerBox;
		if (this.getPos().getType() == PosType.FLOW) {
			if (table) {
				// テーブル
				BlockBuilder builder = (BlockBuilder) layoutStack;
				containerBox = builder.getFlow(builder.getFlowCount() - 2).box;
			} else {
				// 書字方向の混在
				containerBox = layoutStack.getFlowBox();
			}
		} else {
			containerBox = layoutStack.getFlowBox();
		}
		if (!table && containerBox.getType() == BoxType.TABLE_CELL) {
			table = true;
		}
		final BlockParams cParams = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		final WritingMode flow = this.params.flow;
		{
			final LengthType pageType = this.params.size.getPageType(flow);
			// 直交ブロック(親と書字方向の軸が違う)のページ軸%の基準は
			// 親の線軸で、これは常に確定している——ここを親のページ軸
			// (isSpecifiedPageSize)で判定すると縦書き文書内の横ブロックの
			// height:100%が未確定扱い→AUTOフォールスルーで0になり、
			// firstPassLayoutが親線軸基準で出した正しい値を潰す(2026-08-10、
			// 実書籍の資料図版ページ全滅で発見)
			final boolean orthogonal = cParams.flow.isVertical() != flow.isVertical();
			this.specifiedPageAxis = pageType == LengthType.ABSOLUTE || (pageType.needsReference() && (!table
					&& (this.getPos().getType() == PosType.INLINE || orthogonal
							|| containerBox.isSpecifiedPageSize())));
		}

		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		//
		// ■ 行方向幅の計算
		//
		// 論理軸(行方向/ページ方向)で計算し、末尾で物理寸法へ書き戻す。
		final SizingContext context = this.fitContentContext(layoutStack, containerBox, table);
		// **親と同軸の通常フローがここへ来るのは固有寸法キーワード付きの
		// ときだけ**(width:max-content等、2026-08-29。DocumentBuilder.startBox
		// の振り分け)。利用可能寸法と%の基準は包含ブロックの行寸法で、
		// 浮動体用のgetFixedWidth()ではない
		final boolean sameAxisFlow = !table && this.getPos().getType() == PosType.FLOW
				&& cParams.flow.isVertical() == flow.isVertical();
		final double cLine = sameAxisFlow ? lineSize : context.availableLine();

		// 行方向: fit-content と min/max クランプ
		double lineExtent = LayoutUtils.computeDimensionLine(this.size, flow, cLine);
		// aspect-ratio: 行方向autoでページ方向が絶対長なら、fit-contentでは
		// なく比率で行方向を決める(2026-08-29。height:40px;aspect-ratio:2の
		// float/inline-blockは幅80px)
		boolean ratioLine = false;
		if (LayoutUtils.isNone(lineExtent) && this.params.aspectRatio > 0
				&& this.size.getPageType(flow) == LengthType.ABSOLUTE) {
			double page = this.size.getPageLength(flow);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				page -= this.frame.getBorderPageExtent(flow);
			}
			lineExtent = this.aspectRatioLineExtent(page);
			ratioLine = true;
		}
		if (LayoutUtils.isNone(lineExtent)) {
			lineExtent = maxLineAxis;
		} else if (!ratioLine) {
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				lineExtent -= this.frame.getBorderLineExtent(flow);
			}
		}
		final double limitLine = this.availableLineExtent(layoutStack, containerBox, cLine);
		if (this.size.getLineType(flow) == LengthType.AUTO && !ratioLine) {
			final IntrinsicSize intrinsic = table ? null : this.params.intrinsicLine;
			if (intrinsic != null) {
				// 固有寸法キーワード(2026-08-29): max-content/min-contentは
				// 実測値そのもの、fit-content(L)は上限をLに差し替えた
				// shrink-to-fit。紙幅の制限(下の段数倍クランプ)は掛けない
				// ——作者が内容幅を明示した箱で、はみ出すなら仕様どおり
				lineExtent = this.resolveIntrinsicLine(intrinsic, minLineAxis, maxLineAxis, limitLine, cLine);
			} else if (sameAxisFlow) {
				// width:autoでmin/maxだけが固有寸法の通常フロー: 幅は
				// 通常どおり包含ブロックを充填し、下のmin/maxで挟む
				lineExtent = limitLine;
			} else {
				lineExtent = Sizing.fitContent(minLineAxis, lineExtent, limitLine);
				if (!table && sizes.columnInflated() && limitLine > 0 && lineExtent > limitLine) {
					// **段数倍で膨らんだ最小内容寸法で紙の行軸を超えない**
					// (2026-07-28)。
					//
					// `fit-content`は`max(min-content, min(available, max-content))`
					// なので、**最小内容寸法が使える空間より大きいとそれがそのまま
					// 採用される**。画面のブラウザではそれで正しい——はみ出した
					// ぶんはスクロールで読める。しかし紙には続きがない。しかも
					// **行軸は分割できない**(ページ分割はページ軸にしか効かない)
					// ので、行軸をはみ出した内容は次のページへ送られるのではなく、
					// 紙の外の座標にそのまま描かれる。
					//
					// 段組の最小内容寸法は「段数 × 中身の最小内容寸法 + 段間」
					// ——**段数倍に膨らみ**、入れ子にすれば積で効く。実測
					// (2026-07-28、seed 25503): 200pt紙に高さ823ptのフロート
					// (= 4段 × 196pt + 3 × 13pt)ができ、内容が y=-623 に
					// 描かれた。**段は狭くできる**(行軸を段数で割り直すだけ)
					// ので、この下限は守らなくてよい。段は細くなるが紙には載る
					// ——横書きがこの欠陥を1件も出さないのと同じ状態になる。
					//
					// **段数倍が効いたときだけ**にするのが肝心
					// ({@code columnInflated})。`height:150mm`の画像のように
					// 作者が明示した不可分な箱から来た最小内容寸法まで縮めると、
					// 箱だけ縮んで中身は縮まず、**はみ出しが増える**。実測で
					// 400pt紙の`writing-mode:vertical-rl`の箱が425.2→316ptに
					// 縮み、画像が段送りされずその場ではみ出した
					// (`WritingModeColumnTest`)。
					//
					// 明示された`min-*`は下でこの値を上書きするので、作者の
					// 指定は従来どおり通る。
					lineExtent = limitLine;
				}
			}
		}
		// min/max-width は box-sizing のスケールで書かれている。lineExtent は
		// 内容幅なので、border-box なら境界+パディングを引いてから比べる
		// (2026-08-29)。従来は引いておらず、`min-width:100px; padding-inline:8px;
		// box-sizing:border-box` のピルが116pxに広がった(padding-inlineの
		// 対応で顕在化。0510-flex/min-width-nested-containerの期待は75pt)
		final double borderBoxLine = this.params.boxSizing == BoxSizingMode.BORDER_BOX
				? this.frame.getBorderLineExtent(flow)
				: 0;
		double maxLine = LayoutUtils.computeDimensionLine(this.params.maxSize, flow, cLine);
		if (!LayoutUtils.isNone(maxLine)) {
			maxLine = Math.max(0, maxLine - borderBoxLine);
		}
		if (!table && this.params.intrinsicMaxLine != null) {
			// max-width: max-content 等(2026-08-29)。Dimension側はAUTO(none)
			maxLine = this.resolveIntrinsicLine(this.params.intrinsicMaxLine, minLineAxis, maxLineAxis, limitLine,
					cLine);
		}
		if (!LayoutUtils.isNone(maxLine) && lineExtent > maxLine) {
			lineExtent = maxLine;
		}
		double minLine = LayoutUtils.computeDimensionLine(this.minSize, flow, cLine);
		if (!LayoutUtils.isNone(minLine)) {
			minLine = Math.max(0, minLine - borderBoxLine);
		}
		if (!table && this.params.intrinsicMinLine != null) {
			// min-width: max-content 等(2026-08-29)。Dimension側はAUTO(0)
			minLine = this.resolveIntrinsicLine(this.params.intrinsicMinLine, minLineAxis, maxLineAxis, limitLine,
					cLine);
		}
		if (!LayoutUtils.isNone(minLine) && lineExtent < minLine) {
			lineExtent = minLine;
		}

		// ページ方向: min/max と指定寸法。%は percentBasePage が確定している場合のみ解決する
		double minPage;
		switch (this.minSize.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				minPage = this.minSize.getPageLength(flow) * context.percentBasePage();
				break;
			}
			// percentBasePage未確定ならAUTOへフォールスルー(既存の意図的な仕様)
		case AUTO:
			minPage = 0;
			break;
		case ABSOLUTE:
			minPage = this.minSize.getPageLength(flow);
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				minPage = this.minSize.getPageLength(flow) + this.minSize.getPageRatio(flow) * context.percentBasePage();
				break;
			}
			minPage = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		double maxPage;
		switch (this.params.maxSize.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				maxPage = this.params.maxSize.getPageLength(flow) * context.percentBasePage();
				break;
			}
			// percentBasePage未確定ならAUTOへフォールスルー(既存の意図的な仕様)
		case AUTO:
			maxPage = Double.MAX_VALUE;
			break;
		case ABSOLUTE:
			maxPage = this.params.maxSize.getPageLength(flow);
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				maxPage = this.params.maxSize.getPageLength(flow)
						+ this.params.maxSize.getPageRatio(flow) * context.percentBasePage();
				break;
			}
			maxPage = Double.MAX_VALUE;
			break;
		default:
			throw new IllegalStateException();
		}
		// ページ方向のmin/max指定もbox-sizingのスケール。border-boxなら枠を
		// 引いて内寸スケールへ揃えてから比べる(2026-08-29。行方向の
		// borderBoxLineと対。従来はminPageAxis/maxPageAxisが枠込みのまま
		// 残り、setPageAxisが内容高を枠込みの下限まで押し上げていた)
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			final double borderBoxPage = this.getFrame().getBorderPageExtent(flow);
			minPage = Math.max(0, minPage - borderBoxPage);
			if (maxPage != Double.MAX_VALUE) {
				maxPage = Math.max(0, maxPage - borderBoxPage);
			}
		}
		double pageExtent = flow.isVertical() ? this.width : this.height;
		switch (this.size.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				pageExtent = this.size.getPageLength(flow) * context.percentBasePage();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					pageExtent = Math.max(0, pageExtent - this.getFrame().getBorderPageExtent(flow));
				}
				pageExtent = Math.max(pageExtent, minPage);
				pageExtent = Math.min(pageExtent, maxPage);
				minPage = maxPage = pageExtent;
				break;
			}
			// percentBasePage未確定ならAUTOへフォールスルー(既存の意図的な仕様)
		case AUTO:
			// 台帳#4 解消(2026-07-17): 旧実装は縦書きのテーブル時のみ
			// 既値を維持していた。横書きと同じく常に0(内容が後で決める)
			pageExtent = 0;
			break;
		case ABSOLUTE:
			pageExtent = this.size.getPageLength(flow);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				pageExtent = Math.max(0, pageExtent - this.getFrame().getBorderPageExtent(flow));
			}
			pageExtent = Math.max(pageExtent, minPage);
			pageExtent = Math.min(pageExtent, maxPage);
			minPage = maxPage = pageExtent;
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				pageExtent = this.size.getPageLength(flow) + this.size.getPageRatio(flow) * context.percentBasePage();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					pageExtent = Math.max(0, pageExtent - this.getFrame().getBorderPageExtent(flow));
				}
				pageExtent = Math.max(pageExtent, minPage);
				pageExtent = Math.min(pageExtent, maxPage);
				minPage = maxPage = pageExtent;
				break;
			}
			pageExtent = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		if (this.params.aspectRatio > 0 && !this.specifiedPageAxis) {
			// aspect-ratio: ページ方向がautoなら行方向から比率で決める
			// (2026-08-29)。内容が比率高より高いときはoverflow:visibleなら
			// 内容に合わせて伸びる(仕様のmin-height:auto=内容寸法の近似)
			// ——minPageを比率高、maxPageは可視のとき無制限のまま
			double page = this.aspectRatioPageExtent(lineExtent);
			page = Math.max(page, minPage);
			page = Math.min(page, maxPage);
			pageExtent = page;
			minPage = page;
			if (this.params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
				maxPage = page;
			}
			this.specifiedPageAxis = true;
		}
		this.minPageAxis = minPage;
		this.maxPageAxis = maxPage;

		// 物理寸法へ書き戻し
		if (flow.isVertical()) {
			this.height = lineExtent;
			this.width = pageExtent;
		} else {
			this.width = lineExtent;
			this.height = pageExtent;
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
		// 通常フローのautoマージン(margin:0 auto の中央寄せ)はここでは
		// 触らない——BlockBuilder.addBoundがresolvedAlignとともに解決する
		// (直交フローと同じ経路、2026-08-29)
	}

	/**
	 * 行方向の利用可能寸法(content-box)を返します(2026-08-29に
	 * {@link #shrinkToFit}から切り出し。min/maxの固有寸法解決でも使う)。
	 *
	 * @param layoutStack  レイアウトスタック
	 * @param containerBox 包含ブロック
	 * @param cLine        包含ブロックの行方向寸法
	 * @return 利用可能寸法
	 */
	private double availableLineExtent(final LayoutStack layoutStack, final AbstractContainerBox containerBox,
			final double cLine) {
		final WritingMode flow = this.params.flow;
		if (containerBox.getBlockParams().flow.isVertical() == flow.isVertical() || containerBox.isSpecifiedPageSize()) {
			return cLine - this.frame.getFrameLineExtent(flow);
		}
		// 親の幅が不確定の場合はページ寸法を限度とする。基準は
		// ページの**内容域**(マージンの内側)——物理寸法を使うと
		// fit-contentがマージンへ食い込む幅を許してしまう
		// (2026-08-10、縦書き書籍の資料図版ページで実測)
		final AbstractContainerBox fixedLineBox = flow.isVertical() ? layoutStack.getFixedHeightFlowBox()
				: layoutStack.getFixedWidthFlowBox();
		return (fixedLineBox != null ? fixedLineBox.getInnerLineExtent(flow)
				: (flow.isVertical() ? layoutStack.getFixedHeight() : layoutStack.getFixedWidth()))
				- this.frame.getFrameLineExtent(flow);
	}

	/**
	 * fit-content サイズ決定のための制約空間を包含コンテキストから導出します。
	 * 呼び出し前に specifiedPageAxis が確定している必要があります。
	 *
	 * @param layoutStack  レイアウトスタック
	 * @param containerBox 包含ブロック
	 * @param table        テーブル文脈であればtrue
	 * @return 制約空間
	 */
	private SizingContext fitContentContext(LayoutStack layoutStack, AbstractContainerBox containerBox, boolean table) {
		final WritingMode flow = this.params.flow;
		// ページ方向の基準ボックス。
		AbstractContainerBox fixedPageBox = flow.isVertical() ? layoutStack.getFixedWidthFlowBox()
				: layoutStack.getFixedHeightFlowBox();
		if (fixedPageBox == null) {
			fixedPageBox = containerBox;
		}
		// 台帳#3 解消(2026-07-17): 旧実装は縦書きでも InnerHeight を参照
		// していた。ページ方向%の基準は論理ページ軸の内寸(縦書き=幅)。
		// ただし直交ブロックのページ軸は親の線軸に一致するため、基準は
		// 包含ブロックの線軸内寸(2026-08-10、specifiedPageAxisの直交条件と対)
		final BlockParams cParams = containerBox.getBlockParams();
		final double cPage = (cParams.flow.isVertical() != flow.isVertical())
				? containerBox.getInnerLineExtent(cParams.flow)
				: fixedPageBox.getInnerPageExtent(flow);
		final double cLine = table ? containerBox.getInnerLineExtent(flow)
				: (flow.isVertical() ? layoutStack.getFixedHeight() : layoutStack.getFixedWidth());
		// ページ方向の%は基準が確定している場合のみ解決する
		final double pagePercentBase = (!table && this.isSpecifiedPageSize()) ? cPage : LayoutUtils.NONE;
		return new SizingContext(SizingMode.FIT_CONTENT, cLine, cLine, pagePercentBase);
	}

	public void finishLayoutSelf(IFramedBox containerBox) {
		// 位置の計算
		AbstractStaticPos pos = this.getStaticPos();
		if (pos.offset != null) {
			//
			// ■ 相対配置の位置の計算
			//
			this.offsetX = LayoutUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = LayoutUtils.computeOffsetY(pos.offset, containerBox);
		}
	}
}
