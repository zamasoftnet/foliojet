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
		final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = builder.getPageContext();
		final boolean replayed = root != null && root.isSegmentRestyle()
				&& net.zamasoft.foliojet.layout.SourceReplayer.replayChildren(
						root.getPageGenerator().getLayoutSource(), this.getSourceAnchor(), columnBuilder,
						root.getPageGenerator());
		if (!replayed) {
			oldCont.restyle(columnBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED, true);
		}
		assert replayed ? sameText(oldCont, this.container) : true : balanceLostText(oldCont, this.container);
	}

	/**
	 * 段組の組み直しで<b>文字が失われていない</b>ことを確かめます
	 * (2026-07-27新設、assert専用)。
	 *
	 * <p>
	 * ソース再生が成功すると、古い容器は用済みとしてそのまま捨てられる。
	 * ところが再生で作り直せるのは<b>このボックス自身の内容</b>だけなので、
	 * 他所から紛れ込んだ内容があると<b>一緒に黙って消える</b>。
	 * </p>
	 *
	 * <p>
	 * 実際に踏んだ(2026-07-27): restyleの順序の誤りで別の段組のフロートが
	 * この容器へ係留されており、ここで消えて「20万文書に1件の内容の消失」に
	 * なっていた。原因側は直したが、<b>ここが静かな消失を生む増幅器である</b>
	 * ことは変わらない。同種の誤りが再び入ったとき、静かに消すのではなく
	 * 大きな音を立てさせる。
	 * </p>
	 *
	 * <p>
	 * 本番ではassertが無効なのでコストはゼロ。テストでは文書の文字量に
	 * 比例した走査が1回増えるが、<b>絶対要件「内容の消失」を守る値段として
	 * 妥当</b>(ユーザー方針: テストだけに影響するassertは積極的に入れる)。
	 * </p>
	 */
	private static boolean sameText(final Container before, final Container after) {
		return collectText(before).equals(collectText(after));
	}

	private static String balanceLostText(final Container before, final Container after) {
		return "段組の組み直しで文字が変わった。他所の内容が紛れ込んで捨てられた疑い: 前=" + collectText(before)
				+ " 後=" + collectText(after);
	}

	private static String collectText(final Container container) {
		final StringBuilder sb = new StringBuilder();
		container.eachFlowBox(b -> b.getText(sb));
		return sb.toString();
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
		if (this.getBlockParams().overflow != OverflowMode.HIDDEN) {
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
	 * @deprecated {@link #prepareColumnCut(double, BreakMode, byte, BreakPlan)}
	 *             + {@link #commitPreparedColumn(PreparedColumnCut)}へ分離した
	 *             (2026-07-21、M6b Phase B4-Step2)。移行期間中のlegacy wrapper
	 *             として挙動を保つ。
	 */
	@Deprecated
	public Container newColumn(double pageLimit, final BreakMode mode, final byte flags) {
		return switch (this.prepareColumnCut(pageLimit, mode, flags, null)) {
		case ColumnCutResult.Keep keep -> null;
		case ColumnCutResult.Move move -> null;
		case ColumnCutResult.Cut(final PreparedColumnCut prepared) -> {
			this.commitPreparedColumn(prepared);
			yield prepared.ownerRemainder();
		}
		};
	}

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

	public SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final BreakMode xmode = BreakMode.absorbColumn(mode, this.getColumnCount());
		// コンテナ側の三義的返値の解釈はここに集約(コンテナ内部の型付けは M4-A3b)
		final Container nextContainer = this.container.splitPageAxis(pageLimit, xmode, flags);
		if (nextContainer == null) {
			return SplitResult.KEEP;
		}
		if (nextContainer == this.container) {
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
