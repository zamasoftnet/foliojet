package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.util.logging.Level;
import java.util.logging.Logger;


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
	 * 切断段落の尾部ソース再生(M6b v3)。既定有効(退避フラグのみ)。
	 * 従来 OFF の原因だった「charOffset 座標の ±1 文字の不安定さ」は、
	 * 整形器の保留グリフ排出が次の文字のオフセットを流用していたバグ
	 * (pdfg2d FontManagerImpl.CharacterHandler)で、修正済み(2026-07-17)。
	 * これによりグリフのソース対応は正確な1:1になり、ログ座標と一致する。
	 */
	private static final boolean TEXT_TAIL_RESTYLE = !Boolean.getBoolean("foliojet.noSegmentRestyle.textTail");

	/**
	 * 破断(改ページ・改段)の残余再構築中だけ true(segment-restyle の
	 * 適用範囲)。破断は常に構築ヘッドで起きるため、どちらも「ヘッド=
	 * 祖先チェーン」の再開文脈が成立する。
	 */
	private boolean breakRestyle = false;

	/**
	 * 破断残余の再構築ブラケットを開始します(M6b)。
	 */
	public final void beginBreakRestyle() {
		this.breakRestyle = true;
	}

	/**
	 * 破断残余の再構築ブラケットを、記録済みの再生範囲(C2)付きで
	 * 開始します。
	 */
	public final void beginBreakRestyle(
			final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges) {
		this.breakRestyle = true;
		this.activeRanges = ranges;
	}

	/**
	 * 破断残余の再構築ブラケットを終了します(M6b)。
	 */
	public final void endBreakRestyle() {
		this.breakRestyle = false;
		this.activeRanges = java.util.Map.of();
	}

	/**
	 * 現在の破断で記録された閉部分木の再生範囲です(C2)。
	 */
	private java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> activeRanges = java.util.Map
			.of();

	/**
	 * 残余の各閉部分木の再生可否と範囲を破断時に一括判定します(C2:
	 * 記録時判定)。restyle 走行はこの記録を消費するだけで、ゲートを
	 * 再計算しない。判定は従来 replayFromSource が再開時に行っていた
	 * ものと同一(アンカー有効・窓内で閉・Opaque/段組/縦横混在なし)。
	 *
	 * @param container 残余のコンテナ
	 * @param rootFlow  ルートの書字方向
	 * @return ボックス→再生範囲(再生可能なもののみ)
	 */
	final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> stampRanges(
			final net.zamasoft.foliojet.layout.box.content.Container container,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow) {
		final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges = new java.util.IdentityHashMap<>();
		if (!SEGMENT_RESTYLE) {
			return ranges;
		}
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		if (log == null) {
			return ranges;
		}
		this.stampRanges(container, rootFlow, log, ranges);
		return ranges;
	}

	private void stampRanges(final net.zamasoft.foliojet.layout.box.content.Container container,
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow,
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log,
			final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges) {
		container.eachFlowBox(box -> {
			final long startId = box.getParams().sourceEventId;
			if (startId >= 0) {
				final long endId = log.endOf(startId);
				if (endId >= 0 && !log.containsOpaque(startId, endId) && !log.containsMulticol(startId, endId)
						&& !log.containsMixedFlow(startId, endId, rootFlow)) {
					ranges.put(box,
							new net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange(startId, endId));
					// 再生される部分木の内部は走らない(丸ごと再生)
					return;
				}
			}
			if (box instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox containerBox) {
				this.stampRanges(containerBox.getContainer(), rootFlow, log, ranges);
			}
		});
	}

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
	 * ページ生成器を返します(M6c: バランスのソース再生用)。
	 */
	public final PageGenerator getPageGenerator() {
		return this.pageGenerator;
	}

	/**
	 * segment-restyle が有効かを返します(M6c)。
	 */
	public final boolean isSegmentRestyle() {
		return SEGMENT_RESTYLE;
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

		// 継続記述(§5.7)。運搬は残余木の LegacyCarry(C0')、閉部分木の
		// 再生範囲は破断時に一括判定して記録(C2)
		final net.zamasoft.foliojet.layout.fragment.Continuation continuation = new net.zamasoft.foliojet.layout.fragment.Continuation(
				this.flowStack.size(),
				java.util.List.of(new net.zamasoft.foliojet.layout.fragment.Continuation.LegacyCarry(nextRootBox)),
				this.stampRanges(nextRootBox.getContainer(), nextRootBox.getBlockParams().flow));
		this.flowStack.clear();
		pageBox.restyle(this, 0);
		this.resume(continuation);
		this.pageGenerator.compactLayoutSource(watermark);
		assert this.flowStack.size() == continuation.depth()
				: ("break flow failed. " + this.getFlowBox().getParams().element);

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
	 * 継続記述を消費して次ページのビルダー状態と内容を再開します
	 * (§5.7)。C0' 段階では LegacyCarry(残余木の restyle)のみ。
	 * SourceRange/TextTail は C2/C3 で splitPageAxis 側が生成し始めた
	 * ときにここへ消費者が加わる。
	 */
	private void resume(final net.zamasoft.foliojet.layout.fragment.Continuation continuation) {
		net.zamasoft.foliojet.layout.fragment.ResumeTrace.begin("PAGE");
		this.beginBreakRestyle(continuation.ranges());
		try {
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.Item item : continuation.items()) {
				switch (item) {
				case net.zamasoft.foliojet.layout.fragment.Continuation.LegacyCarry(
						final net.zamasoft.foliojet.layout.box.IPageBreakableBox remainder) -> {
					net.zamasoft.foliojet.layout.fragment.Continuation.LEGACY_CARRIES.incrementAndGet();
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(0, "legacy-carry",
							"depth=" + continuation.depth());
					((net.zamasoft.foliojet.layout.box.impl.FlowBlockBox) remainder).restyle(this,
							continuation.depth());
				}
				case net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range -> throw new IllegalStateException(
						"C2 未実装: " + range);
				case net.zamasoft.foliojet.layout.fragment.Continuation.TextTail tail -> throw new IllegalStateException(
						"C3 未実装: " + tail);
				}
			}
		} finally {
			this.endBreakRestyle();
			net.zamasoft.foliojet.layout.fragment.ResumeTrace.end();
		}
	}

	/**
	 * 移動した閉じた部分木のソース再駆動を試みます(M6b)。改ページの
	 * 残余再構築中で、アンカーが現世代かつ窓内で閉じている場合のみ
	 * 再駆動されます。false ならボックス再生でフォールバックします。
	 */
	public boolean replayFromSource(final net.zamasoft.foliojet.layout.box.IBox box, final BlockBuilder target) {
		if (!SEGMENT_RESTYLE || !this.breakRestyle) {
			return false;
		}
		// C2: 判定は破断時に一括記録済み(stampRanges)。ここでは消費のみ
		final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range = this.activeRanges.get(box);
		if (range == null) {
			return false;
		}
		net.zamasoft.foliojet.layout.SourceReplayer.replay(this.pageGenerator.getLayoutSource(), range.fromId(),
				range.toId(), target, this.pageGenerator);
		return true;
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

	/**
	 * 切断段落の尾部再開をソース再駆動で試みます(M6b v3)。
	 * 継続トークンが位置(charOffset)を持つ場合のみ再駆動されます。
	 *
	 * @param textBlock    切断残余のテキストブロック
	 * @param endId        尾部の終端(次の兄弟の EventId。負ならログ末尾)
	 * @param keepTextOpen 再生後もテキストを開いたままにする
	 * @return 再駆動した場合 true
	 */
	public boolean replayTextFrom(final net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock, final long endId,
			final boolean keepTextOpen) {
		if (!TEXT_TAIL_RESTYLE || !SEGMENT_RESTYLE || !this.breakRestyle) {
			return false;
		}
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		if (log == null) {
			return false;
		}
		final net.zamasoft.foliojet.layout.box.content.BreakToken token = textBlock.getBreakToken();
		final int charOffset = switch (token) {
		case net.zamasoft.foliojet.layout.box.content.BreakToken.MidFlow(final int offset) -> offset;
		case net.zamasoft.foliojet.layout.box.content.BreakToken.MidLine(final int offset) -> offset;
		default -> -1;
		};
		if (charOffset < 0) {
			return false;
		}
		// 再駆動が作るテキストは継続(text-indent/:first-line 抑制)。
		// TextBuilder が生成時に builder の breakToken を消費する
		this.setBreakToken(token);
		return net.zamasoft.foliojet.layout.SourceReplayer.replayTextTail(log, charOffset, endId, keepTextOpen, this,
				this.pageGenerator);
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
