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
	 * 破断(改ページ・改段)の残余再構築スコープのスタックです(M6b。
	 * 各要素は破断時に一括記録された閉部分木の再生範囲 = C2)。
	 * 破断は常に構築ヘッドで起きるため「ヘッド=祖先チェーン」の再開
	 * 文脈が成立する。再生した内容が新ページを溢れさせると再開の中で
	 * 改ページが入れ子で起きるため、単一フィールドでは内側の破断が
	 * 外側の再開文脈を破壊する(外部レビュー指摘)— top が現在の文脈。
	 */
	private final java.util.ArrayDeque<java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange>> resumeScopes = new java.util.ArrayDeque<>();

	/**
	 * 破断残余の再構築スコープを、記録済みの再生範囲(C2)付きで
	 * 開始します。
	 */
	public final void beginBreakRestyle(
			final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges) {
		this.resumeScopes.push(ranges);
	}

	/**
	 * 破断残余の再構築スコープを終了します(M6b)。
	 */
	public final void endBreakRestyle() {
		if (this.resumeScopes.isEmpty()) {
			throw new IllegalStateException("再開スコープの対応が壊れています");
		}
		this.resumeScopes.pop();
	}

	/**
	 * 再開中でまだ消費されていない吸収済み再生範囲の保持リースです
	 * (C1c。fromId → lease)。再生した内容が新ページを溢れさせると
	 * 再開中に改ページが入れ子で起きるが、吸収済み範囲はボックスを
	 * 運搬しない(フォールバックなし)ため、入れ子の compact が
	 * イベントを落とさないようリースで保持する(水位の clamp は
	 * LayoutSource が行う)。map 経由の再生(resumeScopes)はボックスが
	 * 残っており box-restyle へ落ちられるのでリース不要。
	 */
	private final java.util.Map<Long, net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease> prefixLeases = new java.util.HashMap<>();

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
							new net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange(-1, startId, endId));
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

		// C1b 事前検分: 祖先チェーン(flowStack[1..])が plain FlowBlockBox
		// のみ(段組・表・縦横混在なし)なら、切断貫通レベルの断片を
		// ボックス構築なしで収集する。カスケードは一度きりのため
		// all-or-nothing(1レベルでも不可なら全て従来経路)
		final net.zamasoft.foliojet.layout.fragment.Continuation.ChainCollector chainCollector;
		{
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow = ((FlowBlockBox) ((Flow) this.flowStack
					.get(0)).box).getBlockParams().flow;
			boolean collectable = this.flowStack.size() >= 2;
			for (int i = 1; collectable && i < this.flowStack.size(); ++i) {
				final net.zamasoft.foliojet.layout.box.AbstractContainerBox b = ((Flow) this.flowStack.get(i)).box;
				collectable = b.getClass() == FlowBlockBox.class && ((FlowBlockBox) b).getColumnCount() <= 1
						&& ((FlowBlockBox) b).getBlockParams().flow == rootFlow;
			}
			chainCollector = collectable ? new net.zamasoft.foliojet.layout.fragment.Continuation.ChainCollector()
					: null;
			if (chainCollector != null) {
				for (int i = 1; i < this.flowStack.size(); ++i) {
					((FlowBlockBox) ((Flow) this.flowStack.get(i)).box).setChainCollector(chainCollector);
				}
			}
		}

		// ルートブロックの分割(C1a: 断片ボックスは split では構築せず、
		// コンテナ切断+断片状態を Continuation に載せて resume が再構成する。
		// RootFragment の構築は水位計算・prefix 吸収(C1c)の後)
		final FlowBlockBox prevRootBox;
		final net.zamasoft.foliojet.layout.box.content.Container nextRootContainer;
		final net.zamasoft.foliojet.layout.fragment.FragmentState rootState;
		final double rootCrossExtent;
		try {
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

			prevRootBox = (FlowBlockBox) root.box;
			final double pageAxis = this.getPageLimit() - root.pageAxis - lastFrame;
			// 旧 AbstractContainerBox.split と同じ前処理(内辺基準・段組フラグ)
			final double innerLimit = pageAxis
					- prevRootBox.getFrame().getFramePageStart(prevRootBox.getBlockParams().flow);
			byte xflags = flags;
			if ((flags & IPageBreakableBox.FLAGS_COLUMN) != 0 && prevRootBox.getColumnCount() > 1) {
				xflags ^= IPageBreakableBox.FLAGS_COLUMN;
			}
			nextRootContainer = prevRootBox.getContainer().splitPageAxis(innerLimit, mode, xflags);
			if (nextRootContainer == null || nextRootContainer == prevRootBox.getContainer()) {
				// KEEP/MOVE: 改ページポイントがない場合
				return false;
			}
			final boolean vertical = prevRootBox.getBlockParams().flow.isVertical();
			rootCrossExtent = vertical ? prevRootBox.getInnerHeight() : prevRootBox.getInnerWidth();
			rootState = prevRootBox.splitPageState(innerLimit, flags);
		} finally {
			if (chainCollector != null) {
				for (int i = 1; i < this.flowStack.size(); ++i) {
					((FlowBlockBox) ((Flow) this.flowStack.get(i)).box).setChainCollector(null);
				}
			}
		}

		// C1b: 収集されたチェーン断片(外→内)。各レベルのコンテナは
		// ルート断片のコンテナから分離されているため、水位と再生範囲の
		// 判定はチェーン側も歩く必要がある
		final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.ChainFragment> chain = chainCollector == null
				? java.util.List.of()
				: chainCollector.outerToInner();

		// ソースログの水位 = 残余の閉じたアイテムの最小 EventId(M6b v3)。
		// これより前のイベントは確定ページに消費済みで破棄できる。
		// 開いているチェーンの StartBlock は compaction が常に保持する。
		// prefix 吸収(C1c)はコンテナからアイテムを消すため、水位は
		// 吸収前に計る
		long watermark = this.sourceWatermark(nextRootContainer);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ChainFragment f : chain) {
			watermark = Math.min(watermark, this.sourceWatermark(f.container()));
		}

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
		this.beginRestyling();

		// 継続記述(§5.7)。ルート断片は再開時に再構成(C1a)、閉部分木の
		// 再生範囲は破断時に一括判定して記録(C2。チェーン断片の
		// コンテナも対象)
		final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow = prevRootBox.getBlockParams().flow;
		final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges = this
				.stampRanges(nextRootContainer, rootFlow);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ChainFragment f : chain) {
			ranges.putAll(this.stampRanges(f.container(), rootFlow));
		}

		// C1c: 収集パスでは各フレームコンテナ最上位の再生可能な閉部分木を
		// ボックスごと吸収し、serial 付き再生範囲(prefixItems)として運ぶ。
		// resume が serial 順で残アイテムと合流させて再駆動する
		final int depth = this.flowStack.size();
		java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> rootPrefix = java.util.List
				.of();
		final java.util.List<java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange>> chainPrefixes = new java.util.ArrayList<>(
				chain.size());
		if (!chain.isEmpty()) {
			final boolean rootVertical = rootFlow.isVertical();
			if (nextRootContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
				// ルートコンテナはチェーン収集時 depth=0 で歩かれる
				rootPrefix = fc.extractReplayable(ranges, rootVertical, 0);
			}
			for (int i = 0; i < chain.size(); ++i) {
				final net.zamasoft.foliojet.layout.fragment.Continuation.ChainFragment f = chain.get(i);
				// 最内レベルだけ残 depth で歩かれる(resume と同じ規約)
				final int walkDepth = i == chain.size() - 1 ? depth - chain.size() : 0;
				chainPrefixes.add(f.container() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
						? fc.extractReplayable(ranges, rootVertical, walkDepth)
						: java.util.List.of());
			}
		}

		// C1d-A: 収集ドラフトを ContinuationFrame の入れ子(外→内)へ
		// 組み上げる。最内フレームだけ legacy の深さ規約(LegacyOpenTail)を
		// 持ち、外側は Child の入れ子。収集不能な破断はルートフレームが
		// LegacyOpenTail(D)(従来の全ボックス restyle)
		net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.LegacyOpenTail(
				depth - chain.size());
		for (int i = chain.size() - 1; i >= 0; --i) {
			final net.zamasoft.foliojet.layout.fragment.Continuation.ChainFragment f = chain.get(i);
			tail = new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(f.prev(), f.state(),
							f.container(), f.crossExtent(), chainPrefixes.get(i), tail));
		}
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame rootFrame = new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(
				prevRootBox, rootState, nextRootContainer, rootCrossExtent, rootPrefix,
				chain.isEmpty() ? new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.LegacyOpenTail(depth)
						: tail);
		final net.zamasoft.foliojet.layout.fragment.Continuation continuation = new net.zamasoft.foliojet.layout.fragment.Continuation(
				depth, java.util.List.of(rootFrame), ranges);

		// C1c: 吸収済み範囲はボックスを運搬しないため、消費されるまで
		// 保持リースで compact から守る(再生内容の溢れによる入れ子
		// 改ページ対策。水位の clamp は LayoutSource が行う)
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		if (log != null) {
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : rootPrefix) {
				this.prefixLeases.put(r.fromId(), log.retainFrom(r.fromId()));
			}
			for (final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix : chainPrefixes) {
				for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : prefix) {
					this.prefixLeases.put(r.fromId(), log.retainFrom(r.fromId()));
				}
			}
		}

		this.flowStack.clear();
		pageBox.restyle(this, 0);
		this.resume(continuation);
		assert java.util.stream.Stream
				.concat(rootPrefix.stream(), chainPrefixes.stream().flatMap(java.util.List::stream))
				.noneMatch(r -> this.prefixLeases.containsKey(r.fromId())) : "未消費の吸収済み再生範囲が残っています";
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
		this.endRestyling();

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
				case net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame frame -> {
					// フレームの再構成(C1a/C1b/C1d-A): 断片ボックスはここで
					// 初めて作られる。開いた子孫は tail の入れ子を外→内に消費
					net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(0, "root-fragment",
							"depth=" + continuation.depth());
					this.resumeFrame(frame, 0, continuation.depth());
				}
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
	/**
	 * 継続フレームを外→内に消費します(C1d-A)。各フレームの断片ボックスを
	 * ここで初めて構成し、コンテナを吸収済み prefix と合流させて歩く。
	 * tail が Child なら depth=0(チェーン子はコンテナに居ない)、
	 * LegacyOpenTail なら従来の深さ規約(最内の moved-open ボックス・
	 * 開きテキストの継続)。
	 *
	 * @param frame フレーム
	 * @param index 外からの位置(0=ルート。トレースの chain-fragment 番号)
	 * @param depth 継続全体の深さ(トレース表示用)
	 */
	private void resumeFrame(final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame frame,
			final int index, final int depth) {
		assert !this.resumeScopes.isEmpty();
		final net.zamasoft.foliojet.layout.box.impl.FlowBlockBox box = (net.zamasoft.foliojet.layout.box.impl.FlowBlockBox) frame
				.prev().continueFragment(frame.state(), frame.container(), frame.crossExtent());
		switch (frame.tail()) {
		case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
				final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) -> {
			this.startFlowBlock(box);
			this.restyleFrame(box.getContainer(), frame.prefixItems(), 0);
			net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(index + 1, "chain-fragment",
					"depth=" + (depth - (index + 1)));
			this.resumeFrame(child, index + 1, depth);
		}
		case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.LegacyOpenTail(final int d) -> {
			if (index == 0) {
				// 収集不能な破断(チェーンなし): 従来の全ボックス restyle。
				// この経路では prefix 吸収は行われていない
				assert frame.prefixItems().isEmpty();
				box.restyle(this, d);
			} else {
				this.startFlowBlock(box);
				this.restyleFrame(box.getContainer(), frame.prefixItems(), d);
			}
		}
		}
	}

	/**
	 * フレームコンテナを再開します(C1c)。吸収済みの再生範囲(prefix)を
	 * serial 順で残アイテムと合流させる。
	 */
	private void restyleFrame(final net.zamasoft.foliojet.layout.box.content.Container container,
			final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix,
			final int depth) {
		if (container instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
			fc.restyle(this, depth, false, prefix);
		} else {
			assert prefix.isEmpty();
			container.restyle(this, depth, false);
		}
	}

	/**
	 * 吸収された閉部分木をソース再駆動します(C1c)。再生可否は破断時に
	 * 判定済み(stampRanges)のため無条件。
	 */
	public void replaySubtree(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range,
			final BlockBuilder target) {
		try {
			if (!net.zamasoft.foliojet.layout.SourceReplayer.replay(this.pageGenerator.getLayoutSource(),
					range.fromId(), range.toId(), target, this.pageGenerator)) {
				// 吸収済み範囲はボックスを運搬しない(フォールバック不可)。
				// pin が守っているはずのイベントが欠けたら実装バグとして失敗
				throw new IllegalStateException("吸収済み再生範囲が失われました: [" + range.fromId() + ", " + range.toId() + "]");
			}
			net.zamasoft.foliojet.layout.SourceReplayer.PREFIX_REPLAYS.incrementAndGet();
		} finally {
			// 消費完了。再生の途中で入れ子の改ページが起きても、finally
			// までリースが残っているため残イベントは compact されない
			final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease = this.prefixLeases
					.remove(range.fromId());
			if (lease != null) {
				lease.close();
			}
		}
	}

	public boolean replayFromSource(final net.zamasoft.foliojet.layout.box.IBox box, final BlockBuilder target) {
		if (!SEGMENT_RESTYLE || this.resumeScopes.isEmpty()) {
			return false;
		}
		// C2: 判定は破断時に一括記録済み(stampRanges)。ここでは消費のみ
		// (現在=最内の再開スコープの記録)
		final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range = this.resumeScopes.peek().get(box);
		if (range == null) {
			return false;
		}
		// 範囲が(入れ子の compact 等で)欠けていれば駆動前に false が返り、
		// ボックスが残っているため box-restyle へフォールバックする
		return net.zamasoft.foliojet.layout.SourceReplayer.replay(this.pageGenerator.getLayoutSource(), range.fromId(),
				range.toId(), target, this.pageGenerator);
	}

	/**
	 * 残余のうち窓内で閉じているアイテムの最小 EventId を返します
	 * (M6b v3 の compaction 水位)。なければ Long.MAX_VALUE。
	 */
	private long sourceWatermark(final net.zamasoft.foliojet.layout.box.content.Container container) {
		final net.zamasoft.foliojet.layout.fragment.LayoutSource log = this.pageGenerator.getLayoutSource();
		if (log == null) {
			return Long.MAX_VALUE;
		}
		final long[] min = { Long.MAX_VALUE };
		container.eachFlowBox(box -> {
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
		if (!TEXT_TAIL_RESTYLE || !SEGMENT_RESTYLE || this.resumeScopes.isEmpty()) {
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
