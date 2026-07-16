package net.zamasoft.foliojet.style.box.content;

import net.zamasoft.foliojet.style.box.params.PageBreakMode;

import net.zamasoft.foliojet.style.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractBlockBox;
import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.AbstractReplacedBox;
import net.zamasoft.foliojet.style.box.IAbsoluteBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IFloatBox;
import net.zamasoft.foliojet.style.box.IFlowBox;
import net.zamasoft.foliojet.style.box.IFramedBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.Absolutes.Absolute;
import net.zamasoft.foliojet.style.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.style.box.content.Floatings.Floating;
import net.zamasoft.foliojet.style.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.impl.TableBox;
import net.zamasoft.foliojet.style.box.impl.TextBlockBox;
import net.zamasoft.foliojet.style.box.params.PosType;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.FlowPos;
import net.zamasoft.foliojet.style.box.params.Pos;

import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;

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
			return StyleUtils.NONE;
		}

		double ascent;
		switch (flow.box.getType()) {
		case BLOCK: {
			AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			double firstAscent = containerBox.getFirstAscent();
			if (StyleUtils.isNone(firstAscent)) {
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
			return StyleUtils.NONE;
		}

		double descent;
		switch (flow.box.getType()) {
		case BLOCK: {
			final AbstractContainerBox containerBox = (AbstractContainerBox) flow.box;
			final double lastDescent = containerBox.getLastDescent();
			if (StyleUtils.isNone(lastDescent)) {
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
		if (this.box.getBlockParams().flow.isVertical()) {
			// 縦書き
			return flow.pageAxis + flow.box.getWidth();
		}
		return flow.pageAxis + flow.box.getHeight();
	}

	public double getCutPoint(double pageAxis) {
		if (this.box.getBlockParams().flow.isVertical()) {
			// 縦書き
			if (this.hasFlows()) {
				for (int i = 0; i < this.flows.size(); ++i) {
					final Flow flow = (Flow) this.flows.get(i);
					final double bottom = flow.pageAxis + flow.box.getWidth();
					if (StyleUtils.compare(bottom, pageAxis) >= 0) {
						if (flow.box.getType() == BoxType.BLOCK) {
							final FlowBlockBox blockBox = (FlowBlockBox) flow.box;
							if (blockBox.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
								pageAxis = bottom;
								break;
							}
							pageAxis = flow.pageAxis
									+ blockBox.getContainer()
											.getCutPoint(pageAxis - flow.pageAxis - blockBox.getFrame().getFrameRight())
									+ blockBox.getFrame().getFrameWidth();
						} else if (flow.box.getType() == BoxType.TEXT_BLOCK) {
							final TextBlockBox blockBox = (TextBlockBox) flow.box;
							pageAxis = flow.pageAxis + blockBox.getCutPoint(pageAxis - flow.pageAxis);
						} else {
							pageAxis = bottom;
						}
						break;
					}
				}
			}
			if (this.hasFloatings()) {
				for (int i = 0; i < this.floatings.getCount(); ++i) {
					final Floating floaing = this.floatings.getFloating(i);
					final double bottom = floaing.pageAxis + floaing.box.getWidth();
					if (StyleUtils.compare(bottom, pageAxis) >= 0) {
						pageAxis = bottom;
						break;
					}
				}
			}
		} else {
			// 横書き
			if (this.hasFlows()) {
				for (int i = 0; i < this.flows.size(); ++i) {
					final Flow flow = (Flow) this.flows.get(i);
					final double bottom = flow.pageAxis + flow.box.getHeight();
					if (StyleUtils.compare(bottom, pageAxis) >= 0) {
						if (flow.box.getType() == BoxType.BLOCK) {
							final FlowBlockBox blockBox = (FlowBlockBox) flow.box;
							if (blockBox.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
								pageAxis = bottom;
								break;
							}
							pageAxis = flow.pageAxis
									+ blockBox.getContainer()
											.getCutPoint(pageAxis - flow.pageAxis - blockBox.getFrame().getFrameTop())
									+ blockBox.getFrame().getFrameHeight();
						} else if (flow.box.getType() == BoxType.TEXT_BLOCK) {
							final TextBlockBox blockBox = (TextBlockBox) flow.box;
							pageAxis = flow.pageAxis + blockBox.getCutPoint(pageAxis - flow.pageAxis);
						} else {
							pageAxis = bottom;
						}
						break;
					}
				}
			}
			if (this.hasFloatings()) {
				for (int i = 0; i < this.floatings.getCount(); ++i) {
					final Floating floaing = this.floatings.getFloating(i);
					final double bottom = floaing.pageAxis + floaing.box.getHeight();
					if (StyleUtils.compare(bottom, pageAxis) >= 0) {
						pageAxis = bottom;
						break;
					}
				}
			}
		}
		return pageAxis;
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
		final double frameStart, pageSize, pageInnerSize;
		if (vertical) {
			frameStart = this.box.getFrame().getFrameRight();
			pageSize = this.box.getWidth();
			pageInnerSize = this.box.getInnerWidth();
		} else {
			frameStart = this.box.getFrame().getFrameTop();
			pageSize = this.box.getHeight();
			pageInnerSize = this.box.getInnerHeight();
		}

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
				IFlowBox nextFlowBox = (IFlowBox) flowBox.splitPageAxis(pageLimit - flow.pageAxis, mode,
						(byte) (lflags & flags));
				assert nextFlowBox != null : "force break failed";
				assert nextFlowBox != flowBox;
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

		if (StyleUtils.compare(pageLimit, 0) <= 0) {
			// 切断線が内上辺以上にある場合
			// System.err.println("B:"+flags+"/"+mode+"/"+pageLimit+"/"+this
			// .getFrameTop());

			// ** <= を使うのは、切断線と上辺が一致した場合に移動しないため **
			// (改ページを最小限にするポリシー)

			if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// 先頭を切断
				return this.cutHead(pageLimit, flags);
			}
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// ページ先頭にある場合
				if (StyleUtils.compare(frameStart, 0) > 0) {
					// 上辺があれば切断
					return this.cutHead(pageLimit, flags);
				}
				// 前ページに残す
				return this.splitFloatings(null, pageLimit, flags);
			}
			// 次に送る
			return this;
		}
		if ((flags & (IPageBreakableBox.FLAGS_SPLIT | IPageBreakableBox.FLAGS_LAST)) == 0
				&& StyleUtils.compare(pageLimit, pageSize) >= 0) {
			// 自動改ページで切断線が内底辺以下にある場合
			// System.err.println("AB:"+flags+"/"+mode);

			// ** >= を使うのは、切断線と底辺が一致した場合に移動しないため **
			// (改ページを最小限にするポリシー)

			// 前ページに残す
			return this.splitFloatings(null, pageLimit, flags);
		}
		final double prevPageSize = pageLimit;
		if ((flags & (IPageBreakableBox.FLAGS_SPLIT | IPageBreakableBox.FLAGS_LAST)) == 0
				&& StyleUtils.compare(pageLimit, pageInnerSize) >= 0) {
			pageLimit = pageInnerSize - StyleUtils.THRESHOLD * 2;
		}

		// System.err.println("ACB C: flags=" + flags + "/pageLimit=" +
		// pageLimit
		// + "/pageInnerSize=" + pageInnerSize + "/mode=" + mode
		// + "/flows.size=" + (this.flows == null ? 0 : this.flows.size())
		// + "/" + this.box.getParams().augmentation);
		if (this.flows == null || this.flows.isEmpty()) {
			// 通常のフローが存在しない場合
			if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// 切断
				return this.cutTail(prevPageSize, flags);
			}
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// 高さがなければ残す
				if (StyleUtils.compare(pageInnerSize, 0) > 0) {
					return this.cutTail(prevPageSize, flags);
				}
				return this.splitFloatings(null, prevPageSize, flags);
			}
			// 高さがあれば次に送る
			if ((flags & IPageBreakableBox.FLAGS_LAST) != 0 || StyleUtils.compare(pageSize, 0) > 0) {
				return this.splitFloatings(this, prevPageSize, flags);
			}
			return this.splitFloatings(null, prevPageSize, flags);
		}

		// 通常のフローで指定位置にさしかかっているボックスを特定
		final BlockParams params = this.box.getBlockParams();
		int lastOrphan;
		for (lastOrphan = this.flows.size() - 1; lastOrphan >= 0; --lastOrphan) {
			Flow flow = (Flow) this.flows.get(lastOrphan);
			double lastBottom = flow.pageAxis;
			if (flow.box.getType() == BoxType.BLOCK) {
				FlowBlockBox flowBlock = (FlowBlockBox) flow.box;
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
				if (params.flow.isVertical()) {
					lastBottom += flow.box.getWidth();
				} else {
					lastBottom += flow.box.getHeight();
				}
			}
			if (StyleUtils.compare(lastBottom, pageLimit) <= 0) {
				break;
			}
		}
		++lastOrphan;

		// System.err.println("ACB E:" + flags + "/" + pageLimit + "/" + mode +
		// "/lastOrphan=" + lastOrphan + "/" +
		// this.box.getParams().augmentation);
		if (lastOrphan == this.flows.size()) {
			// 切断線以下のフローがない場合
			if ((flags & IPageBreakableBox.FLAGS_LAST) == 0) {
				if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0 || (flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
					return this.cutTail(prevPageSize, flags);
				}
				Flow flow = (Flow) this.flows.get(this.flows.size() - 1);
				double contentHeight = flow.pageAxis;
				if (vertical) {
					contentHeight += flow.box.getWidth();
				} else {
					contentHeight += flow.box.getHeight();
				}
				if (StyleUtils.compare(pageInnerSize, contentHeight) > 0) {
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
			if (StyleUtils.compare(prevFlow.pageAxis, 0) > 0) {
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
				nextFlowBox = (IFlowBox) prevFlowBox.splitPageAxis(splitLine, mode, xflags);
			}
				break;
			case BLOCK:
				BlockParams cParams = ((AbstractContainerBox) prevFlow.box).getBlockParams();
				// 改ページ禁止でかつページの頭でない場合、またはページ進行方向が違う場合は内部で改ページしない
				if ((cParams.pageBreakInside != PageBreakMode.AVOID || (xflags & IPageBreakableBox.FLAGS_FIRST) != 0)
						&& vertical == cParams.flow.isVertical()) {
					IPageBreakableBox prevFlowBox = (IPageBreakableBox) prevFlow.box;
					nextFlowBox = (IFlowBox) prevFlowBox.splitPageAxis(splitLine, mode, xflags);
					break;
				}
				if ((xflags & IPageBreakableBox.FLAGS_LAST) != 0) {
					// 末尾の場合、改ページ禁止は必ず送る
					nextFlowBox = prevFlow.box;
					break;
				}
			case REPLACED: {
				// 置換されたボックス
				double prevFlowPageSize;
				if (vertical) {
					prevFlowPageSize = prevFlow.box.getWidth();
				} else {
					prevFlowPageSize = prevFlow.box.getHeight();
				}
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0
						|| StyleUtils.compare(splitLine, prevFlowPageSize) >= 0) {
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
					// ブロック間の改ページ禁止のチェック
					int beforeFlows = 1;
					boolean breakAvoid = prevFlow.box.avoidBreakBefore();
					Flow beforeFlow = (Flow) this.flows.get(i - 1);
					// 接しているブロックで改ページ禁止されていることを確認
					for (int j = i - 1; j >= 0; --j) {
						Flow beforeFlow2 = (Flow) this.flows.get(j);
						double beforeBottom = beforeFlow2.pageAxis;
						if (vertical) {
							beforeBottom += beforeFlow2.box.getWidth();
						} else {
							beforeBottom += beforeFlow2.box.getHeight();
						}
						if (StyleUtils.compare(beforeBottom, prevFlow.pageAxis) < 0) {
							if (j == i - 1) {
								breakAvoid = false;
							}
							break;
						}
						beforeFlows++;
						beforeFlow = beforeFlow2;
						if (beforeFlow.box.avoidBreakAfter()) {
							breakAvoid = true;
						}
					}
					if (breakAvoid) {
						// 切断可能な浮動ボックスがある場合は改ページを区切る
						if (this.floatings != null) {
							for (int k = 0; k < this.floatings.getCount(); ++k) {
								Floating floating = this.floatings.getFloating(k);
								if (StyleUtils.compare(floating.pageAxis, pageLimit) >= 0) {
									breakAvoid = false;
									break;
								}
								double floatPageSize;
								if (vertical) {
									floatPageSize = floating.box.getWidth();
								} else {
									floatPageSize = floating.box.getHeight();
								}
								if (StyleUtils.compare(floating.pageAxis + floatPageSize, pageLimit) <= 0) {
									continue;
								}
								if (floating.box.getType() == BoxType.REPLACED) {
									continue;
								}
								if (((AbstractContainerBox) floating.box)
										.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
									continue;
								}
								breakAvoid = false;
								break;
							}
						}
					}
					// System.err.println("ACB: " + i + "/" + breakAvoid + "/"
					// + this.params.augmentation);
					if (breakAvoid) {
						// ブロック間の改ページ禁止の場合
						assert beforeFlows >= 2;
						i -= beforeFlows;
						pageLimit = beforeFlow.pageAxis - StyleUtils.THRESHOLD * 2;
						if (vertical) {
							pageLimit += beforeFlow.box.getWidth();
							if (beforeFlow.box.getType() == BoxType.BLOCK) {
								pageLimit -= ((AbstractContainerBox) beforeFlow.box).getFrame().getFrameLeft();
							}
						} else {
							pageLimit += beforeFlow.box.getHeight();
							if (beforeFlow.box.getType() == BoxType.BLOCK) {
								pageLimit -= ((AbstractContainerBox) beforeFlow.box).getFrame().getFrameBottom();
							}
						}
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
			// ブロックを残す
			// 末尾のブロックを残すことはない
			assert !((flags & IPageBreakableBox.FLAGS_LAST) != 0 && ((AutoBreakMode) mode).box != this.box);
			pageLimit = savePageLimit;
			if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
				// 切断
				return this.cutTail(prevPageSize, flags);
			}
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				// ページの先頭(FLAGS_LASTを無視する)
				final Flow flow = (Flow) this.flows.get(this.flows.size() - 1);
				double contentHeight = flow.pageAxis;
				if (vertical) {
					contentHeight += flow.box.getWidth();
				} else {
					contentHeight += flow.box.getHeight();
				}
				if (StyleUtils.compare(pageInnerSize, contentHeight) > 0) {
					// 自然の高さより高いボックスは切断
					return this.cutTail(prevPageSize, flags);
				}
				return this.splitFloatings(null, prevPageSize, flags);
			}
			if (lastOrphan == 0) {
				// 切断線より上がなければ次ページに送る
				return this.splitFloatings(this, prevPageSize, flags);
			}
			// 末尾で切る
			return this.cutTail(prevPageSize, flags);
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
						&& StyleUtils.compare(
								this.box.getBlockParams().flow.isVertical() ? this.box.getInnerWidth()
										: this.box.getInnerHeight(),
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
				if (blockBox.getBlockParams().flow.isVertical()) {
					pageAxis -= blockBox.getFrame().getFrameRight();
				} else {
					pageAxis -= blockBox.getFrame().getFrameTop();
				}
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
