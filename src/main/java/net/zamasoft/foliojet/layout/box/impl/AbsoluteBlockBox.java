package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.sizing.AbsoluteSizing;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * ブロックボックスの実装です
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AbsoluteBlockBox extends AbstractBlockBox implements IAbsoluteBox {
	protected final AbsolutePos pos;

	public AbsoluteBlockBox(BlockParams params, AbsolutePos pos) {
		super(params);
		this.pos = pos;
	}

	protected AbsoluteBlockBox(BlockParams params, AbsolutePos pos, Dimension size, Dimension minSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbsolutePos getAbsolutePos() {
		return this.pos;
	}

	public final boolean isSpecifiedPageSize() {
		return true;
	}

	/**
	 * 実測ビルダーの保持(fail closed経路)。seal不適格な本文のみ——適格な
	 * 本文は{@link #deferredBind}へ持ち出され、ビルダー(layoutStack鎖・
	 * 計測器)をページ末のbindまで引き留めない(E-6増分4e、2026-07-24)。
	 */
	private TwoPassBlockBuilder builder;

	/**
	 * seal済み本文の持ち出し形(E-6増分4e)。{@code IntrinsicSizes}の
	 * スナップショット+LayoutSource範囲+保持リースのみを持つ。
	 */
	private TwoPassBlockBuilder.DeferredBind deferredBind;

	public final void prepareBind(TwoPassBlockBuilder builder) {
		// E-6増分4e: 適格(seal済み)ならDeferredBindへ置換、不適格は
		// 従来どおりビルダー保持(fail closed)
		final TwoPassBlockBuilder.DeferredBind deferred = builder.detachDeferredBind();
		if (deferred != null) {
			this.deferredBind = deferred;
		} else {
			this.builder = builder;
		}
	}

	/**
	 * このボックスがどのcontext builderにも係留されておらず、bind予約
	 * (ビルダー保持/DeferredBind)も持たないかを返します(absolute吸収=
	 * codex増分9、2026-07-30)。TwoPass録画中のabsoluteはcontextがTwoPassの
	 * ためprepareBind/addBound/inline登録を一切通らず、常にこの状態——
	 * 親のrange化はこの証明の上でのみボックスを吸収できる(deferredBind
	 * 保持中のボックスを吸収するとリースが誰にもbind/closeされなくなる)。
	 */
	public final boolean isUnattachedForParentRange() {
		return this.builder == null && this.deferredBind == null;
	}

	public final void shrinkToFit(IFramedBox containerBox, IntrinsicSizes sizes) {
		final double minLineAxis = sizes.minContent(), maxLineAxis = sizes.maxContent();
		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();
		{
			double lineAxis;
			if (this.params.flow.isVertical()) {
				// 縦書き
				lineAxis = cHeight;
			} else {
				// 横書き
				lineAxis = cWidth;
			}

			//
			// ■ パディングの計算
			//
			LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineAxis);

			//
			// ■ マージンの計算
			//
			LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineAxis);
		}

		final Insets margin = this.frame.frame.margin;
		final AbsoluteInsets amargin = this.frame.margin;
		final AbsolutePos pos = this.getAbsolutePos();
		final WritingMode flow = this.params.flow;
		final boolean vertical = flow.isVertical();
		final double cLine = vertical ? cHeight : cWidth;
		//
		// ■ 絶対配置または固定配置の行方向幅の計算 (CSS2.1 10.3.7)
		//
		double size = LayoutUtils.computeDimensionLine(this.size, flow, cLine);
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(size)) {
			size -= this.frame.getBorderLineExtent(flow);
		}
		final AbsoluteSizing.Result result = AbsoluteSizing.resolve(new AbsoluteSizing.Input( //
				cLine, size, //
				LayoutUtils.computeDimensionLine(this.params.maxSize, flow, cLine), //
				LayoutUtils.computeDimensionLine(this.minSize, flow, cLine), //
				vertical ? LayoutUtils.computeInsetsTop(pos.location, cLine)
						: LayoutUtils.computeInsetsLeft(pos.location, cLine), //
				vertical ? LayoutUtils.computeInsetsBottom(pos.location, cLine)
						: LayoutUtils.computeInsetsRight(pos.location, cLine), //
				vertical ? amargin.top : amargin.left, //
				vertical ? amargin.bottom : amargin.right, //
				(vertical ? margin.getTopType() : margin.getLeftType()) == LengthType.AUTO, //
				(vertical ? margin.getBottomType() : margin.getRightType()) == LengthType.AUTO, //
				this.frame.getFrameLineExtent(flow), //
				minLineAxis, maxLineAxis));
		// 交差軸(ページ方向)のマージン: auto は未解決(NONE)のままにする
		final double crossStart = (vertical ? margin.getLeftType() : margin.getTopType()) == LengthType.AUTO
				? LayoutUtils.NONE
				: (vertical ? amargin.left : amargin.top);
		final double crossEnd = (vertical ? margin.getRightType() : margin.getBottomType()) == LengthType.AUTO
				? LayoutUtils.NONE
				: (vertical ? amargin.right : amargin.bottom);
		assert !LayoutUtils.isNone(result.insetStart());
		if (vertical) {
			this.offsetY = result.insetStart();
			this.frame.margin.top = result.marginStart();
			this.frame.margin.bottom = result.marginEnd();
			this.frame.margin.left = crossStart;
			this.frame.margin.right = crossEnd;
			this.height = result.size();
			this.width = 0;
		} else {
			this.offsetX = result.insetStart();
			this.frame.margin.left = result.marginStart();
			this.frame.margin.right = result.marginEnd();
			this.frame.margin.top = crossStart;
			this.frame.margin.bottom = crossEnd;
			this.width = result.size();
			this.height = 0;
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final void finishLayoutSelf(final IFramedBox containerBox) {
		if (this.deferredBind != null) {
			// E-6増分4e: seal済み範囲からのSegmentExecutor駆動bind。
			// sizesは模倣計測のスナップショット(現行のintrinsicSizesMeasured()
			// と同値——DeferredBindのjavadoc参照)。リースはbindのfinallyで
			// 解放される
			this.shrinkToFit(containerBox, this.deferredBind.sizes());
			final BlockBuilder absoluteBuilder = new BlockBuilder(this.deferredBind.pageContext(), this);
			this.deferredBind.bind(absoluteBuilder);
			absoluteBuilder.close();
			this.deferredBind = null;
		} else if (this.builder != null) {
			this.shrinkToFit(containerBox, this.builder.intrinsicSizesMeasured());
			final BlockBuilder absoluteBuilder = new BlockBuilder(this.builder.getPageContext(), this);
			this.builder.bind(absoluteBuilder);
			absoluteBuilder.close();
			this.builder = null;
		}

		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();

		// 位置の計算
		final AbsolutePos pos = this.getAbsolutePos();
		//
		// ■ 絶対配置または固定配置のページ方向幅の計算 (CSS2.1 10.6.4)
		// 縦横の物理鏡像は AbsoluteSizing.resolvePage に統合(忠実移植)
		//
		final AbsoluteInsets margin = this.frame.margin;
		final AbsoluteInsets padding = this.frame.padding;
		final RectBorder border = this.frame.frame.border;
		final boolean vertical = this.params.flow.isVertical();
		final double cPage = vertical ? cWidth : cHeight;
		final AbsoluteSizing.PageResult result = AbsoluteSizing.resolvePage(new AbsoluteSizing.PageInput( //
				cPage, //
				vertical ? LayoutUtils.computeDimensionWidth(this.size, cWidth)
						: LayoutUtils.computeDimensionHeight(this.size, cHeight), //
				vertical ? LayoutUtils.computeDimensionWidth(this.params.maxSize, cWidth)
						: LayoutUtils.computeDimensionHeight(this.params.maxSize, cHeight), //
				vertical ? LayoutUtils.computeDimensionWidth(this.minSize, cWidth)
						: LayoutUtils.computeDimensionHeight(this.minSize, cHeight), //
				vertical ? LayoutUtils.computeInsetsLeft(pos.location, cWidth)
						: LayoutUtils.computeInsetsTop(pos.location, cHeight), //
				vertical ? LayoutUtils.computeInsetsRight(pos.location, cWidth)
						: LayoutUtils.computeInsetsBottom(pos.location, cHeight), //
				vertical ? margin.left : margin.top, //
				vertical ? margin.right : margin.bottom, //
				// 内容実寸(旧実装の式を忠実に維持: 縦書き側は width 相当)
				vertical ? this.getWidth() - this.frame.getFrameWidth() : this.height, //
				vertical ? border.getFrameWidth() + padding.getFrameWidth()
						: border.getFrameHeight() + padding.getFrameHeight()));

		double size = result.size();
		assert !LayoutUtils.isNone(result.insetStart());
		assert !LayoutUtils.isNone(result.marginStart());
		assert !LayoutUtils.isNone(result.marginEnd());
		assert !LayoutUtils.isNone(size);
		if (vertical) {
			assert !LayoutUtils.isNone(margin.top);
			assert !LayoutUtils.isNone(margin.bottom);
			this.offsetX = result.insetStart();
			this.frame.margin.left = result.marginStart();
			this.frame.margin.right = result.marginEnd();
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				size -= this.frame.getBorderWidth();
			}
			this.width = size;
		} else {
			assert !LayoutUtils.isNone(margin.right);
			assert !LayoutUtils.isNone(margin.left);
			this.offsetY = result.insetStart();
			this.frame.margin.top = result.marginStart();
			this.frame.margin.bottom = result.marginEnd();
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				size -= this.frame.getBorderHeight();
			}
			this.height = size;
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final boolean isContextBox() {
		return true;
	}

	/**
	 * <b>解決されなかった余白は0として描く</b>(2026-08-05)。
	 *
	 * <p>
	 * {@link #finishLayoutSelf}が走らないまま描画へ届く絶対配置の箱がある。
	 * そのとき{@code frame.margin}は未解決(NONE=1.7e308)のままで、
	 * {@code AbstractBlockBox.pushDrawSteps}の
	 * {@code assert !isNone(y)}が落ち、<b>変換全体が失敗する</b>。
	 *
	 * <p>
	 * 実地コーパス第2波の {@code github-readme} がこれで、
	 * {@code .markdown-heading .anchor{position:absolute;top:50%;height:28px;
	 * display:flex;margin:auto}} という形。**GitHubのREADMEの見出しリンクは
	 * どのページにもある**ので、実利用者の入力として極めてありふれている。
	 *
	 * <p>
	 * <b>これは対症療法である。</b>本筋は「描画には届くのに
	 * {@code finishLayoutSelf}が走らない箱がある」という構造の取りこぼしを
	 * 塞ぐこと——絶対配置の箱は包含ブロックの{@code finishLayout}走査で
	 * 拾われるはずで、拾われない経路が残っている。ただし
	 * <b>変換を止めるより静的位置へ落とすほうが害が小さい</b>
	 * (絶対要件は「変換の失敗が無いこと」)。
	 *
	 * <p>
	 * <b>原因はページ分割である</b>(2026-08-06に計測で特定)。
	 * {@code github-readme} を計測すると:
	 * </p>
	 *
	 * <pre>
	 * 通常のページ分割 …… 未解決 16件
	 * 紙を5000mmにして1ページへ収める …… 未解決 **0件**
	 * </pre>
	 *
	 * <p>
	 * 同じ実行での内訳は、生成120・登録159・{@code finishLayoutSelf}実行151。
	 * <b>継続断片は無関係</b>(断片用の構築子は一度も呼ばれない)。
	 * つまり「そのページの{@code finishLayout}走査が終わったあとに登録された
	 * 絶対配置の箱」が取り残されている。{@code RootBuilder.finishLayout}は
	 * ページごとに{@code pageBox.finishLayout}を呼ぶので、走査後に前ページの
	 * 容器へ登録された箱は誰にも拾われない。
	 * </p>
	 *
	 * <p>
	 * <b>直すときの注意</b>: 単純に後から{@code finishLayoutSelf}を呼ぶだけでは
	 * 足りない。割合の解決に<b>正しい包含ブロック</b>が要る。
	 * </p>
	 *
	 * <p>
	 * <b>ここまで分かっていること</b>(2026-08-06、すべて計測):
	 * </p>
	 *
	 * <ul>
	 * <li>16件<b>すべてが{@code FlowContainer}の絶対配置一覧に登録済み</b>
	 * (テキスト箱経由ではない)</li>
	 * <li>その<b>容器が最後まで一度も走査されない</b>。容器に「走査済み」の
	 * 印を持たせて確かめた——印は最後まで付かなかった</li>
	 * <li>取り残された箱を持つのは、<b>{@code position:relative} の
	 * {@code <div>}}の{@code FlowBlockBox}</b>(GitHubの見出しラッパ
	 * {@code .markdown-heading})。つまり<b>包含ブロックそのもの</b>で、
	 * この箱も走査されていない</li>
	 * <li>それでも<b>描画には届く</b>。描画は
	 * {@code AbstractBlockBox.pushDrawSteps→container.pushDrawAbsolutes}、
	 * 寸法決めは{@code AbstractContainerBox.pushFinishLayoutChildren→
	 * container.pushFinishLayoutChildren}で、<b>同じ容器を別の道で辿る</b>。
	 * 片方だけ届かないので、<b>その容器を持つ箱が寸法決めの木から外れている</b></li>
	 * </ul>
	 *
	 * <p>
	 * <b>潰した仮説</b>: 「走査後にあとから登録された箱が取り残される」
	 * ——<b>誤り</b>。{@code Absolutes}側と{@code FlowContainer}側の両方で
	 * 「走査済みなら登録時に確定させる」を試したが、どちらも
	 * <b>16件のまま変わらなかった</b>。前者は走査時に絶対配置が無いと
	 * {@code Absolutes}が未生成で印を置けない、という別の穴も見つかったが、
	 * 直しても数は動かない。
	 * </p>
	 *
	 * <p>
	 * <b>次の一手</b>: この{@code <div>}の{@code Flow}が、どの容器の
	 * {@code flows}に入っているかを見る。描画には出て寸法決めに出ないので、
	 * <b>親の{@code flows}から外れたあとも描画木からは参照されている</b>形の
	 * はずである(ページを跨ぐ移動・再構築の経路が怪しい)。
	 * </p>
	 */
	private void resolveUnfinishedMargins() {
		final AbsoluteInsets margin = this.frame.margin;
		if (LayoutUtils.isNone(margin.top)) {
			margin.top = 0;
		}
		if (LayoutUtils.isNone(margin.bottom)) {
			margin.bottom = 0;
		}
		if (LayoutUtils.isNone(margin.left)) {
			margin.left = 0;
		}
		if (LayoutUtils.isNone(margin.right)) {
			margin.right = 0;
		}
		if (LayoutUtils.isNone(this.width)) {
			this.width = 0;
		}
		if (LayoutUtils.isNone(this.height)) {
			this.height = 0;
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		this.resolveUnfinishedMargins();
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		this.frames(pageBox, drawer, clip, transform, x, y);
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final AbsolutePos pos = this.getAbsolutePos();
		return (state, container) -> new AbsoluteBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
