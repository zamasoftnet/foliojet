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
		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				Flow c = (Flow) this.flows.get(i);
				if (c.box.getType() == BoxType.BLOCK && ((FlowPos) c.box.getPos()).offset == null) {
					AbstractBlockBox blockBox = (AbstractBlockBox) c.box;
					worklist.push(AbstractContainerBox.framesStep(blockBox, pageBox, drawer, clip, transform, x,
							y + c.pageAxis));
				}
			}
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			x += this.box.getInnerWidth();
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				// 通常のフロー
				Flow c = (Flow) this.flows.get(i);
				if (c.box.getType() == BoxType.BLOCK && ((FlowPos) c.box.getPos()).offset == null) {
					AbstractBlockBox blockBox = (AbstractBlockBox) c.box;
					worklist.push(AbstractContainerBox.framesStep(blockBox, pageBox, drawer, clip, transform,
							x - c.pageAxis + -blockBox.getWidth(), y));
				}
			}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		if (this.flows == null) {
			return;
		}
		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				Flow c = (Flow) this.flows.get(i);
				worklist.push(IBox.drawStep(c.box, pageBox, drawer, visitor, clip, transform, contextX, contextY, x,
						y + c.pageAxis));
			}
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			x += this.box.getInnerWidth();
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				// 通常のフロー
				Flow c = (Flow) this.flows.get(i);
				worklist.push(IBox.drawStep(c.box, pageBox, drawer, visitor, clip, transform, contextX, contextY,
						x - c.pageAxis - c.box.getWidth(), y));
			}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x,
			double y, Deque<TextShapeStep> worklist) {
		if (this.flows == null) {
			return;
		}
		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			// 通常のフロー(元の走査順を保つため、スタックへは逆順でpushする)
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				Flow c = (Flow) this.flows.get(i);
				worklist.push(IBox.textShapeStep(c.box, pageBox, path, transform, x, y + c.pageAxis));
			}
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			x += this.box.getInnerWidth();
			for (int i = this.flows.size() - 1; i >= 0; --i) {
				// 通常のフロー
				Flow c = (Flow) this.flows.get(i);
				worklist.push(
						IBox.textShapeStep(c.box, pageBox, path, transform, x - c.pageAxis - c.box.getWidth(), y));
			}
			break;
		default:
			throw new IllegalStateException();
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
					}
					case SplitResult.Move move -> nextBox.addFlow(flow.serial, flow.box, 0);
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
					case SplitResult.Move move -> nextBox.addFlow(flow.serial, flow.box, 0);
					}
				}
			} else {
				index = this.flows == null ? 0 : this.flows.size();
			}
			nextBox.floatings = this.splitFloatings(pageLimit, flags, index);
			if (nextBox.floatings == this.floatings) {
				this.floatings = null;
			}
			assert nextBox != null;
			assert nextBox != this;
			return chainFrame != null
					? new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(nextBox, chainFrame)
					: plain(nextBox);
		}

		final double prevPageSize = pageLimit;
		// 主ループ前の判定は FlowCutter に純化されている(M4-A2)
		final FlowCutter.PreDecision pre = FlowCutter.preDecide(pageLimit, pageSize, pageInnerSize, frameStart, flags,
				this.flows != null && !this.flows.isEmpty());
		if (!(pre instanceof FlowCutter.PreDecision.Proceed(final double adjustedPageLimit))) {
			return plain(switch (pre) {
			case FlowCutter.PreDecision.CutHead(final double atLimit) -> this.cutHead(atLimit, flags);
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatings(null, atLimit, flags);
			case FlowCutter.PreDecision.MoveAll moveAll -> this;
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this.splitFloatings(this, atLimit,
					flags);
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
				return plain(this.splitFloatings(null, prevPageSize, flags));
			}
			lastOrphan = this.flows.size() - 1;
		}

		FlowContainer nextBox = null;
		boolean ignoreAvoid = false;
		double savePageLimit = pageLimit;
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
						switch (((AbstractBlockBox) prevFlow.box).splitForContinuation(splitLine, mode, xflags,
								plan)) {
						case SplitResult.Keep keep -> nextFlowBox = null;
						case SplitResult.Move move -> nextFlowBox = prevFlow.box;
						case SplitResult.Frame(
								final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f) -> {
							final FlowContainer collectedNext = new FlowContainer();
							return new net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(
									this.splitFloatings(collectedNext, prevPageSize, flags), f);
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
			case REPLACED: {
				// 置換されたボックス
				double prevFlowPageSize = prevFlow.box.getPageExtent(this.box.getBlockParams().flow);
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						|| LayoutUtils.compare(splitLine, prevFlowPageSize) >= 0) {
					// ページの先頭にある場合、ページ下辺にかかっていない場合は残す
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
						return plain(this.splitFloatings(null, prevPageSize, flags));
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
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatings(null, atLimit, flags);
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this.splitFloatings(this, atLimit,
					flags);
			default -> throw new IllegalStateException();
			});
		}

		return plain(this.splitFloatings(nextBox, prevPageSize, flags));
	}

	private static net.zamasoft.foliojet.layout.fragment.ContainerCut plain(final Container container) {
		return new net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain(container);
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
			floatUncut[k] = floating.box.getType() == BoxType.REPLACED
					|| ((AbstractContainerBox) floating.box).getBlockParams().pageBreakInside == PageBreakMode.AVOID;
		}
		return new FloatMeasurements(floatPageStarts, floatPageExtents, floatUncut);
	}

	public Container splitFloatings(Container nextBox, double pageLimit, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 || nextBox != null;
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 || nextBox != this;
		int flowCount = this.flows == null ? 0 : this.flows.size();
		Floatings nextFloatings = this.splitFloatings(pageLimit, flags, flowCount);
		if (nextFloatings != null) {
			if (nextFloatings == this.floatings) {
				if (nextBox == this) {
					return this;
				}
				if (nextBox == null && (flags & IPageBreakableBox.FLAGS_FIRST) == 0
						&& LayoutUtils.compare(
								this.box.getInnerPageExtent(this.box.getBlockParams().flow),
								0) <= 0) {
					return this;

				}
				this.floatings = null;
			}
			if (nextBox == null || nextBox == this) {
				nextBox = new FlowContainer();
			}
			((FlowContainer) nextBox).floatings = nextFloatings;
		}
		return nextBox;
	}

	public final Floatings splitFloatings(double pageLimit, byte flags) {
		int flowCount = this.flows == null ? 0 : this.flows.size();
		Floatings nextFloatings = this.splitFloatings(pageLimit, flags, flowCount);
		if (nextFloatings == this.floatings) {
			this.floatings = null;
		}
		return nextFloatings;
	}

	private Floatings splitFloatings(double pageLimit, byte flags, int index) {
		Floatings nextFloatings;
		if (this.floatings != null) {
			// 浮動ボックスを分割
			nextFloatings = this.floatings.splitPageAxis(this.box, pageLimit, flags);
			if (this.floatings.getCount() == 0) {
				this.floatings = null;
			}
		} else {
			nextFloatings = null;
		}
		for (int i = 0; i < index; ++i) {
			Flow flow = (Flow) this.flows.get(i);
			byte lflags = (byte) 0xFF;
			if (i != 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			if (i != this.flows.size() - 1) {
				lflags ^= IPageBreakableBox.FLAGS_LAST;
			}
			switch (flow.box.getType()) {
			case BLOCK:
				AbstractContainerBox blockBox = (AbstractContainerBox) flow.box;
				double pageAxis = pageLimit - flow.pageAxis;
				pageAxis -= blockBox.getFrame().getFramePageStart(blockBox.getBlockParams().flow);
				Floatings floatings = blockBox.getContainer().splitFloatings(pageAxis, (byte) (lflags & flags));
				if (floatings == null) {
					break;
				}
				if (nextFloatings == this.floatings) {
					this.floatings = null;
				}
				if (nextFloatings == null) {
					nextFloatings = floatings;
					break;
				}
				for (int j = 0; j < floatings.getCount(); ++j) {
					nextFloatings.addFloating(floatings.getFloating(j));
				}
				break;
			}
		}
		assert !(nextFloatings != null && nextFloatings.getCount() == 0);
		return nextFloatings;
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
		nextBox.floatings = this.splitFloatings(pageLimit, flags, 0);
		if (nextBox.floatings == this.floatings) {
			this.floatings = null;
		}
		return nextBox;
	}

	private FlowContainer cutTail(double pageLimit, byte flags) {
		FlowContainer nextBox = new FlowContainer();
		int flowCount = this.flows == null ? 0 : this.flows.size();
		nextBox.floatings = this.splitFloatings(pageLimit, flags, flowCount);
		if (nextBox.floatings == this.floatings) {
			this.floatings = null;
		}
		return nextBox;
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
		// トレース・水位の互換表示は旧 depth 値
		final int depth = shape.depth();
		// フロートは最近接ブロック祖先のコンテナに係留されるため、移動した
		// 部分木の内部フロートは部分木と一緒に動き、ソース再駆動でも二重
		// 生成されない(golden: float-in-moved)。絶対配置ボックスの開始は
		// Opaque として記録されるため、それを含む部分木は containsOpaque が
		// 部分木単位で正しくフォールバックさせる — 階層単位のゲートは不要
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

		if (items != null) {
			Collections.sort(items);
			int size = items.size();
			for (int i = 0; i < size; ++i) {
				BoxHolder holder = (BoxHolder) items.get(i);
				if (holder instanceof Replay replay) {
					// C1c: 吸収された閉部分木のソース再駆動(再生可否は
					// 破断時に判定済みのため無条件。op は従来と同一)
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(depth, "replay-subtree",
							"serial=" + holder.serial);
					builder.getPageContext().replaySubtree(replay.range, builder);
					continue;
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
							net.zamasoft.foliojet.layout.fragment.ContinuationStats.OPEN_TEXT_HANDOFFS
									.incrementAndGet();
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
								containerBox.restyle(builder, inner);
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
