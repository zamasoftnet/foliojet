package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.layout.box.IAbsoluteBox;

import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;

import net.zamasoft.foliojet.layout.builder.PageGenerator;

/**
 * ドキュメント全体を構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: RootBuilder.java 1555 2018-04-26 04:15:29Z miyabe $
 */
public class RootBuilder extends BreakableBuilder {
	private static final Logger LOG = Logger.getLogger(RootBuilder.class.getName());

	/**
	 * 改ページ残余の再構築で、丸ごと移動した閉じた部分木をボックス再生の
	 * 代わりにソースイベントから再駆動します(M6b segment-restyle)。
	 * 移行期間中は opt-in です。
	 */
	private static final boolean SEGMENT_RESTYLE = !Boolean.getBoolean("foliojet.noSegmentRestyle"); // A/B実験: 一時的に既定ON

	/**
	 * 改ページの残余再構築中だけ true(segment-restyle の適用範囲)。
	 * 改段・バランスの再レイアウトはソース位置と対応しないため対象外。
	 */
	private boolean pageBreakRestyle = false;

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
		this.beginBreak();
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
			if (prevRootBox.split(pageAxis, mode, flags) instanceof SplitResult.Split(final IPageBreakableBox remainder)) {
				nextRootBox = (FlowBlockBox) remainder;
			} else {
				// KEEP/MOVE: 改ページポイントがない場合
				return false;
			}
		}

		// 残余のソースアンカーが窓と同期しているか(M6b診断、-ea時のみ)
		assert this.verifySourceAnchors(nextRootBox);

		//
		// 改ページ実行
		//
		this.finishLayout();
		this.pageGenerator.drawPage(this.pageBox);
		final PageBox pageBox = this.pageBox;
		this.pageBox = this.pageGenerator.nextPage();
		if (this.pageSide != PageBreakMode.AUTO) {
			this.pageSide = (this.pageSide == PageBreakMode.VERSO) ? PageBreakMode.RECTO : PageBreakMode.VERSO;
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("breaked: " + mode + "/pageSide=" + this.pageSide);
		}

		// コンテキストを再開
		this.contextFlow = new Flow(this.pageBox, 0, 0);
		this.resetFragmentCursor(0, 0);
		this.restyling = true;

		// 分割後のルートブロックを再開
		final int depth = this.flowStack.size();
		this.flowStack.clear();
		pageBox.restyle(this, 0);
		this.pageBreakRestyle = true;
		try {
			nextRootBox.restyle(this, depth);
		} finally {
			this.pageBreakRestyle = false;
		}
		assert this.flowStack.size() == depth : ("break flow failed. " + this.getFlowBox().getParams().element);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("restyled");
		}

		// 左右改ページ
		if (mode instanceof BreakMode.ForceBreakMode) {
			ForceBreakMode force = (ForceBreakMode) mode;
			if ((force.breakType == PageBreakMode.VERSO || force.breakType == PageBreakMode.RECTO)
					&& (this.pageSide == PageBreakMode.VERSO || this.pageSide == PageBreakMode.RECTO)) {
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

	/**
	 * 残余ボックスのソースアンカー(Params.sourceIndex)が本流セグメント窓
	 * 内を指していることを検査します(M6b診断)。窓の刈り込みは nextPage で
	 * 行われるため、このチェックは必ず刈り込み前に呼びます。
	 */
	private boolean verifySourceAnchors(final FlowBlockBox rootBox) {
		rootBox.getContainer().eachFlowBox(box -> {
			final int sourceIndex = box.getParams().sourceIndex;
			assert sourceIndex < 0 || this.pageGenerator.verifySourceAnchor(box.getParams().sourceEpoch, sourceIndex,
					box.getParams().element) : "source anchor out of window: " + box.getParams().element + "@"
							+ sourceIndex;
		});
		return true;
	}

	/**
	 * 移動した閉じた部分木のソース再駆動を試みます(M6b)。改ページの
	 * 残余再構築中で、アンカーが現世代かつ窓内で閉じている場合のみ
	 * 再駆動されます。false ならボックス再生でフォールバックします。
	 */
	public boolean replayFromSource(final net.zamasoft.foliojet.layout.box.IBox box) {
		if (!SEGMENT_RESTYLE || !this.pageBreakRestyle) {
			return false;
		}
		final net.zamasoft.foliojet.layout.box.params.Params params = box.getParams();
		if (params.sourceIndex < 0 || params.element == null) {
			return false;
		}
		return this.pageGenerator.replaySubtree(params.sourceEpoch, params.sourceIndex, params.element);
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
