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
	private static final boolean SEGMENT_RESTYLE = !Boolean.getBoolean("foliojet.noSegmentRestyle");

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

		// ソースログの水位 = 残余の閉じたアイテムの最小 EventId(M6b v3)。
		// これより前のイベントは確定ページに消費済みで破棄できる。
		// 開いているチェーンの StartBlock は compaction が常に保持する
		final long watermark = this.sourceWatermark(nextRootBox);

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
		this.pageGenerator.compactLayoutSource(watermark);
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
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		final long startId = box.getParams().sourceEventId;
		if (log == null || startId < 0) {
			return false;
		}
		final long endId = log.endOf(startId);
		if (endId < 0 || log.containsOpaque(startId, endId)) {
			// 閉じていない・再生非対応イベントを含む部分木はボックス再生へ
			return false;
		}
		net.zamasoft.foliojet.layout.SourceReplayer.replay(log, startId, endId, this, this.pageGenerator);
		return true;
	}

	/**
	 * 切断された段落の尾部再開をソース再駆動で試みます(M6b Phase B)。
	 * 改ページの残余再構築中で、継続トークンが位置(charOffset)を持ち、
	 * 尾部の終端が特定できる場合のみ再駆動されます。
	 *
	 * @param chainBox  段落を含むチェーンコンテナのボックス
	 * @param textBlock 切断残余のテキストブロック
	 * @param endEpoch  終端アンカーの世代(終端なし時は無視)
	 * @param endIndex  終端アンカーの位置(負なら旧窓末尾まで)
	 * @return 再駆動した場合 true
	 */
	public boolean replayTextFrom(final net.zamasoft.foliojet.layout.box.AbstractContainerBox chainBox,
			final net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock, final int endEpoch,
			final int endIndex) {
		// v1(StyleBuilder再入型)は doc 層との再入衝突で無効化(§5.6 v3)。
		// テキスト尾部再開は v3 の SourceReplayer 拡張で実装し直す
		if (true) {
			return false;
		}
		if (!SEGMENT_RESTYLE || !this.pageBreakRestyle) {
			return false;
		}
		final net.zamasoft.foliojet.layout.box.content.BreakToken token = textBlock.getBreakToken();
		final int charOffset = switch (token) {
		case net.zamasoft.foliojet.layout.box.content.BreakToken.MidFlow(final int offset) -> offset;
		case net.zamasoft.foliojet.layout.box.content.BreakToken.MidLine(final int offset) -> offset;
		default -> -1;
		};
		if (charOffset < 0 || chainBox.getParams().element == null) {
			return false;
		}
		// 再駆動が構築するテキストは継続(text-indent/:first-line 抑制)。
		// TextBuilder が生成時に builder の breakToken を消費する
		this.setBreakToken(token);
		return this.pageGenerator.replayTextTail(chainBox.getParams().element, charOffset, endEpoch, endIndex);
	}

	/**
	 * 残余のうち窓内で閉じているアイテムの最小 EventId を返します
	 * (M6b v3 の compaction 水位)。なければ Long.MAX_VALUE。
	 */
	private long sourceWatermark(final FlowBlockBox rootBox) {
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		if (log == null) {
			return Long.MAX_VALUE;
		}
		final long[] min = { Long.MAX_VALUE };
		rootBox.getContainer().eachFlowBox(box -> {
			final long id = box.getParams().sourceEventId;
			if (id >= 0 && log.endOf(id) >= 0) {
				min[0] = Math.min(min[0], id);
			}
		});
		return min[0];
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
