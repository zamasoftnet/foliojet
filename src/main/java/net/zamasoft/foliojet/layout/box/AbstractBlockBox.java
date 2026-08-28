package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.ParamsType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.layout.draw.DebugDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * ブロックボックスの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public abstract class AbstractBlockBox extends AbstractContainerBox {
	private static final boolean DEBUG = false;

	protected final BlockParams params;

	public AbstractBlockBox(final BlockParams params) {
		super(params.getType() == ParamsType.TABLE ? Dimension.AUTO_DIMENSION : params.size,
				params.getType() == ParamsType.TABLE ? Dimension.ZERO_DIMENSION : params.minSize, new FlowContainer());
		this.params = params;
		final RectFrame frame;
		if (params.getType() == ParamsType.TABLE) {
			frame = RectFrame.NULL_FRAME;
		} else {
			frame = params.frame;
		}
		this.frame = new AbsoluteRectFrame(frame);
		assert this.params.fontStyle != null;
	}

	protected AbstractBlockBox(BlockParams params, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(size, minSize, container);
		this.params = params;
		this.frame = frame;
		assert this.params.fontStyle != null;
	}

	public BoxType getType() {
		return BoxType.BLOCK;
	}

	public Params getParams() {
		return this.params;
	}

	public BlockParams getBlockParams() {
		return this.params;
	}

	public void firstPassLayout(AbstractContainerBox containerBox) {
		BlockParams containerParams = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();

		//
		// ■ パディングの計算
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ マージンの計算
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		//
		// ■ 幅と高さの計算
		//
		switch (containerParams.flow) {
		case WritingMode.TB:
			// 横書き
			this.width = LayoutUtils.computeDimensionWidth(this.size, lineSize);
			if (LayoutUtils.isNone(this.width)) {
				this.width = 0;
			}
			double maxWidth = LayoutUtils.computeDimensionWidth(params.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxWidth)) {
				this.width = Math.min(this.width, maxWidth);
			}
			double minWidth = LayoutUtils.computeDimensionWidth(this.minSize, lineSize);
			this.width = Math.max(this.width, minWidth);
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			this.height = LayoutUtils.computeDimensionHeight(this.size, lineSize);
			if (LayoutUtils.isNone(this.height)) {
				this.height = 0;
			}
			double maxHeight = LayoutUtils.computeDimensionWidth(params.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxHeight)) {
				this.height = Math.min(this.height, maxHeight);
			}
			double minHeight = LayoutUtils.computeDimensionWidth(this.minSize, lineSize);
			this.height = Math.max(this.height, minHeight);
			break;
		default:
			throw new IllegalStateException();
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		// 相対配置のずらしをここでも確定させる(包含ブロックを使わない値なので
		// いつ呼んでも同じ。理由は resolveRelativeOffset の説明)
		if (this.getPos() instanceof net.zamasoft.foliojet.layout.box.params.AbstractStaticPos sp) {
			this.resolveRelativeOffset(sp.offset);
		}
		// SPEC CSS 2.1 9.9.1 #1
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		if (this.params.opacity != 0f && this.frame.isVisible()) {
			final Shape textClip;
			if (this.getBlockParams().frame.background.getBackgroundClip() == Background.TEXT) {
				final GeneralPath path = new GeneralPath();
				this.textShape(pageBox, path, transform, x, y);
				textClip = path;
			}
			else {
				textClip = null;
			}
			// clip-pathは自箱の背景・境界も切る(overflowと違う)
			final Drawable drawable = new AbsoluteRectFrameDrawable(pageBox, this.clipWithClipPath(clip, x, y),
					this.params.opacity, transform, this.frame,
					this.getWidth(), this.getHeight(), textClip);
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);
		this.container.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}
	

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, final Deque<DrawStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);

		transform = this.transform(transform, x, y);

		visitor.visitBox(transform, this, drawer, x, y);

		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(0, 0, 1));
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);
		final double contentBoxX = x;
		final double contentBoxY = y;
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);

		final boolean contextBox = this.isContextBox();
		if (contextBox) {
			contextX = contentBoxX - this.frame.padding.left;
			contextY = contentBoxY - this.frame.padding.top;
		}

		// Tagged PDF: open a structure element for a mappable HTML block so the
		// content drawn inside attaches to it. Zero-size markers, no-op when
		// untagged or non-PDF; deduplicated per element by PageBox.
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		final Shape absolutesClip = contextBox ? clip : null;
		final double fx = x, fy = y;
		// 元の実行順(floatings→flows→absolutes→endStruct)を保つため、
		// スタックへは逆順でpushする
		worklist.push(w -> pageBox.endStruct(drawer, this.params.element, structCount, fx, fy));
		this.container.pushDrawAbsolutes(pageBox, drawer, visitor, absolutesClip, transform, contextX, contextY,
				contentBoxX, contentBoxY, worklist);
		this.container.pushDrawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
		this.container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
	}

	/**
	 * 断片ボックスの再構成レシピを返します(C1d-B)。実装は必要な値を
	 * キャプチャし、this への参照を保持しないこと(FragmentRecipe の
	 * 規約参照)。splitPageState がアンカーを無効化する前に取得すること。
	 */
	public abstract net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe();

	protected final AbstractContainerBox splitPage(final Container container, final double pageLimit,
			final boolean columnSpanning) {
		final boolean vertical = this.params.flow.isVertical();
		final double crossExtent = vertical ? this.height : this.width;
		final net.zamasoft.foliojet.layout.fragment.FragmentState state = this.splitPageState(pageLimit,
				columnSpanning, this.shouldPreserveSpecifiedPageSize(container));
		return this.continueFragment(state, container, crossExtent);
	}

	/**
	 * チェーンメンバーの継続化切断です(C1d-C)。plan がこのボックスを
	 * 選択している破断で、切断が内部を貫通した場合、断片ボックスを
	 * 構築せず ContinuationFrame を {@link SplitResult.Frame} で返します
	 * (親が返り値でさらに外へ伝播する)。KEEP/MOVE はそのまま。
	 * Split(box) は返らない。
	 */
	public final net.zamasoft.foliojet.layout.fragment.SplitResult splitForContinuation(double pageLimit,
			final net.zamasoft.foliojet.layout.box.content.BreakMode mode, final byte flags,
			final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		assert plan.selects(this);
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final net.zamasoft.foliojet.layout.box.content.BreakMode xmode = net.zamasoft.foliojet.layout.box.content.BreakMode
				.absorbColumn(mode, this.getColumnCount());
		final net.zamasoft.foliojet.layout.fragment.ContainerCut cut = this.container.splitPageAxis(pageLimit, xmode,
				flags, plan.next());
		final Container nextContainer;
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame childFrame;
		if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(
				final Container chainStopContainer, final net.zamasoft.foliojet.layout.fragment.ChainStopReason reason)) {
			// chainStopContainerが実際にflow/floatを保持している場合
			// (MOVEなら常に、KEEPでも兄弟の浮動体オーバーフローがあれば
			// 起こりうる——force-branchのsplitFloatings(pageLimit, flags,
			// index)はKEEP/MOVEどちらでも兄弟の浮動体を検分するため)、
			// 破棄すると内容が消える。containerが空の場合のみreasonを
			// そのままbareなKEEP/MOVEとして返し、実内容がある場合は下の
			// 共通Frame構築ロジックへ合流させる(tailは常にOpenTailShape
			// ——MOVE/KEEPどちらでも同じ「開いたまま続く」を表す。専用の
			// MovedOpen型は2026-07-22に撤去した、
			// docs/history/2026-07-22-pagination-contract-consultation.md
			// 参照)。詳細は
			// docs/history/2026-07-22-chainstop-content-loss-safety-net.md
			// 参照
			final boolean hasContent = chainStopContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
					&& (fc.hasFlows() || fc.hasFloatings());
			if (!hasContent) {
				return reason == net.zamasoft.foliojet.layout.fragment.ChainStopReason.KEEP
						? net.zamasoft.foliojet.layout.fragment.SplitResult.KEEP
						: net.zamasoft.foliojet.layout.fragment.SplitResult.MOVE;
			}
			nextContainer = chainStopContainer;
			childFrame = null;
		} else if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(final Container c,
				final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f)) {
			nextContainer = c;
			childFrame = f;
		} else {
			nextContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) cut).container();
			childFrame = null;
		}
		if (nextContainer == null) {
			return net.zamasoft.foliojet.layout.fragment.SplitResult.KEEP;
		}
		if (nextContainer == this.splitMoveSentinel()) {
			// 段組コンテナは切断を最終段へ委譲するので、MOVEの目印は
			// 最終段になる(splitMoveSentinel参照)。ここを this.container
			// とだけ比べていたため、段組の最終段が「残余」と誤読され、
			// **段組の中に残ったまま**継続断片としても組まれていた
			// (2026-07-28、local/shrink/strict-739-min.html)
			assert childFrame == null;
			return net.zamasoft.foliojet.layout.fragment.SplitResult.MOVE;
		}
		// 貫通 = 継続化。レシピは splitPageState(アンカー無効化)より前に
		// 取得する(C1d-B)。prefixItems は pageBreak が水位計算の後に吸収
		final boolean vertical = this.getBlockParams().flow.isVertical();
		final double crossExtent = vertical ? this.getInnerHeight() : this.getInnerWidth();
		final net.zamasoft.foliojet.layout.fragment.FragmentRecipe recipe = this.fragmentRecipe();
		final net.zamasoft.foliojet.layout.fragment.FragmentState state = this.splitPageState(pageLimit,
				mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ColumnBreakMode,
				this.shouldPreserveSpecifiedPageSize(nextContainer));
		final net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = childFrame != null
				? new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(childFrame)
				: new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						net.zamasoft.foliojet.layout.fragment.OpenShape.of(plan.openTailDepth()));
		return new net.zamasoft.foliojet.layout.fragment.SplitResult.Frame(
				new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(recipe, state, nextContainer,
						crossExtent, java.util.List.of(), tail));
	}

	/**
	 * ブロックfloat専用のページ方向切断です(2026-07-24、排除域A-3a)。
	 * {@link AbstractContainerBox#split}のfloat変種で、内部切断(Split)の
	 * 残余boxを即時構築({@code splitPage})せず、材料
	 * ({@link net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment})
	 * のまま返します。残余boxの構築は受け側{@code Floatings}へ接続する
	 * 時点で一度だけ行われる。
	 *
	 * <p>
	 * 材料の取り方は即時経路と同一: 切断判定はコンテナの
	 * {@code splitPageAxis}、前断片のmutationと断片状態は
	 * {@code splitPageState}の実出力(再計算しない)、crossExtentは
	 * mutation前のraw寸法({@code splitPage}と同じ)。recipeは
	 * {@code splitPageState}の前に取得する({@code splitForContinuation}と
	 * 同じC1d-Bの規約。即時経路はmutation後に取得するが、FloatBlockBoxの
	 * recipeは不変のparams/posのみをキャプチャするため等価)。
	 * </p>
	 *
	 * @param serial 呼び出し側({@code Floatings})が管理するfloatの識別子
	 */
	public net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit splitFloatFragment(final int serial,
			double pageLimit, final net.zamasoft.foliojet.layout.box.content.BreakMode mode, final byte flags) {
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final net.zamasoft.foliojet.layout.box.content.BreakMode xmode = net.zamasoft.foliojet.layout.box.content.BreakMode
				.absorbColumn(mode, this.getColumnCount());
		// planなし切断は常にPlain(legacy契約: null=KEEP/sentinel=MOVE/他=残余)。
		// 旧3引数splitPageAxisはこのPlain写像のwrapperだった(増分5で一本化)
		final Container nextContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) this.container
				.splitPageAxis(pageLimit, xmode, flags, null)).container();
		if (System.getProperty("foliojet.debug.floatTrace") != null) {
			final String kind = nextContainer == null ? "KEEP"
					: (nextContainer == this.splitMoveSentinel() ? "MOVE" : "SPLIT");
			System.err.println("[float-split] " + kind + " el=" + this.params.element + " innerLimit=" + pageLimit
					+ " flags=" + flags);
		}
		if (nextContainer == null) {
			return net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.KEEP;
		}
		if (nextContainer == this.splitMoveSentinel()) {
			return net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.MOVE;
		}
		final boolean vertical = this.params.flow.isVertical();
		final double crossExtent = vertical ? this.height : this.width;
		final net.zamasoft.foliojet.layout.fragment.FragmentRecipe recipe = this.fragmentRecipe();
		final net.zamasoft.foliojet.layout.fragment.FragmentState state = this.splitPageState(pageLimit,
				mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ColumnBreakMode,
				this.shouldPreserveSpecifiedPageSize(nextContainer));
		return new net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Prepared(
				new net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment(serial, recipe, state, nextContainer,
						crossExtent));
	}

	/**
	 * ページ方向切断の前断片側を確定し、継続断片の状態を返します(C1a)。
	 * 従来 splitPage(断片ボックス構築込み)が一体で行っていた処理の
	 * 前側半分: 自箱をページ使用量まで切りつめ、終端側フレームを落とす。
	 * 継続断片の構築は {@link #continueFragment} が(必要なら resume 時に)
	 * 行う。
	 */
	public final net.zamasoft.foliojet.layout.fragment.FragmentState splitPageState(final double pageLimit,
			final boolean columnSpanning) {
		return this.splitPageState(pageLimit, columnSpanning, false);
	}

	private net.zamasoft.foliojet.layout.fragment.FragmentState splitPageState(final double pageLimit,
			final boolean columnSpanning, final boolean preserveSpecifiedPageSize) {
		// 分割されたボックスの断片は「継続物」(フレーム切断・内容消費が進行)
		// であり、ソースから新品を再生してはならない。SourceAnchor は
		// ボックス個体に属し(P0)、レシピ構築の断片は最初からアンカーを
		// 持たないため、旧 params.sourceEventId=-1 の無効化は不要
		// (無効化しないと再生が分割進捗を巻き戻し無限改ページ、という
		// 危険は「断片にアンカーが継承されない」ことで構造的に防がれる)
		//
		// 2026-07-28: 上の「構造的に防がれる」は**後ろ半分にしか効かない**。
		// 前断片(this)はアンカーを持ったまま残るので、これをソースから
		// 再生すると要素全体——継続断片が持っている残りを含む——が
		// 組み直され、継続断片の再開と内容が二重になる。通常は前断片が
		// 前ページ/前段に残って二度と再開されないので表に出ないが、
		// 入れ子段組の段バランスでは`ColumnsContainer.restyle`が全段を
		// 一本に組み直すため、前断片と継続断片が**同じ残余に並ぶ**。
		// そこで改段が起きると前断片は「丸ごと移動する閉じた部分木」と
		// 見なされ、`stampRanges`→`replayFromSource`で要素全体が再生
		// される(実測: local/shrink/strict-347-min.html で `<li>` が
		// 同じページに二度描かれる)。切断した側でここを塞ぐ。
		this.markFragmented();
		final boolean vertical = this.params.flow.isVertical();
		final net.zamasoft.foliojet.layout.fragment.FragmentState state = net.zamasoft.foliojet.layout.fragment.FragmentState
				.of(this.params.flow, columnSpanning, this.frame, this.size,
						this.minSize, vertical ? this.width : this.height, pageLimit,
						this.container.getContentSize(), this.isSpecifiedPageSize(), preserveSpecifiedPageSize);
		if (vertical) {
			this.width = state.prevPageExtent();
		} else {
			this.height = state.prevPageExtent();
		}
		this.frame = state.prevFrame();
		return state;
	}

	/**
	 * 強制分割で実内容を1つも取らず、内容が丸ごと次断片へ移った固定寸法箱か。
	 * 背景・枠や実際に消費した空きを複製しない。
	 */
	private boolean shouldPreserveSpecifiedPageSize(final Container nextContainer) {
		final boolean specified = this.isSpecifiedPageSize();
		final boolean frameVisible = this.frame.isVisible();
		final boolean previousContent = this.container.hasNonDecorationContent();
		final double consumed = this.container.getConsumedPageSizeForFragmentation();
		final boolean nextContent = nextContainer.hasNonDecorationContent();
		final boolean preserve = specified && !frameVisible && !previousContent
				&& LayoutUtils.compare(consumed, 0) <= 0 && nextContent;
		if (!preserve) {
			return false;
		}
		// inline-blockは行ボックス内に入り、通常フローのBLOCKだけを辿る
		// 判定では置換画像まで到達できない。前断片が内容も位置も0で、継続側に
		// 実内容があるという断片境界そのものを条件にする。
		return true;
	}

	/**
	 * 断片状態から継続断片ボックスを構成します(C1a)。
	 *
	 * @param state       断片状態({@link #splitPageState} の返値)
	 * @param container   継続断片の内容
	 * @param crossExtent 切断時点の交差軸(行方向)寸法
	 * @return 継続断片
	 */
	public final AbstractBlockBox continueFragment(final net.zamasoft.foliojet.layout.fragment.FragmentState state,
			final Container container, final double crossExtent) {
		return continueFragment(this.fragmentRecipe(), state, container, crossExtent);
	}

	/**
	 * レシピから継続断片ボックスを構成します(C1d-B。resume が
	 * ContinuationFrame の消費に使う — 旧ボックスへの仮想呼び出しなし)。
	 */
	public static AbstractBlockBox continueFragment(final net.zamasoft.foliojet.layout.fragment.FragmentRecipe recipe,
			final net.zamasoft.foliojet.layout.fragment.FragmentState state, final Container container,
			final double crossExtent) {
		final AbstractBlockBox nextBlock = recipe.instantiate(state, container);
		if (nextBlock.params.flow.isVertical()) {
			nextBlock.height = crossExtent;
		} else {
			nextBlock.width = crossExtent;
		}
		return nextBlock;
	}
}
