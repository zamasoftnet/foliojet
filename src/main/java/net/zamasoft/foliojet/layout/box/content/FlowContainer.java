package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.fragment.FlowCutter;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
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

	public boolean avoidBreakBefore() {
		if (this.flows == null || this.flows.isEmpty()) {
			return false;
		}
		for (int i = 0; i < this.flows.size(); ++i) {
			Flow flow = (Flow) this.flows.get(i);
			if (flow.box.avoidBreakBefore()) {
				return true;
			}
			if (flow.box.getHeight() > 0) {
				break;
			}

		}
		return false;
	}

	public boolean avoidBreakAfter() {
		if (this.flows == null || this.flows.isEmpty()) {
			return false;
		}
		for (int i = this.flows.size() - 1; i >= 0; --i) {
			Flow flow = (Flow) this.flows.get(i);
			if (flow.box.avoidBreakAfter()) {
				return true;
			}
			if (flow.box.getHeight() > 0) {
				break;
			}

		}
		return false;
	}

	public void finishLayout(IFramedBox containerBox) {
		if (this.box.isContextBox()) {
			containerBox = (IFramedBox) this.box;
		}
		if (this.flows != null) {
			for (int i = 0; i < this.flows.size(); ++i) {
				final Flow flow = (Flow) this.flows.get(i);
				flow.box.finishLayout(containerBox);
			}
		}
		if (this.floatings != null) {
			for (int i = 0; i < this.floatings.getCount(); ++i) {
				final Floating c = this.floatings.getFloating(i);
				c.box.finishLayout(containerBox);
			}
		}
		if (this.absolutes != null) {
			for (int i = 0; i < this.absolutes.getCount(); ++i) {
				final Absolute c = this.absolutes.getAbsolute(i);
				c.box.finishLayout(containerBox);
			}
		}
	}

	public final void drawFlowFrames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		if (this.flows == null) {
			return;
		}
		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			// 通常のフロー
			for (int i = 0; i < this.flows.size(); ++i) {
				Flow c = (Flow) this.flows.get(i);
				if (c.box.getType() == BoxType.BLOCK && ((FlowPos) c.box.getPos()).offset == null) {
					AbstractBlockBox blockBox = (AbstractBlockBox) c.box;
					blockBox.frames(pageBox, drawer, clip, transform, x, y + c.pageAxis);
				}
			}
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			x += this.box.getInnerWidth();
			for (int i = 0; i < this.flows.size(); ++i) {
				// 通常のフロー
				Flow c = (Flow) this.flows.get(i);
				if (c.box.getType() == BoxType.BLOCK && ((FlowPos) c.box.getPos()).offset == null) {
					AbstractBlockBox blockBox = (AbstractBlockBox) c.box;
					blockBox.frames(pageBox, drawer, clip, transform, x - c.pageAxis + -blockBox.getWidth(), y);
				}
			}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void drawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.flows == null) {
			return;
		}
		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			// 通常のフロー
			for (int i = 0; i < this.flows.size(); ++i) {
				Flow c = (Flow) this.flows.get(i);
				c.box.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y + c.pageAxis);
			}
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			x += this.box.getInnerWidth();
			for (int i = 0; i < this.flows.size(); ++i) {
				// 通常のフロー
				Flow c = (Flow) this.flows.get(i);
				c.box.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY,
						x - c.pageAxis - c.box.getWidth(), y);
			}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y) {
		if (this.flows == null) {
			return;
		}
		switch (this.box.getBlockParams().flow) {
		case WritingMode.TB:
			// 横書き
			// 通常のフロー
			for (int i = 0; i < this.flows.size(); ++i) {
				Flow c = (Flow) this.flows.get(i);
				c.box.textShape(pageBox, path, transform, x, y + c.pageAxis);
			}
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// 縦書き
			x += this.box.getInnerWidth();
			for (int i = 0; i < this.flows.size(); ++i) {
				// 通常のフロー
				Flow c = (Flow) this.flows.get(i);
				c.box.textShape(pageBox,
						path, transform, x - c.pageAxis - c.box.getWidth(), y);
			}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void drawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y) {
		if (this.floatings == null) {
			return;
		}
		this.floatings.draw(this.box, pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
	}

	public final void drawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y) {
		if (this.absolutes == null) {
			return;
		}
		this.absolutes.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
	}

	public Container splitPageAxis(double pageLimit, final BreakMode mode, final byte flags) {
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
				IPageBreakableBox flowBox = (IPageBreakableBox) flow.box;
				final SplitResult forceResult = flowBox.split(pageLimit - flow.pageAxis, mode, (byte) (lflags & flags));
				assert forceResult instanceof SplitResult.Split : "force break failed";
				IFlowBox nextFlowBox = (IFlowBox) ((SplitResult.Split) forceResult).remainder();
				nextBox.addFlow(flow.serial, nextFlowBox, 0);
			} else {
				index = this.flows == null ? 0 : this.flows.size();
			}
			nextBox.floatings = this.splitFloatings(pageLimit, flags, index);
			if (nextBox.floatings == this.floatings) {
				this.floatings = null;
			}
			assert nextBox != null;
			assert nextBox != this;
			return nextBox;
		}

		final double prevPageSize = pageLimit;
		// 主ループ前の判定は FlowCutter に純化されている(M4-A2)
		final FlowCutter.PreDecision pre = FlowCutter.preDecide(pageLimit, pageSize, pageInnerSize, frameStart, flags,
				this.flows != null && !this.flows.isEmpty());
		if (!(pre instanceof FlowCutter.PreDecision.Proceed(final double adjustedPageLimit))) {
			return switch (pre) {
			case FlowCutter.PreDecision.CutHead(final double atLimit) -> this.cutHead(atLimit, flags);
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatings(null, atLimit, flags);
			case FlowCutter.PreDecision.MoveAll moveAll -> this;
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this.splitFloatings(this, atLimit,
					flags);
			case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
			case FlowCutter.PreDecision.Proceed proceed -> throw new IllegalStateException();
			};
		}
		pageLimit = adjustedPageLimit;

		// 通常のフローで指定位置にさしかかっているボックスを特定
		final BlockParams params = this.box.getBlockParams();
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
		int lastOrphan = FlowCutter.lastOrphan(flowBottoms, pageLimit);

		// FlowCutter へ渡す純データ(avoid 押し戻し・後段判定用の計測)
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
		final double[] floatPageStarts;
		final double[] floatPageExtents;
		final boolean[] floatUncut;
		if (this.floatings != null) {
			final int floatCount = this.floatings.getCount();
			floatPageStarts = new double[floatCount];
			floatPageExtents = new double[floatCount];
			floatUncut = new boolean[floatCount];
			for (int k = 0; k < floatCount; ++k) {
				final Floating floating = this.floatings.getFloating(k);
				floatPageStarts[k] = floating.pageAxis;
				floatPageExtents[k] = floating.box.getPageExtent(params.flow);
				floatUncut[k] = floating.box.getType() == BoxType.REPLACED || ((AbstractContainerBox) floating.box)
						.getBlockParams().pageBreakInside == PageBreakMode.AVOID;
			}
		} else {
			floatPageStarts = null;
			floatPageExtents = null;
			floatUncut = null;
		}

		// System.err.println("ACB E:" + flags + "/" + pageLimit + "/" + mode +
		// "/lastOrphan=" + lastOrphan + "/" +
		// this.box.getParams().augmentation);
		if (lastOrphan == this.flows.size()) {
			// 切断線以下のフローがない場合
			if ((flags & IPageBreakableBox.FLAGS_LAST) == 0) {
				if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0 || (flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
					return this.cutTail(prevPageSize, flags);
				}
				final double contentHeight = flowPageStarts[this.flows.size() - 1]
						+ flowPageExtents[this.flows.size() - 1];
				if (LayoutUtils.compare(pageInnerSize, contentHeight) > 0) {
					// 自然の高さより高いボックスは切断
					return this.cutTail(prevPageSize, flags);
				}
				// 前のページに残す
				Container next = this.splitFloatings(null, prevPageSize, flags);
				return next;
			}
			lastOrphan = this.flows.size() - 1;
		}

		// System.err.println("EA: flags=" + flags + "/lastOrphan=" + lastOrphan
		// + "/"
		// + this.box.getParams().augmentation);

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
				};
			}
				break;
			case BLOCK:
				BlockParams cParams = ((AbstractContainerBox) prevFlow.box).getBlockParams();
				// 改ページ禁止でかつページの頭でない場合、またはページ進行方向が違う場合は内部で改ページしない
				if ((cParams.pageBreakInside != PageBreakMode.AVOID || (xflags & IPageBreakableBox.FLAGS_FIRST) != 0)
						&& vertical == cParams.flow.isVertical()) {
					IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
					nextFlowBox = switch (prevFlowBox.split(splitLine, mode, xflags)) {
					case SplitResult.Keep keep -> null;
					case SplitResult.Move move -> prevFlow.box;
					case SplitResult.Split(final IPageBreakableBox remainder) -> (IFlowBox) remainder;
					};
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
					return null;
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
				// System.err.println("ACB F: lflags=" + lflags + "/flags="
				// + flags + "/pageLimit=" + pageLimit + "/mode=" + mode
				// + "/i=" + i + "/" + this.box.getParams().augmentation);
				if ((lflags & IPageBreakableBox.FLAGS_FIRST) != 0) {
					// ボックスの先頭
					pageLimit = savePageLimit;
					if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
						// 強制切断
						return this.cutHead(prevPageSize, flags);
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
							return this.cutTail(prevPageSize, flags);
						}
						// 前ページに残す
						return this.splitFloatings(null, prevPageSize, flags);
					}
					// 全部移動
					return this;
				}
				// System.err.println("ACB FC: ignoreAvoid=" + ignoreAvoid +
				// "/i="
				// + i + "/lastOrphan=" + lastOrphan + "/"
				// + this.params.augmentation);
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

		// System.err.println("ACB J: flags=" + flags + "/leave="
		// + (nextBox == null) + "/" + this.box.getParams().augmentation);
		if (nextBox == null) {
			// ブロックを残す(末尾のブロックを残すことはない)。判定は FlowCutter に純化
			assert !((flags & IPageBreakableBox.FLAGS_LAST) != 0 && ((AutoBreakMode) mode).box != this.box);
			final double lastFlowBottom = flowPageStarts[this.flows.size() - 1]
					+ flowPageExtents[this.flows.size() - 1];
			return switch (FlowCutter.tailDecide(flags, lastOrphan, pageInnerSize, lastFlowBottom, prevPageSize)) {
			case FlowCutter.PreDecision.CutTail(final double atLimit) -> this.cutTail(atLimit, flags);
			case FlowCutter.PreDecision.KeepFloats(final double atLimit) -> this.splitFloatings(null, atLimit, flags);
			case FlowCutter.PreDecision.MoveWithFloats(final double atLimit) -> this.splitFloatings(this, atLimit,
					flags);
			default -> throw new IllegalStateException();
			};
		}

		// System.err.println("ACB G: remove=" + remove + "/leave="
		// + (nextBox == null) + "/floatings=" + (this.floatings != null)
		// + "/flows.size=" + (this.flows == null ? 0 : this.flows.size())
		// + "/" + this.getParams().augmentation);
		return this.splitFloatings(nextBox, prevPageSize, flags);
	}

	public Container splitFloatings(Container nextBox, double pageLimit, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 || nextBox != null;
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 || nextBox != this;
		int flowCount = this.flows == null ? 0 : this.flows.size();
		// System.err.println("ACB: " + flowCount + "/leave=" + (nextBox ==
		// null) +
		// "/pass="
		// + (nextBox == this) + "/" + this.params.augmentation);
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
		// System.out.println("J:"+index+"/"+(this.floatings !=
		// null)+"/"+this.params.augmentation);
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

	public final void getText(StringBuilder textBuff) {
		if (this.flows == null) {
			return;
		}
		for (int i = 0; i < this.flows.size(); ++i) {
			// 通常のフロー
			Flow c = (Flow) this.flows.get(i);
			c.box.getText(textBuff);
		}
	}

	public void restyle(BlockBuilder builder, int depth, boolean restyleAbsolutes) {
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

		if (items != null) {
			Collections.sort(items);
			int size = items.size();
			for (int i = 0; i < size; ++i) {
				BoxHolder holder = (BoxHolder) items.get(i);
				switch (holder.getBox().getType()) {
				case TEXT_BLOCK: {
					// テキストブロックボックス
					final TextBlockBox textBlock = (TextBlockBox) holder.getBox();
					textBlock.restyle(builder);
					// System.err.println("endTextBlock"+depth);
					if (lastFlow != holder || depth != 1) {
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
							if (lastFlow == holder && depth >= 1) {
								containerBox.restyle(builder, depth - 1);
							} else {
								containerBox.restyle(builder, 0);
							}
						}
					} else {
						((Floating) holder).restyle(builder);
					}
				}
					break;

				case TABLE: {
					// テーブル
					TableBox tableBox = (TableBox) holder.getBox();
					builder.addBound(tableBox);
				}
					break;
				case REPLACED: {
					// 置換されたボックス
					AbstractReplacedBox replacedBox = (AbstractReplacedBox) holder.getBox();
					if (replacedBox.getPos().getType() != PosType.FLOAT) {
						builder.addBound(replacedBox);
					} else {
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
