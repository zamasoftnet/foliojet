package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class ColumnsContainer implements Container {

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		for (final Container column : this.columns) {
			column.forEachAssignmentChild(action);
		}
	}
	protected class ColumnRuleDrawable extends AbstractDrawable {
		protected final double x, y;

		public ColumnRuleDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform, double x,
				double y) {
			super(pageBox, clip, opacity, transform);
			this.x = x;
			this.y = y;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			final BlockParams params = ColumnsContainer.this.box.getBlockParams();
			final double columnSize = ColumnsContainer.this.box.getLineSize() + params.columns.gap;
			if (params.flow.isVertical()) {
				if (params.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
								params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
					for (int i = 1; i < ColumnsContainer.this.getColumnCount(); ++i) {
						final double logicalRule = i * columnSize - params.columns.gap / 2;
						final double physicalRule = LayoutUtils.inlineToPhysical(params,
								ColumnsContainer.this.box.getInnerHeight(), logicalRule, logicalRule);
						BorderRenderer.INSTANCE.drawHorizontalBorder(gc, params.columns.rule, x,
								y + physicalRule, ColumnsContainer.this.box.getInnerWidth());
					}
				} else {
					for (int i = 1; i < ColumnsContainer.this.getColumnCount(); ++i) {
						y += columnSize;
						BorderRenderer.INSTANCE.drawHorizontalBorder(gc, params.columns.rule, x,
								y - params.columns.gap / 2, ColumnsContainer.this.box.getInnerWidth());
					}
				}
			} else {
				for (int i = 1; i < ColumnsContainer.this.getColumnCount(); ++i) {
					x += columnSize;
					BorderRenderer.INSTANCE.drawVerticalBorder(gc, params.columns.rule,
							x - params.columns.gap / 2, y, ColumnsContainer.this.box.getInnerHeight());
				}
			}
		}
	}

	protected final List<Container> columns = new ArrayList<Container>();

	protected AbstractContainerBox box;

	public ColumnsContainer(FlowContainer container) {
		this.columns.add(container);
	}

	public int getColumnCount() {
		return this.columns.size();
	}

	public void setBox(AbstractContainerBox box) {
		this.box = box;
	}

	public final FlowContainer getLastColumn() {
		return (FlowContainer) this.columns.get(this.columns.size() - 1);
	}

	public void addFlow(IFlowBox box, double pageAxis) {
		this.getLastColumn().addFlow(box, pageAxis);
	}

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.getLastColumn().addFloating(box, lineAxis, pageAxis);
	}

	public void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.getLastColumn().addAbsolute(box, staticX, staticY);
	}

	@Override
	public void eachAbsoluteBox(final java.util.function.Consumer<IAbsoluteBox> consumer) {
		for (final Container column : this.columns) {
			column.eachAbsoluteBox(consumer);
		}
	}

	public boolean avoidBreakAfter() {
		return false;
	}

	public boolean avoidBreakBefore() {
		return false;
	}

	public double getFirstAscent() {
		return 0;
	}

	public double getLastDescent() {
		return 0;
	}

	public double getContentSize() {
		return this.getLastColumn().getContentSize();
	}

	public double paintedPageEnd() {
		// 段は同じページ軸範囲に並ぶので、最も深くまで描く段を採る
		double end = 0;
		for (int i = 0; i < this.columns.size(); ++i) {
			end = Math.max(end, this.columns.get(i).paintedPageEnd());
		}
		return end;
	}

	public boolean paintsAnything() {
		// 段間罫(column-rule)は中身がなくても引かれる
		if (this.box.getBlockParams().columns.rule.isVisible()) {
			return true;
		}
		for (int i = 0; i < this.columns.size(); ++i) {
			if (this.columns.get(i).paintsAnything()) {
				return true;
			}
		}
		return false;
	}

	public double getCutPoint(double pageAxis) {
		FlowContainer first = (FlowContainer) this.columns.get(0);
		return first.getCutPoint(pageAxis);
	}

	public double getCutPointBelow(double pageAxis) {
		FlowContainer first = (FlowContainer) this.columns.get(0);
		return first.getCutPointBelow(pageAxis);
	}

	public void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		if (params.columns.rule.isVisible()) {
			Drawable drawable = new ColumnRuleDrawable(pageBox, clip, params.opacity, transform, x, y).withBlendMode(params.blendMode).withFilter(params.filter);
			drawer.visitDrawable(drawable, x, y);
		}
		// カラムは行軸に沿って並び、下から上への行内進行だけ物理化時に反転する。
		// カラム数は小さく固定なので直接呼び出してよい(走査順を保つため
		// 逆順で処理する)
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushFramesSteps(pageBox, drawer, clip, transform, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// カラムは行軸に沿って並び、下から上への行内進行だけ物理化時に反転する。
		// カラム数は小さく固定なので、直接呼び出しても(逆順にせずとも)
		// スタック深さは問題にならないが、走査順を保つため逆順で処理する
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushDrawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// 行内進行が下から上なら物理化時に反転する
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// 行内進行が下から上なら物理化時に反転する
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushDrawAbsolutes(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// 元の走査順(先頭カラムから)を保つため、スタックへは逆順(末尾カラムから)でpushする
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final Container container = this.columns.get(i);
			worklist.push(w -> container.pushFinishLayoutChildren(containerBox, w));
		}
	}

	public void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// カラム数は小さく固定なので直接呼び出してよい(走査順を保つため
		// 逆順で処理する)
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			container.pushGetTextSteps(textBuff, worklist);
		}
	}
	
	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist) {
		// TODO
	}

	public boolean hasFloatings() {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			if (container.hasFloatings()) {
				return true;
			}
		}
		return false;
	}

	public boolean hasFlows() {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			if (container.hasFlows()) {
				return true;
			}
		}
		return false;
	}

	public net.zamasoft.foliojet.layout.fragment.ContainerCut splitPageAxis(final double pageLimit,
			final BreakMode mode, final byte flags, final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordColumnsSplitAttempt();
		final FlowContainer lastColumn = this.getLastColumn();
		final net.zamasoft.foliojet.layout.fragment.ContainerCut cut = lastColumn.splitPageAxis(pageLimit, mode,
				flags, plan);
		if (this.columns.size() > 1
				&& cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain(final Container container)
				&& container == lastColumn) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordLastColumnMoveCandidate();
		}
		return cut;
	}

	public FloatTransferResult splitFloatings(FloatTransferTarget target, double pageLimit, byte flags) {
		// 段組コンテナはこの経路でfloatを移動しない(旧APIのnextBox
		// そのまま返し=移動なしと同じ)
		return FloatTransferResult.KEEP_OWNER;
	}

	public java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags) {
		// 段組コンテナはこの経路でfloatを移動しない(3引数版と同じ理由)
		return java.util.Optional.empty();
	}

	public void eachFlowBox(java.util.function.Consumer<IFlowBox> consumer) {
		for (int i = 0; i < this.columns.size(); ++i) {
			((FlowContainer) this.columns.get(i)).eachFlowBox(consumer);
		}
	}

	public void newColumn() {
		Container container = new FlowContainer();
		container.setBox(this.box);
		this.columns.add(container);
	}

	/**
	 * 段に分かれた内容を組み直します。
	 *
	 * <p>
	 * <b>読みながら書いてはいけない</b>(2026-07-27修正)。従来は生きた
	 * {@link #columns}を走査しながら、その同じリストへ組み直していた:
	 * </p>
	 *
	 * <ul>
	 * <li>{@link #addFlow}は常に<b>現在の最終カラム</b>へ書き込む
	 * ({@link #getLastColumn()})</li>
	 * <li>{@link #newColumn()}は走査中のリストへ追加する</li>
	 * <li>再生側の{@code FlowContainer}は自分の{@code flows}を取り出して
	 * {@code null}にする</li>
	 * </ul>
	 *
	 * <p>
	 * 結果、<b>読み出して空にしている容器と、組み直し先が同一</b>になり、
	 * 内容が黙って消えていた。先に写しを取り、空のカラムを1つ据えてから
	 * 写しを読む——{@code AbstractContainerBox.balance()}が既に踏んでいる
	 * 手順と同じ形である。
	 * </p>
	 *
	 * <p>
	 * <b>開いた尾({@code shape})を渡してよいのは最終カラムだけ。</b>
	 * {@code shape}はこのボックスの論理フロー全体の「開いた尾」を表すので、
	 * 最終カラム以外に渡すと{@code FlowContainer.restyleItem}が
	 * open-chain降下を選び、{@code FlowBlockBox.restyle}が
	 * <b>{@code endFlowBlock()}を意図的に省く</b>ため、そのボックスが
	 * {@code flowStack}に開いたまま残る。次のカラムはその中に組まれ、
	 * {@code <ol>}が自分の{@code <li>}の中に入る、という壊れ方をする。
	 * </p>
	 *
	 * <p>
	 * 実測(2026-07-27、10万文書の掃過で発見。20,000文書に1件):
	 * 3重に入れ子にした段組で、内側の内容が丸ごと消えていた。
	 * 経緯は`copperpdf4/docs/consultations/consult-nested-multicol-content-loss-2026-07-27.md`。
	 * </p>
	 */
	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes) {
		final List<Container> snapshot = this.beginRestyleScope();
		final int last = snapshot.size() - 1;
		try {
			for (int i = 0; i <= last; ++i) {
				final FlowContainer container = (FlowContainer) snapshot.get(i);
				container.restyle(builder,
						i == last ? shape : net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED, restyleAbsolutes);
			}
		} finally {
			endRestyleScope();
		}
	}

	/**
	 * 組み直しscopeの開始: 全段のsnapshot→元リストのclear→空の先頭段の
	 * 据え付け→尾部再生の封印、をこの順で行い、再生すべき旧段の写しを
	 * 返します(2026-07-30、legacy再帰撤去=増分1で{@link #restyle}から
	 * 抽出。worklist executorのMULTICOL native降下と二重実装しないための
	 * 共有部品)。
	 *
	 * <p>
	 * 必ず{@link #endRestyleScope()}と対にすること(legacyはtry/finally、
	 * worklistはscope popまたはexecutorのfinally清算)。
	 * </p>
	 *
	 * <p>
	 * 尾部封印の理由: 段の組み直しでは、どの段の断片も「自分が記録した
	 * 分しか持っていない」——切断テキストの尾部再生(charOffsetから
	 * ソース末尾まで)を全段で封じる。開いた尾は{@code shape}が最終段へ
	 * 運び、{@code restyleItem}の{@code open}分岐が元々尾部再生を通さない
	 * (2026-07-28。封じないと段2が"T2 T3 T4"、段3が"T3 T4"を描く:
	 * local/shrink/strict-118665-min.html)
	 * </p>
	 */
	List<Container> beginRestyleScope() {
		final List<Container> snapshot = new ArrayList<Container>(this.columns);
		this.columns.clear();
		final FlowContainer fresh = new FlowContainer();
		fresh.setBox(this.box);
		this.columns.add(fresh);
		FlowContainer.pushTailSeal();
		return snapshot;
	}

	/** {@link #beginRestyleScope()}に対応する終了(尾部封印の解除)。 */
	static void endRestyleScope() {
		FlowContainer.popTailSeal();
	}
}
