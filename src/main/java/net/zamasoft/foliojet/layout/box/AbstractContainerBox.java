package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.OverflowMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.ColumnsContainer;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder;
import net.zamasoft.foliojet.layout.fragment.BreakPlan;
import net.zamasoft.foliojet.layout.fragment.ColumnBalancer;
import net.zamasoft.foliojet.layout.fragment.ColumnCutResult;
import net.zamasoft.foliojet.layout.fragment.ContainerCut;
import net.zamasoft.foliojet.layout.fragment.Continuation;
import net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException;
import net.zamasoft.foliojet.layout.fragment.PreparedColumnCut;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 通常のフローを含むことができるボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractContainerBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public abstract class AbstractContainerBox extends AbstractBox
		implements IPageBreakableBox, INonReplacedBox, IFramedBox {

	protected Dimension size, minSize;

	protected AbsoluteRectFrame frame;

	protected double width = 0;
	protected double height = 0;
	protected double minPageAxis = 0, maxPageAxis = Double.MAX_VALUE;
	protected double offsetX = 0, offsetY = 0;

	/**
	 * <b>Flex/Gridの主軸配置(行/トラック開始位置)</b>(2026-08-06新設)。
	 *
	 * <p>
	 * {@code offsetX}/{@code offsetY}は本来{@code position:relative}の
	 * ずらし量専用の場所だが、{@code FlexItemBox.setFlexLineOffset}/
	 * {@code GridItemBox.setGridLineOffset}が同じ場所へ直接書き込んでいた
	 * ——Flex/Gridアイテムには通常フローのカーソル位置という概念が無く、
	 * 既存の{@code offsetX}を使い回すのが簡便だったため。ここへ
	 * {@link #resolveRelativeOffset}が{@code position:relative}のずらし量を
	 * <b>代入</b>すると、Flex/Gridが計算した配置が丸ごと消える(実地: 検索
	 * ボタンが左右逆転・アイコンが原点へ集まる)。この{@code baseOffsetX}/
	 * {@code baseOffsetY}へFlex/Gridの配置を退避し、
	 * {@code resolveRelativeOffset}はこの上へ<b>加算</b>することで両立する。
	 * </p>
	 */
	protected double baseOffsetX = 0, baseOffsetY = 0;

	/**
	 * <b>相対配置のずらしを確定します</b>(2026-08-06新設)。
	 *
	 * <p>
	 * この計算は<b>包含ブロックを一切必要としません</b>——
	 * {@code LayoutUtils.computeOffsetX/Y} は引数の容器を使わず、
	 * 絶対長ならその値、割合とautoなら0を返すだけである(割合は未実装の
	 * まま。既存のTODO)。したがって<b>いつ呼んでも同じ値</b>になる
	 * ({@link #baseOffsetX}/{@link #baseOffsetY}を基準に毎回同じ結果へ
	 * 収束するため、複数回呼んでも安全)。
	 * </p>
	 *
	 * <p>
	 * <b>なぜ寸法決めの走査任せにしないか。</b> ストリーミングでは、
	 * 確定したページの容器が走査から外れることがあり、そこに居た箱は
	 * {@code finishLayoutSelf}を通らない。ずらし量には番兵値が無いので
	 * <b>0のまま静かに出て誰も気づかない</b>(実測: github-readmeで
	 * {@code top:20pt}を与えてもずれない)。包含ブロックが要らない値まで
	 * 走査に預ける理由は無いので、描画の直前にも確定させる。
	 * </p>
	 */
	protected final void resolveRelativeOffset(final net.zamasoft.foliojet.layout.box.params.Offset offset) {
		if (offset == null) {
			return;
		}
		this.offsetX = this.baseOffsetX + net.zamasoft.foliojet.layout.util.LayoutUtils.computeOffsetX(offset, this);
		this.offsetY = this.baseOffsetY + net.zamasoft.foliojet.layout.util.LayoutUtils.computeOffsetY(offset, this);
	}

	protected Container container;

	protected AbstractContainerBox(Dimension size, Dimension minSize, Container container) {
		assert size != null;
		this.size = size;
		this.minSize = minSize;
		this.container = container;
		container.setBox(this);
	}

	public final Container getContainer() {
		return this.container;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 枠線・背景が見える箱は箱いっぱいに描きます。見えない箱は<b>中身が描く
	 * ところまで</b>しか描かず、中身が何も描かなければ<b>0</b>になります
	 * (余った指定寸法・内容の後ろの余白は何も描かない)。
	 * 書字方向が問い合わせ側と違う箱はページ軸が一致しないため、
	 * 幾何寸法をそのまま返します(安全側)。
	 * </p>
	 */
	@Override
	public double paintedPageExtent(final WritingMode flow) {
		final double full = this.getPageExtent(flow);
		if (this.frame.isVisible()) {
			return full;
		}
		final BlockParams params = this.getBlockParams();
		if (params.flow != flow) {
			return full;
		}
		final double inner = this.container.paintedPageEnd();
		if (LayoutUtils.compare(inner, 0) <= 0) {
			// 中身が何も描かない = この箱は何も描かない
			return 0;
		}
		final double painted = this.frame.getFramePageStart(flow) + inner;
		return params.overflow == OverflowMode.HIDDEN || params.paintClip ? Math.min(full, painted) : painted;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 枠線・背景を持つ箱は描きます。持たない箱は<b>中身が描くかどうか</b>が
	 * そのまま答えです(余った指定寸法・内容の後ろの余白は何も描かない)。
	 * 不透明度0は「描かない」ですが、あえて見ません——安全側だからです。
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		return this.frame.isVisible() || this.container.paintsAnything();
	}

	/**
	 * コンテナボックスのパラメータを返します。
	 * 
	 * @return
	 */
	public abstract BlockParams getBlockParams();

	/**
	 * ページ方向に拡張します。
	 * 
	 * @param newSize
	 */

	public void setPageAxis(final double newSize) {
		final BlockParams params = this.getBlockParams();
		if (params.flow.isVertical()) {
			// 縦書き
			if (newSize <= this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		} else {
			// 横書き
			if (newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
	}

	/**
	 * ページ方向サイズが明示されていればtrueを返します。
	 * 
	 * @return
	 */
	public abstract boolean isSpecifiedPageSize();

	/**
	 * 絶対配置の基準となるボックスではtrueを返します。
	 * 
	 * @return
	 */
	public abstract boolean isContextBox();

	/**
	 * 枠を描画します(2026-07-20、反復化——drawと同じ理由。深いネストでの
	 * StackOverflowErrorを避けるため、明示的な{@link Deque}をワークリストと
	 * して使う反復DFSに置き換えた)。
	 *
	 * @param pageBox
	 *            TODO
	 * @param drawer
	 * @param clip
	 * @param transform
	 *            TODO
	 * @param x
	 * @param y
	 */
	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		final java.util.Deque<FramesStep> worklist = new java.util.ArrayDeque<>();
		worklist.push(AbstractContainerBox.framesStep(this, pageBox, drawer, clip, transform, x, y));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * {@code box}の{@link #pushFramesSteps}を実行する1つの{@link FramesStep}を
	 * 作ります。
	 */
	public static FramesStep framesStep(final AbstractContainerBox box, final PageBox pageBox, final Drawer drawer,
			final Shape clip, final AffineTransform transform, final double x, final double y) {
		return worklist -> box.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	/**
	 * このボックス(とその子孫)の枠描画手順を{@code worklist}へ積みます。
	 * {@link IBox#pushDrawSteps}と同じ規約(元の走査順を保つため**逆順**で
	 * push)に従ってください。
	 */
	public abstract void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform,
			double x, double y, java.util.Deque<FramesStep> worklist);

	/**
	 * 行幅を返します。
	 * 
	 * @return
	 */
	public final double getLineSize() {
		final BlockParams params = this.getBlockParams();
		double lineSize = LayoutUtils.getMaxAdvance(this);
		final int columnCount = this.getColumnCount();
		if (columnCount >= 2) {
			// マルチカラム
			lineSize = (lineSize + params.columns.gap) / columnCount - params.columns.gap;
		}
		return lineSize;
	}

	public int getColumnCount() {
		return 1;
	}

	/**
	 * 行方向サイズが AUTO(内容依存)であれば true を返します(M2c)。
	 */
	public final boolean isAutoLineSize() {
		return this.size.getLineType(this.getBlockParams().flow) == LengthType.AUTO;
	}

	public final boolean canColumnBreak() {
		final int columnCount = this.getColumnCount();
		if (columnCount < 2) {
			return false;
		}
		if (this.isSpecifiedPageSize()) {
			return true;
		}
		return this.getActualColumnCount() < columnCount;
	}

	protected int getActualColumnCount() {
		if (this.container instanceof ColumnsContainer columns) {
			return columns.getColumnCount();
		}
		return 1;
	}

	public final boolean isFixedMulticolumn() {
		if (this.getColumnCount() <= 1) {
			return false;
		}
		BlockParams params = this.getBlockParams();
		if (params.flow.isVertical()) {
			if (this.size.getWidthType() == LengthType.AUTO) {
				return false;
			}
		} else {
			if (this.size.getHeightType() == LengthType.AUTO) {
				return false;
			}
		}
		return true;
	}

	public final void balance(final BlockBuilder builder) {
		final Container oldCont = this.container;

		final int acc = this.getActualColumnCount();
		final int columnCount = this.getColumnCount();
		final boolean vertical = this.getBlockParams().flow.isVertical();
		final double pageSize;
		if (acc >= 2) {
			// 既に複数段に分かれて構築済みの場合は元の単一スタックが失われて
			// いるため、旧来の均等割り(総量/段数の一回スナップ)を使う
			final double total = oldCont.getContentSize() + (vertical ? this.width : this.height) * (acc - 1);
			pageSize = oldCont.getCutPoint(total / columnCount);
		} else {
			// 単一スタックの切り下げ境界で実切断を模擬し、全段に収まる
			// 最小容量を探索する(M5-B)
			pageSize = ColumnBalancer.balance(oldCont::getCutPointBelow, oldCont.getContentSize(), columnCount);
		}

		// 2026-07-25(排除域P2の撤回): 上の容量計算に加えて隔離セッションで
		// 中身を丸ごと組み直す「バランスプローブ」(M6c-2〜M6c-5)は全撤去した。
		// 同じ問い(段が収まる最小容量)を二重に解いており、重い試行20回分の
		// コストと隔離機構(専用builder/PageGenerator/実行パスThreadLocal)に
		// 見合わない——独立3者レビュー全員一致+ユーザー裁定。段の高さの
		// 揃え精度はColumnBalancerの一発勝負ぶん下がるが、フロートを含む
		// 段組で多少不揃いになるのは許容(段組×float領域は妥協が許される)
		if (vertical) {
			this.maxPageAxis = this.width = pageSize;
		} else {
			this.maxPageAxis = this.height = pageSize;
		}

		this.container = new FlowContainer();
		this.container.setBox(this);

		final ColumnBuilder columnBuilder = new ColumnBuilder(builder, this);
		// バランス時この multicol は閉じた部分木(直後が SAX ヘッド)なので、
		// 内容をソースから再構築できる(M6c)。ボックス再生と違い非破壊で、
		// 将来は容量プローブの反復にも使える。範囲不明・Opaque 含み・
		// 分割済み(アンカー無効)の場合はボックス再生へフォールバック
		// ——「アンカー無効」は継続断片側にしか効かないので、前断片側は
		// isSourceReplayable()で明示的に外す(2026-07-28。切断済みの
		// multicol を子範囲から組み直すと、継続断片が持っている残りまで
		// この断片に入り、内容が二重になる)
		final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = builder.getPageContext();
		final boolean replayed = root != null && root.isSegmentRestyle() && this.isSourceReplayable()
				&& net.zamasoft.foliojet.layout.SourceReplayer.replayChildren(
						root.getPageGenerator().getLayoutSource(), this.getSourceAnchor(), columnBuilder,
						root.getPageGenerator());
		if (!replayed) {
			oldCont.restyle(columnBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED, true);
		}
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getFirstAscent() {
		return this.container.getFirstAscent();
	}

	public final double getLastDescent() {
		return this.container.getLastDescent();
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final double getTextIndent() {
		final double textIndent;
		final BlockParams params = this.getBlockParams();
		switch (params.textIndent.getType()) {
		case ABSOLUTE:
			textIndent = params.textIndent.getLength();
			break;
		case RELATIVE:
			textIndent = params.textIndent.getLength() * this.getLineSize();
			break;
		case MIXED:
			textIndent = params.textIndent.getLength() + params.textIndent.getRatio() * this.getLineSize();
			break;
		default:
			throw new IllegalStateException();
		}
		return textIndent;
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	public final void addFlow(IFlowBox box, double pageAxis) {
		this.container.addFlow(box, pageAxis);
	}

	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.container.addAbsolute(box, staticX, staticY);
	}

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.container.addFloating(box, lineAxis, pageAxis);
	}

	/**
	 * このクラス自体に局所処理は無い(2026-07-20、finishLayout反復化。
	 * 局所処理を持つ具象サブクラスはこれをオーバーライドする)。
	 */
	public void finishLayoutSelf(IFramedBox containerBox) {
	}

	public void pushFinishLayoutChildren(final IFramedBox containerBox,
			final java.util.Deque<FinishLayoutStep> worklist) {
		final Container container = this.container;
		worklist.push(w -> container.pushFinishLayoutChildren(containerBox, w));
	}

	protected final Shape clip(Shape clip, double x, double y) {
		final BlockParams params = this.getBlockParams();
		if (params.overflow != OverflowMode.HIDDEN && !params.paintClip) {
			return clip;
		}
		Rectangle2D.Double newClip = new Rectangle2D.Double(
				x + this.frame.frame.border.getLeft().width + this.frame.margin.left,
				y + this.frame.frame.border.getTop().width + this.frame.margin.top,
				this.width + this.frame.padding.getFrameWidth(), this.height + this.frame.padding.getFrameHeight());
		if (clip == null) {
			return newClip;
		}
		return newClip.createIntersection((Rectangle2D) clip);
	}

	protected abstract AbstractContainerBox splitPage(Container container, double pageLimit, boolean columnSpanning);

	/**
	 * 改段の切断だけを行い、ownerへの新column追加・builder resume開始は
	 * まだcommitしません(2026-07-21新設、M6b Phase B4-Step2)。
	 *
	 * <p>
	 * 「prepare」は完全に副作用のないdry-runという意味ではない——
	 * {@code ownerContainer.splitPageAxis()}による元active columnの切断は
	 * ここで既に行われる。正確な意味は「ownerへの新column追加とbuilder
	 * resume開始をまだcommitしていない切断結果」である(ChatGPT Pro相談、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
	 * 参照)。split後の構造検証失敗は変換全体を中断すべきであり、legacy
	 * 経路へrollbackして再実行してはいけない。
	 * </p>
	 *
	 * @param pageLimit 内辺から始まる改段位置
	 * @param mode      改段モード
	 * @param flags     {@code IPageBreakableBox.FLAGS_*}
	 * @param plan      収集可能プレフィックスの計画(未対応の間はnull)
	 */
	public ColumnCutResult prepareColumnCut(final double pageLimit, final BreakMode mode, final byte flags,
			final BreakPlan plan) {
		final Container ownerContainer = this.container;
		final Container activeColumn = ownerContainer instanceof ColumnsContainer columns ? columns.getLastColumn()
				: ownerContainer;
		final int actualColumns = this.getActualColumnCount();

		final ContainerCut cut = ownerContainer.splitPageAxis(pageLimit, mode, flags, plan);
		final Container remainder;
		final Continuation.ContinuationFrame childFrame;
		if (cut instanceof ContainerCut.PlainWithChainStop(final Container chainStopContainer,
				final net.zamasoft.foliojet.layout.fragment.ChainStopReason reason)) {
			// AbstractBlockBox.splitForContinuationと同じ理由(コンテンツ
			// 消失リスク)。containerが空の場合のみbareなKEEP/MOVEとして
			// 返し、実内容がある場合は下の共通Cut構築ロジックへ合流させる
			// (childFrameは常にnull——専用のMovedOpen型は2026-07-22に撤去
			// した、docs/history/2026-07-22-pagination-contract
			// -consultation.md参照)。詳細は
			// docs/history/2026-07-22-chainstop-content-loss-safety-net.md
			// 参照
			final boolean hasContent = chainStopContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
					&& (fc.hasFlows() || fc.hasFloatings());
			if (!hasContent) {
				return reason == net.zamasoft.foliojet.layout.fragment.ChainStopReason.KEEP ? new ColumnCutResult.Keep()
						: new ColumnCutResult.Move();
			}
			remainder = chainStopContainer;
			childFrame = null;
		} else if (cut instanceof ContainerCut.WithFrame(final Container c, final Continuation.ContinuationFrame f)) {
			remainder = c;
			childFrame = f;
		} else {
			remainder = ((ContainerCut.Plain) cut).container();
			childFrame = null;
		}

		if (remainder == null) {
			return new ColumnCutResult.Keep();
		}
		if (remainder == activeColumn) {
			return new ColumnCutResult.Move();
		}

		return new ColumnCutResult.Cut(
				new PreparedColumnCut(this, ownerContainer, activeColumn, actualColumns, pageLimit, remainder, childFrame));
	}

	/**
	 * {@link #prepareColumnCut}が返した{@link ColumnCutResult.Cut}を
	 * ownerへcommitします(2026-07-21新設、M6b Phase B4-Step2)——
	 * ownerを実際に{@link ColumnsContainer}へラップし(未ラップなら)、
	 * page軸寸法を更新し、新しい空columnを追加します。
	 *
	 * <p>
	 * prepare後にowner状態が変化していないかを、commit前に全て検証する
	 * (owner identity・container identity・実段数)——codexレビューで
	 * 指摘された、{@code expectedActiveColumn}を保持するのに検証しない
	 * 欠落を修正済み。commit後は新column追加が正確に1回だけ行われたことも
	 * 確認する。
	 * </p>
	 */
	public void commitPreparedColumn(final PreparedColumnCut cut) {
		if (cut.owner() != this) {
			throw new ContinuationInvariantViolationException("column owner changed");
		}
		if (this.container != cut.expectedOwnerContainer()) {
			throw new ContinuationInvariantViolationException("owner container changed before column commit");
		}
		final Container currentActiveColumn = this.container instanceof ColumnsContainer columns
				? columns.getLastColumn()
				: this.container;
		if (currentActiveColumn != cut.expectedActiveColumn()) {
			throw new ContinuationInvariantViolationException("active column changed before column commit");
		}
		if (this.getActualColumnCount() != cut.expectedActualColumnCount()) {
			throw new ContinuationInvariantViolationException("actual column count changed before commit");
		}

		final ColumnsContainer columns;
		if (this.container instanceof ColumnsContainer existing) {
			columns = existing;
		} else {
			this.container = columns = new ColumnsContainer((FlowContainer) this.container);
			columns.setBox(this);
		}

		if (this.getBlockParams().flow.isVertical()) {
			this.width = cut.newPageExtent();
		} else {
			this.height = cut.newPageExtent();
		}

		columns.newColumn();

		if (this.getActualColumnCount() != cut.expectedActualColumnCount() + 1) {
			throw new ContinuationInvariantViolationException("new column was not added exactly once");
		}
	}

	/**
	 * {@code this.container.splitPageAxis()}が返す<b>MOVE の目印</b>
	 * (「全部移動した」を表す自己参照)と比較すべきコンテナを返します
	 * (2026-07-28新設)。
	 *
	 * <p>
	 * {@link ColumnsContainer}は切断を<b>最終段へ委譲してその結果を
	 * そのまま返す</b>ので、MOVEの目印は段組コンテナ自身ではなく
	 * <b>最終段</b>になる。{@code this.container}と比べると一致せず、
	 * <b>最終段が「残余コンテナ」として解釈される</b>——ところがその
	 * 最終段は<b>段組コンテナの中に残ったまま</b>なので、同じ内容が
	 * 前の段と継続断片の両方から描かれる(2026-07-28実測、
	 * local/shrink/strict-739-min.html: 入れ子段組で T4 が二度描かれる)。
	 * </p>
	 *
	 * <p>
	 * {@link #prepareColumnCut}は最初からこの比較を行っている
	 * ({@code activeColumn})。{@code ContainerCut.Plain}のjavadocが
	 * 「層ごとに何とidentity比較するかが違う」と記していた差の、
	 * 誤っていた側をこちらへ揃える。
	 * </p>
	 */
	protected final Container splitMoveSentinel() {
		return this.container instanceof ColumnsContainer columns ? columns.getLastColumn() : this.container;
	}

	public SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final BreakMode xmode = BreakMode.absorbColumn(mode, this.getColumnCount());
		// コンテナ側の三義的返値の解釈はここに集約(コンテナ内部の型付けは M4-A3b)。
		// planなし切断は常にPlain——旧3引数splitPageAxisはこのPlain写像の
		// wrapperだった(増分5で一本化)
		final Container nextContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) this.container
				.splitPageAxis(pageLimit, xmode, flags, null)).container();
		if (nextContainer == null) {
			return SplitResult.KEEP;
		}
		if (nextContainer == this.splitMoveSentinel()) {
			return SplitResult.MOVE;
		}
		return new SplitResult.Split(
				this.splitPage(nextContainer, pageLimit, mode instanceof BreakMode.ColumnBreakMode));
	}

	/**
	 * コンテナの内容からテキストを抽出します。
	 *
	 * <p>
	 * 2026-07-25: {@code final}を外した。{@code RubyUnitBox}は子ボックスを
	 * 持たず(コンテナは空)、整形済みグリフ列を自前で持つ合成箱のため、
	 * 抽出を上書きしないと親からの反復抽出(リンクの代替テキスト・
	 * string-setのcontent()・ブックマーク見出し・target-text())で
	 * ルビの親文字が丸ごと落ちる。
	 * </p>
	 */
	public void pushGetTextSteps(StringBuilder textBuff, java.util.Deque<GetTextStep> worklist) {
		this.container.pushGetTextSteps(textBuff, worklist);
	}
	
	public final void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x,
			double y, java.util.Deque<TextShapeStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		transform = this.transform(transform, x, y);
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		this.container.pushTextShapeSteps(pageBox, path, transform, x, y, worklist);
	}

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		this.container.restyle(builder, shape, false);
	}
}
