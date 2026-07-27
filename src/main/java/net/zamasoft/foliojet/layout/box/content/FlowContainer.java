package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.fragment.FlowCutter;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.Absolutes.Absolute;
import net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.Floatings.Floating;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public class FlowContainer implements Container {
	/**
	 * 通常のフローのコンテンツです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: FlowContainer.java 1631 2022-05-15 05:43:49Z miyabe $
	 */
	protected static class Flow extends BoxHolder {
		public final IFlowBox box;
		public final double pageAxis;

		public Flow(int serial, IFlowBox box, double pageAxis) {
			super(serial);
			this.box = box;
			this.pageAxis = pageAxis;
		}

		public IBox getBox() {
			return this.box;
		}
	}

	/**
	 * 吸収された閉部分木の再生範囲です(C1c)。ボックスを持たず、
	 * restyle 走行の serial 合流順にソース再駆動を発火させます。
	 */
	private static class Replay extends BoxHolder {
		final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range;

		Replay(net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range) {
			super(range.serial());
			this.range = range;
		}

		public IBox getBox() {
			return null;
		}
	}

	protected AbstractContainerBox box;

	protected int serial = 0;

	/**
	 * 通常のフローのコンテンツ。
	 */
	protected List<Flow> flows = null;

	protected Floatings floatings = null;

	protected Absolutes absolutes = null;

	public FlowContainer() {
		// default
	}

	public final void setBox(AbstractContainerBox box) {
		this.box = box;
	}

	public final void addFlow(IFlowBox box, double pageAxis) {
		assert box != null;
		this.addFlow(++this.serial, box, pageAxis);
	}

	private final void addFlow(int serial, IFlowBox box, double pageAxis) {
		assert box != null;
		Flow flow = new Flow(serial, box, pageAxis);
		if (this.flows == null) {
			this.flows = new ArrayList<Flow>();
		}
		this.flows.add(flow);
	}

	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		if (this.absolutes == null) {
			this.absolutes = new Absolutes();
		}
		this.absolutes.addAbsolute(box, staticX, staticY);
	}

	public final void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		if (this.floatings == null) {
			this.floatings = new Floatings();
		}
		final Floating floating = new Floating(++this.serial, box, lineAxis, pageAxis);
		this.floatings.addFloating(floating);
	}

	public boolean hasFlows() {
		return this.flows != null && !this.flows.isEmpty();
	}

	public boolean hasFloatings() {
		return this.floatings != null && this.floatings.getCount() > 0;
	}

	public double getFirstAscent() {
		final Flow flow = this.getFirstFlow();
		if (flow == null) {
			return LayoutUtils.NONE;
		}

		double ascent;
		switch (flow.box.getType()) {
		case BLOCK: {
			AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			double firstAscent = containerBox.getFirstAscent();
			if (LayoutUtils.isNone(firstAscent)) {
				return firstAscent;
			}
			ascent = firstAscent;
		}
			break;

		case TEXT_BLOCK: {
			TextBlockBox textBox = (TextBlockBox) flow.box;
			double firstAscent = textBox.getFirstAscent();
			ascent = firstAscent;
		}
			break;

		case RESCUE:
		case REPLACED:
		case TABLE:
			ascent = flow.box.getHeight();
			break;
		default:
			throw new IllegalStateException(String.valueOf(flow.box.getType()));
		}

		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			ascent += this.box.getFrame().getFrameTop();
			break;
		case WritingMode.RL:
			// 縦書き(モンゴル)
			ascent += this.box.getFrame().getFrameLeft();
			break;
		case WritingMode.LR:
			// 縦書き(日本)
			ascent += this.box.getFrame().getFrameRight();
			break;
		default:
			throw new IllegalStateException();
		}
		return ascent;
	}

	public double getLastDescent() {
		final Flow flow = this.getLastFlow();
		if (flow == null) {
			return LayoutUtils.NONE;
		}

		double descent;
		switch (flow.box.getType()) {
		case BLOCK: {
			final AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			final double lastDescent = containerBox.getLastDescent();
			if (LayoutUtils.isNone(lastDescent)) {
				return lastDescent;
			}
			descent = lastDescent;
		}
			break;

		case TEXT_BLOCK: {
			final TextBlockBox textBox = (TextBlockBox) flow.box;
			double lastDescent = textBox.getLastDescent();
			descent = lastDescent;
		}
			break;

		case RESCUE:
		case REPLACED:
		case TABLE:
			descent = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			descent += this.box.getFrame().getFrameBottom();
			break;
		case WritingMode.RL:
			// 縦書き(日本)
			descent += this.box.getFrame().getFrameLeft();
			break;
		case WritingMode.LR:
			// 縦書き(モンゴル)
			descent += this.box.getFrame().getFrameRight();
			break;
		default:
			throw new IllegalStateException();
		}
		return descent;
	}

	public double getContentSize() {
		final Flow flow = this.getLastFlow();
		if (flow == null) {
			return 0;
		}
		return flow.pageAxis + flow.box.getPageExtent(this.box.getBlockParams().flow);
	}

	public double paintedPageEnd() {
		if (this.absolutes != null) {
			// 絶対配置は静的位置と無関係に描かれうる。読み切れないので
			// 「箱いっぱいに描く」と見なす(安全側)
			return this.box.getInnerPageExtent(this.box.getBlockParams().flow);
		}
		final WritingMode flow = this.box.getBlockParams().flow;
		double end = 0;
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				end = Math.max(end, paintedEndOf(f.pageAxis, f.box, flow));
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				end = Math.max(end, paintedEndOf(floating.pageAxis, floating.box, flow));
			}
		}
		return end;
	}

	public boolean paintsAnything() {
		if (this.absolutes != null) {
			// 絶対配置は静的位置と無関係に描かれうる。読み切れないので
			// 「描く」と見なす(安全側)
			return true;
		}
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				if (((Flow) this.flows.get(i)).box.paintsAnything()) {
					return true;
				}
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				if (this.floatings.getFloating(i).box.paintsAnything()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 何も描かない子({@code paintedPageExtent==0})は<b>位置によらず0</b>を
	 * 寄与します——「ページの奥に置かれた、何も描かない箱」で
	 * {@link #paintedPageEnd()}が0でなくなるのを防ぎます。
	 */
	private static double paintedEndOf(final double pageAxis, final IBox box, final WritingMode flow) {
		final double extent = box.paintedPageExtent(flow);
		return LayoutUtils.compare(extent, 0) <= 0 ? 0 : pageAxis + extent;
	}

	public double getCutPoint(double pageAxis) {
		final WritingMode flow = this.box.getBlockParams().flow;
		if (this.hasFlows()) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				final double bottom = f.pageAxis + f.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
					if (f.box.getType() == BoxType.BLOCK) {
						final FlowBlockBox blockBox = (FlowBlockBox) f.box;
						if (blockBox.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
							pageAxis = bottom;
							break;
						}
						final AbsoluteRectFrame frame = blockBox.getFrame();
						pageAxis = f.pageAxis
								+ blockBox.getContainer()
										.getCutPoint(pageAxis - f.pageAxis - frame.getFramePageStart(flow))
								+ frame.getFramePageStart(flow) + frame.getFramePageEnd(flow);
					} else if (f.box.getType() == BoxType.TEXT_BLOCK) {
						pageAxis = f.pageAxis + ((TextBlockBox) f.box).getCutPoint(pageAxis - f.pageAxis);
					} else {
						pageAxis = bottom;
					}
					break;
				}
			}
		}
		if (this.hasFloatings()) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				final double bottom = floating.pageAxis + floating.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) >= 0) {
					pageAxis = bottom;
					break;
				}
			}
		}
		return pageAxis;
	}

	/**
	 * 浮動体の行方向寸法の最大値を返します(M2c: 使用行寸法の読み取り用)。
	 */
	public double floatingsLineExtent(final WritingMode flow) {
		double max = 0;
		if (this.hasFloatings()) {
			final boolean vertical = flow.isVertical();
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				max = Math.max(max, vertical ? floating.box.getHeight() : floating.box.getWidth());
			}
		}
		return max;
	}

	public double getCutPointBelow(final double pageAxis) {
		final WritingMode flow = this.box.getBlockParams().flow;
		double result = 0;
		if (this.hasFlows()) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow f = (Flow) this.flows.get(i);
				final double bottom = f.pageAxis + f.box.getPageExtent(flow);
				if (LayoutUtils.compare(bottom, pageAxis) <= 0) {
					// 完全に手前に収まるフロー
					result = bottom;
					continue;
				}
				// 提案位置に跨るフロー: 内部の境界を探す
				if (f.box.getType() == BoxType.BLOCK) {
					final FlowBlockBox blockBox = (FlowBlockBox) f.box;
					if (blockBox.getBlockParams().pageBreakInside != PageBreakMode.AVOID) {
						final double frameStart = blockBox.getFrame().getFramePageStart(flow);
						final double inner = blockBox.getContainer()
								.getCutPointBelow(pageAxis - f.pageAxis - frameStart);
						if (LayoutUtils.compare(inner, 0) > 0) {
							result = Math.max(result, f.pageAxis + frameStart + inner);
						}
					}
					// 内部に境界がない場合はブロックの前(直前の result)で切る
				} else if (f.box.getType() == BoxType.TEXT_BLOCK) {
					final double inner = ((TextBlockBox) f.box).getCutPointBelow(pageAxis - f.pageAxis);
					if (LayoutUtils.compare(inner, 0) > 0) {
						result = Math.max(result, f.pageAxis + inner);
					}
				}
				break;
			}
		}
		if (this.hasFloatings()) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating floating = this.floatings.getFloating(i);
				final double top = floating.pageAxis;
				final double bottom = top + floating.box.getPageExtent(flow);
				if (LayoutUtils.compare(top, result) < 0 && LayoutUtils.compare(bottom, result) > 0) {
					// 切断位置に跨る浮動体の前まで引き下げる
					result = top;
				}
			}
		}
		return result;
	}

	public void eachFlowBox(final java.util.function.Consumer<IFlowBox> consumer) {
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				consumer.accept(((Flow) this.flows.get(i)).box);
			}
		}
	}

	protected Flow getFirstFlow() {
		if (this.flows == null || this.flows.isEmpty()) {
			return null;
		}
		return (Flow) this.flows.get(0);
	}

	protected Flow getLastFlow() {
		if (this.flows == null || this.flows.isEmpty()) {
			return null;
		}
		return (Flow) this.flows.get(this.flows.size() - 1);
	}

	/**
	 * {@code avoidBreakBefore}/{@code avoidBreakAfter}の反復化用ワーク
	 * リストの1フレームです(2026-07-20、ARCHITECTURE.md不変条件6)。
	 * ある{@code FlowContainer}の{@code flows}を末尾または先頭から順に
	 * 見ている状態を表します。{@code awaitingChild}は、現在の
	 * {@code index}の{@link FlowBlockBox}が持つ内部コンテナへ降りるため
	 * 子フレームをpushした直後で、その子フレームの解決(popされて
	 * このフレームへ戻ってきた=trueは見つからなかった)を待っている
	 * ことを表します。
	 */
	private static final class AvoidBreakFrame {
		final List<Flow> flows;
		int index;
		boolean awaitingChild;

		AvoidBreakFrame(List<Flow> flows, int index) {
			this.flows = flows;
			this.index = index;
		}
	}

	public boolean avoidBreakBefore() {
		return this.walkAvoidBreak(false);
	}

	public boolean avoidBreakAfter() {
		return this.walkAvoidBreak(true);
	}

	/**
	 * {@code avoidBreakBefore}/{@code avoidBreakAfter}の実装です。
	 *
	 * <p>
	 * 旧実装は{@code FlowContainer.avoidBreak{Before,After}()}が
	 * {@code flow.box.avoidBreak{Before,After}()}を呼び、それが
	 * {@link FlowBlockBox}であれば自分の内部コンテナ(通常は別の
	 * {@code FlowContainer})へ委譲する、というポリモーフィックな相互
	 * 再帰で、深いネスト文書(改ページを跨ぐ開いた祖先チェーン)で
	 * {@code StackOverflowError}を起こしていた(2026-07-20、
	 * {@code DeepNestingRestyleTest}で確認。restyle系の反復化に着手する
	 * 前に、この別系統の再帰も同じ不変条件6違反として発見された)。
	 * </p>
	 *
	 * <p>
	 * 本メソッドは、{@link FlowBlockBox}への降下だけを明示的
	 * {@link Deque}のワークリストへ置き換える(finishLayout等と同じ
	 * 反復DFSパターン)。{@link IFlowBox}の他の実装
	 * ({@link TableBox}・{@link TextBlockBox}・{@link net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox})
	 * と{@link Container}の他の実装({@link ColumnsContainer})はいずれも
	 * 末端(再帰しない)であることを確認済みのため、それらは直接呼び出す。
	 * </p>
	 */
	private boolean walkAvoidBreak(final boolean after) {
		if (this.flows == null || this.flows.isEmpty()) {
			return false;
		}
		final Deque<AvoidBreakFrame> stack = new ArrayDeque<AvoidBreakFrame>();
		stack.push(new AvoidBreakFrame(this.flows, after ? this.flows.size() - 1 : 0));
		while (!stack.isEmpty()) {
			final AvoidBreakFrame frame = stack.peek();
			if (frame.index < 0 || frame.index >= frame.flows.size()) {
				stack.pop();
				continue;
			}
			final Flow flow = frame.flows.get(frame.index);
			final IFlowBox box = flow.box;
			boolean result;
			if (frame.awaitingChild) {
				// 子コンテナへの降下から戻ってきた。子がtrueを見つけて
				// いれば、その時点で既にreturn trueしているため、ここに
				// 来るのはfalseで確定した場合のみ。
				frame.awaitingChild = false;
				result = false;
			} else if (box instanceof FlowBlockBox) {
				final FlowBlockBox flowBlockBox = (FlowBlockBox) box;
				final PageBreakMode mode = after ? flowBlockBox.getFlowPos().pageBreakAfter
						: flowBlockBox.getFlowPos().pageBreakBefore;
				if (mode == PageBreakMode.AVOID) {
					return true;
				}
				final Container inner = flowBlockBox.getContainer();
				if (inner instanceof FlowContainer) {
					final FlowContainer innerFlowContainer = (FlowContainer) inner;
					if (innerFlowContainer.flows != null && !innerFlowContainer.flows.isEmpty()) {
						// 子コンテナへ降りる。戻ってきたら上のawaitingChild
						// 分岐で続き(高さ判定・次の候補への移動)を処理する。
						frame.awaitingChild = true;
						stack.push(new AvoidBreakFrame(innerFlowContainer.flows,
								after ? innerFlowContainer.flows.size() - 1 : 0));
						continue;
					}
					result = false;
				} else {
					// ColumnsContainer等: 再帰しないことを確認済みの末端
					result = after ? inner.avoidBreakAfter() : inner.avoidBreakBefore();
				}
			} else {
				// TableBox/TextBlockBox/FlowReplacedBox: 再帰しない末端
				result = after ? box.avoidBreakAfter() : box.avoidBreakBefore();
			}
			if (result) {
				return true;
			}
			if (box.getHeight() > 0) {
				frame.index = after ? -1 : frame.flows.size();
			} else {
				frame.index += after ? -1 : 1;
			}
		}
		return false;
	}

	public void pushFinishLayoutChildren(IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		if (this.box.isContextBox()) {
			containerBox = (IFramedBox) this.box;
		}
		final IFramedBox childContainerBox = containerBox;
		// 元の走査順(flows→floatings→absolutes、各々先頭から)を保つため、
		// スタックへは逆順(absolutes→floatings→flows、各々末尾から)でpushする
		if (this.absolutes != null) {
			for (int i = this.absolutes.getCount() - 1; i >= 0; --i) {
				final Absolute c = this.absolutes.getAbsolute(i);
				worklist.push(IBox.step(c.box, childContainerBox));
			}
		}
		if (this.floatings != null) {
			for (int i = this.floatings.getCount() - 1; i >= 0; --i) {
				final Floating c = this.floatings.getFloating(i);
				worklist.push(IBox.step(c.box, childContainerBox));
			}
		}
		if (this.flows != null) {
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flows.get(i);
				worklist.push(IBox.step(flow.box, childContainerBox));
			}
		}
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 論理位置→物理座標の変換は LayoutUtils.drawX/drawY に集約する
		// (2026-07-25、vertical-lr対応。従来はここで RL 専用式を手書きしていた)
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			final boolean rescued = isRescuedFrameOwner(c.box);
			if (!rescued && !(c.box.getType() == BoxType.BLOCK && ((FlowPos) c.box.getPos()).offset == null)) {
				continue;
			}
			final double cx = LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis,
					c.pageAxis + c.box.getWidth(), 0);
			final double cy = LayoutUtils.drawY(flow, y, c.pageAxis, 0);
			if (rescued) {
				// 2026-07-25(救済分割・増分6): ブロックを元にした断片は
				// 枠(背景・ボーダー)もこのフレームパスで描かれる
				((net.zamasoft.foliojet.layout.rescue.VisualRescueBox) c.box).pushSourceFramesSteps(pageBox, drawer,
						clip, transform, cx, cy, worklist);
			} else {
				worklist.push(AbstractContainerBox.framesStep((AbstractBlockBox) c.box, pageBox, drawer, clip,
						transform, cx, cy));
			}
		}
	}

	/**
	 * 救済断片のうち、枠(背景・ボーダー)をフレームパスで描くべきもので
	 * あればtrueを返します(2026-07-25、増分6)。
	 *
	 * <p>
	 * 判定は元ボックスの配置が通常フローの{@code offset == null}
	 * (=相対配置でない)かどうかで、非救済のブロックとまったく同じ条件
	 * です。テキストブロックを元にした断片は{@code FlowPos}を持たない
	 * ためここでfalseになり、置換要素を元にした断片は自分の描画で枠を
	 * 描くため{@code pushSourceFramesSteps}側で何も積みません。
	 * </p>
	 */
	private static boolean isRescuedFrameOwner(final IFlowBox box) {
		return box.getType() == BoxType.RESCUE && box.getPos() instanceof FlowPos flowPos && flowPos.offset == null;
	}

	public final void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 論理位置→物理座標は LayoutUtils.drawX/drawY に集約(2026-07-25)
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.drawStep(c.box, pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis, c.pageAxis + c.box.getWidth(), 0),
					LayoutUtils.drawY(flow, y, c.pageAxis, 0)));
		}
	}

	public final void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x,
			double y, Deque<TextShapeStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 論理位置→物理座標は LayoutUtils.drawX/drawY に集約(2026-07-25)
		final WritingMode flow = this.box.getBlockParams().flow;
		final double parentPageExtent = this.box.getInnerWidth();
		// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			final Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.textShapeStep(c.box, pageBox, path, transform,
					LayoutUtils.drawX(flow, x, parentPageExtent, c.pageAxis, c.pageAxis + c.box.getWidth(), 0),
					LayoutUtils.drawY(flow, y, c.pageAxis, 0)));
		}
	}

	public final void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.floatings == null) {
			return;
		}
		this.floatings.pushDraw(this.box, pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
	}

	public final void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.absolutes == null) {
			return;
		}
		this.absolutes.pushDraw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public Container splitPageAxis(double pageLimit, final BreakMode mode, final byte flags) {
		// legacy 契約(null=KEEP / this=MOVE / 他=残余)。plan なしでは
		// フレームは生成されない
		return ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) this.splitPageAxis(pageLimit, mode, flags,
				null)).container();
	}

	/**
	 * 継続化計画付きのページ方向切断です(C1d-C)。単一実装 — legacy の
	 * 3引数版はこの Plain 写像。plan が選択したチェーンメンバー(常に
	 * 末尾フロー)の断片は WithFrame の返り値で親へ伝播する。
	 */
	public net.zamasoft.foliojet.layout.fragment.ContainerCut splitPageAxis(double pageLimit, final BreakMode mode,
			final byte flags, final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		final boolean vertical = this.box.getBlockParams().flow.isVertical();
		final double frameStart = this.box.getFrame().getFramePageStart(this.box.getBlockParams().flow);
		final double pageSize = this.box.getPageExtent(this.box.getBlockParams().flow);
		final double pageInnerSize = this.box.getInnerPageExtent(this.box.getBlockParams().flow);

		// System.err.println("ACB A: flags=" + flags + "/" + mode +
		// "/pageLimit=" + pageLimit + "/vertical="+vertical+"/pageInnerSize=" +
		// pageInnerSize
		// + "/flows.size=" + (this.flows == null ? 0 : this.flows.size())
		// + "/" + this.box.getParams().element);
		if (mode instanceof BreakMode.ForceBreakMode) {
			// 強制改ページが指定されている場合
			FlowContainer nextBox;
			ForceBreakMode force = (ForceBreakMode) mode;
			int index;
			net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame chainFrame = null;
			boolean moved = false;
			net.zamasoft.foliojet.layout.fragment.ChainStopReason chainStopReason = null;
			nextBox = new FlowContainer();
			if (this.box != force.box) {
				index = this.flows.size() - 1;
				byte lflags = (byte) 0xFF;
				if (index != 0) {
					lflags ^= IPageBreakableBox.FLAGS_FIRST;
				}
				if (index != this.flows.size() - 1) {
					lflags ^= IPageBreakableBox.FLAGS_LAST;
				}
				Flow flow = (Flow) this.flows.get(index);
				if (plan != null && plan.selects(flow.box)) {
					// C1d-C: チェーンメンバーの継続化。断片はボックスではなく
					// フレームとして返り値で親へ伝播する
					switch (((AbstractBlockBox) flow.box).splitForContinuation(pageLimit - flow.pageAxis, mode,
							(byte) (lflags & flags), plan)) {
					case SplitResult.Frame(
							final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f) ->
						chainFrame = f;
					case SplitResult.Split(final IPageBreakableBox remainder) -> throw new IllegalStateException(
							"チェーンメンバーは Split を返さない");
					case SplitResult.Keep keep -> {
						// 継続化不成立(chainFrame は null のまま)。box 全体を
						// this 側に残す — 720行目の chainFrame==null 分岐が
						// plain(nextBox) へ自然にフォールバックする
						chainStopReason = net.zamasoft.foliojet.layout.fragment.ChainStopReason.KEEP;
					}
					case SplitResult.Move move -> {
						// box全体をnextBox側へ送る。自動改ページ主ループ
						// (942-950行目)と同様、this.flows側からも除去
						// しないと同一boxが前後ページに二重に残ってしまう
						// (除去自体は下のsplitFloatings呼び出しの後——
						// そちらがthis.flowsの元のサイズを前提にしている)
						nextBox.addFlow(flow.serial, flow.box, 0);
						moved = true;
						chainStopReason = net.zamasoft.foliojet.layout.fragment.ChainStopReason.MOVE;
					}
					}
				} else {
					IPageBreakableBox flowBox = (IPageBreakableBox) flow.box;
					final SplitResult forceResult = flowBox.split(pageLimit - flow.pageAxis, mode,
							(byte) (lflags & flags));
					switch (forceResult) {
					case SplitResult.Split(final IPageBreakableBox remainder) -> nextBox.addFlow(flow.serial,
							(IFlowBox) remainder, 0);
					case SplitResult.Frame frame -> throw new IllegalStateException("継続化は plan の選択なしには起きない");
					case SplitResult.Keep keep -> {
						// box 全体を this 側に残す(nextBox には何も加えない)
					}
					case SplitResult.Move move -> {
						// 同上: this.flows側からも除去する(除去はsplitFloatings
						// 呼び出しの後)
						nextBox.addFlow(flow.serial, flow.box, 0);
						moved = true;
					}
					}
				}
			} else {
				index = this.flows == null ? 0 : this.flows.size();
			}
			final FloatAggregate aggregate = this.aggregateFloatings(pageLimit, flags, index);
			if (moved) {
				// aggregateFloatings(pageLimit, flags, index) は呼び出し時点の
				// this.flows.size()==index+1 を前提に0..index-1を走査する
				// ため、除去はその呼び出しの後に行う(先に除去すると
				// FLAGS_LAST判定がずれる)
				this.flows.remove(index);
			}
			this.attachAggregate(nextBox, aggregate);
			assert nextBox != null;
			assert nextBox != this;
			if (chainFrame != null) {
				return new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(nextBox, chainFrame);
			}
			return chainStopReason != null
					? new net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(nextBox,
							chainStopReason)
					: plain(nextBox);
		}

		final double prevPageSize = pageLimit;
		// 主ループ前の判定は FlowCutter に純化されている(M4-A2)
		final FlowCutter.PreDecision pre = FlowCutter.preDecide(pageLimit, pageSize, pageInnerSize, frameStart, flags,
				this.flows != null && !this.flows.isEmpty());
		if (!(pre instanceof FlowCutter.PreDecision.Proceed(final double adjustedPageLimit))) {
			return plain(switch (pre) {
			case FlowCutter.PreDecision.CutHead(final double atLimit) -> this.cutHead(atLimit, flags);
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatingsKeepingOwner(atLimit,
					flags);
			case FlowCutter.PreDecision.MoveAll moveAll -> this;
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this
					.splitFloatingsMovingOwner(atLimit, flags);
			case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
			case FlowCutter.PreDecision.Proceed proceed -> throw new IllegalStateException();
			});
		}
		pageLimit = adjustedPageLimit;

		// 通常のフローで指定位置にさしかかっているボックスを特定
		final BlockParams params = this.box.getBlockParams();
		final double[] flowBottoms = this.computeFlowBottoms(params);
		int lastOrphan = FlowCutter.lastOrphan(flowBottoms, pageLimit);

		// FlowCutter へ渡す純データ(avoid 押し戻し・後段判定用の計測)
		final FlowMeasurements flowMeasurements = this.measureFlows(params);
		final double[] flowPageStarts = flowMeasurements.pageStarts();
		final double[] flowPageExtents = flowMeasurements.pageExtents();
		final boolean[] avoidBefore = flowMeasurements.avoidBefore();
		final boolean[] avoidAfter = flowMeasurements.avoidAfter();
		final double[] flowPageEndFrames = flowMeasurements.pageEndFrames();
		final FloatMeasurements floatMeasurements = this.measureFloats(params);
		final double[] floatPageStarts = floatMeasurements.pageStarts();
		final double[] floatPageExtents = floatMeasurements.pageExtents();
		final boolean[] floatUncut = floatMeasurements.uncut();

		if (lastOrphan == this.flows.size()) {
			// 切断線以下のフローがない場合
			if ((flags & IPageBreakableBox.FLAGS_LAST) == 0) {
				if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0 || (flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
					return plain(this.cutTail(prevPageSize, flags));
				}
				final double contentHeight = flowPageStarts[this.flows.size() - 1]
						+ flowPageExtents[this.flows.size() - 1];
				if (LayoutUtils.compare(pageInnerSize, contentHeight) > 0) {
					// 自然の高さより高いボックスは切断
					return plain(this.cutTail(prevPageSize, flags));
				}
				// 前のページに残す
				return plain(this.splitFloatingsKeepingOwner(prevPageSize, flags));
			}
			lastOrphan = this.flows.size() - 1;
		}

		FlowContainer nextBox = null;
		boolean ignoreAvoid = false;
		double savePageLimit = pageLimit;
		// B5c-2 Step3(自動改ページ主ループ、2026-07-22再挑戦): plan選択
		// 済みチェーンメンバー(常にthis.flowsの末尾)が一度でも検分され
		// たかどうか。チェーンメンバー自身のKeep/Moveの生値はここでは
		// 使わない(pushback巻き戻しで2回検分されうる非純粋呼び出しの
		// ため、switch直後の値は最終配置と食い違いうると判明——詳細は
		// docs/history/2026-07-22-open-chain-b5c2-autoloop-root-cause.md
		// 参照)。かわりに、コンテナ全体の結論が確定する「nextBox+
		// splitFloatings」の最終returnでのみ、その時点で確定している
		// 事実(nextBoxがチェーンメンバー単体だけを含むか)から
		// PlainWithChainStopを付与するか判断する(force-branchと同型の
		// 状況に限定——複数flowが絡む部分継続は単純な二値では表現できない
		// ため、それ以外はplain()のまま、既存のcontainer-identity比較
		// フォールバックに委ねる)。
		boolean sawChainMember = false;
		// 上から下へチェックする
		for (int i = lastOrphan; i < this.flows.size(); ++i) {
			Flow prevFlow = (Flow) this.flows.get(i);
			final double splitLine = pageLimit - prevFlow.pageAxis;
			byte lflags = (byte) 0xFF;
			if (LayoutUtils.compare(prevFlow.pageAxis, 0) > 0) {
				// ボックスの上端がページの上部から離れている場合は、前ページに残さない。
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			if (((AutoBreakMode) mode).box == this.box || (i != (this.flows.size() - 1))) {
				// 現在のフローまたは途中のフローは自由に扱う
				lflags ^= IPageBreakableBox.FLAGS_LAST;
			}
			byte xflags = (byte) (lflags & flags);

			// System.err.println("M: xflags=" + xflags + "/flags=" + flags
			// + "/flows.size=" + this.flows.size() + "/i=" + i
			// + "/this==box=" + (((AutoBreakMode) mode).box == this)
			// + "/this.box=" + this.box.getParams().element
			// + "/prevFlow=" + prevFlow.box.getParams().element);

			IFlowBox nextFlowBox;
			switch (prevFlow.box.getType()) {
			case TABLE:
			case TEXT_BLOCK: {
				// 2026-07-25(救済分割・増分6): 巨大な行。答申§1のとおり
				// TextBlockBox.split()を呼ぶ**前に**行の物理下端を検査する。
				// LineCutterはフラグメント先頭で実質1行しかなければ無条件に
				// KEEPを返す(=行分割では前進しない)ため、切断結果からは
				// その非進行を区別できないからである。巨大フォント・背の
				// 高いインラインブロック・インラインテーブル・ルビ単位・
				// インライン置換要素は、すべて「背の高い1行」としてこの
				// 一点で捕捉される——個別の分岐は作らない
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						&& prevFlow.box instanceof net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock) {
					final double unbreakableEnd = textBlock.getUnbreakableLinePageEnd();
					if (!LayoutUtils.isNone(unbreakableEnd) && LayoutUtils.compare(splitLine, unbreakableEnd) < 0) {
						final IFlowBox rescued = this.rescueSplit(i, prevFlow, splitLine, prevPageSize);
						if (rescued != null) {
							nextFlowBox = rescued;
							break;
						}
					}
				}
				IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
				nextFlowBox = switch (prevFlowBox.split(splitLine, mode, xflags)) {
				case SplitResult.Keep keep -> null;
				case SplitResult.Move move -> prevFlow.box;
				case SplitResult.Split(final IPageBreakableBox remainder) -> (IFlowBox) remainder;
				case SplitResult.Frame frame -> throw new IllegalStateException(
						"チェーン継続は表・テキストでは起きない");
				};
			}
				break;
			case BLOCK:
				BlockParams cParams = ((AbstractContainerBox) prevFlow.box).getBlockParams();
				// 改ページ禁止でかつページの頭でない場合、またはページ進行方向が違う場合は内部で改ページしない
				if ((cParams.pageBreakInside != PageBreakMode.AVOID || (xflags & IPageBreakableBox.FLAGS_FIRST) != 0)
						&& vertical == cParams.flow.isVertical()) {
					if (plan != null && plan.selects(prevFlow.box)) {
						// C1d-C: チェーンメンバーの継続化。断片はボックスでは
						// なくフレームとして返り値で親へ伝播する
						// (チェーン子は常に末尾のため後続フローの移送はない)
						if (i != this.flows.size() - 1) {
							throw new IllegalStateException("continuation frame child is not the open-tail flow");
						}
						sawChainMember = true;
						switch (((AbstractBlockBox) prevFlow.box).splitForContinuation(splitLine, mode, xflags,
								plan)) {
						case SplitResult.Keep keep -> nextFlowBox = null;
						case SplitResult.Move move -> nextFlowBox = prevFlow.box;
						case SplitResult.Frame(
								final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f) -> {
							// Existing指定では結果は常にcollectedNext自身
							// (移動があれば台帳が装着される)——旧APIの
							// 返り値(=nextBox)と同じ
							final FlowContainer collectedNext = new FlowContainer();
							this.splitFloatings(new FloatTransferTarget.Existing(collectedNext), prevPageSize, flags);
							return new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(collectedNext, f);
						}
						case SplitResult.Split(final IPageBreakableBox remainder) -> throw new IllegalStateException(
								"チェーンメンバーは Split を返さない");
						}
						break;
					}
					IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
					switch (prevFlowBox.split(splitLine, mode, xflags)) {
					case SplitResult.Keep keep -> nextFlowBox = null;
					case SplitResult.Move move -> nextFlowBox = prevFlow.box;
					case SplitResult.Split(final IPageBreakableBox remainder) -> nextFlowBox = (IFlowBox) remainder;
					case SplitResult.Frame frame -> throw new IllegalStateException("継続化は plan の選択なしには起きない");
					}
					break;
				}
				if ((xflags & IPageBreakableBox.FLAGS_LAST) != 0) {
					// 末尾の場合、改ページ禁止は必ず送る
					nextFlowBox = prevFlow.box;
					break;
				}
			case RESCUE:
				// 2026-07-25(救済分割・増分5): 救済断片の続き。断片は
				// 「元ボックスの残余」を表す不可分な箱なので、置換要素と
				// まったく同じ判定でよい(先頭なら再度救済、収まるなら
				// そのまま残す、途中なら丸ごと次フラグメンテナへ)
			case REPLACED: {
				// 置換されたボックス
				double prevFlowPageSize = prevFlow.box.getPageExtent(this.box.getBlockParams().flow);
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						|| LayoutUtils.compare(splitLine, prevFlowPageSize) >= 0) {
					// ページの先頭にある場合、ページ下辺にかかっていない場合は残す
					if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
							&& LayoutUtils.compare(splitLine, prevFlowPageSize) < 0) {
						// 2026-07-25(救済分割・増分4/5): 「フラグメント先頭・
						// 分割不能・なお超過」——現在はここで「はみ出したまま
						// 描画」に落ちる唯一の非進行点(答申§1)
						// 容量の基準は「調整前の切断線」= このコンテナの
						// 始端からフラグメンテナ終端までの距離。コンテナ
						// 自身の内寸(pageInnerSize)は自動高さだと内容に
						// つれて伸びるため基準にならない
						final IFlowBox rescued = this.rescueSplit(i, prevFlow, splitLine, prevPageSize);
						if (rescued != null) {
							nextFlowBox = rescued;
							break;
						}
					}
					nextFlowBox = null;
				} else {
					// 次ページに送る
					nextFlowBox = prevFlow.box;
				}
			}
				break;
			default:
				throw new IllegalStateException(prevFlow.box.toString());
			}

			// System.err.println("ACB H: leave=" + (nextFlowBox == null)
			// + "/pass=" + (nextFlowBox == prevFlow.box) + "/i=" + i
			// + "/lastOrphan="+lastOrphan+ "/xflags="+xflags+"/" +
			// this.box.getParams().element);
			if (nextFlowBox == null) {
				if ((xflags & IPageBreakableBox.FLAGS_LAST) != 0) {
					// ページの末尾で残す場合は、全て残す
					return plain(null);
				}
				if (i >= lastOrphan) {
					// 改ページ禁止により牽引されていない
					continue;
				}
				// 続くボックスで牽引する
				nextFlowBox = prevFlow.box;
			}
			if (nextFlowBox == prevFlow.box) {
				// 分割不可能な場合
				if ((lflags & IPageBreakableBox.FLAGS_FIRST) != 0) {
					// ボックスの先頭
					pageLimit = savePageLimit;
					if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
						// 強制切断
						return plain(this.cutHead(prevPageSize, flags));
					}
					if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
						// ページの先頭
						if (i < lastOrphan) {
							// 改ページ禁止を無視する
							i = lastOrphan - 1;
							ignoreAvoid = true;
							continue;
						}
						if ((flags & IPageBreakableBox.FLAGS_LAST) != 0) {
							// 末尾なら切断
							return plain(this.cutTail(prevPageSize, flags));
						}
						// 前ページに残す
						return plain(this.splitFloatingsKeepingOwner(prevPageSize, flags));
					}
					// 全部移動
					return plain(this);
				}
				if (!ignoreAvoid && i > 0 && i <= lastOrphan) {
					// ボックスの2つめ以降の要素に限る
					// ブロック間の改ページ禁止のチェック(判定は FlowCutter に純化)
					final FlowCutter.AvoidPushback pushback = FlowCutter.avoidPushback(i, pageLimit, flowPageStarts,
							flowPageExtents, avoidBefore, avoidAfter, flowPageEndFrames, floatPageStarts,
							floatPageExtents, floatUncut);
					if (pushback != null) {
						// ブロック間の改ページ禁止の場合
						i = pushback.resumeIndex();
						pageLimit = pushback.newPageLimit();
						continue;
					}
				}
				nextBox = new FlowContainer();
				nextBox.flows = new ArrayList<Flow>();
			} else {
				nextBox = new FlowContainer();
				nextBox.addFlow(nextFlowBox, 0);
				++i;
			}
			int remove = 0;
			for (int j = i; j < this.flows.size(); ++j) {
				Flow f = (Flow) this.flows.get(j);
				nextBox.flows.add(f);
				++remove;
			}
			for (int j = 0; j < remove; ++j) {
				this.flows.remove(this.flows.size() - 1);
			}
			break;
		}

		if (nextBox == null) {
			// ブロックを残す(末尾のブロックを残すことはない)。判定は FlowCutter に純化
			assert !((flags & IPageBreakableBox.FLAGS_LAST) != 0 && ((AutoBreakMode) mode).box != this.box);
			final double lastFlowBottom = flowPageStarts[this.flows.size() - 1]
					+ flowPageExtents[this.flows.size() - 1];
			return plain(switch (FlowCutter.tailDecide(flags, lastOrphan, pageInnerSize, lastFlowBottom, prevPageSize)) {
			case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatingsKeepingOwner(atLimit,
					flags);
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this
					.splitFloatingsMovingOwner(atLimit, flags);
			default -> throw new IllegalStateException();
			});
		}

		// nextBoxがチェーンメンバー一つだけを含む(=force-branchと同型の
		// 「識別では判別できない素の全体move」)場合に限り
		// PlainWithChainStopを付与する。複数flowが絡む場合(pushback巻き
		// 戻しで途中の兄弟が実際の切断点になったケース等)は既存の
		// container-identity比較へ安全にフォールバックさせる
		final boolean chainMemberAlone = sawChainMember && nextBox.flows != null && nextBox.flows.size() == 1;
		// Existing指定では結果は常にnextBox自身(移動があれば台帳が装着
		// される)——旧APIの返り値(=nextBox)と同じ
		this.splitFloatings(new FloatTransferTarget.Existing(nextBox), prevPageSize, flags);
		final Container splitResult = nextBox;
		return chainMemberAlone
				? new net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(splitResult,
						net.zamasoft.foliojet.layout.fragment.ChainStopReason.MOVE)
				: plain(splitResult);
	}

	private static net.zamasoft.foliojet.layout.fragment.ContainerCut plain(final Container container) {
		return new net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain(container);
	}

	/**
	 * 救済分割(visual rescue split)の、通常フローにおける唯一の差し込み
	 * 地点です(2026-07-25新設。仕様と設計判断の根拠は
	 * {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}の
	 * クラス説明に集約しています)。
	 *
	 * <p>
	 * 呼ばれるのは「フラグメント先頭・分割不能・なお超過」という
	 * <b>非進行点</b>——現在「はみ出したまま描画」に落ちる唯一の地点——
	 * だけです。通常経路(収まる/一度の延期で収まる)は一切通りません。
	 * </p>
	 *
	 * <p>
	 * 断片の運搬は<b>既存の残余運搬機構にそのまま乗ります</b>:
	 * {@code this.flows}の当該要素を先頭断片(head)へ差し替え、残余断片
	 * (tail)を戻り値として返すと、呼び出し側の
	 * {@code SplitResult.Split(remainder)}と同じ経路で次フラグメンテナの
	 * コンテナへ載ります。答申§2の「全断片を先に作らず、各改ページで
	 * head一個とtail一個だけ作る」がそのまま実現されます。
	 * </p>
	 *
	 * @param index     {@code this.flows}での位置(head へ差し替える)
	 * @param prevFlow  非進行点に到達したフロー
	 * @param available このフラグメンテナで使えるページ方向の量
	 * @param capacity  フラグメンテナのページ方向内寸(極小断片の判定用)
	 * @return 次フラグメンテナへ送る残余断片。救済しないなら{@code null}
	 */
	private IFlowBox rescueSplit(final int index, final Flow prevFlow, final double available, final double capacity) {
		final IFlowBox box = prevFlow.box;
		final WritingMode progression = this.box.getBlockParams().flow;
		final IFlowBox source;
		final double sourcePageExtent;
		final double offset;
		if (box instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox fragment) {
			// 救済済み断片の続き。区間はoffset/sliceExtentだけで表す
			// (断片の断片は作らない)
			source = (IFlowBox) fragment.getSource();
			sourcePageExtent = fragment.getSourcePageExtent();
			offset = fragment.getOffset();
		} else {
			source = box;
			sourcePageExtent = box.getPageExtent(progression);
			offset = 0;
		}
		final net.zamasoft.foliojet.layout.rescue.RescueDecision decision = net.zamasoft.foliojet.layout.rescue.RescueStats
				.record(net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner.planInFragmentainer(
						box.getPos().getType(), true, capacity, available, sourcePageExtent, offset));
		if (!(decision instanceof net.zamasoft.foliojet.layout.rescue.RescueDecision.Slice slice)) {
			return null;
		}
		if (!net.zamasoft.foliojet.layout.rescue.RescuePolicy.isEnabled()) {
			// テスト専用の注入点(従来の挙動との比較用)。本番は常に有効
			return null;
		}
		if (!isRescueEnabled(box)) {
			return null;
		}
		if (slice.lastFragment()) {
			// 呼び出し条件(なお超過)からここには来ない。念のため救済しない
			return null;
		}
		final double tailOffset = slice.nextOffset();
		final double tailExtent = sourcePageExtent - tailOffset;
		// 実行時の前進検査(答申§5「offsetの厳密増加を実行時にも検査し、
		// 失敗時はtailを作らない」)。判定器の不変条件と二重になるが、
		// 無限ループの不在は絶対要件なので実行時にも守る
		if (!(tailOffset > offset) || !(tailExtent > 0)) {
			return null;
		}
		final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox head = new net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox(
				source, progression, sourcePageExtent, slice.offset(), slice.sliceExtent());
		final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox tail = new net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox(
				source, progression, sourcePageExtent, tailOffset, tailExtent);
		this.flows.set(index, new Flow(prevFlow.serial, head, prevFlow.pageAxis));
		net.zamasoft.foliojet.layout.rescue.RescueStats.recordEnabled();
		return tail;
	}

	/**
	 * 救済分割を実際に有効にする範囲です。仕様と設計判断の根拠は
	 * {@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}の
	 * クラス説明に集約しています(ここには置きません)。
	 *
	 * <p>
	 * <b>ここはクラス列挙ではありません</b>。この判定に到達する時点で
	 * 「フラグメント先頭・分割不能・なお超過」というエンジン自身の分類は
	 * 済んでいます(答申§4)——
	 * </p>
	 *
	 * <ul>
	 * <li>{@code REPLACED}は元から分割の入口を持たない。</li>
	 * <li>{@code TEXT_BLOCK}は「先頭行が容量を超えている」という、行分割
	 * では前進できない形でだけここへ来る(呼び出し元の事前検査)。</li>
	 * <li>{@code BLOCK}は、書字方向が幹と食い違う等でエンジンが
	 * <b>atomicに分類してREPLACED経路へフォールスルーさせた</b>ものだけが
	 * ここへ来る。通常の(幹と同方向の)ブロックは自分のコンテナで再帰的に
	 * 分割されるため、この地点には到達しない。</li>
	 * <li>{@code RESCUE}は救済済み断片の続き。</li>
	 * </ul>
	 *
	 * <p>
	 * フラグメンテナの種類(ページ・段・表セル)による差はありません。
	 * 段組の中・表セルの中でも、判定({@code prevPageSize}=そのフラグメン
	 * テナ容量)も運搬(残余を戻り値で親へ返す)もまったく同一の経路です。
	 * </p>
	 *
	 * <p>
	 * <b>有効化していないもの</b>: {@code TABLE}(表全体を幾何学的に切る
	 * 経路)。理由は{@code VisualRescuePlanner}のクラス説明§4。
	 * </p>
	 */
	private static boolean isRescueEnabled(final IFlowBox box) {
		return switch (box.getType()) {
		case REPLACED, BLOCK -> box.getPos().getType() == PosType.FLOW;
		case TEXT_BLOCK -> box.getPos().getType() == PosType.TEXT_BLOCK;
		case RESCUE -> true;
		default -> false;
		};
	}

	/**
	 * splitPageAxis の切断判定に渡すフロー計測値(FlowCutter.avoidPushback/tailDecide 用の純データ)。
	 */
	private record FlowMeasurements(double[] pageStarts, double[] pageExtents, boolean[] avoidBefore,
			boolean[] avoidAfter, double[] pageEndFrames) {
	}

	/**
	 * splitPageAxis の切断判定に渡すフロート計測値。フロートが無い場合は全フィールド null(旧コードの契約を維持)。
	 */
	private record FloatMeasurements(double[] pageStarts, double[] pageExtents, boolean[] uncut) {
	}

	/**
	 * 各フローの「内容の下端(ページ軸上の到達位置)」を計算します。
	 * この製品の内部規約(縦書きの pageAxis は常に右→左、LR は描画段で反転)により、
	 * RL/LR は別枝で境界フレームの辺を選びます。
	 */
	private double[] computeFlowBottoms(final BlockParams params) {
		final double[] flowBottoms = new double[this.flows.size()];
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			double lastBottom = flow.pageAxis;
			if (flow.box.getType() == BoxType.BLOCK) {
				final FlowBlockBox flowBlock = (FlowBlockBox) flow.box;
				switch (params.flow) {
				case WritingMode.TB: {
					// 横書き
					lastBottom += Math.max(flowBlock.getInnerHeight(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameTop();
					break;
				}
				case WritingMode.RL: {
					// 縦書き(日本語)
					lastBottom += Math.max(flowBlock.getInnerWidth(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameRight();
					break;
				}
				case WritingMode.LR: {
					// 縦書き(モンゴル)
					lastBottom += Math.max(flowBlock.getInnerWidth(), flowBlock.getContentSize())
							+ flowBlock.getFrame().getFrameLeft();
					break;
				}
				default:
					throw new IllegalStateException();
				}
			} else {
				lastBottom += flow.box.getPageExtent(params.flow);
			}
			flowBottoms[i] = lastBottom;
		}
		return flowBottoms;
	}

	private FlowMeasurements measureFlows(final BlockParams params) {
		final double[] flowPageStarts = new double[this.flows.size()];
		final double[] flowPageExtents = new double[this.flows.size()];
		final boolean[] avoidBefore = new boolean[this.flows.size()];
		final boolean[] avoidAfter = new boolean[this.flows.size()];
		final double[] flowPageEndFrames = new double[this.flows.size()];
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			flowPageStarts[i] = flow.pageAxis;
			flowPageExtents[i] = flow.box.getPageExtent(params.flow);
			avoidBefore[i] = flow.box.avoidBreakBefore();
			avoidAfter[i] = flow.box.avoidBreakAfter();
			flowPageEndFrames[i] = flow.box.getType() == BoxType.BLOCK
					? ((AbstractContainerBox) flow.box).getFrame().getFramePageEnd(params.flow)
					: 0;
		}
		return new FlowMeasurements(flowPageStarts, flowPageExtents, avoidBefore, avoidAfter, flowPageEndFrames);
	}

	private FloatMeasurements measureFloats(final BlockParams params) {
		if (this.floatings == null) {
			return new FloatMeasurements(null, null, null);
		}
		final int floatCount = this.floatings.getCount();
		final double[] floatPageStarts = new double[floatCount];
		final double[] floatPageExtents = new double[floatCount];
		final boolean[] floatUncut = new boolean[floatCount];
		for (int k = 0; k < floatCount; ++k) {
			final Floating floating = this.floatings.getFloating(k);
			floatPageStarts[k] = floating.pageAxis;
			floatPageExtents[k] = floating.box.getPageExtent(params.flow);
			// RESCUE(救済断片)は通常の意味では切断不能——行・行グループの
			// ような内部の切断点を持たない。幾何学的な救済切断はこの
			// avoid押し戻しの判断とは別の話なので、置換要素とまったく
			// 同じ扱いにする(2026-07-25、増分7)
			floatUncut[k] = floating.box.getType() == BoxType.REPLACED || floating.box.getType() == BoxType.RESCUE
					|| ((AbstractContainerBox) floating.box).getBlockParams().pageBreakInside == PageBreakMode.AVOID;
		}
		return new FloatMeasurements(floatPageStarts, floatPageExtents, floatUncut);
	}

	/**
	 * 浮動ボックス(直接保持分+子flowの再帰集約)をページ分割し、移動分の
	 * 行き先を型で返します(2026-07-24、P2-4。分岐表の正本:
	 * {@code docs/history/2026-07-24-p2-splitfloatings-branch-table.md}の
	 * 「public 3引数版」の表と1:1対応)。
	 *
	 * <table>
	 * <caption>行き先の写像</caption>
	 * <tr><td>移動なし</td><td>{@link FloatTransferResult#KEEP_OWNER}
	 * (呼び出し側はtargetのコンテナをそのまま使う)</td></tr>
	 * <tr><td>MoveAllかつtarget=MOVE_OWNER</td>
	 * <td>{@link FloatTransferResult#MOVE_OWNER}</td></tr>
	 * <tr><td>MoveAllかつtarget=KEEPかつ非FIRSTかつinnerPageExtent&lt;=0</td>
	 * <td>{@link FloatTransferResult#MOVE_OWNER}(<b>空コンテナ全体を
	 * floatごと移動する特例</b>——台帳はownerに付いたまま)</td></tr>
	 * <tr><td>その他(移動あり)</td><td>{@code Remainder}(targetが
	 * Existingならそのコンテナへ、KEEP/MOVE_OWNERなら新FlowContainerへ
	 * 台帳を装着)</td></tr>
	 * </table>
	 */
	public FloatTransferResult splitFloatings(final FloatTransferTarget target, final double pageLimit,
			final byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 || target instanceof FloatTransferTarget.Existing;
		final int flowCount = this.flows == null ? 0 : this.flows.size();
		return switch (this.aggregateFloatings(pageLimit, flags, flowCount)) {
		case FloatAggregate.None none -> FloatTransferResult.KEEP_OWNER;
		case FloatAggregate.OwnerAll ownerAll -> {
			if (target instanceof FloatTransferTarget.MoveOwner) {
				yield FloatTransferResult.MOVE_OWNER;
			}
			if (target instanceof FloatTransferTarget.Keep && (flags & IPageBreakableBox.FLAGS_FIRST) == 0
					&& LayoutUtils.compare(this.box.getInnerPageExtent(this.box.getBlockParams().flow), 0) <= 0) {
				// 空コンテナ全体をfloatごと移動する特例(分岐表)
				yield FloatTransferResult.MOVE_OWNER;
			}
			final Floatings moved = this.floatings;
			this.floatings = null;
			yield remainderWith(target, moved);
		}
		case FloatAggregate.Detached(final Floatings moved) -> remainderWith(target, moved);
		};
	}

	/**
	 * target={@code KEEP}での型付き呼び出しを、切断経路の既存Container契約
	 * (null=移動なし / this=owner丸ごと移動 / 新=残余コンテナ)へ写す補助
	 * です(P2-4)。この契約の消費側({@code ContainerCut.Plain})はP2の
	 * 対象外。
	 */
	private Container splitFloatingsKeepingOwner(final double pageLimit, final byte flags) {
		return switch (this.splitFloatings(FloatTransferTarget.KEEP, pageLimit, flags)) {
		case FloatTransferResult.KeepOwner keepOwner -> null;
		case FloatTransferResult.MoveOwner moveOwner -> this;
		case FloatTransferResult.Remainder(final FlowContainer container) -> container;
		};
	}

	/**
	 * target={@code MOVE_OWNER}(owner自身が丸ごと次フラグメントへ移動する
	 * 文脈)での型付き呼び出しの補助です(P2-4)。移動なしでもownerが移動
	 * するため、KeepOwner/MoveOwnerのどちらもthisになる(旧APIで
	 * {@code nextBox==this}を渡していた契約と同一)。
	 */
	private Container splitFloatingsMovingOwner(final double pageLimit, final byte flags) {
		return switch (this.splitFloatings(FloatTransferTarget.MOVE_OWNER, pageLimit, flags)) {
		case FloatTransferResult.KeepOwner keepOwner -> this;
		case FloatTransferResult.MoveOwner moveOwner -> this;
		case FloatTransferResult.Remainder(final FlowContainer container) -> container;
		};
	}

	private static FloatTransferResult remainderWith(final FloatTransferTarget target, final Floatings moved) {
		final FlowContainer container = target instanceof FloatTransferTarget.Existing(final FlowContainer existing)
				? existing
				: new FlowContainer();
		container.floatings = moved;
		return new FloatTransferResult.Remainder(container);
	}

	public final java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags) {
		final int flowCount = this.flows == null ? 0 : this.flows.size();
		return switch (this.aggregateFloatings(pageLimit, flags, flowCount)) {
		case FloatAggregate.None none -> java.util.Optional.empty();
		case FloatAggregate.OwnerAll ownerAll -> {
			// 自分の台帳をdetachして返す(子flow再帰の内部契約)
			final Floatings moved = this.floatings;
			this.floatings = null;
			yield java.util.Optional.of(moved);
		}
		case FloatAggregate.Detached(final Floatings moved) -> java.util.Optional.of(moved);
		};
	}

	/**
	 * 再帰集約の内部結果です(P2-4。codex設計§2.3の局所状態
	 * {@code NONE/OWNER_ALL/DETACHED}を型で表す。外へは公開しない)。
	 */
	private sealed interface FloatAggregate {
		/** 移動するfloatなし。 */
		record None() implements FloatAggregate {
		}

		/**
		 * ownerの直接保持分が丸ごと移動——台帳はまだownerに付いたまま
		 * (遅延detach。付け替えは呼び出し側が確定する)。
		 */
		record OwnerAll() implements FloatAggregate {
		}

		/**
		 * 移動台帳(ownerからdetach済みの自台帳、直接分割のremainder、
		 * または子から引き取ったFloatings)。
		 */
		record Detached(Floatings floatings) implements FloatAggregate {
		}
	}

	private static final FloatAggregate AGGREGATE_NONE = new FloatAggregate.None();
	private static final FloatAggregate AGGREGATE_OWNER_ALL = new FloatAggregate.OwnerAll();

	/**
	 * 直接保持分と子flow [0..index) の浮動ボックスを分割・集約します
	 * (P2-4で旧private 3引数版のsentinel状態機械を型付きへ置換)。
	 */
	private FloatAggregate aggregateFloatings(final double pageLimit, final byte flags, final int index) {
		// 入口final snapshot(addBound事故の教訓——codex設計§2.5)。
		// lflagsのLAST判定は旧実装では「現在の」this.flows.size()を見ていた
		// (ループ上限indexは呼び出し時点のスナップショットという非対称)。
		// このメソッドの実行中this.flowsは変異しない(子再帰は子自身の
		// containerのみを変異させる)ため、入口snapshotと現在値は常に一致
		// し、snapshot化は等価。呼び出し元の変異順序もこの前提を守っている
		// (force-branchの「flow除去はsplitFloatings呼び出しの後」コメント)。
		final int originalFlowCount = this.flows == null ? 0 : this.flows.size();
		assert index <= originalFlowCount;
		FloatAggregate state;
		if (this.floatings != null) {
			// 直接保持分を分割
			state = switch (this.floatings.splitPageAxis(this.box, pageLimit, flags)) {
			case FloatSplitResult.KeepAll keepAll -> AGGREGATE_NONE;
			case FloatSplitResult.MoveAll moveAll -> AGGREGATE_OWNER_ALL;
			case FloatSplitResult.Partition(final Floatings remainder) -> new FloatAggregate.Detached(remainder);
			};
			if (this.floatings.getCount() == 0) {
				// 旧実装からの防御(plan駆動commitのPartitionはsource側を
				// 空にしないため、現行では到達しない)
				this.floatings = null;
			}
		} else {
			state = AGGREGATE_NONE;
		}
		for (int i = 0; i < index; ++i) {
			final Flow flow = (Flow) this.flows.get(i);
			byte lflags = (byte) 0xFF;
			if (i != 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			if (i != originalFlowCount - 1) {
				lflags ^= IPageBreakableBox.FLAGS_LAST;
			}
			switch (flow.box.getType()) {
			case RESCUE:
				// 2026-07-25(救済分割・増分6): 救済断片は排除域の台帳を
				// 持たず、子コンテナへも降りない。増分6で自前のコンテナを
				// 持つ元ボックス(書字方向が食い違うブロック・テキスト
				// ブロック)が来るようになったが、<b>何もしないのが正しい</b>。
				//
				// 理由: 救済断片は「元ボックス全体を、消費済み量だけずらして
				// クリップして描く」ものである(答申§2)。元ボックスの中の
				// フロートは、その元ボックスの描画の一部として、各断片の
				// クリップ内に見える範囲だけが描かれる。ここで
				// detachMovedFloatings して次フラグメントの台帳へ移すと、
				// (a) 元ボックスからフロートが失われるため先頭断片で消え、
				// (b) 次フラグメントでは元の幾何を無視した新しい位置へ
				// 置き直される——「レイアウト計算は変えない。同じ箱を
				// ずらしてクリップするだけ」という設計の核を破る。
				//
				// 断片がフラグメント上で占める量(=この断片の排除域の高さ)は
				// sliceExtent であり、それは flow.box.getPageExtent() が
				// 返す値そのものなので、追加の帳簿は要らない。残余(tail)は
				// 次フラグメントで通常どおり配置され、そこで同じ規則が
				// 適用される(答申§5)。
				assert flow.box instanceof net.zamasoft.foliojet.layout.rescue.VisualRescueBox : flow.box;
				break;
			case BLOCK:
				final AbstractContainerBox blockBox = (AbstractContainerBox) flow.box;
				double pageAxis = pageLimit - flow.pageAxis;
				pageAxis -= blockBox.getFrame().getFramePageStart(blockBox.getBlockParams().flow);
				// 子は自分の台帳をdetachして返す(detachMovedFloatingsの再帰)
				final java.util.Optional<Floatings> childDetached = blockBox.getContainer()
						.detachMovedFloatings(pageAxis, (byte) (lflags & flags));
				if (childDetached.isEmpty()) {
					break;
				}
				final Floatings childFloatings = childDetached.get();
				switch (state) {
				case FloatAggregate.None none ->
					// 子のFloatingsオブジェクトをそのまま採用(コンテナごと引き取り)
					state = new FloatAggregate.Detached(childFloatings);
				case FloatAggregate.OwnerAll ownerAll -> {
					// 子float追加時に初めてownerからdetachが確定する
					final Floatings owned = this.floatings;
					this.floatings = null;
					for (int j = 0; j < childFloatings.getCount(); ++j) {
						owned.addFloating(childFloatings.getFloating(j));
					}
					state = new FloatAggregate.Detached(owned);
				}
				case FloatAggregate.Detached(final Floatings moved) -> {
					for (int j = 0; j < childFloatings.getCount(); ++j) {
						moved.addFloating(childFloatings.getFloating(j));
					}
				}
				}
				break;
			}
		}
		assert !(state instanceof FloatAggregate.Detached(final Floatings moved) && moved.getCount() == 0);
		return state;
	}

	private FlowContainer cutHead(double pageLimit, byte flags) {
		if (pageLimit < 0) {
			pageLimit = 0;
		}
		FlowContainer nextBox = new FlowContainer();
		if (this.flows != null) {
			nextBox.flows = this.flows;
			this.flows = null;
		}
		// flowsは先にnextBoxへ移送済みのため、集約対象は直接保持分のみ
		// (index=0。this.flows==nullなのでLAST判定にも影響しない)
		this.attachAggregate(nextBox, this.aggregateFloatings(pageLimit, flags, 0));
		return nextBox;
	}

	private FlowContainer cutTail(double pageLimit, byte flags) {
		FlowContainer nextBox = new FlowContainer();
		int flowCount = this.flows == null ? 0 : this.flows.size();
		this.attachAggregate(nextBox, this.aggregateFloatings(pageLimit, flags, flowCount));
		return nextBox;
	}

	/**
	 * 集約結果の移動台帳を{@code nextBox}へ装着します(P2-4。旧
	 * 「nextBox.floatings代入+identity比較でthis.floatings=null」の置換)。
	 */
	private void attachAggregate(final FlowContainer nextBox, final FloatAggregate aggregate) {
		switch (aggregate) {
		case FloatAggregate.None none -> {
		}
		case FloatAggregate.OwnerAll ownerAll -> {
			nextBox.floatings = this.floatings;
			this.floatings = null;
		}
		case FloatAggregate.Detached(final Floatings moved) -> nextBox.floatings = moved;
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		if (this.flows == null) {
			return;
		}
		// 元の走査順を保つため、スタックへは逆順でpushする
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			// 通常のフロー
			Flow c = (Flow) this.flows.get(i);
			worklist.push(IBox.getTextStep(c.box, textBuff));
		}
	}

	/**
	 * stampRanges 済みの閉部分木をコンテナから吸収します(C1c)。最上位の
	 * 閉じた plain ブロック(restyle 走行で replay-subtree になるもの:
	 * BLOCK・非フロート・書字方向一致)のうち再生範囲が記録されたものを
	 * フローから除去し、serial 付きの再生範囲として返します。resume は
	 * これを {@link #restyle(BlockBuilder, int, boolean, List)} の prefix に
	 * 渡し、serial 順で残アイテムと合流させて再駆動します。
	 * 呼び出しはソースログ水位の計算後であること(吸収されたアイテムは
	 * コンテナを歩く水位計算から見えなくなるため)。
	 */
	public final List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> extractReplayable(
			final java.util.Map<IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges,
			final boolean rootVertical, final int walkDepth) {
		if (this.flows == null || ranges.isEmpty()) {
			return List.of();
		}
		List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix = null;
		// walkDepth >= 1 のとき末尾フローは開いた継続(depth>1 なら moved-open
		// チェーン子、depth==1 なら開きテキスト)であり、たとえソースログ上で
		// 閉じていても(イベント全着)flowStack への再積みが必要なため吸収
		// しない。また末尾を抜くと開き判定(lastFlow)が前のアイテムへ
		// ずれる — C1b までは walk 時の lastFlow 判定が replay より先に
		// 効いて守られていた条件の、記録時への移し替え
		int limit = walkDepth >= 1 ? this.flows.size() - 1 : this.flows.size();
		for (int i = 0; i < limit;) {
			final Flow flow = this.flows.get(i);
			if (flow.box.getType() != BoxType.BLOCK || flow.box.getPos().getType() == PosType.FLOAT
					|| ((AbstractContainerBox) flow.box).getBlockParams().flow.isVertical() != rootVertical) {
				// 表・置換・テキスト・縦横混在は従来経路のまま
				++i;
				continue;
			}
			final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range = ranges.remove(flow.box);
			if (range == null) {
				++i;
				continue;
			}
			if (prefix == null) {
				prefix = new ArrayList<>();
			}
			prefix.add(new net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange(flow.serial, range.fromId(),
					range.toId()));
			this.flows.remove(i);
			--limit;
		}
		if (prefix == null) {
			return List.of();
		}
		if (this.flows.isEmpty()) {
			this.flows = null;
		}
		return prefix;
	}

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes) {
		this.restyle(builder, shape, restyleAbsolutes, List.of());
	}

	/**
	 * 吸収された再生範囲(C1c)を serial 順で合流させながら再開します。
	 */
	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		if (isWorklistMode()) {
			// 2026-07-22(B6a1): 明示スタック駆動のworklist executor
			// (`RootBuilder`が適格判定した継続のterminal restyleのみ、
			// pushWorklistOverride/popWorklistOverrideで囲まれている間)。
			// 呼び出し元(AbstractContainerBox.restyle/RootBuilder
			// .restyleFrame)は一切変更せずこのpublic restyle()自体が
			// 分岐する。
			this.restyleWorklist(builder, shape, restyleAbsolutes, prefix);
			return;
		}
		// トレース・水位の互換表示は旧 depth 値
		final int depth = shape.depth();
		final CollectedItems collected = this.collectItems(builder, restyleAbsolutes, prefix);
		if (collected.items() != null) {
			Collections.sort(collected.items());
			moveOpenChainTailLast(collected.items(), collected.lastFlow(), shape);
			int size = collected.items().size();
			for (int i = 0; i < size; ++i) {
				this.restyleItem(builder, collected.items(), i, size, collected.lastFlow(), shape, depth,
						RECURSIVE_DESCENDER);
			}
		}
	}

	/**
	 * 継続ごとの限定的なworklist有効化カウンタです(2026-07-22新設、
	 * B6a1)。{@code RootBuilder}が「この継続はrooted PAGE/COLUMNで
	 * 残存tailが全てPLAIN_FLOW」({@code WorklistTailGate
	 * .WORKLIST_ELIGIBLE})と判定した場合にのみ
	 * {@link #pushWorklistOverride}/{@link #popWorklistOverride}で
	 * 該当のterminal restyle呼び出しだけを囲む(try/finally必須)。
	 * カウンタ(bool ではなく)にしているのは、入れ子の破断
	 * (`ResumeTrace`の「nested resume」相当)でも安全に対称にpush/pop
	 * できるようにするため。
	 */
	private static final ThreadLocal<int[]> worklistOverrideDepth = ThreadLocal.withInitial(() -> new int[1]);

	/**
	 * {@link #worklistOverrideDepth}を1増やします。必ず{@link #popWorklistOverride}とtry/finallyで対にすること。
	 *
	 * <p>
	 * 2026-07-25(独立レビュー指摘): 素の{@code static int}だと複数変換の
	 * 並行実行で他スレッドのrestyleまでworklist扱いになり得たため
	 * ThreadLocalへ変更した。2026-07-24に{@code ContinuationStats}の
	 * 継続経路スタックと{@code ResumeTrace}のバッファを同じ理由で
	 * ThreadLocal化した際の取りこぼし。
	 * </p>
	 */
	public static void pushWorklistOverride() {
		++worklistOverrideDepth.get()[0];
	}

	/**
	 * {@link #worklistOverrideDepth}を1減らします。
	 *
	 * <p>
	 * 2026-07-25(独立レビュー指摘): 0まで戻ったらThreadLocalを除去する。
	 * この変換で二度と使わない値をスレッドへ残すと、スレッドプール上では
	 * スレッドの寿命だけ滞留するため。あわせて非対称なpop(pushとの
	 * try/finally対応が崩れている)を即時例外にする——静かに負の値へ
	 * 落ちると、以後この変換の間ずっとworklist経路が無効化され、
	 * 「なぜかlegacy経路を通る」という追いにくい不具合になる。
	 * </p>
	 */
	public static void popWorklistOverride() {
		final int[] depth = worklistOverrideDepth.get();
		if (depth[0] <= 0) {
			worklistOverrideDepth.remove();
			throw new IllegalStateException("popWorklistOverride without a matching push");
		}
		if (--depth[0] == 0) {
			worklistOverrideDepth.remove();
		}
	}

	/**
	 * {@code OpenChain}を明示スタックで駆動するworklist executorの
	 * 選択判定です(2026-07-22新設、B6a1)。{@link #worklistOverrideDepth}
	 * >0——`RootBuilder`が継続ごとに適格性判定({@code WorklistTailGate})
	 * した上で有効化している間——のみ真。広範囲検証(12+414+591文書・
	 * 3層検証すべてlegacyと完全一致)を経て、2026-07-24に
	 * `foliojet.openTailExecutor`切替スイッチ(グローバル強制worklist/
	 * legacy退避)は撤去した(B6検証インフラ退役)。
	 */
	private static boolean isWorklistMode() {
		return worklistOverrideDepth.get()[0] > 0;
	}

	/**
	 * worklist executorの1段です(2026-07-22新設、B6a1)。sort済み
	 * {@code items}・次に処理するindex・その段の{@code lastFlow}/
	 * {@code shape}/trace用{@code depth}を保持する可変クラス。
	 */
	private static final class RestyleFrame {
		final List<BoxHolder> items;
		final Flow lastFlow;
		final net.zamasoft.foliojet.layout.fragment.OpenShape shape;
		final int depth;
		int nextIndex = 0;

		RestyleFrame(List<BoxHolder> items, Flow lastFlow, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
				int depth) {
			this.items = items;
			this.lastFlow = lastFlow;
			this.shape = shape;
			this.depth = depth;
		}
	}

	/**
	 * {@code OpenChain}を明示スタックで駆動するworklist executor本体
	 * です(2026-07-22新設、B6a1——`docs/consultations/consult-b6a1
	 * -explicit-worklist-executor-codex.txt`の設計をそのまま実装)。
	 * TEXT/BLOCK/TABLE/REPLACEDの意味は{@link #restyleItem}をlegacy
	 * 再帰driverと共有し、複製しない——{@code OpenChain}降下だけが
	 * 違う: 子コンテナが{@link FlowContainer}であれば再帰せず新しい
	 * {@link RestyleFrame}をこの{@code Deque}へpushする。子コンテナが
	 * FlowContainerでない(表・段組等)場合は改ページ契約上atomicな
	 * 境界でありworklist化の対象外——{@code containerBox.restyle
	 * (builder, inner)}への通常の(再帰)フォールバックへ委ねる。
	 *
	 * <p>
	 * <b>2026-07-22の実バグ修正</b>:
	 * {@code containerBox.restyle(builder, inner)}は{@code
	 * AbstractContainerBox.restyle()}ではなく{@code FlowBlockBox
	 * .restyle()}(オーバーライド)へ多態的に解決される——そちらは
	 * {@code builder.startFlowBlock(this)}を呼んだ**後で**
	 * {@code this.container.restyle(...)}へ委譲し、{@code shape}が
	 * {@code Closed}のときだけ{@code builder.endFlowBlock()}を呼ぶ
	 * (`FlowBlockBox.java:628`付近)。最初の実装はこの
	 * {@code startFlowBlock}呼び出しを素通りして{@code containerBox
	 * .getContainer()}のitemsを直接dequeへpushしていたため、
	 * {@code flowStack}が正しい深さまで育たず`ContinuationInvariant
	 * ViolationException(flowStack.size() != continuation.depth())`
	 * を引き起こした(`docs/history/2026-07-22-b6a1-worklist-executor
	 * -bug-found-and-reverted.md`参照)。{@code OpenChain}降下の
	 * {@code inner}は`OpenShape.of()`の構成上常にOpenChainかOpenText
	 * であり決してClosedにならないため、対応する{@code endFlowBlock}
	 * 呼び出しは(legacy再帰と同じく)不要——{@code startFlowBlock}
	 * だけをこのpush分岐へ追加すれば`FlowBlockBox.restyle()`と同じ
	 * 効果になる。
	 * </p>
	 */
	private void restyleWorklist(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes, List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		final Deque<RestyleFrame> stack = new ArrayDeque<>();
		this.pushWorklistFrame(stack, builder, shape, restyleAbsolutes, prefix);
		final ChainDescender worklistDescender = (b, containerBox, inner) -> {
			final Container childContainer = containerBox.getContainer();
			if (childContainer instanceof FlowContainer childFc && containerBox instanceof FlowBlockBox flowBox) {
				// legacy再帰(FlowBlockBox.restyle())が暗黙に行う
				// startFlowBlockを、push分岐でも明示的に再現する
				// (上記javadocの実バグ修正)。
				b.startFlowBlock(flowBox);
				childFc.pushWorklistFrame(stack, b, inner, false, List.of());
			} else {
				// 改ページ契約上atomicな境界(表・段組等、またはFlowBlockBox
				// でない子コンテナ)。worklist化の対象外——通常の
				// (再帰)フォールバックへ委ねる(再入したrestyle()も
				// 同じisWorklistMode()判定を通る)。
				containerBox.restyle(b, inner);
			}
		};
		while (!stack.isEmpty()) {
			final RestyleFrame frame = stack.peek();
			if (frame.items == null || frame.nextIndex >= frame.items.size()) {
				stack.pop();
				continue;
			}
			final int i = frame.nextIndex++;
			// restyleItem()はthisのインスタンス状態を一切参照しない
			// (items/lastFlow/shape等パラメータのみで完結する)ため、
			// frameがどのFlowContainerに由来するかによらず同じ呼び出しで
			// 正しく動く——呼び出し先はthis固定でよい。
			this.restyleItem(builder, frame.items, i, frame.items.size(), frame.lastFlow, frame.shape, frame.depth,
					worklistDescender);
		}
	}

	private void pushWorklistFrame(Deque<RestyleFrame> stack, BlockBuilder builder,
			net.zamasoft.foliojet.layout.fragment.OpenShape shape, boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		final CollectedItems collected = this.collectItems(builder, restyleAbsolutes, prefix);
		if (collected.items() != null) {
			Collections.sort(collected.items());
			moveOpenChainTailLast(collected.items(), collected.lastFlow(), shape);
			stack.push(new RestyleFrame(collected.items(), collected.lastFlow(), shape, shape.depth()));
		}
	}

	/**
	 * <b>開いたまま降りるボックスは、必ず最後に処理する</b>(2026-07-27新設)。
	 *
	 * <p>
	 * {@link #collectItems}はfloatとflowを1つのリストへ合流し、呼び出し側が
	 * serial順に並べる。ところが{@code aggregateFloatings}が子から引き取った
	 * floatは<b>子の採番のまま</b>入るため、親子の採番が混ざり、
	 * {@code lastFlow}が末尾に来る保証がない。
	 * </p>
	 *
	 * <p>
	 * {@code lastFlow}が開いた尾のとき{@code FlowBlockBox.restyle}は
	 * <b>{@code endFlowBlock()}を意図的に省く</b>ので、その後ろに残った項目は
	 * <b>開いたままの他人のボックスの中へ</b>組まれる。flowはボックスの同一性で
	 * 再係留されるので影響を受けないが、<b>floatは「そのとき開いているフロー」へ
	 * 位置的に係留される</b>({@code BlockBuilder.commitFloatPlacement})ため、
	 * 順序の誤りだけで別の部分木へ移ってしまう。その部分木が
	 * {@code balance()}のソース再駆動で捨てられると、内容が黙って消える。
	 * </p>
	 *
	 * <p>
	 * <b>{@code OpenText}は対象外。</b>ライブ構築の{@code toAddFloating}と
	 * 同じ保留になるため並べ替えは不要で、実測では並べ替えると
	 * {@code FloatPagebreakTest}・{@code ImageAfterAvoidTest}が落ちる。
	 * </p>
	 *
	 * <p>
	 * 実測(2026-07-27、20万文書の掃過で発見。50,000文書に1件):
	 * 段組の中のフロートの内容が丸ごと消えていた。
	 * </p>
	 */
	private static void moveOpenChainTailLast(final List<BoxHolder> items, final Flow lastFlow,
			final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (lastFlow == null || !(shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenChain)) {
			return;
		}
		// BoxHolder は equals を上書きしないので indexOf は同一性比較
		final int at = items.indexOf(lastFlow);
		if (at < 0 || at == items.size() - 1) {
			return;
		}
		items.remove(at);
		items.add(lastFlow);
	}

	/**
	 * {@link #collectItems}の戻り値です(2026-07-22、B6a1準備)。sort済み
	 * ではない生の合流結果——呼び出し側がsortする。
	 */
	private record CollectedItems(List<BoxHolder> items, Flow lastFlow) {
	}

	/**
	 * floatings・(有効なら)absolutes・flows・prefixを1つの{@code items}
	 * リストへ合流します(2026-07-22、B6a1準備で{@code restyle()}から
	 * 抽出——挙動は一切変えていない純粋な関数抽出)。{@code this
	 * .floatings}/{@code this.absolutes}/{@code this.flows}を消費して
	 * null化する副作用は元のまま維持する。worklist executor
	 * (`restyleWorklist`)が子{@code FlowContainer}へ降りる際も同じ
	 * メソッドを呼ぶことで、合流ロジックの二重実装を避ける。
	 */
	private CollectedItems collectItems(BlockBuilder builder, boolean restyleAbsolutes,
			List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix) {
		// フロートは最近接ブロック祖先のコンテナに係留されるため、移動した
		// 部分木の内部フロートは部分木と一緒に動き、ソース再駆動でも二重
		// 生成されない(golden: float-in-moved)。絶対配置ボックスを含む
		// 部分木は stampRanges の containsAbsolute ゲート(E-6増分4e以前は
		// Opaque記録によるcontainsOpaque)が部分木単位で正しくフォールバック
		// させる — 階層単位のゲートは不要
		List<BoxHolder> items = null;
		if (this.floatings != null) {
			Floatings floatings = this.floatings;
			this.floatings = null;
			int size = floatings.getCount();
			if (size > 0) {
				if (items == null) {
					items = new ArrayList<BoxHolder>();
				}
				for (int i = 0; i < size; ++i) {
					items.add(floatings.getFloating(i));
				}
			}
		}

		if (restyleAbsolutes && this.absolutes != null) {
			Absolutes absolutes = this.absolutes;
			this.absolutes = null;
			int size = absolutes.getCount();
			for (int i = 0; i < size; ++i) {
				builder.addBound(absolutes.getAbsolute(i).box);
			}
		}

		Flow lastFlow = null;
		if (this.flows != null) {
			List<Flow> flows = this.flows;
			this.flows = null;
			int size = flows.size();
			if (size > 0) {
				if (items == null) {
					items = new ArrayList<BoxHolder>();
				}
				for (int i = 0; i < size; ++i) {
					items.add(flows.get(i));
				}
				lastFlow = (Flow) flows.get(size - 1);
			}
		}

		if (!prefix.isEmpty()) {
			// C1c: 吸収された閉部分木を serial 順の合流に加える
			if (items == null) {
				items = new ArrayList<BoxHolder>();
			}
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range : prefix) {
				items.add(new Replay(range));
			}
		}
		return new CollectedItems(items, lastFlow);
	}

	/**
	 * {@code OpenChain}の子孫へ降りる方法です(2026-07-22新設、B6a1)。
	 * legacy再帰driverは{@code containerBox.restyle(builder, inner)}を
	 * 直接呼ぶ(再帰)。worklist driver({@link #restyleWorklist})は、
	 * 子コンテナが{@link FlowContainer}であれば
	 * 再帰せず明示スタックへ新しいframeをpushする——この差し替え1点
	 * だけがlegacy/worklistの違いであり、それ以外のTEXT/BLOCK/TABLE/
	 * REPLACEDの意味は{@link #restyleItem}を両driverが共有するため
	 * 一切複製されない。
	 */
	@FunctionalInterface
	private interface ChainDescender {
		void descend(BlockBuilder builder, net.zamasoft.foliojet.layout.box.AbstractContainerBox containerBox,
				net.zamasoft.foliojet.layout.fragment.OpenShape inner);
	}

	/** legacy再帰driver用の{@link ChainDescender}——常に直接再帰する、旧来どおりの挙動。 */
	private static final ChainDescender RECURSIVE_DESCENDER = (builder, containerBox,
			inner) -> containerBox.restyle(builder, inner);

	/**
	 * sort済み{@code items}の1件を処理する共有dispatchです(2026-07-22、
	 * B6a1準備で{@code restyle()}のforループ本体から抽出——挙動は一切
	 * 変えていない純粋な関数抽出(旧{@code continue}は{@code return}へ
	 * 機械的に置換しただけ)。将来のworklist executor
	 * (`docs/history/2026-07-22-b6a1-trailing-items-measurement.md`
	 * 参照)が、この共有dispatchを複製せずそのまま呼べるようにする
	 * ための下ごしらえ——TEXT/BLOCK/TABLE/REPLACEDの意味を二重実装
	 * しない、というcodex設計相談の要件(却下案「switch全体を新
	 * executor側へコピーする」)に対応する。{@code OpenChain}降下の
	 * 方法だけは{@code descender}へ委譲し、legacy再帰driver/worklist
	 * driverで差し替える。
	 */
	private void restyleItem(BlockBuilder builder, List<BoxHolder> items, int i, int size, Flow lastFlow,
			net.zamasoft.foliojet.layout.fragment.OpenShape shape, int depth, ChainDescender descender) {
		// 以下2重の{}は抽出前のインデント(if/forの2階層)をそのまま残す
		// ための意図的なもの——大量行の再インデントによる誤りを避けた
		{
			{
				BoxHolder holder = (BoxHolder) items.get(i);
				if (holder instanceof Replay replay) {
					// C1c: 吸収された閉部分木のソース再駆動(再生可否は
					// 破断時に判定済みのため無条件。op は従来と同一)
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-subtree",
							"serial=" + holder.serial);
					builder.getPageContext().replaySubtree(replay.range, builder);
					return;
				}
				switch (holder.getBox().getType()) {
				case TEXT_BLOCK: {
					// テキストブロックボックス
					final TextBlockBox textBlock = (TextBlockBox) holder.getBox();
					final boolean open = lastFlow == holder
							&& shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenText;
					boolean replayed = false;
					// open(live ストリームが続きを流し込む)場合の尾部再生は
					// box-restyle に委ねる。かつての理由「charOffset の±1」は
					// 整形器バグとして根治済み(2026-07-17)だが、解禁実験は
					// 多数の失敗を示した — 残る実質は live shaper の保留
					// バッファと builder テキスト状態の受け渡し(deliveredCharEnd
					// と unitizer 保留の境界)であり、M3b のトークン再開で回収する
					if (!open
							&& (builder instanceof net.zamasoft.foliojet.layout.builder.impl.RootBuilder
									|| builder instanceof net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder)
							&& builder.getPageContext() != null) {
						final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = builder.getPageContext();
						// 尾部の終端: 次の item のアンカーがあれば上限として使う。
						// なくても tailBound がログ構造(囲みブロックの EndBlock
						// またはブロック級兄弟の Start)から終端を導出するため、
						// 次兄弟が分割断片(アンカー無効)でも再生できる(2026-07-17)
						long endId = -1;
						if (i + 1 < size) {
							// 次アイテムが吸収済み再生範囲(C1c)なら fromId が
							// そのボックスのアンカーと同値
							final BoxHolder next = (BoxHolder) items.get(i + 1);
							endId = next instanceof Replay replay ? replay.range.fromId()
									: next.getBox().getSourceAnchor();
						}
						// 切断段落の尾部をソース再駆動(M6b v3)
						replayed = root.replayTextFrom(textBlock, endId, open);
					}
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth,
							replayed ? "text-tail" : (open ? "restyle-text-open" : "restyle-text"),
							"serial=" + holder.serial);
					if (!replayed) {
						if (open) {
							// M3b Phase 1: スライス運搬経由(restyle 内部で
							// record→replay)。Phase 2/3 の TextTail 型付き化の実測
							net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenTextHandoff();
						}
						textBlock.restyle(builder);
					}
					// System.err.println("endTextBlock"+depth);
					if (!open && !replayed) {
						// 再駆動時はドライバの finishReplay が既に閉じている
						builder.endTextBlock();
					}
				}
					break;
				case BLOCK: {
					if (holder.getBox().getPos().getType() != PosType.FLOAT) {
						AbstractContainerBox containerBox = (AbstractContainerBox) holder.getBox();
						if (containerBox.getBlockParams().flow.isVertical() != builder.getRootBox().getBlockParams().flow.isVertical()) {
							// 書字方向が違う場合
							builder.addBound(containerBox);
						} else {
							// ブロックボックス
							// 匿名ボックス
							// テーブルキャプション
							if (lastFlow == holder
									&& shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.OpenChain(
											final net.zamasoft.foliojet.layout.fragment.OpenShape inner)) {
								// 開いたままの祖先チェーン
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-chain",
										"serial=" + holder.serial);
								net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordChainFiring();
								descender.descend(builder, containerBox, inner);
							} else if (!((builder instanceof net.zamasoft.foliojet.layout.builder.impl.RootBuilder
									|| builder instanceof net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder)
									&& builder.getPageContext() != null
									&& builder.getPageContext().replayFromSource(containerBox, builder))) {
								// 丸ごと移動した閉じた部分木はソース再駆動される(M6b
								// segment-restyle)。false ならボックス再生でフォールバック。
								// lastFlow && OpenText の末尾も閉じたボックス(次段は Closed)
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-box",
										"serial=" + holder.serial);
								containerBox.restyle(builder,
										net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
							} else {
								net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-subtree",
										"serial=" + holder.serial);
							}
						}
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
					}
				}
					break;

				case TABLE: {
					// テーブル
					TableBox tableBox = (TableBox) holder.getBox();
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-table",
							"serial=" + holder.serial);
					builder.addBound(tableBox);
				}
					break;
				case RESCUE: {
					// 2026-07-25(救済分割・増分5): 救済断片の残余。BoxType
					// を偽装せず専用の入口へ明示dispatchする(答申§5——
					// 通常のaddBound()へ流すとParamsのキャストで落ちる)
					if (holder.getBox().getPos().getType() == PosType.FLOAT) {
						// 増分7: 浮動体の残余は通常のfloat配置をやり直す
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float-rescue",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
						break;
					}
					final net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox rescueBox = (net.zamasoft.foliojet.layout.rescue.VisualRescueFlowBox) holder
							.getBox();
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-rescue",
							"serial=" + holder.serial);
					builder.addRescueBound(rescueBox);
					break;
				}
				case REPLACED: {
					// 置換されたボックス
					AbstractReplacedBox replacedBox = (AbstractReplacedBox) holder.getBox();
					if (replacedBox.getPos().getType() != PosType.FLOAT) {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "bound-replaced",
								"serial=" + holder.serial);
						builder.addBound(replacedBox);
					} else {
						net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "restyle-float-replaced",
								"serial=" + holder.serial);
						((Floating) holder).restyle(builder);
					}
					break;
				}
				default:
					throw new IllegalStateException(holder.getBox().toString());
				}
			}
		}
	}

	public double getMaxWidth() {
		if (this.flows == null) {
			return 0;
		}
		double width = 0;
		for (int i = 0; i < this.flows.size(); ++i) {
			Flow flow = (Flow) this.flows.get(i);
			width = Math.max(width, flow.box.getWidth());
		}
		return width;
	}

	public String toString() {
		return super.toString() + "/flowCount=" + (this.flows == null ? 0 : this.flows.size());
	}
}
