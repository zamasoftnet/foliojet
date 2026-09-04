package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: FlowBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FlowBlockBox extends AbstractStaticBlockBox implements IFlowBox {
	private static final boolean DEBUG = false;

	protected final FlowPos pos;

	protected double contentSize;

	/** RowSplitContainerの再構築で、子を流す前に使う一回限りの使用済み行寸法。 */
	private double restyleLineWidth = LayoutUtils.NONE;
	private double restyleLineHeight = LayoutUtils.NONE;

	/**
	 * 解決済みの整列です(表の auto マージン整列)。初期値は pos.align で、
	 * shrinkToFit(table) が解決結果を保存します。共有 pos への書き戻しは
	 * しない(ログが参照する pos は record 後不変 — §5.7 前提 (ii))。
	 * 分割断片へは splitPage が引き継ぎます。
	 */
	protected Align resolvedAlign;

	public FlowBlockBox(BlockParams params, FlowPos pos) {
		super(params);
		this.pos = pos;
		this.resolvedAlign = pos.align;
	}

	protected FlowBlockBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
		this.resolvedAlign = pos.align;
	}

	/**
	 * 解決済みの整列を返します(表整列の解決後はその結果)。
	 */
	public final Align getResolvedAlign() {
		return this.resolvedAlign;
	}

	/**
	 * restyle再構築で膨らんだpage軸の内容寸法を復元します(2026-08-08、
	 * {@code RowSplitContainer.restoreAnchoredPageAxis}専用。flexから
	 * flex/grid共通へ一般化——2026-08-10)。汎用再構築はitemを縦積みで
	 * 再登録するため、item側のendFlowBlockが親であるこの箱へΣitem高を
	 * 書き込む——{@code setPageAxis}のcontentSizeはMath.maxの単調増加
	 * なので、後から正しい値を渡しても縦積みの値が残る。ここだけは
	 * 代入で戻す。
	 */
	public final void restoreContentExtent(final double content) {
		this.contentSize = content;
		if (this.getBlockParams().flow.isVertical()) {
			this.width = content;
		} else {
			this.height = content;
		}
	}

	/**
	 * restyle再構築で潰れた確定寸法を復元します(2026-08-08、
	 * {@code RowSplitContainer.restyle}専用。flexからflex/grid共通へ
	 * 一般化——2026-08-10)。itemの寸法はitem coordinator(FlexBuilder/
	 * GridBuilder)が所有するが、ページ跨ぎ移動後の汎用再構築は
	 * {@code startFlowBlock.calculateSize}がwidth:autoを包含幅へ、
	 * {@code endFlowBlock}がheight:autoを内容高(絶対配置子だけなら0)へ
	 * 再解決してしまう——yahoo.co.jpのランキング順位バッジが行全幅の
	 * 色帯になった実バグ。
	 */
	public final void restoreExtents(final double width, final double height) {
		// 呼び出し側はIBox.getWidth/getHeightで採った枠込み外寸を渡す。
		// 内寸フィールドへそのまま代入すると、restyleのたびにmargin/border/
		// paddingをもう一度足し、枠を持つitemが世代ごとに膨らむ。
		this.width = width - this.frame.getFrameWidth();
		this.height = height - this.frame.getFrameHeight();
	}

	/**
	 * 次の{@link #calculateSize}で、flex/gridが確定した使用済み行寸法を先に
	 * 適用します。後から箱幅だけを復元すると、内側のテキストは包含幅で
	 * 改行済みのままになり、固定幅兄弟の画像へ描画が重なるためです。
	 */
	public final void prepareRestyleLineExtent(final double width, final double height, final boolean vertical) {
		if (vertical) {
			this.restyleLineHeight = height;
		} else {
			this.restyleLineWidth = width;
		}
	}

	/** 再生がボックス再構築を通らなかった場合の一回限り指定を破棄します。 */
	public final void clearRestyleLineExtent() {
		this.restyleLineWidth = LayoutUtils.NONE;
		this.restyleLineHeight = LayoutUtils.NONE;
	}

	/**
	 * item単位のauto marginを配置コーディネータ(flex §8.1)が所有するか。
	 * flex itemのauto marginはFlexBuilderが行の自由空間から解決して
	 * 線方向位置({@code flexLineOffset})・cross offsetへ焼き込み済みで、
	 * marginの絶対値(amargin)は0のまま運ばれる。ページ跨ぎ継続断片の
	 * restyle再構築が{@code calculateSize}のブロック則(§10.3.3)で
	 * 再解決すると、(1)焼き込み済みの位置へマージンをもう一度加算し、
	 * (2){@code restoreExtents}の枠控除が肥大して内寸が世代ごとに
	 * 2×margin縮む——固定幅+{@code margin:0 auto}のflex item(本文カラム)が
	 * ページを追うごとに右へずれながら1文字幅まで潰れた
	 * (asahi.com記事ページ、2026-08-27)。trueのとき、
	 * {@code calculateSize}はAUTOのmargin辺を再解決せず運ばれた
	 * 絶対値のまま使う。
	 */
	public boolean coordinatorOwnsAutoMargins() {
		return false;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbstractStaticPos getStaticPos() {
		return this.pos;
	}

	public final FlowPos getFlowPos() {
		return this.pos;
	}

	public final void shrinkToFit(LayoutStack layoutStack, IntrinsicSizes sizes, boolean table) {
		super.shrinkToFit(layoutStack, sizes, table);
		final AbstractContainerBox containerBox;
		if (!table) {
			return;
		}
		// テーブル
		BlockBuilder builder = (BlockBuilder) layoutStack;
		containerBox = builder.getFlow(builder.getFlowCount() - 2).box;

		Align align = this.resolvedAlign;
		if (containerBox.getBlockParams().flow.isVertical()) {
			// 縦書き
			if (align == Align.START) {
				Insets margin = this.getBlockParams().frame.margin;
				if (margin.getTopType() == LengthType.AUTO) {
					if (margin.getBottomType() == LengthType.AUTO) {
						align = Align.CENTER;
					} else {
						align = Align.END;
					}
				}
			}
			final double remainder = containerBox.getLineSize() - this.height;
			switch (align) {
			case Align.START:
				this.frame.margin.bottom = remainder;
				break;
			case Align.END:
				this.frame.margin.top = remainder;
				break;
			case Align.CENTER:
				this.frame.margin.top = this.frame.margin.bottom = remainder / 2;
				break;
			}
			// 幅を固定
			this.size = Dimension.create(0, this.height, LengthType.AUTO, LengthType.ABSOLUTE);
		} else {
			// 横書き
			if (align == Align.START) {
				Insets margin = this.getBlockParams().frame.margin;
				if (margin.getLeftType() == LengthType.AUTO) {
					if (margin.getRightType() == LengthType.AUTO) {
						align = Align.CENTER;
					} else {
						align = Align.END;
					}
				}
			}
			final double remainder = containerBox.getLineSize() - this.width;
			// **包含ブロックより広い箱では始端に揃える**（2026-08-03）。
			//
			// CSS 2.1 §10.3.3: 幅が指定されていて合計が包含ブロックを超える場合、
			// direction: ltr では margin-right の指定が無視される——箱は始端に揃い、
			// 終端側へ溢れる。従来は余りを機械的に2で割っていたため、余りが負の
			// ときに**負の始端マージン**ができ、内容の左半分が紙の外へ出て
			// 切れていた。固定幅の版面を margin: 0 auto で中央寄せする作りは
			// 実地で極めて多い（総務省統計局のページを取り込んだ第3波で発覚、PLAN §3）。
			// 紙幅より広い版面はどのみち溢れるが、**始端側を守れば読める**。
			if (remainder < 0) {
				align = Align.START;
			}
			switch (align) {
			case Align.START:
				this.frame.margin.right = remainder;
				break;
			case Align.END:
				this.frame.margin.left = remainder;
				break;
			case Align.CENTER:
				this.frame.margin.left = this.frame.margin.right = remainder / 2;
				break;
			}
			// 幅を固定
			this.size = Dimension.create(this.width, 0, LengthType.ABSOLUTE, LengthType.AUTO);
		}
		this.resolvedAlign = align;
	}

	/**
	 * {@code aspect-ratio}で、確定した行方向内寸からページ方向内寸を決めます
	 * (2026-08-29。flex/gridのitemが行方向の確定寸法を受け取った直後、本文bindの前に呼ぶ——G7でFlexItemBoxから引き上げた)。
	 * ページ方向が絶対長で指定されているとき・比率指定が無いときは何も
	 * しない。内容が比率高より高いときは{@code overflow:visible}なら伸びる
	 * ({@code minPageAxis}=比率高、{@code maxPageAxis}は可視のとき無制限)。
	 */
	public void applyAspectRatio(final double lineExtent) {
		final BlockParams params = this.getBlockParams();
		if (!(params.aspectRatio > 0) || this.size.getPageType(params.flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			return;
		}
		final double page = this.aspectRatioPageExtent(lineExtent);
		if (net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(page)) {
			return;
		}
		if (params.flow.isVertical()) {
			this.width = page;
		} else {
			this.height = page;
		}
		this.minPageAxis = page;
		if (params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
			this.maxPageAxis = page;
		}
		this.specifiedPageAxis = true;
	}

	/**
	 * <b>指定されたページ方向内寸を自己適用します</b>(G7、2026-08-29)。
	 *
	 * <p>
	 * takeoverしたitem箱({@code GridItemBox})は{@code calculateSize}を
	 * 通らないので、{@code specifiedPageAxis}が立たない。立てずに高さだけ
	 * 入れると、断片化({@link net.zamasoft.foliojet.layout.fragment.FragmentState})が
	 * 「指定寸法ではない」と見なして<b>継続断片へ元の指定高をそのまま
	 * 渡す</b>——行分割された固定高itemの継続が残余でなく全高になる
	 * (files/unittest/0500-grid/row-split-carry.htmlの2ページ目で実測)。
	 * </p>
	 */
	public final void applySpecifiedPageAxis(final double pageExtent) {
		this.setPageAxis(pageExtent);
		this.specifiedPageAxis = true;
	}

	public final void setPageAxis(final double newSize) {
		this.contentSize = Math.max(this.contentSize, newSize);

		if (this.params.flow.isVertical()) {
			// 縦書き
			if (newSize <= this.width) {
				return;
			}
			if ((this.isSpecifiedPageSize() || this.getColumnCount() > 1) && newSize < this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		} else {
			// 横書き
			if (newSize == this.height) {
				return;
			}
			if ((this.isSpecifiedPageSize() || this.getColumnCount() > 1) && newSize < this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
	}

	/**
	 * 行方向のmin/max指定値(box-sizingスケール)を内容寸法のスケールへ
	 * 換算します(2026-08-29)。border-boxなら行方向の境界+パディングを引く。
	 * 呼び出し側でパディングは解決済みであること(computePaddingsの後)。
	 */
	private double lineMinMaxToContent(final double specified, final boolean lineIsHeight) {
		if (this.params.boxSizing != BoxSizingMode.BORDER_BOX) {
			return specified;
		}
		return Math.max(0, specified - (lineIsHeight ? this.frame.getBorderHeight() : this.frame.getBorderWidth()));
	}

	public void calculateSize(LayoutStack layoutStack, double xmargin, double lineSize) {
		final AbstractContainerBox containerBox = layoutStack.getFlowBox();
		final BlockParams cParams = containerBox.getBlockParams();
		// Flexの中立wrapperが行方向の寸法指定を引き取っている場合、直下の
		// 子は行方向を充填(auto)として解決する(2026-08-08——wrapperと
		// 子の双方が%を解決すると二重適用になる。FlexItemBox参照)
		final boolean neutralLineFill = containerBox instanceof FlexItemBox item && item.isNeutralLineFill();
		if (this.params.flow.isVertical()) {
			// 縦書きのフロー
			this.specifiedPageAxis = this.size.getWidthType() == LengthType.ABSOLUTE
					|| (this.size.getWidthType().needsReference() && containerBox.isSpecifiedPageSize());
		} else {
			// 横書きのフロー
			this.specifiedPageAxis = this.size.getHeightType() == LengthType.ABSOLUTE
					|| (this.size.getHeightType().needsReference() && containerBox.isSpecifiedPageSize());
		}

		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		Insets margin = this.frame.frame.margin;
		AbsoluteInsets amargin = this.frame.margin;
		double marginLeft, marginRight, marginTop, marginBottom;

		//
		// ■ 静的配置または相対配置の行方向幅の計算
		//

		// 行方向幅の計算
		double minWidth = LayoutUtils.NONE, maxWidth = LayoutUtils.NONE, minHeight = LayoutUtils.NONE,
				maxHeight = LayoutUtils.NONE;
		if (cParams.flow.isVertical()) {
			// 縦書きのフロー
			marginLeft = amargin.left;
			marginRight = amargin.right;
			this.height = neutralLineFill ? LayoutUtils.NONE
					: LayoutUtils.computeDimensionHeight(this.size, lineSize);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(this.height)) {
				this.height -= this.frame.getBorderHeight();
			}
			if (LayoutUtils.isNone(this.height) && !neutralLineFill && this.params.aspectRatio > 0) {
				// aspect-ratio: 行方向(高さ)autoでページ方向(幅)が確定なら
				// 比率で高さを決める(2026-08-29)
				final double definite = this.definitePageExtentForRatio(
						this.isSpecifiedPageSize() ? containerBox.getInnerWidth() : LayoutUtils.NONE);
				if (!LayoutUtils.isNone(definite)) {
					this.height = this.aspectRatioLineExtent(definite);
				}
			}
			marginTop = marginBottom = 0;
			for (int state = 0; state < 2; ++state) {
				if (!LayoutUtils.isNone(this.height)) {
					// 固定幅の場合(auto marginは再解決しない——
					// coordinatorOwnsAutoMarginsの説明を参照)
					final boolean ownedMargins = this.coordinatorOwnsAutoMargins();
					marginTop = margin.getTopType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.top;
					marginBottom = margin.getBottomType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.bottom;
					if (LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
						// 上下のマージンを同じにする
						marginTop = marginBottom = (lineSize - this.height - this.frame.getFrameHeight()) / 2.0;
					} else if (LayoutUtils.isNone(marginTop)) {
						// 上のマージンが不確定
						marginTop = lineSize - this.height - this.frame.getFrameHeight();
					} else if (LayoutUtils.isNone(marginBottom)) {
						// 下のマージンが不確定
						marginBottom = lineSize - marginBottom - this.frame.getFrameHeight();
					} else {
						// 制限しすぎ
												switch (this.resolvedAlign) {
						case Align.START:
							// 上寄せ
							marginBottom = 0;
							break;
						case Align.END:
							// 下寄せ
							marginTop += lineSize - this.height - this.frame.getFrameHeight();
							break;
						case Align.CENTER:
							// 中央
							double remainder = lineSize - this.height - this.frame.getFrameHeight();
							remainder /= 2.0;
							marginTop += remainder;
							marginBottom += remainder;
							break;
						default:
							throw new IllegalStateException();
						}
					}
				} else {
					// 自動幅の場合
					marginTop = amargin.top;
					marginBottom = amargin.bottom;
					this.height = lineSize - this.frame.getFrameHeight();
				}
				switch (state) {
				case 0:
					maxHeight = LayoutUtils.computeDimensionHeight(this.params.maxSize, lineSize);
					if (LayoutUtils.isNone(maxHeight)) {
						maxHeight = Double.MAX_VALUE;
					} else {
						// min/max-heightはbox-sizingのスケール。this.heightは
						// 内寸なので、border-boxなら枠を引いてから比べる
						// (2026-08-29。横書き側の同名処理を参照)
						maxHeight = this.lineMinMaxToContent(maxHeight, true);
						if (this.height > maxHeight) {
							this.height = maxHeight;
							continue;
						}
					}
					state = 1;
				case 1:
					minHeight = this.lineMinMaxToContent(LayoutUtils.computeDimensionHeight(this.minSize, lineSize), true);
					if (this.height < minHeight) {
						this.height = minHeight;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(minHeight);
			assert !LayoutUtils.isNone(maxHeight);
			switch (this.minSize.getWidthType()) {
			// min/maxの%の基準は包含ブロックのpage軸内寸——判定は容器側の
			// 確定性(containerBox)で行う。従来はthis側(sizeの確定性)を見て
			// おり、width:66pt(確定)の箱のmax-width:90%が未確定の親幅(0)に
			// 対して0へ潰れた(2026-08-23、v2掲過 seed 2006804)
			case RELATIVE:
				if (containerBox.isSpecifiedPageSize()) {
					minWidth = this.minSize.getWidth() * containerBox.getInnerWidth();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				minWidth = 0;
				break;
			case ABSOLUTE:
				minWidth = this.minSize.getWidth();
				break;
			case MIXED:
				if (containerBox.isSpecifiedPageSize()) {
					minWidth = this.minSize.getWidth() + this.minSize.getWidthRatio() * containerBox.getInnerWidth();
					break;
				}
				minWidth = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.params.maxSize.getWidthType()) {
			case RELATIVE:
				if (containerBox.isSpecifiedPageSize()) {
					maxWidth = this.params.maxSize.getWidth() * containerBox.getInnerWidth();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				maxWidth = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxWidth = this.params.maxSize.getWidth();
				break;
			case MIXED:
				if (containerBox.isSpecifiedPageSize()) {
					maxWidth = this.params.maxSize.getWidth()
							+ this.params.maxSize.getWidthRatio() * containerBox.getInnerWidth();
					break;
				}
				maxWidth = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			// ページ方向のmin/max指定はbox-sizingのスケール。this.width(内寸)と
			// 比べる前に、border-boxなら枠を引いて内寸スケールへ揃える
			// (2026-08-29)。従来はminPageAxis/maxPageAxisが枠込みのまま残り、
			// setPageAxisが内容高を枠込みの下限まで押し上げていた(min-height:
			// 40px+padding-block:8pxのborder-boxが56pxに)
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				minWidth = Math.max(0, minWidth - this.getFrame().getBorderWidth());
				if (maxWidth != Double.MAX_VALUE) {
					maxWidth = Math.max(0, maxWidth - this.getFrame().getBorderWidth());
				}
			}
			switch (this.size.getWidthType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					this.width = this.size.getWidth() * containerBox.getInnerWidth();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.width = Math.max(0, this.width - this.getFrame().getBorderWidth());
					}
					this.width = Math.max(this.width, minWidth);
					this.width = Math.min(this.width, maxWidth);
					minWidth = maxWidth = this.width;
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				if (!this.params.flow.isVertical()) {
					// 横書きのボックス
					this.width = layoutStack.getFixedWidth() - this.frame.getFrameWidth();
				} else {
					this.width = 0;
				}
				this.width = Math.max(this.width, minWidth);
				break;
			case ABSOLUTE:
				this.width = this.size.getWidth();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					this.width = Math.max(0, this.width - this.getFrame().getBorderWidth());
				}
				this.width = Math.max(this.width, minWidth);
				this.width = Math.min(this.width, maxWidth);
				minWidth = maxWidth = this.width;
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					this.width = this.size.getWidth() + this.size.getWidthRatio() * containerBox.getInnerWidth();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.width = Math.max(0, this.width - this.getFrame().getBorderWidth());
					}
					this.width = Math.max(this.width, minWidth);
					this.width = Math.min(this.width, maxWidth);
					minWidth = maxWidth = this.width;
					break;
				}
				if (!this.params.flow.isVertical()) {
					this.width = layoutStack.getFixedWidth() - this.frame.getFrameWidth();
				} else {
					this.width = 0;
				}
				this.width = Math.max(this.width, minWidth);
				break;
			default:
				throw new IllegalStateException();
			}
			if (!this.specifiedPageAxis && this.params.aspectRatio > 0) {
				// aspect-ratio: ページ方向(幅)がautoなら高さから比率で決める
				// (2026-08-29。横書き側の同名処理と対)
				double page = this.aspectRatioPageExtent(this.height);
				page = Math.max(page, minWidth);
				page = Math.min(page, maxWidth);
				this.width = page;
				minWidth = page;
				if (this.params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
					maxWidth = page;
				}
				this.specifiedPageAxis = true;
			}
			marginTop += xmargin;
		} else {
			// 横書きのフロー
			marginTop = amargin.top;
			marginBottom = amargin.bottom;
			this.width = neutralLineFill ? LayoutUtils.NONE
					: LayoutUtils.computeDimensionWidth(this.size, lineSize);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(this.width)) {
				this.width -= this.frame.getBorderWidth();
			}
			if (LayoutUtils.isNone(this.width) && !neutralLineFill && this.params.aspectRatio > 0) {
				// aspect-ratio: 幅autoで高さが確定なら幅=高さ×比率(2026-08-29、
				// css-sizing-4 §5.1。height:40px;aspect-ratio:2のブロックは
				// 幅80pxになり、残りはmargin側へ)
				final double definite = this.definitePageExtentForRatio(
						this.isSpecifiedPageSize() ? containerBox.getInnerHeight() : LayoutUtils.NONE);
				if (!LayoutUtils.isNone(definite)) {
					this.width = this.aspectRatioLineExtent(definite);
				}
			}
			marginLeft = marginRight = 0;
			for (int state = 0; state < 2; ++state) {
				if (!LayoutUtils.isNone(this.width)) {
					// 固定幅の場合(auto marginは再解決しない——
					// coordinatorOwnsAutoMarginsの説明を参照)
					final boolean ownedMargins = this.coordinatorOwnsAutoMargins();
					marginLeft = margin.getLeftType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.left;
					marginRight = margin.getRightType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.right;
					final double autoRemainder = lineSize - this.width - this.frame.getFrameWidth();
					if (autoRemainder < 0 && (LayoutUtils.isNone(marginLeft) || LayoutUtils.isNone(marginRight))) {
						// **包含ブロックより広い箱は始端に揃える**（2026-08-03）。
						// CSS 2.1 §10.3.3: 幅が指定されていて合計が包含ブロックを超える場合、
						// direction: ltr では margin-right の指定が無視される——箱は始端に
						// 揃い、終端側へ溢れる。従来は余りを機械的に2で割っており、
						// 余りが負のときに**負の左マージン**ができて内容の左半分が
						// 紙の外へ出ていた。固定幅の版面を margin: 0 auto で中央寄せする
						// 作りは実地で極めて多い（総務省統計局のページで発覚、PLAN §3）。
						marginLeft = LayoutUtils.isNone(marginLeft) ? 0 : marginLeft;
						marginRight = LayoutUtils.isNone(marginRight) ? 0 : marginRight;
					} else if (LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
						// 左右のマージンを同じにする
						marginLeft = marginRight = autoRemainder / 2.0;
					} else {
						if (LayoutUtils.isNone(marginLeft) && !LayoutUtils.isNone(marginRight)) {
							// 左が不確定
							marginLeft = lineSize - this.width - this.frame.getFrameWidth();
						} else if (LayoutUtils.isNone(marginRight)) {
							// 右が不確定
							marginRight = lineSize - this.width - this.frame.getFrameWidth();
						} else {
							// 制限しすぎ
														switch (this.resolvedAlign) {
							case Align.START:
								// 左寄せ
								marginRight = 0;
								break;
							case Align.END:
								// 右寄せ
								marginLeft += lineSize - this.width - this.frame.getFrameWidth();
								break;
							case Align.CENTER:
								// 中央
								double remainder = lineSize - this.width - this.frame.getFrameWidth();
								remainder /= 2.0;
								marginLeft += remainder;
								marginRight += remainder;
								break;
							default:
								throw new IllegalStateException();
							}
						}
					}
				} else {
					// 自動幅の場合
					marginLeft = amargin.left;
					marginRight = amargin.right;
					this.width = lineSize - this.frame.getFrameWidth();
				}
				switch (state) {
				case 0:
					maxWidth = LayoutUtils.computeDimensionWidth(this.params.maxSize, lineSize);
					if (LayoutUtils.isNone(maxWidth)) {
						maxWidth = Double.MAX_VALUE;
					} else {
						// min/max-widthはbox-sizingのスケール。this.widthは内寸
						// なので、border-boxなら境界+パディングを引いてから比べる
						// (2026-08-29)。従来は引いておらず、`min-width:100px;
						// padding-inline:8px; box-sizing:border-box`のピル(flex item
						// 内のgrid)が内寸100+枠16=116pxに広がった。通常フローの
						// ブロックはAbstractStaticBlockBoxでなくここで幅が決まる
						// (BlockBuilder.calculateSize)ため、そちらの換算だけでは
						// 効かなかった(0510-flex/min-width-nested-container)
						maxWidth = this.lineMinMaxToContent(maxWidth, false);
						if (this.width > maxWidth) {
							this.width = maxWidth;
							continue;
						}
					}
					state = 1;
				case 1:
					minWidth = this.lineMinMaxToContent(LayoutUtils.computeDimensionWidth(this.minSize, lineSize), false);
					if (this.width < minWidth) {
						this.width = minWidth;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(minWidth);
			assert !LayoutUtils.isNone(maxWidth);
			switch (this.minSize.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					minHeight = this.minSize.getHeight() * containerBox.getInnerHeight();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				minHeight = 0;
				break;
			case ABSOLUTE:
				minHeight = this.minSize.getHeight();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					minHeight = this.minSize.getHeight() + this.minSize.getHeightRatio() * containerBox.getInnerHeight();
					break;
				}
				minHeight = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.params.maxSize.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					maxHeight = this.params.maxSize.getHeight() * containerBox.getInnerHeight();
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				maxHeight = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxHeight = this.params.maxSize.getHeight();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					maxHeight = this.params.maxSize.getHeight()
							+ this.params.maxSize.getHeightRatio() * containerBox.getInnerHeight();
					break;
				}
				maxHeight = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			// ページ方向のmin/maxをborder-boxなら内寸スケールへ(2026-08-29。
			// 縦書き側の同名処理を参照)
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				minHeight = Math.max(0, minHeight - this.getFrame().getBorderHeight());
				if (maxHeight != Double.MAX_VALUE) {
					maxHeight = Math.max(0, maxHeight - this.getFrame().getBorderHeight());
				}
			}
			switch (this.size.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					this.height = this.size.getHeight() * containerBox.getInnerHeight();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.height = Math.max(0, this.height - this.getFrame().getBorderHeight());
					}
					this.height = Math.max(this.height, minHeight);
					this.height = Math.min(this.height, maxHeight);
					minHeight = this.height;
					maxHeight = this.height;
					break;
				}
				// isSpecifiedPageSize()がfalseならAUTOへフォールスルー(既存の意図的な仕様)
			case AUTO:
				if (this.params.flow.isVertical()) {
					// 縦書きのボックス
					this.height = layoutStack.getFixedHeight() - this.frame.getFrameHeight();
				} else {
					this.height = 0;
				}
				this.height = Math.max(this.height, minHeight);
				break;
			case ABSOLUTE:
				this.height = this.size.getHeight();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					this.height = Math.max(0, this.height - this.getFrame().getBorderHeight());
				}
				this.height = Math.max(this.height, minHeight);
				this.height = Math.min(this.height, maxHeight);
				minHeight = this.height;
				// 指定幅に固定する
				maxHeight = this.height;
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					this.height = this.size.getHeight() + this.size.getHeightRatio() * containerBox.getInnerHeight();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.height = Math.max(0, this.height - this.getFrame().getBorderHeight());
					}
					this.height = Math.max(this.height, minHeight);
					this.height = Math.min(this.height, maxHeight);
					minHeight = this.height;
					maxHeight = this.height;
					break;
				}
				if (this.params.flow.isVertical()) {
					this.height = layoutStack.getFixedHeight() - this.frame.getFrameHeight();
				} else {
					this.height = 0;
				}
				this.height = Math.max(this.height, minHeight);
				break;
			default:
				throw new IllegalStateException();
			}
			if (!this.specifiedPageAxis && this.params.aspectRatio > 0) {
				// aspect-ratio: 高さがauto(または基準未確定の%)なら幅から比率で
				// 決める(2026-08-29)。通常フローのブロックの幅は常に確定
				// しているので、これがサムネイルの16:9を決める本線。内容が
				// 比率高より高いときはoverflow:visibleなら内容に合わせて伸びる
				// (min-height:auto=内容寸法の近似。setPageAxisはminPageAxis
				// 以上・maxPageAxis以下へ丸める)
				double page = this.aspectRatioPageExtent(this.width);
				page = Math.max(page, minHeight);
				page = Math.min(page, maxHeight);
				this.height = page;
				minHeight = page;
				if (this.params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
					maxHeight = page;
				}
				this.specifiedPageAxis = true;
			}
			marginLeft += xmargin;
		}
		if (this.params.flow.isVertical()) {
			this.minPageAxis = minWidth;
			this.maxPageAxis = maxWidth;
		} else {
			this.minPageAxis = minHeight;
			this.maxPageAxis = maxHeight;
		}
		assert !LayoutUtils.isNone(marginTop);
		assert !LayoutUtils.isNone(marginRight);
		assert !LayoutUtils.isNone(marginBottom);
		assert !LayoutUtils.isNone(marginLeft);
		this.frame.margin.top = marginTop;
		this.frame.margin.right = marginRight;
		this.frame.margin.bottom = marginBottom;
		this.frame.margin.left = marginLeft;
		if (!LayoutUtils.isNone(this.restyleLineWidth)) {
			this.width = this.restyleLineWidth - this.frame.getFrameWidth();
		}
		if (!LayoutUtils.isNone(this.restyleLineHeight)) {
			this.height = this.restyleLineHeight - this.frame.getFrameHeight();
		}
		this.clearRestyleLineExtent();
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final double getContentSize() {
		return this.contentSize;
	}

	/**
	 * aspect-ratioの逆算(ページ方向確定→行方向)に使う、ページ方向の
	 * 確定content-box寸法です(2026-08-29)。絶対長、または基準が確定した
	 * %・混合値のときだけ値を返し、それ以外はNONE。
	 *
	 * @param percentBase %の基準(未確定ならNONE)
	 */
	private double definitePageExtentForRatio(final double percentBase) {
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.params.flow;
		final LengthType type = this.size.getPageType(flow);
		double page;
		switch (type) {
		case ABSOLUTE:
			page = this.size.getPageLength(flow);
			break;
		case RELATIVE:
			if (LayoutUtils.isNone(percentBase)) {
				return LayoutUtils.NONE;
			}
			page = this.size.getPageLength(flow) * percentBase;
			break;
		case MIXED:
			if (LayoutUtils.isNone(percentBase)) {
				return LayoutUtils.NONE;
			}
			page = this.size.getPageLength(flow) + this.size.getPageRatio(flow) * percentBase;
			break;
		default:
			return LayoutUtils.NONE;
		}
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			page -= this.frame.getBorderPageExtent(flow);
		}
		return Math.max(0, page);
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(.5f, 1f, .5f));
			drawer.visitDrawable(drawable, x, y);
		}

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		if (this.getFlowPos().offset != null || this.params.isStackingContext()) {
			this.frames(pageBox, drawer, clip, transform, x, y);
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		// 解決済み整列は断片間で共有される状態(旧実装の pos 書き戻し相当)。
		// レシピは値をキャプチャし、this を保持しない
		final Align resolvedAlign = this.resolvedAlign;
		// 固有寸法キーワードで決めた行方向幅は継続断片へ確定値として渡す
		// (2026-08-29)。断片のsizeは行方向AUTOのままなので、restyle→
		// startFlowBlock→calculateSizeが包含ブロック幅へ再解決してしまう。
		// 解決値は内容全体の実測で断片間で共通なので、ABSOLUTEに固定する
		final Dimension resolvedLine = params.hasIntrinsicLine() ? this.resolvedLineSize() : null;
		return (state, container) -> {
			final Dimension nextSize = resolvedLine == null ? state.nextSize()
					: withLine(state.nextSize(), resolvedLine, params.flow);
			final FlowBlockBox next = new FlowBlockBox(params, pos, nextSize, state.nextMinSize(),
					state.nextFrame(), container);
			next.resolvedAlign = resolvedAlign;
			return next;
		};
	}

	/**
	 * 行方向の使用寸法を指定寸法(ABSOLUTE)として表したDimensionを返します
	 * (2026-08-29、{@link #fragmentRecipe}用)。border-box指定なら
	 * calculateSizeが差し引くぶんを足しておく。
	 */
	private Dimension resolvedLineSize() {
		final WritingMode flow = this.params.flow;
		final double line = (flow.isVertical() ? this.height : this.width)
				+ (this.params.boxSizing == BoxSizingMode.BORDER_BOX ? this.frame.getBorderLineExtent(flow) : 0);
		return Dimension.create(line, line, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
	}

	/** {@code page}のページ方向に{@code line}の行方向を合成します。 */
	private static Dimension withLine(final Dimension page, final Dimension line, final WritingMode flow) {
		return flow.isVertical()
				? Dimension.create(page.getWidth(), page.getWidthRatio(), line.getHeight(), 0, page.getWidthType(),
						LengthType.ABSOLUTE)
				: Dimension.create(line.getWidth(), 0, page.getHeight(), page.getHeightRatio(), LengthType.ABSOLUTE,
						page.getHeightType());
	}

	public final void restyle(final BlockBuilder builder, final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		builder.startFlowBlock(this);
		super.restyle(builder, shape);
		if (!(shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.Closed)) {
			// 開いた継続: 末尾はこの箱の中で live が続く
			return;
		}
		if (!builder.hasOpenFlow()) {
			// 閉部分木の再生中にcontextFlowをownerとする改段が起きると、
			// pruneFlowStackTo()が内側を全て消費し、継続側にもopen flowを
			// 積み直さない場合がある。そのときだけ古い呼出しフレームから
			// 二重に閉じない。継続が別identityで同じ深度を積み直した場合は、
			// その新しい最上位を通常どおり1段閉じる(seed 4540)。
			return;
		}
		builder.endFlowBlock();
	}

	public boolean avoidBreakBefore() {
		if (this.getFlowPos().pageBreakBefore == PageBreakMode.AVOID) {
			return true;
		}
		return this.container.avoidBreakBefore();
	}

	public boolean avoidBreakAfter() {
		if (this.getFlowPos().pageBreakAfter == PageBreakMode.AVOID) {
			return true;
		}
		return this.container.avoidBreakAfter();
	}
}
