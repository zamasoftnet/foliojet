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
import net.zamasoft.foliojet.layout.box.params.Fiducial;
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
		// 固有寸法キーワード(2026-08-29)。引数無しfit-contentはautoの
		// shrink-to-fit(CSS2.1 §10.3.7)そのものなのでAbsoluteSizingに任せ、
		// それ以外はここで長さへ解いて指定幅として渡す。min/maxの
		// fit-contentの上限は包含ブロック幅からフレームを引いた近似
		final double availableLine = cLine - this.frame.getFrameLineExtent(flow);
		final net.zamasoft.foliojet.layout.box.params.IntrinsicSize intrinsic = this.params.intrinsicLine;
		if (intrinsic != null && (intrinsic.kind() != net.zamasoft.foliojet.layout.box.params.IntrinsicSize.Kind.FIT_CONTENT
				|| intrinsic.hasArgument())) {
			size = this.resolveIntrinsicLine(intrinsic, minLineAxis, maxLineAxis, availableLine, cLine);
		}
		double maxLine = LayoutUtils.computeDimensionLine(this.params.maxSize, flow, cLine);
		if (this.params.intrinsicMaxLine != null) {
			maxLine = this.resolveIntrinsicLine(this.params.intrinsicMaxLine, minLineAxis, maxLineAxis, availableLine,
					cLine);
		}
		double minLine = LayoutUtils.computeDimensionLine(this.minSize, flow, cLine);
		if (this.params.intrinsicMinLine != null) {
			minLine = this.resolveIntrinsicLine(this.params.intrinsicMinLine, minLineAxis, maxLineAxis, availableLine,
					cLine);
		}
		final AbsoluteSizing.Result result = AbsoluteSizing.resolve(new AbsoluteSizing.Input( //
				cLine, size, //
				maxLine, //
				minLine, //
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

	/**
	 * 保留していた本文を結び付けます。{@link #finishLayoutSelf}が呼ぶほか、
	 * ページ確定時の脚注の呼び出し走査({@code RootBuilder.scanFootnoteCalls})が
	 * <b>finishLayoutより先に</b>呼ぶ(2026-09-02)——走査はページのfinishLayoutの
	 * 前に走るので、そのままでは絶対配置の中の呼び出しが見えず、注が次の
	 * ページへ送られていた。結び付け済みなら何もしない。
	 */
	public final void bindDeferredContent(final IFramedBox containerBox) {
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
	}

	public final void finishLayoutSelf(final IFramedBox containerBox) {
		this.bindDeferredContent(containerBox);

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
	 * <b>包含ブロックを失った絶対配置を静的位置へ落とします</b>
	 * (2026-08-06、応急処置から仕様へ昇格)。
	 *
	 * <p>
	 * <b>これは「あるはずのない状態の握り潰し」ではなく、この構造で
	 * 定義された振る舞いである。</b> ストリーミングの版面生成では、確定した
	 * ページの容器は生き続けない。絶対配置の最終解決は
	 * {@code containerBox.getInnerWidth()} を必要とする(auto余白・割合)ので、
	 * 包含ブロックが失われた箱については<b>解くための情報が存在しない</b>。
	 * DOMを保持するブラウザなら木を歩き直せるが、ここでは歩き直す木が無い。
	 * </p>
	 *
	 * <p>
	 * CSSの側にも寄る辺は無い。「包含ブロックがページを跨いだとき、絶対配置の
	 * 包含ブロックは何か」は仕様が答えを持たず、ブラウザの印刷実装も割れている。
	 * したがって<b>正解に合わせるという発想が成り立たない</b>——決めて書くしかない。
	 * </p>
	 *
	 * <p>
	 * <b>決めた振る舞い</b>: 未解決の余白・寸法を0とみなし、<b>静的位置</b>へ置く。
	 * 恣意的な0埋めではなく、CSSがoffset autoに与える答え(静的位置)と地続きで
	 * ある。変換は失敗させない(絶対要件)。
	 * </p>
	 *
	 * <p>
	 * <b>これで覆えるのは片方だけ</b>という点に注意。走査から落ちる容器では
	 * {@code finishLayoutSelf} の仕事がすべて飛ぶが、実際に仕事をするのは
	 * 2種類しかない——絶対配置の解決(ここ)と、
	 * {@code position:relative} のずらし量。後者は<b>包含ブロックを必要としない</b>
	 * ので走査に預ける理由が無く、2026-08-06に描画直前でも確定させるようにした
	 * ({@code AbstractContainerBox.resolveRelativeOffset})。
	 * </p>
	 *
	 * <p>
	 * <b>発火は数える</b>({@link #FALLBACK_COUNT})。定義された振る舞いでも、
	 * どれだけ踏んでいるかを知らないまま放置しない。実測(2026-08-06)では
	 * 実物大コーパス235文書のうち{@code github-readme}の16件だけだった。
	 * </p>
	 *
	 * <p>
	 * <b>直せるならなお良い</b>: 容器が走査から落ちる仕組みは未特定で、
	 * 仮説を3つ実測で潰してある(継続断片・走査後の登録・C1c吸収のいずれも
	 * 誤り)。塞げれば静的位置への退避は発火しなくなる。ただし
	 * <b>塞いだあともこの退避は残すこと</b>——別の経路で同じ状況が起きても
	 * 変換を止めないための最後の砦である。
	 * </p>
	 */
	/** 静的位置への退避が発火した回数(定義された振る舞いだが数は知りたい)。 */
	public static final java.util.concurrent.atomic.AtomicLong FALLBACK_COUNT =
			new java.util.concurrent.atomic.AtomicLong();

	private void resolveUnfinishedMargins() {
		final AbsoluteInsets margin = this.frame.margin;
		if (LayoutUtils.isNone(margin.top) || LayoutUtils.isNone(margin.bottom) || LayoutUtils.isNone(margin.left)
				|| LayoutUtils.isNone(margin.right) || LayoutUtils.isNone(this.width)
				|| LayoutUtils.isNone(this.height)) {
			FALLBACK_COUNT.incrementAndGet();
		}
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
		if (this.getAbsolutePos().fiducial != Fiducial.CONTEXT && !pageBox.isReplayPage()) {
			// position:fixedはビューポート(=版面)に貼り付き、ビューポートの
			// 外はスクロールしても到達できないためブラウザは4辺とも描かない。
			// クリップしないと、負座標へ退避したoff-canvas UI(kanaloco.jpの
			// #site-menuドロワー等)の端が用紙余白に描かれる(2026-08-09)。
			// フロー内容には適用しない——印刷のブリード・トンボ・表の
			// 境界は版面の外に描くのが正当(imageTestのmarks/border-collapse
			// 群で実測)
			final java.awt.geom.Rectangle2D.Double icb = new java.awt.geom.Rectangle2D.Double(0, 0,
					pageBox.getWidth(), pageBox.getHeight());
			clip = clip == null ? icb : icb.createIntersection((java.awt.geom.Rectangle2D) clip);
		}
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		this.frames(pageBox, drawer, clip, transform, x, y);
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			// 負の z-index の子はここまで(自分の背景・枠)の後、残りの内容の前に描く(Appendix E ③)
			drawer.markOwnDecorationEnd();
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final AbsolutePos pos = this.getAbsolutePos();
		return (state, container) -> new AbsoluteBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
