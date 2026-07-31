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
	 * 自動改ページごとの指紋をダンプするデバッグスイッチ。ホットパスで
	 * 毎回{@code System.getProperty}(同期Hashtable)を引かないよう起動時に固定します。
	 */
	private static final boolean DEBUG_BREAK_FINGERPRINT = System.getProperty("foliojet.debug.breakFingerprint") != null;

	/**
	 * 進捗のない自動改ページ(ライブロック)の検出用の状態です(2026-07-27新設)。
	 * 直前の自動改ページ時点の「状態の指紋」と、それが変わらないまま
	 * 繰り返した回数を持ちます。詳細は
	 * {@link net.zamasoft.foliojet.layout.fragment.ContinuationStats#STALLED_AUTO_BREAK_LIMIT}。
	 */
	private long lastBreakIngest = Long.MIN_VALUE;
	private int lastBreakDepth = -1;
	private double lastBreakPageAxis = Double.NaN;
	private int lastBreakTarget = 0;
	private int stalledBreakRun = 0;

	/**
	 * 自動改ページが1回転しても状態が全く変わっていないかを検査します。
	 *
	 * <p>
	 * <b>強制改ページは対象外</b>——作者が枚数を指定した改ページは、
	 * 内容を消費しなくても正しい(実測でも97回連続する例がある)。
	 * </p>
	 *
	 * @param mode 今回の改ページのモード
	 */
	/**
	 * @return ライブロックが確定したので<b>改ページをあきらめる</b>べきなら true
	 *         ({@code ContinuationStats.guardBreakProgress}参照)
	 */
	private boolean guardBreakProgress(final BreakMode mode) {
		if (!(mode instanceof BreakMode.AutoBreakMode auto)) {
			// 強制改ページは進捗で測らない。指紋も持ち越さない
			this.stalledBreakRun = 0;
			this.lastBreakDepth = -1;
			return false;
		}
		final net.zamasoft.foliojet.layout.fragment.LayoutSource source = this.pageGenerator.getLayoutSource();
		final long ingest = (source == null) ? -1L : source.nextId();
		final int depth = this.flowStack.size();
		final int target = (auto.box == null) ? 0 : System.identityHashCode(auto.box.getParams().element);
		if (DEBUG_BREAK_FINGERPRINT && ingest != this.lastBreakIngest) {
			System.out.println("[fp] ingest=" + ingest + " depth=" + depth + " pageAxis=" + this.pageAxis);
		}
		if (ingest == this.lastBreakIngest && depth == this.lastBreakDepth
				&& Double.compare(this.pageAxis, this.lastBreakPageAxis) == 0 && target == this.lastBreakTarget) {
			++this.stalledBreakRun;
		} else {
			this.stalledBreakRun = 0;
			this.lastBreakIngest = ingest;
			this.lastBreakDepth = depth;
			this.lastBreakPageAxis = this.pageAxis;
			this.lastBreakTarget = target;
		}
		if (net.zamasoft.foliojet.layout.fragment.ContinuationStats.guardBreakProgress(this.stalledBreakRun)) {
			// **あきらめは1回だけ**(2026-07-29)。カウンタを戻さないと、
			// 閾値へ到達して以降の自動改ページが<b>全て</b>拒否され、
			// ページが1枚も出なくなる。その結果
			// `AbstractUserAgent`の無進捗締切(120秒)に掛かり、
			// 変換が`Aborted.`で終わる(seed 213026で警告3,909回を実測)。
			this.stalledBreakRun = 0;
			this.lastBreakDepth = -1;
			return true;
		}
		return false;
	}

	/**
	 * 改ページ残余の再構築で、丸ごと移動した閉じた部分木をボックス再生の
	 * 代わりにソースイベントから再駆動します(M6b segment-restyle)。
	 * 移行期間中は opt-in です。
	 */
	private static final boolean SEGMENT_RESTYLE = !Boolean.getBoolean("foliojet.noSegmentRestyle");

	/**
	 * 切断段落の尾部ソース再生(M6b v3)。<b>既定無効</b>
	 * (2026-07-28。{@code -Dfoliojet.segmentRestyle.textTail=true} で有効化)。
	 *
	 * <p>
	 * <b>この機構には上限が無い。</b> 尾部再生は「{@code breakToken} の
	 * 文字位置から<b>ソースの末尾まで</b>」を流す({@code replayTextTail})。
	 * 断片が流れの最後なら正しいが、<b>そうでないことを知る手段が無い</b>
	 * ——終端は次の兄弟の {@code SourceAnchor} から導く設計だったが、
	 * 継続断片はレシピ構築でアンカーを持たない({@code -1})。しかも
	 * 実測すると次の断片は<b>同じ items に並ばない</b>(別の段・別の
	 * コンテナにいる)ため、そもそも兄弟として見えない。結果
	 * {@code cap} がログ末尾になり、後続の断片が組む分まで先に組む。
	 * </p>
	 *
	 * <p>
	 * {@code ColumnsContainer.restyle} の {@code pushTailSeal} はこの穴の
	 * <b>一部</b>(段の組み直しの最中)しか塞いでいない。残りは外側の
	 * PAGE 再開で {@code sealed=false} のまま発火する。
	 * </p>
	 *
	 * <p>
	 * <b>実測(2026-07-28)</b>: 掃過で最後まで残っていた「内容の複製」
	 * 3件(seed 115029 / 184116 / 186070)は<b>いずれもこれ単独が原因</b>で、
	 * 無効化すると3件とも複製も消失もゼロになる。さらに、この機構の
	 * <b>専用の基準出力そのものが複製を焼き込んでいた</b>——
	 * {@code 0460-segment-restyle/nested-break-in-replay.html} の
	 * {@code <p id="b1">} は 12×4+8=<b>56文字</b>だが、基準は
	 * <b>59文字</b>を描いており「ろはに」が二重になっている。無効時は
	 * ちょうど56文字になる。基準は2026-07-17に、この欠陥を含んだまま
	 * 採取されていた。
	 * </p>
	 *
	 * <p>
	 * 無効化の代償は<b>測って無い</b>: 430クラス890テストのうち、出力が
	 * 変わったのは上記の専用文書<b>1件だけ</b>(と、その再開トレース)。
	 * 断片は自分の {@code TextReplaySlice} を持っており境界付きで権威が
	 * あるので、ボックス再生へ落ちても内容は失われない。
	 * </p>
	 *
	 * <p>
	 * <b>再有効化するなら上限を与えること</b>——断片に「自分の内容が
	 * 終わるソース文字位置」を持たせ、event-id ではなく<b>文字レベル</b>の
	 * 上限として {@code replayTextTail} へ渡す。詳細は
	 * {@code copperpdf4/docs/NEXT-SESSION.md}。
	 * </p>
	 */
	private static final boolean TEXT_TAIL_RESTYLE = Boolean.getBoolean("foliojet.segmentRestyle.textTail");

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
	final class ResumeSession implements AutoCloseable, net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession {
		enum State {
			NEW, RESUMING, CONSUMED, FAILED, CLOSED
		}

		private final net.zamasoft.foliojet.layout.fragment.Continuation continuation;

		/**
		 * 破断時snapshot(実fragment署名の直接照合(E-3増分2)と、検証済み
		 * open path形からのtail policy導出(E-3増分3)に使う)。
		 */
		private final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot;

		/**
		 * 吸収済み再生範囲のリース(occurrence 単位)。吸収済み範囲は
		 * ボックスを運搬しない(フォールバックなし)ため、消費されるまで
		 * compact から守る(水位の clamp は LayoutSource が行う)。
		 * map 経由の再生(resumeScopes)はボックスが残っており
		 * box-restyle へ落ちられるのでリース不要。
		 */
		private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange, net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease> leases = new java.util.IdentityHashMap<>();

		private State state = State.NEW;

		ResumeSession(final net.zamasoft.foliojet.layout.fragment.Continuation continuation,
				final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot) {
			this.continuation = continuation;
			this.snapshot = snapshot;
			// 2026-07-30(legacy再帰撤去=増分4d): tail policy
			// (WorklistTailGate)は退役——worklist executorが唯一のdriverと
			// なり、routing判定そのものが消えた。
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
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.beginContinuationPath(false);
			RootBuilder.this.beginBreakRestyle(this.continuation.ranges());
			try {
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(0, "root-fragment",
						"depth=" + this.continuation.depth());
				RootBuilder.this.resumeFrame(this.continuation.root(), 0, this.continuation.depth(), this.snapshot);
				this.state = State.CONSUMED;
			} catch (RuntimeException | Error e) {
				this.state = State.FAILED;
				throw e;
			} finally {
				RootBuilder.this.endBreakRestyle();
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.endContinuationPath();
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.end();
				RootBuilder.this.sessions.pop();
			}
		}

		/**
		 * 吸収済み範囲の消費完了です(replaySubtree の finally から)。
		 */
		public void releaseLease(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange occurrence) {
			final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease = this.leases
					.remove(occurrence);
			if (lease != null) {
				lease.close();
			}
		}

		public boolean hasUnconsumedLeases() {
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
	 * {@code AbstractContainerBox.prepareColumnCut()}が返した{@link
	 * net.zamasoft.foliojet.layout.fragment.PreparedColumnCut}から、COLUMN
	 * 継続の正本トークン({@link
	 * net.zamasoft.foliojet.layout.fragment.ColumnContinuation})を構築・
	 * 検証します(2026-07-21新設、M6b Phase B4-Step4。2026-07-24のE-3増分5
	 * でprogram(ColumnResumeProgram)生成を除去し、正本トークン構築へ置換)。
	 * ownerへのcommit・実行(session)はまだ行わない——呼び出し側が
	 * 「検証→column commit→executor開始」の順序を守れるようにする
	 * (ChatGPT Pro相談、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
	 * 参照)。PAGEの{@code pageBreak()}と同型のprefix
	 * 吸収ロジック(stampRanges+extractReplayable)をCOLUMN向けに複製した
	 * ——既存のPAGE経路には一切触れずに済むよう、意図的に共有せず並行
	 * 実装している。{@code ranges}はconsume-once用のmutableなマップの
	 * まま{@code ColumnContinuation}に載せて運ぶ({@code replayFromSource()}
	 * が消費時に直接remove()するため、read-onlyにしてはいけない——実測で
	 * 発見・修正済みの規約)。
	 */
	final net.zamasoft.foliojet.layout.fragment.ColumnContinuation prepareColumnContinuation(
			final net.zamasoft.foliojet.layout.box.params.WritingMode ownerFlow,
			final net.zamasoft.foliojet.layout.fragment.PreparedColumnCut prepared,
			final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot) {
		final net.zamasoft.foliojet.layout.box.content.Container ownerRemainder = prepared.ownerRemainder();

		final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame> innerFrames = new java.util.ArrayList<>();
		for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = prepared.childFrame(); f != null;) {
			innerFrames.add(f);
			f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child : null;
		}

		final java.util.Map<net.zamasoft.foliojet.layout.box.IBox, net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> ranges = this
				.stampRanges(ownerRemainder, ownerFlow);
		for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
			ranges.putAll(this.stampRanges(f.container(), ownerFlow));
		}

		final boolean vertical = ownerFlow.isVertical();
		java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> anchorPrefix = java.util.List
				.of();
		final java.util.List<java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange>> framePrefixes = new java.util.ArrayList<>(
				innerFrames.size());
		if (!innerFrames.isEmpty()) {
			if (ownerRemainder instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
				anchorPrefix = fc.extractReplayable(ranges, vertical, 0);
			}
			for (final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f : innerFrames) {
				// walk depthはChild=0(このフレームはまだ内側へ続く)、
				// OpenTailShape=実際の残り深さ(0にすると末尾moved flowを
				// 閉部分木としてprefixへ吸収し、二重再生または内容消失に
				// つながりうる)
				final int walkDepth = switch (f.tail()) {
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child child -> 0;
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> shape.depth();
				};
				framePrefixes
						.add(f.container() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
								? fc.extractReplayable(ranges, vertical, walkDepth)
								: java.util.List.of());
			}
		}

		net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = null;
		for (int i = innerFrames.size() - 1; i >= 0; --i) {
			final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = innerFrames.get(i);
			tail = new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(f.recipe(), f.state(),
							f.container(), f.crossExtent(), framePrefixes.get(i), tail == null ? f.tail() : tail));
		}
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame childFrame = tail instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
				final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child : null;

		final net.zamasoft.foliojet.layout.fragment.ColumnAnchor anchor = new net.zamasoft.foliojet.layout.fragment.ColumnAnchor(
				ownerRemainder, anchorPrefix);
		// 2026-07-24(E-3増分1/5): 正本(COLUMN入力)を直接検証する(旧
		// compiler/verifierの不変条件はContinuationValidatorへ移植済み)——
		// 呼び出し元(BreakableBuilder.columnBreak)のcommitPreparedColumn
		// より前なので、検証失敗時はownerへのcommitなしで安全に止まる。
		final net.zamasoft.foliojet.layout.fragment.ContinuationValidator.PathShape pathShape = net.zamasoft.foliojet.layout.fragment.ContinuationValidator
				.validateColumn(anchor, snapshot, childFrame);
		return new net.zamasoft.foliojet.layout.fragment.ColumnContinuation(snapshot, anchor, childFrame, ranges,
				pathShape);
	}

	/**
	 * 検証済み{@link net.zamasoft.foliojet.layout.fragment.ColumnContinuation}
	 * を消費し、新columnのビルダー状態と内容を再開します(2026-07-21新設、
	 * M6b Phase B4-Step4)。呼び出し側は{@link #prepareColumnContinuation}の
	 * 後、{@code owner.commitPreparedColumn()}を実行済みであること。
	 *
	 * @param target 状態変異を適用する先のbuilder(改段を駆動している
	 *               実際のBreakableBuilder。nested な{@code ColumnBuilder}
	 *               の場合もある)
	 */
	final void resumeColumn(final BreakableBuilder target,
			final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation) {
		try (ColumnResumeSession session = new ColumnResumeSession(target, continuation)) {
			session.resume();
			assert !session.hasUnconsumedLeases() : "未消費の吸収済み再生範囲が残っています";
		}
	}

	/**
	 * COLUMN継続の一回きりの消費セッションです(2026-07-21新設、
	 * M6b Phase B4-Step4)。{@link ResumeSession}のCOLUMN版——設計は
	 * 同一(状態遷移・リース所有・例外時清算の対称性)。
	 */
	final class ColumnResumeSession implements AutoCloseable, net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession {
		enum State {
			NEW, RESUMING, CONSUMED, FAILED, CLOSED
		}

		private final BreakableBuilder target;
		/** COLUMN継続の正本トークンです(E-3増分5でprogramを置換)。 */
		private final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation;
		private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange, net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease> leases = new java.util.IdentityHashMap<>();
		private State state = State.NEW;

		ColumnResumeSession(final BreakableBuilder target,
				final net.zamasoft.foliojet.layout.fragment.ColumnContinuation continuation) {
			this.target = target;
			this.continuation = continuation;
			final net.zamasoft.foliojet.layout.fragment.LayoutSource log = RootBuilder.this.pageGenerator
					.getLayoutSource();
			if (log != null) {
				for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : continuation.anchor()
						.prefixItems()) {
					this.leases.put(r, log.retainFrom(r.fromId()));
				}
				for (net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = continuation
						.childFrame(); f != null;) {
					for (final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange r : f.prefixItems()) {
						this.leases.put(r, log.retainFrom(r.fromId()));
					}
					f = f.tail() instanceof net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
							final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) ? child
									: null;
				}
			}
		}

		void resume() {
			if (this.state != State.NEW) {
				throw new IllegalStateException("継続は一度だけ消費できる: " + this.state);
			}
			this.state = State.RESUMING;
			RootBuilder.this.sessions.push(this);
			net.zamasoft.foliojet.layout.fragment.ResumeTrace.begin("COLUMN");
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.beginContinuationPath(true);
			this.target.beginRestyling();
			RootBuilder.this.beginBreakRestyle(this.continuation.ranges());
			try {
				if (this.continuation.childFrame() != null) {
					RootBuilder.this.restyleFrame(this.target, this.continuation.anchor().remainder(),
							this.continuation.anchor().prefixItems(),
							net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
					RootBuilder.this.resumeFragmentChain(this.continuation.childFrame(), 1,
							this.continuation.snapshot().depth(), this.continuation.snapshot(), this.target);
				} else {
					assert this.continuation.anchor().prefixItems().isEmpty();
					// E-3増分5: 終端の開き形はpathShape.terminalShape()が
					// 正本(旧program.tail().openDepth()と同値——
					// childFrame==nullではvalidateColumnが
					// OpenShape.of(snapshot.depth())を返し、旧compilerの
					// OpenText(1)/LegacyOpen(1, snapshotDepth)と一致する)。
					// 2026-07-30(増分4d): worklist適格判定とoverrideは退役
					// ——restyle()自体が無条件にworklist executorで駆動する。
					this.continuation.anchor().remainder().restyle(this.target,
							this.continuation.pathShape().terminalShape(), false);
				}
				this.state = State.CONSUMED;
			} catch (RuntimeException | Error e) {
				this.state = State.FAILED;
				throw e;
			} finally {
				RootBuilder.this.endBreakRestyle();
				this.target.endRestyling();
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.endContinuationPath();
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.end();
				RootBuilder.this.sessions.pop();
			}
		}

		public void releaseLease(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange occurrence) {
			final net.zamasoft.foliojet.layout.fragment.LayoutSource.RetentionLease lease = this.leases
					.remove(occurrence);
			if (lease != null) {
				lease.close();
			}
		}

		public boolean hasUnconsumedLeases() {
			return !this.leases.isEmpty();
		}

		@Override
		public void close() {
			if (this.state == State.CLOSED) {
				return;
			}
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
	 * 入れ子改ページ・改段で入れ子になる。top が現在のセッション)。
	 * 2026-07-21(M6b Phase B4-Step4): PAGE専用の{@code ResumeSession}から
	 * {@link net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession}へ
	 * 一般化した——COLUMN側の{@link ColumnResumeSession}も同じスタックで
	 * 管理することで、COLUMN resume中にPAGE breakが入れ子になっても
	 * (またはその逆でも)、{@link #replaySubtree}が常に「現在のtop
	 * セッション」だけを見ればよいようにする(ChatGPT Pro相談、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b4-column-target.md
	 * 参照)。
	 */
	private final java.util.ArrayDeque<net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession> sessions = new java.util.ArrayDeque<>();

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
			// isSourceReplayable(2026-07-28): 切断済みの前断片はアンカーを
			// 持ち続けるが、その範囲は継続断片が持っている残りも含む。
			// 刻印すると再開で要素全体が再生され、継続断片の再開と二重に
			// なる(入れ子段組の段バランスで実測)。ボックス再生へ落とす
			final long startId = box.isSourceReplayable() ? box.getSourceAnchor() : -1;
			if (startId >= 0) {
				final long endId = log.endOf(startId);
				// containsAbsolute(E-6増分4e): 絶対配置は増分4e以前はOpaque
				// 記録でcontainsOpaqueが捕捉していた。recipe記録化後も、
				// 絶対配置を含む部分木のソース再生置換は係留・deferred bindの
				// 二重化を生むため従来どおりbox-restyleへフォールバックさせる
				// (LayoutSource.containsAbsoluteのjavadoc参照)
				// isIntact(2026-07-27): compactは「開いているStart」だけを
				// 水位より前から残すので、破断時にまだ開いていた要素は
				// 「Startだけ残って中身が消えた」状態になりうる。その要素が
				// 後で閉じるとendOf()は疎な保持列の上で終端を返してしまい、
				// 穴あきの範囲を再生可能と誤って刻印する。吸収済み範囲
				// (prefixItems)はボックスを運搬しない=フォールバック不能
				// なので、刻印の時点で密度を確かめる(確かめないと
				// replaySubtreeが「吸収済み再生範囲が失われました」で
				// 変換ごと停止する。実測: 掃過10万件中15件)
				// containsFloat(2026-07-28): 部分木の中のフロートは
				// 「最近接ブロック祖先のコンテナに係留されるので部分木と
				// 一緒に動く」——という前提が段組では崩れる。フロートは
				// 集約({@code aggregateFloatings})で段のコンテナへ**引き上げ
				// られる**ため、部分木が丸ごと移動しても<b>フロートは元の
				// 段に残る</b>。その部分木をソースから再生すると、引き上げ
				// られた側とあわせて<b>二度組まれる</b>(実測:
				// local/shrink/strict-29708-min.html ほか。float内の
				// "T3 T4" が同じページに二度描かれる)。
				// {@code SourceReplayer.canReplayChildren}と
				// {@code replayTextTail}は最初からこのゲートを持っており、
				// 「係留の再実行(二重化)の危険」を同じ理由で避けている——
				// ここだけ抜けていた。ボックス再生へ落とす
				// containsTable(表セット、2026-07-30): 表のrecipe記録化により
				// 表はOpaqueでなくなった。<b>TABLE自身を根とする範囲だけ</b>
				// 刻印を許可する(T-b。消費者はrestyleItem case TABLEの直接
				// replay=T-c)——表を「含む」BLOCK部分木のreplaySubtreeでの
				// 表再構築は未検証のため従来どおりbox-restyleへ(codex増分11で
				// 解禁を検討)。根が表のとき自身のStart(startId)は範囲に
				// 含まれて当然なので内容側(startId+1〜)だけを検査し、
				// セル内の入れ子表はfail closedで従来どおり弾く
				final long tableCheckFrom = box instanceof net.zamasoft.foliojet.layout.box.impl.TableBox
						? startId + 1
						: startId;
				if (endId >= 0 && log.isIntact(startId, endId) && !log.containsOpaque(startId, endId)
						&& !log.containsTable(tableCheckFrom, endId)
						&& !log.containsAbsolute(startId, endId)
						&& !log.containsFloat(startId, endId)
						&& !log.containsMulticol(startId, endId)
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

	@Override
	protected final boolean supportsNamedPages() {
		return true;
	}

	@Override
	protected final String currentPageName() {
		return this.pageGenerator.getPageName();
	}

	@Override
	protected final void setNextPageName(final String pageName) {
		this.pageGenerator.setPageName(pageName);
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
		if (this.guardBreakProgress(mode)) {
			// **ライブロック確定。あきらめるのではなく、逃げ道へ入れる**
			// (2026-07-29)。
			//
			// 以前はここで false を返して改ページを放棄していたが、それは
			// 内容を1つも消費しないので同じ状態へ戻るだけだった
			// (seed 213026で実測: 120秒・1ページも出ず・あきらめ17,522回、
			// ingestは87で停止)。
			//
			// 停滞の実体は「紙面に収まらない浮動体が、送り先で同じ相対位置
			// へ再配置されて再び溢れる」循環で、これを断つ逃げ道
			// ({@code FloatSplitPlan}の分岐表5「先頭ならはみ出させて置く」)
			// は既にある。ただし条件が`fragmentHead()`(pageAxis<=0)なので、
			// 停滞点(実測 pageAxis=154.6)では決して成立しない。
			// そこで**この一回だけ first 扱いにするビットを立てて続行する**。
			flags |= IPageBreakableBox.FLAGS_LIVELOCK;
		}

		// ボックスの高さを計算
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			flow.box.setPageAxis(this.pageAxis - flow.pageAxis);
		}

		// C1b/C1d-C 事前検分: 祖先チェーン(flowStack[1..])の先頭から plain
		// FlowBlockBox(段組・表・縦横混在なし)が連続する「収集可能な
		// プレフィックス」だけを読み取り専用の計画に載せ、切断貫通レベルの
		// 断片をボックス構築なしで継続化する。最初に違反したレベルで
		// スキャンを止める(2026-07-20、以前は1レベルでも不可なら
		// 全体をall-or-nothingで従来経路に落としていたため、多数のplain
		// ラッパーの外側にmulticol等が1つ混ざっただけで祖先チェーン全体が
		// 未反復のOpenChain再帰に回っていた——実測でdepth 74に到達する
		// ケースを確認済み。BreakPlan.depth はプレフィックス長ではなく
		// 常に flowStack.size()(不変)を渡す。BreakPlan.openTailDepth()
		// = depth - index - 1 はこの depth を歩かずに得られる値のまま
		// 保つことで、プレフィックスの外に落ちた残り(違反箇所+その内側)
		// だけがOpenChainの実深さになる——depth自体を短縮すると
		// OpenShapeの入れ子数と実ボックス木の開き構造が食い違い、
		// まだ開いているボックスを閉じたものとして誤処理しうるため、
		// 絶対に触らない(外部レビューで確認済み、
		// docs/consultations/consult-open-chain-prefix-*.md参照)。
		// 断片は split の返り値(SplitResult.Frame → ContainerCut.WithFrame)
		// で外へ伝播する — side channel なし
		//
		// 2026-07-21(B2): スキャン自体を OpenPathScan.capture() へ委譲した
		// (挙動不変。B1のContinuationCapability分類をそのまま使う)。
		// スナップショットはこの後 ContinuationValidator の検証にも使う
		// (再分類しない——ChatGPT Pro相談で確認、
		// docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b2-resume-program.md)。
		//
		// 2026-07-21(B3a): MULTICOLをPAGE自動改ページ(ForceBreakMode以外)
		// でのみ収集可能にした——強制改ページでは
		// FlowContainer.splitPageAxisがKEEP/MOVEを無条件に
		// AssertionError("force break failed")へ落とす経路があり、
		// 現時点では安全と確認できていない(B3bとして見送り。ChatGPT Pro
		// 相談で指摘・検証済み、
		// docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-b3-multicol-split-through.md)。
		final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot;
		final net.zamasoft.foliojet.layout.fragment.BreakPlan plan;
		{
			final java.util.List<net.zamasoft.foliojet.layout.box.AbstractContainerBox> openBoxes = new java.util.ArrayList<>(
					this.flowStack.size());
			for (int i = 0; i < this.flowStack.size(); ++i) {
				openBoxes.add(((Flow) this.flowStack.get(i)).box);
			}
			final net.zamasoft.foliojet.layout.fragment.OpenPathScan scan = net.zamasoft.foliojet.layout.fragment.OpenPathScan
					.capture(openBoxes, mode);
			scan.snapshot().firstBarrier().ifPresent(barrier -> net.zamasoft.foliojet.layout.fragment.ContinuationStats
					.recordCapabilityScanStop(barrier.reason()));
			snapshot = scan.snapshot();
			plan = scan.toBreakPlan();
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
			if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(
					final net.zamasoft.foliojet.layout.box.content.Container chainStopContainer,
					final net.zamasoft.foliojet.layout.fragment.ChainStopReason reason)) {
				// AbstractBlockBox.splitForContinuationと同じ理由
				// (コンテンツ消失リスク)。containerが空の場合のみ
				// 「改ページポイントなし」としてfalseを返し、実内容が
				// ある場合は下の共通ルートフレーム構築ロジックへ合流
				// させる(専用のMovedOpen型は2026-07-22に撤去した、
				// docs/history/2026-07-22-pagination-contract
				// -consultation.md参照)。詳細はdocs/history/2026-07-22
				// -chainstop-content-loss-safety-net.md参照
				final boolean hasContent = chainStopContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
						&& (fc.hasFlows() || fc.hasFloatings());
				if (!hasContent) {
					// KEEP/MOVE: 改ページポイントがない場合
					return false;
				}
				nextRootContainer = chainStopContainer;
				rootChildFrame = null;
			} else if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(
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

		// 2026-07-21: 終端の OpenTailShape 深さはこの時点で既に確定している
		// (splitForContinuation が破断時に計算済み)。2026-07-30(増分4c):
		// worklist一本化でOpenChain降下が非再帰となったため深さ64の型付き
		// 例外ガードは退役し、観測用の最大深さ記録だけを残した。
		{
			final int terminalOpenDepth;
			if (rootChildFrame == null) {
				terminalOpenDepth = this.flowStack.size();
			} else {
				final net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail lastTail = innerFrames
						.get(innerFrames.size() - 1).tail();
				// innerFramesの走査規約上lastTailがChildになることは構造的に
				// ありえない
				terminalOpenDepth = switch (lastTail) {
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> shape.depth();
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child child ->
					throw new IllegalStateException("innerFrames walk must terminate on a non-Child tail");
				};
			}
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenDepth(terminalOpenDepth, false);
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
		// 何も描かないページは出力されない(css-break-3 §4.4)。落ちた
		// ページは面(recto/verso)を消費しないので、こちらの面の追跡も
		// 進めてはならない——進めると以後の左右改ページが全部裏返る
		if (mode instanceof ForceBreakMode force && force.namedTransition) {
			// 名前遷移で閉じたページは白紙なら落とす(N2b——drawPageが判定)
			this.pageBox.markNamedTransitionClosed();
		}
		final boolean emitted = this.pageGenerator.drawPage(this.pageBox, false,
				mode instanceof BreakMode.ForceBreakMode);
		final PageBox pageBox = this.pageBox;
		this.pageBox = this.pageGenerator.nextPage();
		if (mode instanceof BreakMode.ForceBreakMode) {
			// 強制改ページで始まったページは、白紙でも作者の意図として残す
			this.pageBox.markForcedBreakOrigin();
		}
		// 脚注F4: 送られてきた脚注(carry-in)を新ページの容量へ最優先で
		// 再予約する——継続本文がrestyle・構築される前でなければ、予約
		// なしの容量で組まれてしまう
		this.reserveFootnotes();
		if (emitted && this.pageSide != PageBreakMode.AUTO) {
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
		// walk depth はフレームの tail から導出(Child=0、OpenTailShape=d)
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
				// walk depthはChild=0、OpenTailShape=実際の残り深さ
				// (上記prepareColumnContinuationと同じ理由)
				final int walkDepth = switch (f.tail()) {
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child child -> 0;
				case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> shape.depth();
				};
				framePrefixes.add(f.container() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
						? fc.extractReplayable(ranges, rootVertical, walkDepth)
						: java.util.List.of());
			}
		}

		// C1d-C: prefix を焼き込んだフレーム木を内→外に再構成する。
		// 最内フレームは cascade が確定した OpenTailShape を保持
		net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = null;
		for (int i = innerFrames.size() - 1; i >= 0; --i) {
			final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f = innerFrames.get(i);
			tail = new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(f.recipe(), f.state(),
							f.container(), f.crossExtent(), framePrefixes.get(i), tail == null ? f.tail() : tail));
		}
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame rootFrame = new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(
				rootRecipe, rootState, nextRootContainer, rootCrossExtent, rootPrefix,
				tail == null ? new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						net.zamasoft.foliojet.layout.fragment.OpenShape.of(depth)) : tail);
		final net.zamasoft.foliojet.layout.fragment.Continuation continuation = new net.zamasoft.foliojet.layout.fragment.Continuation(
				depth, rootFrame, ranges);

		// 2026-07-24(E-3増分4): 正本(Continuation)を直接検証する(旧
		// ResumeProgramCompiler/ContinuationVerifierの不変条件は
		// ContinuationValidatorへ移植済み——programはもう生成しない)。
		// malformedな継続はこの時点(flowStack.clear()・resume側の状態変異
		// より前)で例外を投げて安全に停止する。2026-07-30(増分4d):
		// 戻り値のPathShapeはtail policy(WorklistTailGate)導出にのみ
		// 使われていたため、gate退役に伴い構造検証だけを残して捨てる。
		net.zamasoft.foliojet.layout.fragment.ContinuationValidator.validatePage(snapshot, continuation);

		this.flowStack.clear();
		// 2026-07-23(排除域P1増分1): 旧断片のhiddenスコープ台帳を捨てる
		// (再開されるhidden flowはresumeのstartFlowBlock()が積み直す)。
		this.rebuildNoOverflowFloatingScopes();
		pageBox.restyle(this, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
		// P1: セッションがリース(occurrence 単位)とスコープを所有し、
		// consume-once と例外時清算を対称に保証する
		try (ResumeSession session = new ResumeSession(continuation, snapshot)) {
			session.resume();
			assert !session.hasUnconsumedLeases() : "未消費の吸収済み再生範囲が残っています";
		}
		this.pageGenerator.compactLayoutSource(watermark);
		// 2026-07-21: 旧来はassertのみ(本番では無検査)だったが、ChatGPT Pro
		// 相談で「直交writing-modeの表(IncrementalTableBuilder経由の改ページ、
		// BreakableBuilder.forceBreak()がbreakDepth障壁を迂回する)」が
		// この不変条件を破る既存の到達可能経路であることが判明し、実測でも
		// 確認した(本セッションの変更とは無関係の既存バグ)。本番でこの
		// チェックが無効だと、flowStackが破断前後で不整合なまま処理が
		// 継続し、検知されないコンテンツ破損に至る恐れがあるため、
		// テスト・本番を問わず例外を投げる形に変更する。
		if (this.flowStack.size() != continuation.depth()) {
			throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					"break flow failed (flowStack.size()=" + this.flowStack.size() + ", continuation.depth()="
							+ continuation.depth() + "): " + this.getFlowBox().getParams().element);
		}

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
	 * OpenTailShape なら従来の深さ規約(最内の moved-open ボックス・
	 * 開きテキストの継続)。
	 *
	 * <p>
	 * 2026-07-20: {@code Child}分岐の自己再帰(チェーン断片1段につき1回)を
	 * 明示的ループへ反復化した(ARCHITECTURE.md不変条件6)。
	 * {@code DeepNestingRestyleTest}(深さ200)で
	 * {@code ContinuationStats.CHILD_FRAMES}が実際に1000超発火することを
	 * 確認済みで、再帰のままでは深いネスト文書でStackOverflowErrorに
	 * 到達しうる。再帰呼び出しがswitch文の唯一かつ末尾の文だった
	 * (呼び出し後に何もしない末尾再帰)ため、`frame`/`index`を書き換えて
	 * ループ先頭へ戻すだけで挙動を変えずに反復化できる。
	 * </p>
	 *
	 * @param frame 開始フレーム
	 * @param index 外からの位置(0=ルート。トレースの chain-fragment 番号)
	 * @param depth 継続全体の深さ(トレース表示用)
	 * @param snapshot 破断時snapshot(実fragment署名の直接照合、E-3増分2)
	 */
	private void resumeFrame(net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame frame, int index,
			final int depth, final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot) {
		this.resumeFragmentChain(frame, index, depth, snapshot, this);
	}

	/**
	 * PAGE/COLUMN共有のfragment chain executorです(2026-07-21、
	 * M6b Phase B4残作業でPAGE専用の{@code resumeFrame}から改名・明示的に
	 * 共有メソッドとして切り出した)。{@code index==0}の全ボックス
	 * restyle(収集不能な破断、チェーンなし)分岐はPAGE root専用に見えるが、
	 * 実際にはこのメソッド自体がPAGE/COLUMN両方の入口であり、COLUMN側
	 * (owner内側のfragment chain実行)は常に{@code index=1}から呼ぶため、
	 * この分岐は構造的にCOLUMN側からは到達しない(indexは単調増加する
	 * ため、一度でもindex&gt;0になれば以降index==0には戻らない)——別の
	 * メソッドへ完全に分離すると{@code continueFragment}のfragment
	 * 再構成を二重に行うリスクがあるため、単一ループ内で条件分岐する形を
	 * 維持している。
	 *
	 * @param target 状態変異(startFlowBlock/restyle)を適用する先の
	 *               builder(2026-07-21新設、M6b Phase B4-Step4)。PAGEは
	 *               常に{@code RootBuilder.this}(旧来どおり)。COLUMNは
	 *               改段を駆動している実際の{@code BreakableBuilder}
	 *               (nested な{@code ColumnBuilder}の場合もある——M6c
	 *               の段バランスprobe中に、probeの内容自体がさらに改段を
	 *               要する場合)を渡す。
	 */
	private void resumeFragmentChain(net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame frame, int index,
			final int depth, final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot snapshot,
			final BlockBuilder target) {
		while (true) {
			this.checkAbort();
			assert !this.resumeScopes.isEmpty();
			final net.zamasoft.foliojet.layout.box.AbstractBlockBox block = net.zamasoft.foliojet.layout.box.AbstractBlockBox
					.continueFragment(frame.recipe(), frame.state(), frame.container(), frame.crossExtent());
			// P1: 型検査つきの消費(表フレーム等の新種別は明示的に追加する —
			// FrameRemainder sum type の下地。盲目的キャストで壊れない)
			if (!(block instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox box)) {
				throw new IllegalStateException("未対応のフレーム種別: " + block.getClass().getName());
			}
			// 2026-07-24(E-3増分2): instantiate直後・builder状態変異
			// (startFlowBlock/restyle)前に、実fragmentの署名を破断時
			// snapshotと直接照合する(shadowのInstantiate照合が持っていた
			// 唯一の独立価値の直接化。不一致は型付き例外で停止し、legacyで
			// 再試行しない)。
			final net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.FragmentSignature signature = net.zamasoft.foliojet.layout.fragment.OpenPathSnapshot.FragmentSignature
					.from(box);
			net.zamasoft.foliojet.layout.fragment.ContinuationValidator.checkFragmentSignature(snapshot, index,
					signature);
			switch (frame.tail()) {
			case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(
					final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame child) -> {
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordChildFrame();
				target.startFlowBlock(box);
				this.restyleFrame(target, box.getContainer(), frame.prefixItems(),
						net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
				net.zamasoft.foliojet.layout.fragment.ResumeTrace.op(index + 1, "chain-fragment",
						"depth=" + (depth - (index + 1)));
				frame = child;
				++index;
			}
			case net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
					final net.zamasoft.foliojet.layout.fragment.OpenShape shape) -> {
				// 2026-07-30(増分4c/4d): 深さガードの重複検査と、B6a1由来の
				// worklist適格判定+override(旧: WORKLIST_ELIGIBLEのときだけ
				// terminal restyleをworklistで駆動)は退役した——restyle()
				// 自体が無条件にworklist executorで駆動する。
				if (index == 0) {
					// 収集不能な破断(チェーンなし): 従来の全ボックス restyle。
					// この経路では prefix 吸収は行われていない
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordUnchainedRestyle();
					assert frame.prefixItems().isEmpty();
					box.restyle(target, shape);
				} else {
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordOpenTail();
					target.startFlowBlock(box);
					this.restyleFrame(target, box.getContainer(), frame.prefixItems(), shape);
				}
				return;
			}
			}
		}
	}

	/**
	 * フレームコンテナを再開します(C1c)。吸収済みの再生範囲(prefix)を
	 * serial 順で残アイテムと合流させる。
	 *
	 * @param target 状態変異を適用する先のbuilder(2026-07-21、B4-Step4で
	 *               {@code this}固定から一般化)。
	 */
	private void restyleFrame(final BlockBuilder target, final net.zamasoft.foliojet.layout.box.content.Container container,
			final java.util.List<net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange> prefix,
			final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (container instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc) {
			fc.restyle(target, shape, false, prefix);
		} else {
			assert prefix.isEmpty();
			container.restyle(target, shape, false);
		}
	}

	/**
	 * 吸収された閉部分木をソース再駆動します(C1c)。再生可否は破断時に
	 * 判定済み(stampRanges)のため無条件。
	 */
	public void replaySubtree(final net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange range,
			final BlockBuilder target) {
		final net.zamasoft.foliojet.layout.fragment.ReplayLeaseSession session = this.sessions.peek();
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
		this.attachFootnotes();
		this.pageBox.finishLayout(this.pageBox);
	}

	public void finish() {
		this.finishLayout();
		// 脚注F4: 容量送りされた脚注が残っていれば、note-onlyページを
		// pendingが空になるまで生成する。前進しない回(1件も配置できない)は
		// call消失か走査欠落の不変条件違反として型付き失敗にする
		// (送り続けて無限ページを生まない)
		while (!this.pendingFootnotes.isEmpty()) {
			this.footnoteProgressed = false;
			this.pageGenerator.drawPage(this.pageBox, false, false);
			this.pageBox = this.pageGenerator.nextPage();
			this.reserveFootnotes();
			this.finishLayout();
			// 前進の無い回は、どのページにも置けない脚注(call消失等)と
			// して型付き失敗にする(無限ページ防止)
			if (!this.footnoteProgressed) {
				throw new FootnoteOverflowException("footnote cannot be placed"
						+ " (too large atomic content or missing call): " + this.pendingFootnotes.size()
						+ " pending at EOF");
			}
		}
		this.pageGenerator.drawPage(this.pageBox, true, false);
	}

	// ------------------------------------------------------------------
	// 脚注(F2〜F4、2026-07-31——consult-codex-2026-07-31-footnote.txt §3と
	// 同-f4.txt。初期サブセットはオーナー承認済み: 文書通番・保守的確保・
	// 空ページにも入らない巨大脚注は型付きエラー・縦書き/段組/@footnote/
	// 分割は後続増分)

	/**
	 * 未配置の脚注1件です。{@code committed}は「呼び出しが過去の確定
	 * ページに残った」——容量送り(carry-in)された脚注は次ページで
	 * callゼロ件でも最優先で配置しなければならない(F4答申の要点)。
	 */
	private static final class FootnoteEntry {
		final long id;

		final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox;

		boolean committed = false;

		/**
		 * ページローカルの脚注番号です(F5、1始まり。未採番は-1)。番号の
		 * スコープはnote配置ページではなく<b>callが残ったページ</b>——
		 * carry-inされたnoteは後続ページでもcallページの番号を保つ。
		 */
		int assignedNumber = -1;

		FootnoteEntry(final long id, final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox) {
			this.id = id;
			this.noteBox = noteBox;
		}
	}

	/** 未配置の脚注(文書順が正本。箱木の走査順はbidi等で崩れるため)。 */
	private final java.util.ArrayDeque<FootnoteEntry> pendingFootnotes = new java.util.ArrayDeque<>();

	/**
	 * 現ページに予約済みのpending先頭prefixの件数と、その予約量
	 * (gap込み、ページ方向)。予約はページ内で単調非減少——呼び出しが
	 * 次ページへ移っても返さない「保守的確保」(前ページ下端に空きが
	 * 残り得る。明示的仕様逸脱)。
	 */
	private int footnoteReservedCount = 0;

	private double footnoteReservation = 0;

	/** 本文と脚注領域の間隙(UA固定。separator罫線はこのgapの中央)。 */
	private static final double FOOTNOTE_GAP = 6;

	/**
	 * 脚注のページ方向占有量です(axis-neutral——F6/F7答申②)。箱の幾何と
	 * 描画実測の大きい方(既存floatのoccupied-page-extent規則と同じ)。
	 */
	private double footnoteExtent(final net.zamasoft.foliojet.layout.box.IBox box) {
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.pageBox.getBlockParams().flow;
		return Math.max(box.getPageExtent(flow), box.paintedPageExtent(flow));
	}

	/**
	 * 完成した脚注本文を台帳へ加えます({@code DocumentBuilder.endBox}の
	 * FLOAT分岐から)。現ページの容量に入る分だけ予約が伸び、本文容量
	 * ({@link #getPageLimit()})が縮んで以後の溢れ検査・改ページが新しい
	 * 容量で行われる。容量を超えた分は予約されず次ページへ送られる(F4)。
	 */
	public void addFootnote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox) {
		final double noteExtent = this.footnoteExtent(noteBox);
		final double maxArea = super.getPageLimit() - MIN_PAGE_LIMIT;
		if (FOOTNOTE_GAP + noteExtent > maxArea) {
			// 空ページの最大脚注領域にも収まらない——病的なレイアウトとして
			// 救済(複数ページ分割)せず型付き失敗にする(オーナー裁定
			// 2026-07-31: 分割機構F6は実装後に撤去。復元が必要になったら
			// c9592cd参照)。版面の9割超を占める単一脚注だけが該当し、
			// それ未満はF4の丸ごと送りで次ページへ置ける
			throw new FootnoteOverflowException("footnote too large for any page: " + noteExtent
					+ "pt (max footnote area " + maxArea + "pt)");
		}
		this.pendingFootnotes
				.addLast(new FootnoteEntry(noteBox.getParams().footnoteId, noteBox));
		this.reserveFootnotes();
	}

	/**
	 * pendingの先頭prefixのうち現ページの最大脚注領域に収まる分まで
	 * 予約を伸ばします(FIFO——途中を飛ばさない)。
	 */
	private void reserveFootnotes() {
		final double maxArea = super.getPageLimit() - MIN_PAGE_LIMIT;
		int i = 0;
		for (final FootnoteEntry entry : this.pendingFootnotes) {
			if (i >= this.footnoteReservedCount) {
				final double cost = (this.footnoteReservation == 0 ? FOOTNOTE_GAP : 0)
						+ this.footnoteExtent(entry.noteBox);
				if (this.footnoteReservation + cost > maxArea) {
					// 入らない分はF4のFIFO送り(次ページで再予約)
					break;
				}
				this.footnoteReservation += cost;
				++this.footnoteReservedCount;
			}
			++i;
		}
	}

	@Override
	public double getPageLimit() {
		final double base = super.getPageLimit();
		if (this.footnoteReservation == 0) {
			return base;
		}
		return Math.max(MIN_PAGE_LIMIT, base - this.footnoteReservation);
	}

	/**
	 * ページ確定時の脚注の清算です(finishLayoutから=分割完了後・描画前)。
	 * 確定した箱木に残った::footnote-callのID集合を採取し、pendingの
	 * 先頭から「carry-in済み(committed)またはcallがこのページに残った」
	 * 連続prefixだけを版面下端へ配置する。callがこのページに残ったが
	 * 配置されなかった脚注(容量送り・順序保持)はcommittedにして次ページで
	 * 最優先配置。配置座標は予約高ではなく実配置分の合計高で下端揃え
	 * (call移動で一部を送った場合、予約高のままだと下端に浮く)。
	 * 台帳状態は配置ゼロ件でも必ず清算する(次ページへ漏らさない)。
	 */
	private void attachFootnotes() {
		if (this.pendingFootnotes.isEmpty()) {
			this.footnoteReservedCount = 0;
			this.footnoteReservation = 0;
			return;
		}
		final FootnoteCallScan scan = this.scanFootnoteCalls(this.pageBox);
		final java.util.Set<Long> retained = scan.ids();

		// F5: 採番——このページにcallが残った未採番entryへ、FIFO(文書順)で
		// 1から割り当てる。committed(過去ページで採番済みのcarry-in)は
		// 再採番しない
		{
			int nextNumber = 1;
			for (final FootnoteEntry entry : this.pendingFootnotes) {
				if (!entry.committed && retained.contains(entry.id)) {
					entry.assignedNumber = nextNumber++;
				}
			}
		}
		// 配置計画(変異なしで全件の行き先を確定してから一度だけcommit)
		int attachCount = 0;
		double attachedExtent = 0;
		{
			int i = 0;
			for (final FootnoteEntry entry : this.pendingFootnotes) {
				if (i >= this.footnoteReservedCount
						|| (!entry.committed && !retained.contains(entry.id))) {
					break;
				}
				++attachCount;
				attachedExtent += this.footnoteExtent(entry.noteBox);
				++i;
			}
		}
		// 配置されない残りのうち、callがこのページに残ったものはcarry-in
		{
			int i = 0;
			for (final FootnoteEntry entry : this.pendingFootnotes) {
				if (i >= attachCount && retained.contains(entry.id)) {
					entry.committed = true;
				}
				++i;
			}
		}
		// F5: このページの確定木に残ったcallラベルを解決する(markerは
		// note側なのでattach時)。pendingに居ないIDのラベル(過去に配置済み
		// のnote内marker等)はスキップ
		{
			final java.util.Map<Long, Integer> numbers = new java.util.HashMap<>();
			for (final FootnoteEntry entry : this.pendingFootnotes) {
				if (entry.assignedNumber > 0) {
					numbers.put(entry.id, entry.assignedNumber);
				}
			}
			for (final net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label : scan.labels()) {
				if (!label.isMarker()) {
					final Integer number = numbers.get(label.getFootnoteId());
					if (number != null) {
						label.resolve(number);
					}
				}
			}
		}
		final double base = super.getPageLimit();
		double pageAxis = base - attachedExtent;
		for (int i = 0; i < attachCount; ++i) {
			final FootnoteEntry entry = this.pendingFootnotes.removeFirst();
			if (entry.assignedNumber < 0) {
				throw new FootnoteOverflowException(
						"footnote attached without an assigned number: id=" + entry.id);
			}
			// note本文先頭の::footnote-markerラベルをcallページの番号で解決
			for (final net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label : this
					.scanFootnoteCalls(entry.noteBox).labels()) {
				if (label.isMarker()) {
					label.resolve(entry.assignedNumber);
				}
			}
			this.pageBox.getContainer().addFloating(entry.noteBox, 0, pageAxis);
			pageAxis += this.footnoteExtent(entry.noteBox);
			this.footnoteProgressed = true;
		}
		if (attachCount > 0) {
			// separator罫線(F6/F7答申①): 既存gapの中央に置くため予約は
			// 増えない。描画はPageSequence.drawPageのflow後(artifact)
			this.pageBox.setFootnoteSeparatorAxis(base - attachedExtent - FOOTNOTE_GAP / 2);
		}
		this.footnoteReservedCount = 0;
		this.footnoteReservation = 0;
	}

	/** 直近のattachで配置が進んだか(finish()の前進性ガード)。 */
	private boolean footnoteProgressed = false;

	/** 走査結果: callのID集合と、見つかった脚注ラベル(call/marker両方)。 */
	private record FootnoteCallScan(java.util.Set<Long> ids,
			java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels) {
	}

	/**
	 * 箱木から脚注のcall ID集合とラベル原子を採取します(F4答申の候補A改+
	 * F5のラベル解決)。行跨ぎで同一callのインライン断片が複製されても
	 * 集合なので1件に畳まれる。走査は明示worklistの反復DFS(flow・float・
	 * 行・インラインのみ。表・絶対配置内のcallは初期サブセット外)。
	 */
	private FootnoteCallScan scanFootnoteCalls(final net.zamasoft.foliojet.layout.box.AbstractContainerBox root) {
		final java.util.Set<Long> ids = new java.util.HashSet<>();
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels = new java.util.ArrayList<>();
		final java.util.ArrayDeque<Object> work = new java.util.ArrayDeque<>();
		work.push(root);
		while (!work.isEmpty()) {
			final Object node = work.pop();
			if (node instanceof net.zamasoft.foliojet.layout.box.IBox box) {
				final net.zamasoft.foliojet.layout.box.params.Params params = box.getParams();
				if (params != null && params.element == net.zamasoft.foliojet.css.CSSElement.FOOTNOTE_CALL
						&& params.footnoteId >= 0) {
					ids.add(params.footnoteId);
				}
			}
			if (node instanceof net.zamasoft.foliojet.layout.box.AbstractReplacedBox replaced
					&& replaced.getReplacedParams().image instanceof net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label) {
				labels.add(label);
			}
			if (node instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox container) {
				container.getContainer().eachFlowBox(work::push);
				container.getContainer().eachFloatingBox(work::push);
			} else if (node instanceof net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock) {
				textBlock.forEachLine(work::push);
			} else if (node instanceof net.zamasoft.foliojet.layout.box.AbstractTextBox textBox) {
				textBox.forEachInlineBox(work::push);
			}
		}
		return new FootnoteCallScan(ids, labels);
	}
}
