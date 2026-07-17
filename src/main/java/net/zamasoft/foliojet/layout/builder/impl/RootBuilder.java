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
	 * 継続の一回きりの消費セッションです(P1。外部レビュー設計)。
	 *
	 * <p>
	 * 再開スコープと吸収済み再生範囲のリースを所有し、例外時も含めて
	 * 対称に清算する。リースの所有は occurrence(SourceRange
	 * インスタンス)単位 — 同じ fromId を入れ子の継続が独立に持っても
	 * 互いに干渉しない。状態遷移 NEW → RESUMING → CONSUMED / FAILED →
	 * CLOSED を強制し、consume-once を型と実行時検証で明示する。
	 * M6c の反復プローブは将来 ContinuationTemplate から fresh session を
	 * 作る形で拡張する(同じ session の再利用は不可)。
	 * </p>
	 */
	final class ResumeSession implements AutoCloseable {
		enum State {
			NEW, RESUMING, CONSUMED, FAILED, CLOSED
		}

		private final net.zamasoft.foliojet.layout.fragment.Continuation continuation;

		/**
		 * 吸収済み再生範囲のリース(occurrence 単位)。吸収済み範囲は
		 * ボックスを運搬しない(フォールバックなし)ため、消費されるまで
		 * compact から守る(水位の clamp は LayoutSource が行う)。
		 * map 経由の再生(resumeScopes)はボックスが残っており
		 * box-restyle へ落ちられるのでリース不要。
		 */
		private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange, net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease> leases = new java.util.IdentityHashMap<>();

		private State state = State.NEW;

		ResumeSession(final net.zamasoft.foliojet.layout.fragment.Continuation continuation) {
			this.continuation = continuation;
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log = RootBuilder.this.pageGenerator
					.getLayoutSource();
			if (log != null) {
				for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = continuation
						.root(); f != null;) {
					for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : f.prefixItems()) {
						this.leases.put(r, log.retainFrom(r.fromId()));
					}
					f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
							final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child
									: null;
				}
			}
		}

		/**
		 * 継続を消費して次ページのビルダー状態と内容を再開します(§5.7)。
		 * ルートフレームを外→内に再構成する(断片ボックスはここで初めて
		 * 作られる)。一度だけ呼べる。
		 */
		void resume() {
			if (this.state != State.NEW) {
				throw new IllegalStateException("継続は一度だけ消費できる: " + this.state);
			}
			this.state = State.RESUMING;
			RootBuilder.this.sessions.push(this);
			net.zamasoft.foliojet.layout.fragment.ResumeTrace.begin("PAGE");
			RootBuilder.this.beginBreakRestyle(this.continuation.ranges());
			try {
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(0, "root-fragment",
						"depth=" + this.continuation.depth());
				RootBuilder.this.resumeFrame(this.continuation.root(), 0, this.continuation.depth());
				this.state = State.CONSUMED;
			} catch (RuntimeException | Error e) {
				this.state = State.FAILED;
				throw e;
			} finally {
				RootBuilder.this.endBreakRestyle();
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.end();
				RootBuilder.this.sessions.pop();
			}
		}

		/**
		 * 吸収済み範囲の消費完了です(replaySubtree の finally から)。
		 */
		void releaseLease(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange occurrence) {
			final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease = this.leases
					.remove(occurrence);
			if (lease != null) {
				lease.close();
			}
		}

		boolean hasUnconsumedLeases() {
			return !this.leases.isEmpty();
		}

		@Override
		public void close() {
			if (this.state == State.CLOSED) {
				return;
			}
			// 正常消費なら全リース解放済み。例外時の残りをここで清算する
			// (取り残すと以後の compact が永久に clamp される)
			for (final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease : this.leases
					.values()) {
				lease.close();
			}
			this.leases.clear();
			this.state = State.CLOSED;
		}
	}

	/**
	 * 実行中の再開セッションのスタックです(再生内容の溢れによる
	 * 入れ子改ページで入れ子になる。top が現在のセッション)。
	 */
	private final java.util.ArrayDeque<ResumeSession> sessions = new java.util.ArrayDeque<>();

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
			final long startId = box.getSourceAnchor();
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

		// C1b/C1d-C 事前検分: 祖先チェーン(flowStack[1..])が plain
		// FlowBlockBox のみ(段組・表・縦横混在なし)なら、切断貫通レベルの
		// 断片をボックス構築なしで継続化する読み取り専用の計画を作る。
		// カスケードは一度きりのため all-or-nothing(1レベルでも不可なら
		// 全て従来経路)。断片は split の返り値(SplitResult.Frame →
		// ContainerCut.WithFrame)で外へ伝播する — side channel なし
		final net.zamasoft.foliojet.layout.fragment.BreakPlan plan;
		{
			final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow = ((FlowBlockBox) ((Flow) this.flowStack
					.get(0)).box).getBlockParams().flow;
			boolean collectable = this.flowStack.size() >= 2;
			for (int i = 1; collectable && i < this.flowStack.size(); ++i) {
				final net.zamasoft.foliojet.layout.box.AbstractContainerBox b = ((Flow) this.flowStack.get(i)).box;
				collectable = b.getClass() == FlowBlockBox.class && ((FlowBlockBox) b).getColumnCount() <= 1
						&& ((FlowBlockBox) b).getBlockParams().flow == rootFlow;
			}
			if (collectable) {
				final java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> chainBoxes = new java.util.ArrayList<>(
						this.flowStack.size() - 1);
				for (int i = 1; i < this.flowStack.size(); ++i) {
					chainBoxes.add(((Flow) this.flowStack.get(i)).box);
				}
				plan = new net.zamasoft.foliojet.layout.fragment.BreakPlan(java.util.List.copyOf(chainBoxes),
						this.flowStack.size(), 0);
			} else {
				plan = null;
			}
		}

		// ルートブロックの分割(C1a: 断片ボックスは split では構築せず、
		// コンテナ切断+断片状態を Continuation に載せて resume が再構成する。
		// ルートフレームの構築は水位計算・prefix 吸収(C1c)の後)
		final FlowBlockBox prevRootBox;
		final net.zamasoft.foliojet.layout.box.content.Container nextRootContainer;
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame rootChildFrame;
		final net.zamasoft.foliojet.layout.fragment.FragmentRecipe rootRecipe;
		final net.zamasoft.foliojet.layout.fragment.FragmentState rootState;
		final double rootCrossExtent;
		{
			final Flow root = (Flow) this.flowStack.get(0);

			// 段組みのための枠計算
			double lastFrame = 0;
			for (int i = this.flowStack.size() - 1; i >= 0; --i) {
				final Flow flow = (Flow) this.flowStack.get(i);
				if (flow.box.getColumnCount() > 1) {
					lastFrame = this.lastFrame(root, this.flowStack.size() - i);
					mode = net.zamasoft.foliojet.layout.box.content.BreakMode.column(mode);
					break;
				}
			}

			prevRootBox = (FlowBlockBox) root.box;
			final double pageAxis = this.getPageLimit() - root.pageAxis - lastFrame;
			// 旧 AbstractContainerBox.split と同じ前処理(内辺基準・改段吸収)
			final double innerLimit = pageAxis
					- prevRootBox.getFrame().getFramePageStart(prevRootBox.getBlockParams().flow);
			final net.zamasoft.foliojet.layout.box.content.BreakMode xmode = net.zamasoft.foliojet.layout.box.content.BreakMode
					.absorbColumn(mode, prevRootBox.getColumnCount());
			final net.zamasoft.foliojet.layout.fragment.ContainerCut cut = prevRootBox.getContainer()
					.splitPageAxis(innerLimit, xmode, flags, plan);
			if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(
					final net.zamasoft.foliojet.layout.box.content.Container c,
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f)) {
				nextRootContainer = c;
				rootChildFrame = f;
			} else {
				nextRootContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) cut).container();
				rootChildFrame = null;
			}
			if (nextRootContainer == null || nextRootContainer == prevRootBox.getContainer()) {
				// KEEP/MOVE: 改ページポイントがない場合
				return false;
			}
			final boolean vertical = prevRootBox.getBlockParams().flow.isVertical();
			rootCrossExtent = vertical ? prevRootBox.getInnerHeight() : prevRootBox.getInnerWidth();
			// レシピは splitPageState(アンカー無効化)より前に取得(C1d-B)
			rootRecipe = prevRootBox.fragmentRecipe();
			rootState = prevRootBox.splitPageState(innerLimit,
					mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ColumnBreakMode);
		}

		// C1d-C: 貫通フレーム(外→内)。各レベルのコンテナはルート
		// フレームのコンテナから分離されているため、水位と再生範囲の
		// 判定はフレーム側も歩く必要がある
		final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame> innerFrames = new java.util.ArrayList<>();
		for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = rootChildFrame; f != null;) {
			innerFrames.add(f);
			f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child : null;
		}

		// ソースログの水位 = 残余の閉じたアイテムの最小 EventId(M6b v3)。
		// これより前のイベントは確定ページに消費済みで破棄できる。
		// 開いているチェーンの StartBlock は compaction が常に保持する。
		// prefix 吸収(C1c)はコンテナからアイテムを消すため、水位は
		// 吸収前に計る
		long watermark = this.sourceWatermark(nextRootContainer);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
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
		// 再生範囲は破断時に一括判定して記録(C2。貫通フレームの
		// コンテナも対象)
		final net.zamasoft.foliojet.layout.box.params.WritingMode rootFlow = prevRootBox.getBlockParams().flow;
		final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges = this
				.stampRanges(nextRootContainer, rootFlow);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
			ranges.putAll(this.stampRanges(f.container(), rootFlow));
		}

		// C1c: 継続化パスでは各フレームコンテナ最上位の再生可能な閉部分木を
		// ボックスごと吸収し、serial 付き再生範囲(prefixItems)として運ぶ。
		// resume が serial 順で残アイテムと合流させて再駆動する。
		// walk depth はフレームの tail から導出(Child=0、LegacyOpenTail=d)
		final int depth = this.flowStack.size();
		java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> rootPrefix = java.util.List
				.of();
		final java.util.List<java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange>> framePrefixes = new java.util.ArrayList<>(
				innerFrames.size());
		if (rootChildFrame != null) {
			final boolean rootVertical = rootFlow.isVertical();
			if (nextRootContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
				// ルートコンテナは継続化時 depth=0 で歩かれる
				rootPrefix = fc.extractReplayable(ranges, rootVertical, 0);
			}
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
				final int walkDepth = f
						.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.LegacyOpenTail(
								final int d) ? d : 0;
				framePrefixes.add(f.container() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
						? fc.extractReplayable(ranges, rootVertical, walkDepth)
						: java.util.List.of());
			}
		}

		// C1d-C: prefix を焼き込んだフレーム木を内→外に再構成する。
		// 最内フレームは cascade が確定した LegacyOpenTail を保持
		net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = null;
		for (int i = innerFrames.size() - 1; i >= 0; --i) {
			final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = innerFrames.get(i);
			tail = new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(f.recipe(), f.state(),
							f.container(), f.crossExtent(), framePrefixes.get(i), tail == null ? f.tail() : tail));
		}
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame rootFrame = new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(
				rootRecipe, rootState, nextRootContainer, rootCrossExtent, rootPrefix,
				tail == null ? new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.LegacyOpenTail(depth)
						: tail);
		final net.zamasoft.foliojet.layout.fragment.Continuation continuation = new net.zamasoft.foliojet.layout.fragment.Continuation(
				depth, rootFrame, ranges);

		this.flowStack.clear();
		pageBox.restyle(this, 0);
		// P1: セッションがリース(occurrence 単位)とスコープを所有し、
		// consume-once と例外時清算を対称に保証する
		try (ResumeSession session = new ResumeSession(continuation)) {
			session.resume();
			assert !session.hasUnconsumedLeases() : "未消費の吸収済み再生範囲が残っています";
		}
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
		final net.zamasoft.foliojet.layout.box.AbstractBlockBox block = net.zamasoft.foliojet.layout.box.AbstractBlockBox
				.continueFragment(frame.recipe(), frame.state(), frame.container(), frame.crossExtent());
		// P1: 型検査つきの消費(表フレーム等の新種別は明示的に追加する —
		// FrameRemainder sum type の下地。盲目的キャストで壊れない)
		if (!(block instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox box)) {
			throw new IllegalStateException("未対応のフレーム種別: " + block.getClass().getName());
		}
		switch (frame.tail()) {
		case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
				final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) -> {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.CHILD_FRAMES.incrementAndGet();
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
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.LEGACY_ROOTS.incrementAndGet();
				assert frame.prefixItems().isEmpty();
				box.restyle(this, d);
			} else {
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.LEGACY_TAILS.incrementAndGet();
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
		final ResumeSession session = this.sessions.peek();
		try {
			if (!net.zamasoft.foliojet.layout.SourceReplayer.replay(this.pageGenerator.getLayoutSource(),
					range.fromId(), range.toId(), target, this.pageGenerator)) {
				// 吸収済み範囲はボックスを運搬しない(フォールバック不可)。
				// リースが守っているはずのイベントが欠けたら実装バグとして失敗
				throw new IllegalStateException("吸収済み再生範囲が失われました: [" + range.fromId() + ", " + range.toId() + "]");
			}
			net.zamasoft.foliojet.layout.SourceReplayer.PREFIX_REPLAYS.incrementAndGet();
		} finally {
			// 消費完了。再生の途中で入れ子の改ページが起きても、finally
			// までリースが残っているため残イベントは compact されない
			if (session != null) {
				session.releaseLease(range);
			}
		}
	}

	public boolean replayFromSource(final net.zamasoft.foliojet.layout.box.IBox box, final BlockBuilder target) {
		if (!SEGMENT_RESTYLE || this.resumeScopes.isEmpty()) {
			return false;
		}
		// C2: 判定は破断時に一括記録済み(stampRanges)。ここでは消費のみ
		// (現在=最内の再開スコープの記録)。consume-once: 同じ範囲が
		// 二度再生されない(P0。外部レビュー指摘の明示化)
		final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range = this.resumeScopes.peek()
				.remove(box);
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
			final long id = box.getSourceAnchor();
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
