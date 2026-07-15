package net.zamasoft.foliojet.style.builder.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.style.box.IAbsoluteBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.style.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.builder.PageGenerator;

/**
 * ドキュメント全体を構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: RootBuilder.java 1555 2018-04-26 04:15:29Z miyabe $
 */
public class RootBuilder extends BreakableBuilder {
	private static final Logger LOG = Logger.getLogger(RootBuilder.class.getName());

	private final PageGenerator pageGenerator;

	private PageBox pageBox;

	public RootBuilder(PageGenerator pageGenerator, byte mode) {
		super(null, null, mode);
		this.pageGenerator = pageGenerator;
		this.pageBox = pageGenerator.nextPage();

		this.pageSide = this.pageGenerator.getPageSide();
		this.contextFlow = new Flow(this.pageBox, 0, 0);
	}

	public final boolean isMain() {
		return true;
	}

	public final RootBuilder getPageContext() {
		return this;
	}

	/**
	 * 改ページの実行。
	 * 
	 * @param mode
	 * @param flags
	 */
	protected boolean pageBreak(BreakMode mode, byte flags) {
		assert this.textBuilder == null;
		this.breakFloats = 0;
		this.breakAfter = -1;
		this.canBreakBefore = false;
		this.interflowBreak = false;
		if (this.flowStack.isEmpty()) {
			return false;
		}

		// ボックスの高さを計算
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		}

		// ルートブロックの分割
		// System.err.println("RB break: flags=" + flags + "/"
		// + this.getFlowBox().getParams().element);
		final FlowBlockBox nextRootBox;
		{
			final Flow root = (Flow) this.flowStack.get(0);

			// 段組みのための枠計算
			double lastFrame = 0;
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow.box.getColumnCount() > 1) {
					lastFrame = this.lastFrame(root, this.flowStack.size() - i);
					flags |= IPageBreakableBox.FLAGS_COLUMN;
					break;
				}
			}

			final FlowBlockBox prevRootBox = (FlowBlockBox) root.box;
			final double pageAxis = this.getPageLimit() - root.pageAxis - lastFrame;
			// System.err.println("PAGE BREAK: " + pageAxis + "/"
			// +prevRootBox.getInnerHeight() +"/"+ mode);
			nextRootBox = (FlowBlockBox) prevRootBox.splitPageAxis(pageAxis, mode, flags);
			// assert prevRootBox != nextRootBox && nextRootBox != null;
			if (prevRootBox == nextRootBox || nextRootBox == null) {
				// 改ページポイントがない場合
				// System.err.println("RB no break");
				return false;
			}
		}

		//
		// 改ページ実行
		//
		this.finishLayout();
		this.pageGenerator.drawPage(this.pageBox);
		final PageBox pageBox = this.pageBox;
		this.pageBox = this.pageGenerator.nextPage();
		if (this.pageSide != Types.PAGE_BREAK_AUTO) {
			this.pageSide = (this.pageSide == Types.PAGE_BREAK_VERSO) ? Types.PAGE_BREAK_RECTO : Types.PAGE_BREAK_VERSO;
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("breaked: " + mode + "/pageSide=" + this.pageSide);
		}

		// コンテキストを再開
		this.contextFlow = new Flow(this.pageBox, 0, 0);
		this.pageAxis = 0;
		this.lineAxis = 0;
		this.poLastMargin = 0;
		this.neLastMargin = 0;
		this.widows = 0;
		this.floatings = null;
		this.restyling = true;

		// 分割後のルートブロックを再開
		final int depth = this.flowStack.size();
		this.flowStack.clear();
		pageBox.restyle(this, 0);
		nextRootBox.restyle(this, depth);
		assert this.flowStack.size() == depth : ("break flow failed. " + this.getFlowBox().getParams().element);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("restyled");
		}

		// 左右改ページ
		if (mode instanceof BreakMode.ForceBreakMode) {
			ForceBreakMode force = (ForceBreakMode) mode;
			if ((force.breakType == Types.PAGE_BREAK_VERSO || force.breakType == Types.PAGE_BREAK_RECTO)
					&& (this.pageSide == Types.PAGE_BREAK_VERSO || this.pageSide == Types.PAGE_BREAK_RECTO)) {
				if (force.breakType != this.pageSide) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("white page: " + force);
					}
					this.forceBreak(force.breakType);
				}
			}
		}
		this.restyling = false;

		return true;
	}

	public void addPageContent(IAbsoluteBox box) {
		box.finishLayout(this.pageBox);
		this.pageBox.addPageContent(box);
	}

	protected void finishLayout() {
		this.pageBox.finishLayout(this.pageBox);
	}

	public void finish() {
		this.finishLayout();
		this.pageGenerator.drawPage(this.pageBox);
	}
	//
	// public final void startFlowBlock(FlowBlockBox flowBox) {
	// System.err.println((this.flowStack == null ? 0 :
	// this.flowStack.size())+"/"+flowBox.getParams().augmentation);
	// super.startFlowBlock(flowBox);
	// }
	//
	// public void endFlowBlock() {
	// Flow flow = (Flow) this.flowStack.get(this.flowStack.size() - 1);
	// System.err.println((this.flowStack == null ? 0 :
	// this.flowStack.size())+"/"+flow.box.getParams().augmentation);
	// super.endFlowBlock();
	// }
}
