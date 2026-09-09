package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.util.logging.Level;
import java.util.logging.Logger;


import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.FloatMeasurement;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;

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
	 * 一つの再開チェーン内で現れた自動改ページの「状態の指紋」と、
	 * 各指紋の出現回数を持ちます。直前だけを比較すると A/B/A/B のような
	 * 周期2以上のライブロックを検出できないためです。詳細は
	 * {@link net.zamasoft.foliojet.layout.fragment.ContinuationStats#STALLED_AUTO_BREAK_LIMIT}。
	 */
	private record BreakFingerprint(long ingest, long boundTableRows, long emittedTableFragments,
			int depth, long pageAxisBits, int target) {
	}

	/** 上端ページフロートのFIFO-prefix配置計画です。 */
	static final class TopFloatPlan {
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> boxes;
		final double dy;

		TopFloatPlan(final java.util.List<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> boxes,
				final double dy) {
			this.boxes = java.util.List.copyOf(boxes);
			this.dy = dy;
		}
	}

	private final java.util.Map<BreakFingerprint, Integer> breakFingerprintCounts = new java.util.HashMap<>();
	/**
	 * 深さを含めない第二指紋の計数(2026-08-23)。再開処理の途中で同じ
	 * 改ページへ再入し続けるライブロック(wild seed 1490848)は、未完了
	 * ResumeSessionとopen-tail flowが一組ずつ増えて物理深さが毎回変わる
	 * ため、深さ入りの第一指紋では全反復が別状態に見えて発火しない。
	 * 逆に深さを一律に補正すると、深さが安定した周期2ライブロック
	 * (seed 44749)の検出が壊れる——両方を数え、どちらかが閾値へ達したら
	 * 打ち切る。入力が進まず・カーソルも・対象要素も同じ改ページが33回
	 * 重なる状況は深さ差があっても進捗ではない。
	 */
	private final java.util.Map<BreakFingerprint, Integer> depthFreeBreakCounts = new java.util.HashMap<>();
	private long breakHistoryIngest = Long.MIN_VALUE;
	private long boundTableRows, emittedTableFragments;
	private long breakHistoryTableRows, breakHistoryTableFragments;

	/** Pass C送出対象の実行消費だけを数える。入力収集・MEASURE・追記通知は数えない。 */
	final void noteRetainedTableRowsBound(final int rows) {
		this.boundTableRows += rows;
	}

	/** 親が前頁へ切り離した実断片。表全体の移動では進めない。 */
	final void noteRetainedTableFragmentEmitted() {
		++this.emittedTableFragments;
	}
	private int stalledBreakRun = 0;
	/** LayoutSourceを持たないscratch用の自動改ページ打切り状態。 */
	private boolean autoBreaksAbandoned = false;

	/** 強制改ページまたは実進捗の確定後に、自動改ページの停滞履歴を捨てます。 */
	private void clearBreakProgressHistory() {
		this.stalledBreakRun = 0;
		this.breakFingerprintCounts.clear();
		this.depthFreeBreakCounts.clear();
		this.breakHistoryIngest = Long.MIN_VALUE;
	}

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
	 * @return ライブロックが確定したので改ページを放棄すべきならtrue
	 */
	private boolean guardBreakProgress(final BreakMode mode) {
		if (!(mode instanceof BreakMode.AutoBreakMode auto)) {
			// 強制改ページは進捗で測らない。次の再開チェーンへ指紋も
			// 持ち越さない
			this.clearBreakProgressHistory();
			return false;
		}
		final net.zamasoft.foliojet.layout.fragment.LayoutSource source = this.pageGenerator.getLayoutSource();
		// Cはキューを消費している間も入力が前進する。Bが追記したログ末尾を
		// 指紋にすると、EOFの一括配達を停滞と誤認してしまう。
		final long ingest = (source == null) ? -1L : Math.min(source.nextId(), this.pageGenerator.getDeliveredEventEnd());
		final int depth = this.flowStack.size();
		// 継続再構築のたびにboxもparams.elementも新しいインスタンスになる。
		// identityHashCodeでは論理的に同じtbodyを毎ページ別物と見なし、
		// 同じ入力位置・深さ・カーソルの空改ページを検出できなかった
		// (wild seed 7662は1,475ページ)。要素の不変な記述を指紋に使う。
		final Object element = auto.box == null || auto.box.getParams() == null ? null
				: auto.box.getParams().element;
		final int target;
		if (element instanceof net.zamasoft.foliojet.css.StructureElement structure) {
			// 実要素は文書順のelementKey、匿名/擬似要素は要素名で安定化。
			// CSSElement.toString()はObject.toString()を先頭に含むので不可。
			target = 31 * Long.hashCode(structure.elementKey())
					+ java.util.Objects.hashCode(structure.lName());
		} else {
			target = element == null ? 0 : element.getClass().getName().hashCode();
		}
		// 継続再構築はtbodyと祖先を交互に切断しながら新しい入力位置まで
		// 進み、そこでまた同じ循環を始めうる。ひとつでもライブロックが
		// 確定した文書は、残りの自動改ページを止めて現在ページへ
		// はみ出させる。強制改ページはこのメソッドの先頭で除外済み。
		if (source != null ? source.areAutoBreaksAbandoned() : this.autoBreaksAbandoned) {
			return true;
		}
		// 入力イベントまたはPass Cの実行消費が進んだら、以前の反復は捨てる。
		// 表の送出がない文書では両カウンタは0のままで、従来の指紋と同じ判定になる。
		// sessions が一旦空になっても履歴は捨てない。seed 7662 の周期1は
		// 各ページの再開を終えてから次の同一改ページへ進むため、そこで
		// 区切ると従来検出できていたライブロックを見逃してしまう。
		if (ingest != this.breakHistoryIngest || this.boundTableRows != this.breakHistoryTableRows
				|| this.emittedTableFragments != this.breakHistoryTableFragments) {
			this.breakFingerprintCounts.clear();
			this.depthFreeBreakCounts.clear();
			this.breakHistoryIngest = ingest;
			this.breakHistoryTableRows = this.boundTableRows;
			this.breakHistoryTableFragments = this.emittedTableFragments;
		}
		final BreakFingerprint fingerprint = new BreakFingerprint(ingest, this.boundTableRows,
				this.emittedTableFragments, depth,
				Double.doubleToLongBits(this.pageAxis), target);
		final int occurrences = this.breakFingerprintCounts.merge(fingerprint, 1, Integer::sum);
		// 深さ非依存の第二指紋(depth=-1固定。フィールドコメント参照)
		final BreakFingerprint depthFree = new BreakFingerprint(ingest, this.boundTableRows,
				this.emittedTableFragments, -1,
				Double.doubleToLongBits(this.pageAxis), target);
		final int depthFreeOccurrences = this.depthFreeBreakCounts.merge(depthFree, 1, Integer::sum);
		this.stalledBreakRun = Math.max(occurrences, depthFreeOccurrences) - 1;
		if (DEBUG_BREAK_FINGERPRINT) {
			System.out.println("[fp] ingest=" + ingest + " depth=" + depth + " pageAxis=" + this.pageAxis
					+ " boundTableRows=" + this.boundTableRows + " emittedTableFragments=" + this.emittedTableFragments
					+ " target=" + target + " resumeDepth=" + this.sessions.size() + " stalled="
					+ this.stalledBreakRun);
		}
		if (net.zamasoft.foliojet.layout.fragment.ContinuationStats.guardBreakProgress(this.stalledBreakRun)) {
			// ここで同じ分割をもう一度実行すれば、同じ断片を次ページへ
			// 複製するだけになる。falseを受けた呼び出し側はループを抜け、
			// 内容を現在の断片にはみ出して配置する(seed 7662)。
			// breakByClearを含む全反復箇所はfalseで抜けるようになっている。
			this.stalledBreakRun = 0;
			this.breakFingerprintCounts.clear();
			this.depthFreeBreakCounts.clear();
			this.breakHistoryIngest = Long.MIN_VALUE;
			if (source != null) {
				source.abandonAutoBreaks();
			} else {
				this.autoBreaksAbandoned = true;
			}
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
			RootBuilder.this.enterTranslateBlockScope();
			try {
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
			} finally {
				RootBuilder.this.exitTranslateBlockScope();
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
			RootBuilder.this.enterTranslateBlockScope();
			try {
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
			} finally {
				RootBuilder.this.exitTranslateBlockScope();
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
				// containsCaption(caption recipe化C1、2026-08-01): キャプション
				// はOpaque記録からrecipe記録へ移ったが、文脈依存kind(囲み
				// TableBuilderが必要)のため含む範囲は従来どおりbox-restyleへ
				// ——表根の範囲(tableCheckFrom=startId+1)でも内容の
				// キャプションを弾く。C2のcontext-complete検証で解禁するまで
				// routing不変
				if (endId >= 0 && endId < this.pageGenerator.getDeliveredEventEnd()
						&& log.isIntact(startId, endId) && !log.containsOpaque(startId, endId)
						&& !log.observeCaptionGate(startId, endId)
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

	/** 子ビルダー・再生・継続処理がRootの外側で開いている深さです。 */
	private int translateBlockDepth = 0;

	/** 現在のPageBoxが描画前の確定処理へ入った後ならtrue。 */
	private boolean pageFinished = false;

	/**
	 * 現ページの平行移動を禁止する子スコープへ入ります。入れ子の
	 * DocumentBuilder、TwoPass bind、ソース再生、継続再開で共有します。
	 */
	public final void enterTranslateBlockScope() {
		++this.translateBlockDepth;
	}

	/** 現ページの平行移動を禁止する子スコープから出ます。 */
	public final void exitTranslateBlockScope() {
		assert this.translateBlockDepth > 0 : "translate block scope depth became negative";
		--this.translateBlockDepth;
	}

	/** 現在開いている平行移動禁止スコープの深さです。 */
	public final int getTranslateBlockDepth() {
		return this.translateBlockDepth;
	}

	/** 現在のPageBoxが描画前の確定処理へ入った後ならtrue。 */
	public final boolean isPageFinished() {
		return this.pageFinished;
	}

	/** 新しいPageBoxごとに作り直す、当該ページのtopフロート排除域。 */
	private java.util.List<FloatExclusion> topPageFloatExclusions;

	/** 当該ページへFIFO配置済みtop prefixのページ軸終端。 */
	private double topPageFloatStackEnd = 0;

	/** topフロートだけの不変スナップショット。 */
	private ExclusionSpace topPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;

	/** 未来のpageSpanを含むbottomフロートだけの不変スナップショット。 */
	private ExclusionSpace bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;

	/** top/bottomページフロートをまとめた行走査用不変スナップショット。 */
	private ExclusionSpace pageFloatExclusionSnapshot = ExclusionSpace.EMPTY;

	/** PageBox生成ごとの世代。ページフロートの通常floatと別のorder名前空間にも使う。 */
	private long pageGeneration = 0;
	/** 頁内の改段commit履歴。段組ownerが閉じた後も頁終了まで保持する。 */
	private int committedColumnsOnPage;
	private final boolean debugFootnote = Boolean.getBoolean("net.zamasoft.foliojet.debug.footnote");

	/** balanceの局所再生ではなく、BreakableBuilderの改段commit成功後だけ呼ぶ。 */
	final void columnCommitted(final BreakableBuilder builder, final Flow flow,
			final net.zamasoft.foliojet.layout.fragment.PreparedColumnCut prepared) {
		++this.committedColumnsOnPage;
		this.traceFootnote("column-commit", null, 0, java.util.Set.of());
		final FootnoteHost previous = this.columnFootnoteHost;
		if (previous == null || previous.owner != prepared.owner()
				|| previous.container.get() != prepared.expectedActiveColumn()) return;
		if (!previous.pendingFootnotes.isEmpty()) {
			this.attachColumnFootnotes(previous, prepared.newPageExtent());
		}
		this.columnFootnoteHost = null;
		this.openFootnoteColumn(builder, flow);
		if (this.columnFootnoteHost != null) {
			this.columnFootnoteHost.pendingFootnotes.addAll(previous.pendingFootnotes);
			previous.pendingFootnotes.clear();
			this.reserveColumnFootnotes(this.columnFootnoteHost);
		} else {
			this.transferColumnFootnotes(previous);
		}
	}

	public long getPageGeneration() {
		return this.pageGeneration;
	}

	/** 同一ページ内のページフロート安定連番。 */
	private int pageFloatSequence = 0;

	/** pendingへ登録したboxと、その登録を行ったページ世代。 */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> pendingTopFloatGenerations =
			new java.util.IdentityHashMap<>();

	/** 現ページで登録され、まだ現ページ上端への配置資格を持つtop floatです。 */
	private record CurrentTopFloat(net.zamasoft.foliojet.layout.box.impl.FloatBlockBox box, long generation) {
	}

	/** boxの登録順を保つ、現ページtop floatの世代付き台帳です。 */
	private final java.util.List<CurrentTopFloat> pendingCurrentTopFloats = new java.util.ArrayList<>();

	/** 当該ページに既に置いたboxと世代。addPageFloatの再生重複と配置反復を防ぐ。 */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> placedTopFloatGenerations =
			new java.util.IdentityHashMap<>();

	/** bottomのpending/配置済み世代。TwoPass再生による二重登録を防ぐ。 */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> pendingBottomFloatGenerations =
			new java.util.IdentityHashMap<>();
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> placedBottomFloatGenerations =
			new java.util.IdentityHashMap<>();

	/** footnote移動時にも変えない、当該ページ内bottomの安定order。 */
	private final java.util.IdentityHashMap<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox, Long> bottomFloatOrders =
			new java.util.IdentityHashMap<>();

	/**
	 * 現PageBoxに配置済みの分割不能floatが占めるrootページ軸終端の最大値
	 * (2026-09-04)。
	 *
	 * <p>
	 * 初版はRootBuilder自身が配置し、rootまで全flowが同一WritingModeである
	 * 通常flowだけを対象にする。ローカル座標を持つ子context builder
	 * (nested BFC・relative/absolute・TwoPass)、段組、直交flowは除外する。
	 * 子contextから親へオフセット変換して伝播するのは後続版の課題である。
	 * </p>
	 */
	private double atomicFloatFloor = 0;

	public RootBuilder(PageGenerator pageGenerator, byte mode) {
		super(null, null, mode);
		this.pageGenerator = pageGenerator;
		this.pageBox = this.nextPage();
		this.beginPage();

		this.pageSide = this.pageGenerator.getPageSide();
		this.contextFlow = new Flow(this.pageBox, 0, 0);
	}

	/** PageBoxと、それだけに属するページフロート排除域を同時に作る。 */
	private PageBox nextPage() {
		if (this.pageGeneration == 0x7fff_ffffL) {
			throw new IllegalStateException("page float generation exhausted");
		}
		final PageBox next = this.pageGenerator.nextPage();
		++this.pageGeneration;
		this.committedColumnsOnPage = 0;
		this.pendingCurrentTopFloats.removeIf(entry -> entry.generation() != this.pageGeneration);
		this.pageFinished = false;
		this.pageFloatSequence = 0;
		this.topPageFloatExclusions = new java.util.ArrayList<>();
		this.topPageFloatStackEnd = 0;
		this.topPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.pageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.placedTopFloatGenerations.clear();
		this.placedBottomFloatGenerations.clear();
		this.bottomFloatOrders.clear();
		this.bottomFloatOneDimensionalFallback = false;
		this.atomicFloatFloor = 0;
		this.narrowTopPlacedWithTextBeside = false;
		return next;
	}

	/** rootまでのopen flowが完全に同じWritingModeならtrue。 */
	private boolean hasRootWritingModePath() {
		final WritingMode rootFlow = this.pageBox.getBlockParams().flow;
		for (int i = 0; i < this.getFlowCount(); ++i) {
			if (this.getFlow(i).box.getBlockParams().flow != rootFlow) {
				return false;
			}
		}
		return true;
	}

	private void refreshPageFloatExclusionSnapshot() {
		this.pageFloatExclusionSnapshot = this.topPageFloatExclusionSnapshot
				.mergedWith(this.bottomPageFloatExclusionSnapshot);
	}

	@Override
	protected ExclusionSpace pageFloatExclusionsForLineLayout() {
		if (!this.hasRootWritingModePath()) {
			// RootBuilder内でも直交flowは同じflowStackを使う。祖先に一つでも
			// 軸変換があれば頁座標をその内側へ渡さず、外枠配置に任せる。
			return ExclusionSpace.EMPTY;
		}
		return this.pageFloatExclusionSnapshot;
	}

	/**
	 * {@link BlockBuilder#commitFloatPlacement(FloatPlacementDelta)}で確定した
	 * 分割不能floatの占有終端を通知します(2026-09-04)。呼出し元をcommitだけに限定し、
	 * max更新にすることでTwoPassの再通知を冪等にします。
	 */
	final void reportAtomicFloatPlacement(final net.zamasoft.foliojet.layout.box.IFloatBox box,
			final WritingMode ownerFlow, final double pageStart) {
		if (this.columnFootnoteHost != null && ownerFlow == this.columnFootnoteHost.owner.getBlockParams().flow
				&& this.isEligibleFootnoteColumnOwner(this, this.columnFootnoteHost.owner)) {
			final FootnoteHost host = this.columnFootnoteHost;
			host.atomicFloatFloor = Math.max(host.atomicFloatFloor,
					pageStart - host.pageOrigin + FloatMeasurement.occupiedPageExtent(box, ownerFlow));
			return;
		}
		if (this.getMulticolumnBox() != null || ownerFlow != this.pageBox.getBlockParams().flow
				|| !this.hasRootWritingModePath()) {
			return;
		}
		final double floor = pageStart + FloatMeasurement.occupiedPageExtent(box, ownerFlow);
		if (net.zamasoft.foliojet.layout.util.LayoutUtils.compare(floor, this.atomicFloatFloor) <= 0) {
			return;
		}
		this.atomicFloatFloor = floor;
		// 既存bottom予約はこのページに確定済み。floorは追加分だけを止める。
		this.reserveBottomFloats();
		this.updateBottomFloatFallbackForCurrentPosition();
	}

	/** 符号bitを立て、通常floatの非負orderと衝突しない頁世代+安定連番を返す。 */
	private long nextPageFloatOrder() {
		if (this.pageFloatSequence == Integer.MAX_VALUE) {
			throw new IllegalStateException("too many page floats on one page");
		}
		return Long.MIN_VALUE | (this.pageGeneration << 32) | Integer.toUnsignedLong(this.pageFloatSequence++);
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
		final var observer = pageNameObserver;
		if (observer != null) observer.accept(this, pageName);
	}

	/** 名前遷移の裁定回数をC/B別に観測する試験用。通常はnullです。 */
	static volatile java.util.function.BiConsumer<RootBuilder, String> pageNameObserver;

	public final RootBuilder getPageContext() {
		return this;
	}

	/** 現在組版中のページです。字面輪郭の局所計測など描画前の処理が使います。 */
	public final PageBox getCurrentPageBox() {
		return this.pageBox;
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
			return false;
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
			plan = this.columnFootnoteHost == null || this.columnFootnoteHost.footnoteReservation == 0 ? scan.toBreakPlan()
					: scan.toBreakPlan().withColumnLimit(new net.zamasoft.foliojet.layout.fragment.BreakPlan.ColumnLimit(
							this.columnFootnoteHost.owner, this.columnFootnoteHost.footnoteReservation));
			// 増分5(grok レビュー必須1): 切断後は root の内寸が切り詰められ
			// `getPageOwnerLimit()` が変わるので、最後の段の容量は切断前に固定する。
			this.columnFootnoteCutCapacity = this.columnFootnoteHost == null ? Double.NaN
					: this.columnFootnoteHost.capacityBase.getAsDouble();
			this.columnFootnoteCarryChainIndex = -1;
			if (this.columnFootnoteHost != null) {
				for (int i = 0; i < this.flowStack.size(); ++i) {
					if (((Flow) this.flowStack.get(i)).box == this.columnFootnoteHost.owner) {
						this.columnFootnoteCarryChainIndex = i;
						break;
					}
				}
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
			final double pageAxis = this.getPageOwnerLimit() - root.pageAxis - lastFrame;
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
			rootState = prevRootBox.splitPageState(plan.contentLimit(prevRootBox, innerLimit), innerLimit,
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
		this.pageBox = this.nextPage();
		this.beginPage();
		this.resetPageMarginNoteCursors();
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

		// コンテキストを再開。ページフロート(上端)は新ページの先頭へ
		// 置き、本文はページ先頭から二次元排除する。
		this.contextFlow = new Flow(this.pageBox, 0, 0);
		this.reserveBottomFloats();
		this.placeTopPageFloats(this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
				super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation, true));
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
		// 破断時に何が積まれていたかを控える(不変条件が破れたときだけ使う)
		final String flowsAtBreak = this.describeFlowStack();
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
			// **何が積まれていたか/積み直されたかまで書く。** 深さの数字だけでは
			// どの流し込みが落ちたのか分からず、診断に何時間もかかった(2026-08-03)
			throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					"break flow failed (flowStack.size()=" + this.flowStack.size() + ", continuation.depth()="
							+ continuation.depth() + ")\n  破断時: " + flowsAtBreak + "\n  再開後: "
							+ this.describeFlowStack());
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
		// 増分5: 継続の再生で owner の継続が持ち越しを受け取らなかった(段組が続かない、
		// 別の段組が別の位置で開いた、継続が入れ子で不適格になった)なら、本文が
		// 組まれる前に頁の宿主へ返す(codex レビュー 2026-09-08 必須 2)。
		this.flushColumnFootnoteCarry();

		return true;
	}

	/** 流し込みスタックの中身を人が読める形にします(不変条件の診断用)。 */
	private String describeFlowStack() {
		final StringBuilder out = new StringBuilder();
		for (int i = 0; i < this.flowStack.size(); ++i) {
			final Flow flow = (Flow) this.flowStack.get(i);
			if (i > 0) {
				out.append(" / ");
			}
			out.append(flow.box.getClass().getSimpleName());
			if (flow.box.getParams() != null && flow.box.getParams().element != null) {
				out.append('<').append(flow.box.getParams().element).append('>');
			}
		}
		return out.toString();
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
		// 増分5: 頁分割(切断成功後)で閉じる最後の段は、旧頁に残った容器を
		// 走査して段のblock-endへ添付し、置けなかった注は次頁へ持ち越す。
		// 段が開いていなくても持ち越しが残っていれば頁の宿主へ返す。
		if (this.recoveredColumnFootnotes != null) this.settleRecoveredFootnotes(this.pageAxis);
		if (this.columnFootnoteHost != null) {
			final FootnoteHost host = this.columnFootnoteHost;
			this.columnFootnoteHost = null;
			if (!host.pendingFootnotes.isEmpty()) {
				final double capacity = Double.isNaN(this.columnFootnoteCutCapacity) ? host.capacityBase.getAsDouble()
						: this.columnFootnoteCutCapacity;
				this.attachColumnFootnotes(host, capacity);
				this.columnFootnoteCarry.addAll(host.pendingFootnotes);
				host.pendingFootnotes.clear();
			}
		} else {
			this.flushColumnFootnoteCarry();
		}
		this.columnFootnoteCutCapacity = Double.NaN;
		this.pageFinished = true;
		// **予約したまま置かれない脚注は、ページを無限に作る**
		// (2026-08-21、掃過seed 439857ほか)。脚注は呼び出しが確定した
		// ページに置かれるが、呼び出しが「入れ子の段組の中で毎回次ページへ
		// 送られる」内容にあると、予約(版面を狭める)だけが残り、狭いせいで
		// 内容がまた送られる——同じ形のページが上限まで積み上がる。
		// 予約を保持したまま、中身の無いページが2ページ連続したら、先頭の
		// 注の予約を外して(deferred)呼び出しが確定する頁を待つ。
		//
		// **2026-09-03 に変えた**(cti.li の報告、[[2026-09-02-cti-li-footnote-
		// numbering.md]])。以前は EOF ドレインと同じ強制配置
		// (forceFootnoteAttach)へ切り替えていたが、①その旗は二度と戻らず、
		// 以後の注が全部「登録された頁」に呼び出し抜きで置かれて番号が通番に
		// 落ちた。②改頁を避ける大きな図が数頁ぶん溜まって順に頁へ割られる
		// 間(各頁に中身はある)にも発火し、注が呼び出しの 2 頁前に出た。
		// 「中身の無い頁」の条件は掃過 seed 439857 の形(空の段組枠だけの頁が
		// 積み上がる)を残し、図が順に置かれていく形を除く。予約を外せば
		// 内容が収まって呼び出しが確定し、注は F4 の carry-in で次頁の先頭に
		// 置かれる(番号は呼び出しの頁のもの)
		final boolean hadReservation = this.pageFootnoteHost.footnoteReservedCount > 0 || !this.footnotePlan.isEmpty();
		this.pageFootnoteHost.footnoteProgressed = false;
		this.pageHadContent = false;
		final double notesExtent = this.attachFootnotes();
		this.columnPageEntries.clear();
		this.columnPageLabels.clear();
		if (hadReservation && !this.pageFootnoteHost.footnoteProgressed && !this.pageHadContent) {
			if (++this.footnoteStallPages >= 2) {
				final FootnoteEntry head = this.pageFootnoteHost.pendingFootnotes.peekFirst();
				if (head != null && !head.committed) {
					head.deferred = true;
				}
				this.footnoteStallPages = 0;
			}
		} else {
			this.footnoteStallPages = 0;
		}
		this.attachBottomPageFloats(notesExtent);
		this.pageBox.finishLayout(this.pageBox);
	}

	/** 予約を保持したまま脚注配置が進まなかった連続ページ数。 */
	private int footnoteStallPages = 0;

	/** 直近の走査で、ページに行か置換要素があったか(停滞の安全弁の判定材料)。 */
	private boolean pageHadContent = false;

	public void finish() {
		this.requireNoIncompleteTable();
		this.finishLayout();
		// 脚注F4: 容量送りされた脚注が残っていれば、note-onlyページを
		// pendingが空になるまで生成する。前進しない回(1件も配置できない)は
		// call消失か走査欠落の不変条件違反として型付き失敗にする
		// (送り続けて無限ページを生まない)
		while (!this.pageFootnoteHost.pendingFootnotes.isEmpty() || !this.columnFootnoteCarry.isEmpty()
				|| this.hasPendingPageFloats()) {
			this.pageFootnoteHost.footnoteProgressed = false;
			this.pageFloatProgressed = false;
			this.pageGenerator.drawPage(this.pageBox, false, false);
			this.pageBox = this.nextPage();
			this.beginPage();
			this.pendingCurrentTopFloats.removeIf(entry -> entry.generation() != this.pageGeneration);
			this.resetPageMarginNoteCursors();
			this.contextFlow = new Flow(this.pageBox, 0, 0);
			this.reserveFootnotes();
			this.reserveBottomFloats();
			this.placeTopPageFloats(this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
					super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation, true));
			this.resetFragmentCursor(0, 0);
			this.finishLayout();
			// 前進の無い回は、呼び出しがどのページにも残らなかった脚注
			// (表のセル・絶対配置の中にある呼び出しは走査の対象外)。
			// **変換は失敗させない**(ARCHITECTURE.md §5.13)——次の回は
			// 呼び出しの有無に関わらず先頭から置き、それでも進まなければ
			// 残りを捨てて警告する(無限ページを作らないため)
			if (!this.pageFootnoteHost.footnoteProgressed && !this.pageFloatProgressed) {
				if (this.forceFootnoteAttach) {
					LOG.warning("giving up on footnotes whose calls were never found: "
							+ this.pageFootnoteHost.pendingFootnotes.size() + " pending at EOF");
					this.pageFootnoteHost.pendingFootnotes.clear();
					this.pendingTopFloats.clear();
					this.pendingBottomFloats.clear();
					break;
				}
				this.forceFootnoteAttach = true;
			}
		}
		this.pageGenerator.drawPage(this.pageBox, true, false);
		this.traceFootnote("finish", null, 0, java.util.Set.of());
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

		net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox;
		/** 本文が未到着でも、Bの計測高とcallページの採番を保持できます。 */
		double measuredHeight = Double.NaN;

		boolean committed = false;
		/** 同頁の確定段にcallが残った。採番・committed化は頁確定まで待つ。 */
		boolean columnCallRetained = false;
		/** 段の末尾に最終添付した宿主(balance前の回収に使う。増分6)。 */
		FootnoteHost attachedColumnHost;
		/** この頁世代では予約しない(balance 後に収まらない回収注。次頁の carry-in へ)。 */
		long holdReservationUntil = -1;

		/**
		 * ページローカルの脚注番号です(F5、1始まり。未採番は-1)。番号の
		 * スコープはnote配置ページではなく<b>callが残ったページ</b>——
		 * carry-inされたnoteは後続ページでもcallページの番号を保つ。
		 */
		int assignedNumber = -1;

		/**
		 * 予約を外して呼び出しの頁を待つ注です(2026-09-03)。予約が内容を
		 * 押し出し続けて呼び出しが確定しない停滞のときに立つ。呼び出しが
		 * 確定した頁では予約が無いので置けず、carry-in(committed)で次頁の
		 * 先頭に置かれる。
		 */
		boolean deferred = false;

		FootnoteEntry(final long id, final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox) {
			this.id = id;
			this.noteBox = noteBox;
		}
	}

	/**
	 * 脚注の宿主に属する状態。予約・採番・救済の判断はRootに残します。
	 * 頁の容器と容量の基点は使用時に参照し、改頁・切断前の値を固定しません。
	 */
	private static final class FootnoteHost {
		/** 未配置の脚注(文書順が正本。箱木の走査順はbidi等で崩れるため)。 */
		final java.util.ArrayDeque<FootnoteEntry> pendingFootnotes = new java.util.ArrayDeque<>();

		/**
		 * 現ページに予約済みのpending先頭prefixの件数と、その予約量
		 * (gap込み、ページ方向)。予約はページ内で単調非減少——呼び出しが
		 * 次ページへ移っても返さない「保守的確保」(前ページ下端に空きが
		 * 残り得る。明示的仕様逸脱)。
		 */
		int footnoteReservedCount = 0;
		double footnoteReservation = 0;
		/** 明示した下限の空きと、注が実際に使う量を区別します。 */
		double footnoteUsed = 0;
		/** 直近のattachで配置が進んだか(finish()の前進性ガード)。 */
		boolean footnoteProgressed = false;
		double atomicFloatFloor = 0;

		final java.util.function.Supplier<net.zamasoft.foliojet.layout.box.content.Container> container;
		final java.util.function.DoubleSupplier capacityBase;
		final java.util.function.DoubleSupplier lineSize;
		final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner;
		final double lineOrigin;
		final double pageOrigin;

		FootnoteHost(final java.util.function.Supplier<net.zamasoft.foliojet.layout.box.content.Container> container,
				final java.util.function.DoubleSupplier capacityBase, final java.util.function.DoubleSupplier lineSize,
				final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner,
				final double lineOrigin, final double pageOrigin) {
			this.container = container;
			this.capacityBase = capacityBase;
			this.lineSize = lineSize;
			this.owner = owner;
			this.lineOrigin = lineOrigin;
			this.pageOrigin = pageOrigin;
		}

		/** 原点は頁内座標。添付先の段容器には局所座標で置く。 */
		static FootnoteHost forColumn(final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner,
				final net.zamasoft.foliojet.layout.box.content.FlowContainer column, final double lineOrigin,
				final double pageOrigin, final double capacity, final double lineSize) {
			return new FootnoteHost(() -> column, () -> capacity, () -> lineSize, owner, lineOrigin, pageOrigin);
		}

		void addFloating(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox, final double pageAxis) {
			// 頁宿主(原点0)では加算を増やさず、従来の座標をそのまま渡す。
			this.container.get().addFloating(noteBox, 0, pageAxis);
		}
	}

	/** 頁宿主は文書を通して一つ。bottom・固定帯も状態だけを共有し、既存経路で扱う。 */
	private final FootnoteHost pageFootnoteHost = new FootnoteHost(
			() -> this.pageBox.getContainer(), super::getPageLimit, () -> this.pageBox.getLineSize(), null, 0, 0);
	/** 現在の段。対象注が届くまでは予約・追加走査を行わない。 */
	private FootnoteHost columnFootnoteHost;
	/**
	 * 頁分割で段が閉じたとき、旧頁の最後の段に置けなかった注(呼び出しが
	 * 次頁へ移った・段に収まらなかった)。次頁で最初に開く段の宿主へ渡し、
	 * 段が開かないまま注が届く/頁が終わるなら頁の宿主へ返す(増分5)。
	 */
	private final java.util.ArrayDeque<FootnoteEntry> columnFootnoteCarry = new java.util.ArrayDeque<>();
	/** 頁分割の切断前に固定した最後の段の容量(切断後の root 内寸に依存しない)。 */
	private double columnFootnoteCutCapacity = Double.NaN;
	/**
	 * 持ち越しを渡す継続 owner の識別: 切断時の open chain(flowStack)での owner の
	 * 位置。継続の再生は同じ順序で箱を作り直すので、再生中に同じ位置で開いた
	 * 段組だけを owner の継続とみなす(grok レビュー任意、2026-09-07)。
	 */
	private int columnFootnoteCarryChainIndex = -1;
	/** balance 前に回収した段の注。balance 後に収容判定してから頁の宿主へ移す(増分6)。 */
	private FootnoteHost recoveredColumnFootnotes;
	/** 段添付でFIFOを離れたentryも、頁の文書順採番が終わるまで保持する。 */
	private final java.util.SortedMap<Long, FootnoteEntry> columnPageEntries = new java.util.TreeMap<>();
	private final java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> columnPageLabels = new java.util.ArrayList<>();

	/** 切断済み段の値だけを観測する。木やentryを試験側へ保持しない。 */
	public record ColumnFootnotePlacement(long generation, double lineOrigin, double pageOrigin,
			double capacity, double lineSize, double reservation, double attachedExtent,
			java.util.List<Long> attachedIds, java.util.Set<Long> retainedIds) { }
	static volatile java.util.function.Consumer<ColumnFootnotePlacement> columnFootnoteObserver;

	final void openFootnoteColumn(final BreakableBuilder builder, final Flow flow) {
		if (this.isBottomFootnoteArea() || this.footnoteArea().isHeightFixed()
				|| !this.isEligibleFootnoteColumnOwner(builder, flow.box)) return;
		final var owner = flow.box;
		final var column = owner.getContainer() instanceof net.zamasoft.foliojet.layout.box.content.ColumnsContainer columns
				? columns.getLastColumn() : owner.getContainer();
		if (!(column instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer)) return;
		if (this.columnFootnoteHost != null && this.columnFootnoteHost.owner == owner
				&& this.columnFootnoteHost.container.get() == column) return;
		int depth = 1;
		for (int i = builder.getFlowCount() - 1; i >= 0 && builder.getFlow(i) != flow; --i) ++depth;
		final double lastFrame = builder.lastFrame(flow, depth);
		final double lineOrigin = flow.lineAxis
				+ (owner.getActualColumnCount() - 1) * (owner.getLineSize() + owner.getBlockParams().columns.gap);
		this.columnFootnoteHost = new FootnoteHost(() -> column,
				() -> this.getPageOwnerLimit() - flow.pageAxis - lastFrame, owner::getLineSize,
				owner, lineOrigin, flow.pageAxis);
		if (!this.columnFootnoteCarry.isEmpty() && builder == this && this.isRestyling()
				&& this.flowStack.size() - 1 == this.columnFootnoteCarryChainIndex) {
			// 前頁の最後の段から持ち越した注は、継続の再生で同じ位置に開いた
			// owner の継続の最初の段へ(継続本文の再生より前なので、段は予約済みの
			// 容量で組まれる)。継続でなければ再生の終わりに頁の宿主へ返す。
			for (final FootnoteEntry entry : this.columnFootnoteCarry) {
				this.columnPageEntries.put(entry.id, entry);
				this.traceFootnote("column-carry", entry, 0, java.util.Set.of());
			}
			this.columnFootnoteHost.pendingFootnotes.addAll(this.columnFootnoteCarry);
			this.columnFootnoteCarry.clear();
			this.reserveColumnFootnotes(this.columnFootnoteHost);
		}
	}

	/** 段が開かないまま注が届く/頁が終わるとき、持ち越しを頁の宿主へ返す。 */
	private void flushColumnFootnoteCarry() {
		if (this.columnFootnoteCarry.isEmpty()) return;
		final FootnoteHost carrier = new FootnoteHost(() -> null, () -> 0, () -> 0, null, 0, 0);
		carrier.pendingFootnotes.addAll(this.columnFootnoteCarry);
		this.columnFootnoteCarry.clear();
		this.transferColumnFootnotes(carrier);
	}

	/** endFlowBlockはspan-allによる区切り・auto終了も通り、balanceより先に呼ぶ。 */
	final void closeFootnoteColumn(final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		final FootnoteHost host = this.columnFootnoteHost;
		if (host == null || host.owner != owner) return;
		this.columnFootnoteHost = null;
		if (owner.getBlockParams().columns.fill == net.zamasoft.foliojet.layout.box.params.Columns.FILL_BALANCE) {
			// 増分6: balanceは段の容器を再生し、段の末尾に最終添付した注を
			// 保たない(ソース再生では存在せず、箱再生では通常floatになる)。
			// 再生の前に全段の添付済み注を取り外し、頁の宿主へ文書順で移す。
			for (final FootnoteEntry entry : this.columnPageEntries.values()) {
				final FootnoteHost attached = entry.attachedColumnHost;
				if (attached == null || attached.owner != owner) continue;
				if (attached.container.get() instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer column) {
					column.removeFloating(entry.noteBox);
				}
				entry.attachedColumnHost = null;
				host.pendingFootnotes.addLast(entry);
				this.traceFootnote("column-recover", entry, 0, java.util.Set.of());
			}
			this.pageBox.removeColumnFootnoteSeparators(owner);
			// 頁の残容量は balance 後の段組の高さで決まるので、移管はそれから。
			this.recoveredColumnFootnotes = host;
			return;
		}
		this.transferColumnFootnotes(host);
	}

	/**
	 * balance 後(段組の高さ確定後)に、回収した注が段組の後の残容量に収まるか
	 * 見て頁の宿主へ移します。収まらなければこの頁では予約せず、呼び出しの
	 * 頁の番号を保って次頁の carry-in にします(F4。閉じた段組は最終段しか
	 * 切れないので、予約で本文を押し出すと先行段が注と重なる。grok レビュー必須3)。
	 */
	final void settleRecoveredFootnotes(final double pageAxisAfterOwner) {
		final FootnoteHost host = this.recoveredColumnFootnotes;
		if (host == null) return;
		this.recoveredColumnFootnotes = null;
		double needed = FOOTNOTE_GAP;
		for (final FootnoteEntry entry : host.pendingFootnotes) needed += this.footnoteExtent(entry.noteBox);
		final double available = this.getPageLimit() - pageAxisAfterOwner;
		if (needed > available) {
			for (final FootnoteEntry entry : host.pendingFootnotes) {
				entry.holdReservationUntil = this.pageGeneration;
				this.traceFootnote("column-hold", entry, needed - available, java.util.Set.of());
			}
		}
		this.transferColumnFootnotes(host);
	}

	private void transferColumnFootnotes(final FootnoteHost host) {
		if (host.pendingFootnotes.isEmpty()) return;
		final java.util.SortedMap<Long, FootnoteEntry> entries = new java.util.TreeMap<>();
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) entries.put(entry.id, entry);
		for (final FootnoteEntry entry : host.pendingFootnotes) entries.put(entry.id, entry);
		this.pageFootnoteHost.pendingFootnotes.clear();
		this.pageFootnoteHost.pendingFootnotes.addAll(entries.values());
		host.pendingFootnotes.clear();
		// prefixへ割り込む場合も文書順で予約し直す。既存の保守的確保は返さない。
		final double reserved = this.pageFootnoteHost.footnoteReservation;
		this.pageFootnoteHost.footnoteReservedCount = 0;
		this.pageFootnoteHost.footnoteReservation = 0;
		this.pageFootnoteHost.footnoteUsed = 0;
		this.reserveFootnotes();
		this.pageFootnoteHost.footnoteReservation = Math.max(reserved, this.pageFootnoteHost.footnoteReservation);
	}

	private double columnFootnoteExtent(final FootnoteHost host, final FootnoteEntry entry) {
		return entry.noteBox.getPageExtent(host.owner.getBlockParams().flow);
	}

	private void reserveColumnFootnotes(final FootnoteHost host) {
		if (host.pendingFootnotes.isEmpty()) return;
		final double maxArea = host.capacityBase.getAsDouble() - Math.max(MIN_PAGE_LIMIT, host.atomicFloatFloor);
		int i = 0;
		for (final FootnoteEntry entry : host.pendingFootnotes) {
			if (i++ < host.footnoteReservedCount) continue;
			if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) break;
			final double cost = (host.footnoteUsed == 0 ? FOOTNOTE_GAP : 0) + this.columnFootnoteExtent(host, entry);
			if (host.footnoteUsed + cost > maxArea) {
				if (host.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					host.footnoteReservation = maxArea;
					host.footnoteUsed = maxArea;
					host.footnoteReservedCount = 1;
				}
				break;
			}
			host.footnoteUsed += cost;
			host.footnoteReservation = Math.max(host.footnoteUsed, Math.min(maxArea, this.footnoteArea().minHeight));
			++host.footnoteReservedCount;
		}
	}

	/** commit済みの旧段だけを走査・最終添付する。番号はここでは解決しない。 */
	private void attachColumnFootnotes(final FootnoteHost host, final double capacity) {
		final FootnoteCallScan scan = scanFootnoteCalls(host.container.get(), host.owner, true);
		this.columnPageLabels.addAll(scan.labels());
		int count = 0;
		double extent = 0;
		for (final FootnoteEntry entry : host.pendingFootnotes) {
			if (scan.ids().contains(entry.id)) entry.columnCallRetained = true;
		}
		for (final FootnoteEntry entry : host.pendingFootnotes) {
			if (count >= host.footnoteReservedCount || (!entry.committed && !entry.columnCallRetained)) break;
			final double nextExtent = extent + this.columnFootnoteExtent(host, entry);
			if (FOOTNOTE_GAP + nextExtent > capacity - MIN_PAGE_LIMIT && !entry.committed) break;
			extent = nextExtent;
			++count;
		}
		double pageAxis = capacity - extent;
		final var observer = columnFootnoteObserver;
		final java.util.List<Long> attachedIds = observer == null ? null : new java.util.ArrayList<>();
		for (int i = 0; i < count; ++i) {
			final FootnoteEntry entry = host.pendingFootnotes.removeFirst();
			if (attachedIds != null) attachedIds.add(entry.id);
			host.addFloating(entry.noteBox, pageAxis);
			entry.attachedColumnHost = host;
			pageAxis += this.columnFootnoteExtent(host, entry);
			this.columnPageLabels.addAll(this.scanFootnoteCalls(entry.noteBox).labels());
			host.footnoteProgressed = true;
		}
		if (count > 0) {
			this.pageBox.addColumnFootnoteSeparator(host.owner, host.owner.getBlockParams().flow, host.lineOrigin,
					host.pageOrigin, host.lineSize.getAsDouble(), capacity - extent - FOOTNOTE_GAP / 2);
		}
		if (observer != null) observer.accept(new ColumnFootnotePlacement(this.pageGeneration, host.lineOrigin,
				host.pageOrigin, capacity, host.lineSize.getAsDouble(), host.footnoteReservation, extent,
				java.util.List.copyOf(attachedIds), java.util.Set.copyOf(scan.ids())));
	}

	/** 到着元から一番近い段組ownerを探す。局所builderをまたぐ場合も内側を優先する。 */
	public static net.zamasoft.foliojet.layout.box.AbstractContainerBox footnoteColumnOwner(
			final net.zamasoft.foliojet.layout.builder.LayoutStack parent) {
		for (net.zamasoft.foliojet.layout.builder.LayoutStack stack = parent; stack != null;
				stack = stack.getParentBuilder()) {
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner = stack.getMulticolumnBox();
			if (owner != null) return owner;
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox context = stack.getRootBox();
			if (context != null && context.getColumnCount() > 1) return context;
		}
		return null;
	}

	/** Rootの通常フロー上の、外側に段組を持たない可変高さownerだけを受ける。 */
	public boolean isEligibleFootnoteColumnOwner(final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		if (owner == null || owner.getColumnCount() <= 1 || owner.isFixedMulticolumn()
				|| footnoteColumnOwner(parent) != owner) return false;
		boolean reachesRoot = false;
		for (net.zamasoft.foliojet.layout.builder.LayoutStack stack = parent; stack != null;
				stack = stack.getParentBuilder()) {
			if (stack instanceof ColumnBuilder) return false;
			if (stack == this) {
				reachesRoot = true;
				break;
			}
			if (stack.getMulticolumnBox() != null || stack.getRootBox().getColumnCount() > 1) return false;
		}
		if (!reachesRoot) return false;
		for (int i = 0; i < this.getFlowCount(); ++i) {
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox box = this.getFlow(i).box;
			if (box.getColumnCount() <= 1) continue;
			return box == owner;
		}
		return false;
	}

	private FootnoteHost selectFootnoteHost(final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		if (this.isBottomFootnoteArea() || this.footnoteArea().isHeightFixed()) return this.pageFootnoteHost;
		return this.columnFootnoteHost != null && this.columnFootnoteHost.owner == owner
				&& this.isEligibleFootnoteColumnOwner(parent, owner) ? this.columnFootnoteHost : this.pageFootnoteHost;
	}

	/** 頁注は従来計測(NONE)、段注だけ宿主の行長を包含ブロックのinline寸法にする。 */
	public double getFootnoteLineSize(final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		final FootnoteHost host = this.selectFootnoteHost(parent, owner);
		return host == this.pageFootnoteHost ? net.zamasoft.foliojet.layout.util.LayoutUtils.NONE : host.lineSize.getAsDouble();
	}
	/** FootnoteSamePageTestの既存観測口。FIFOの実体・更新は頁宿主だけが持つ。 */
	private final java.util.ArrayDeque<FootnoteEntry> pendingFootnotes = this.pageFootnoteHost.pendingFootnotes;

	/** デバッグと試験には値だけを渡す。変換はDirectSessionの別スレッドで動く。 */
	public record FootnoteTrace(String event, long generation, int committedColumns, long id,
			double delta, double reservation,
			double pageLimit, int reservedCount, int pendingCount, boolean committed, boolean deferred,
			int number, java.util.Set<Long> retainedIds) { }
	static volatile java.util.function.Consumer<FootnoteTrace> footnoteTraceObserver;

	private void traceFootnote(final String event, final FootnoteEntry entry, final double delta,
			final java.util.Set<Long> retained) {
		final var observer = footnoteTraceObserver;
		if (!this.debugFootnote && observer == null) return;
		final FootnoteTrace trace = new FootnoteTrace(event, this.pageGeneration, this.committedColumnsOnPage,
				entry == null ? -1 : entry.id, delta, this.pageFootnoteHost.footnoteReservation,
				this.getPageOwnerLimit(), this.pageFootnoteHost.footnoteReservedCount, this.pageFootnoteHost.pendingFootnotes.size(),
				entry != null && entry.committed, entry != null && entry.deferred,
				entry == null ? -1 : entry.assignedNumber, java.util.Set.copyOf(retained));
		if (this.debugFootnote) System.err.println("[footnote] " + trace);
		if (observer != null) observer.accept(trace);
	}

	/** 台帳へ届いた脚注の論理ID。同じ注を二度受け取らないため(2026-09-02)。 */
	private final java.util.Set<Long> registeredFootnotes = new java.util.HashSet<>();
	/** Bだけが使う後着本文の採番と、再生可能な登録の寿命です。MAINとは共有しません。 */
	private java.util.Map<Long, Integer> probeCallNumbers;
	private java.util.Map<Long, Long> probeFootnoteAnchors;
	/** Cの未配置ID台帳。予約だけのID・後着本文待ちもFIFOに含めます。 */
	private final java.util.Map<Long, FootnoteEntry> bottomFootnotes = new java.util.HashMap<>();
	private record FootnoteReservation(double height, boolean oversized) { }
	/** ID別の予約資格。pendingの先頭prefix件数とは独立です。 */
	private final java.util.Map<Long, FootnoteReservation> footnotePlan = new java.util.HashMap<>();
	private boolean initialFootnotePagePending;

	private boolean hasFootnotePlan() {
		return this.isBottomFootnoteArea() && this.pageGenerator.isFootnotePageProbeEnabled();
	}

	private FootnoteEntry bottomFootnote(final long id) {
		FootnoteEntry entry = this.bottomFootnotes.get(id);
		if (entry == null) {
			entry = new FootnoteEntry(id, null);
			this.bottomFootnotes.put(id, entry);
			this.pageFootnoteHost.pendingFootnotes.addLast(entry);
			// TwoPass本文の完成順と文書順は別。IDだけの先行登録も同じFIFOへ統合する。
			final java.util.List<FootnoteEntry> sorted = new java.util.ArrayList<>(this.pageFootnoteHost.pendingFootnotes);
			sorted.sort(java.util.Comparator.comparingLong(value -> value.id));
			this.pageFootnoteHost.pendingFootnotes.clear();
			this.pageFootnoteHost.pendingFootnotes.addAll(sorted);
		}
		return entry;
	}

	private boolean isFootnoteProbe() {
		return this.pageGenerator instanceof net.zamasoft.foliojet.layout.MeasurePageGenerator measure
				&& measure.isFootnoteProbe();
	}

	/** 配置済みかつBの再生水位より前の登録・計測高を解放します。 */
	public void reclaimProbeFootnotes(final long fromId) {
		if (this.probeFootnoteAnchors == null) return;
		final java.util.Set<Long> pending = new java.util.HashSet<>();
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) pending.add(entry.id);
		// 継続表は配置済みヘッダーのcallを再利用する。現在木から消えるまでは
		// 登録も残し、次の確定ページで後着本文用の番号を再登録させない。
		final java.util.Set<Long> retained = collectFootnoteCalls(this.pageBox);
		final var iterator = this.probeFootnoteAnchors.entrySet().iterator();
		while (iterator.hasNext()) {
			final var entry = iterator.next();
			if (entry.getValue() < fromId && !pending.contains(entry.getKey()) && !retained.contains(entry.getKey())) {
				this.registeredFootnotes.remove(entry.getKey());
				if (this.probeCallNumbers != null) this.probeCallNumbers.remove(entry.getKey());
				((net.zamasoft.foliojet.layout.MeasurePageGenerator) this.pageGenerator).forgetFootnote(entry.getKey());
				iterator.remove();
			}
		}
	}

	/** Bの長文試験用。後着番号と再生可能な登録の回収後件数です。 */
	public int probeFootnoteLedgerSize() {
		return (this.probeCallNumbers == null ? 0 : this.probeCallNumbers.size())
				+ (this.probeFootnoteAnchors == null ? 0 : this.probeFootnoteAnchors.size());
	}

	private boolean warnedFootnoteAreaLimit;

	private net.zamasoft.foliojet.ua.FootnoteArea footnoteArea() {
		// 柱・running・部分範囲の計測用ミニページには、文書の帯を予約しない。
		if (this.pageGenerator instanceof net.zamasoft.foliojet.layout.MeasurePageGenerator measure
				&& !measure.isFootnoteProbe()) return net.zamasoft.foliojet.ua.FootnoteArea.DEFAULT;
		return this.pageBox.getUserAgent().getUAContext().getFootnoteArea();
	}

	private double requestedFootnoteArea(final double maxArea) {
		final var area = this.footnoteArea();
		final double requested = Math.max(area.minHeight, area.height == null ? 0 : area.height);
		// 警告は本番(C)だけ。仮組み(B)は別のRootBuilderなので同じ文書で二重に出る。
		if (requested > maxArea && !this.warnedFootnoteAreaLimit && !this.isFootnoteProbe()) {
			this.warnedFootnoteAreaLimit = true;
			LOG.warning("footnote area limited to " + maxArea + "pt (requested " + requested + "pt)");
		}
		return Math.min(requested, maxArea);
	}

	private double blockFootnoteMaxArea() {
		return Math.max(0, this.pageBox.getInnerPageExtent(this.pageBox.getBlockParams().flow) - MIN_PAGE_LIMIT);
	}

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

	/** 横組みページのbottomは、従来のblock-end経路へそのまま通します。 */
	private boolean isBottomFootnoteArea() {
		return this.pageBox.getUserAgent().getUAContext().getFootnoteArea().position
				== net.zamasoft.foliojet.ua.FootnoteArea.Position.BOTTOM
				&& this.pageBox.getBlockParams().flow.isVertical();
	}

	/**
	 * 脚注帯の用紙縦方向の占有量です。領域が横書きならblock方向、縦書きなら
	 * inline方向を測るため、物理高さを返すTB軸を使います。上下浮動体の測度とは別。
	 */
	public static double footnoteBandExtent(final net.zamasoft.foliojet.layout.box.IBox box) {
		return Math.max(box.getPageExtent(WritingMode.TB), box.paintedPageExtent(WritingMode.TB));
	}

	private static final double MAX_FOOT_AREA_RATIO = 0.6;

	/**
	 * Bは持ち越しだけ、Cは対応するB報告の計測済みIDも加えて一度だけ予約します。
	 * height固定ならBを使わず毎ページ予約、min-heightは予約の下限です。
	 * 未予約・実高超過の注は行長を変えず、callを確定して次の帯へ送ります(F4)。
	 * block軸のfootnoteReservationは0のままなので、上下浮動体の容量も不変です。
	 */
	private void beginPage() {
		if (!this.isBottomFootnoteArea()) {
			if (this.footnoteArea().isHeightFixed() || this.footnoteArea().minHeight > 0) {
				this.pageFootnoteHost.footnoteUsed = 0;
				this.pageFootnoteHost.footnoteReservation = this.requestedFootnoteArea(this.blockFootnoteMaxArea());
				if (this.footnoteArea().isHeightFixed()) this.reserveFixedFootnotes();
			}
			return;
		}
		final double innerWidth = this.pageBox.getInnerWidth();
		final double innerHeight = this.pageBox.getInnerHeight();
		if (this.pageGeneration == 1 && this.hasFootnotePlan()) {
			// 最初だけは幾何を先に渡してBを起動し、Cの最初の生入力まで予約を待つ。
			this.initialFootnotePagePending = true;
			this.pageGenerator.pageStarted(this.pageBox, innerWidth, innerHeight);
			return;
		}
		this.reserveBottomFootnotes();
		this.pageGenerator.pageStarted(this.pageBox, innerWidth, innerHeight);
	}

	public void startFootnoteInput() {
		if (!this.initialFootnotePagePending) return;
		this.initialFootnotePagePending = false;
		this.reserveBottomFootnotes();
	}

	private void reserveBottomFootnotes() {
		if (this.footnoteArea().isHeightFixed()) {
			final double maxArea = Math.max(0, this.pageBox.getInnerHeight()) * MAX_FOOT_AREA_RATIO;
			this.pageBox.reserveFootArea(this.requestedFootnoteArea(maxArea));
			this.reserveFixedFootnotes();
			return;
		}
		this.footnotePlan.clear();
		this.pageFootnoteHost.footnoteReservedCount = 0;
		final boolean planned = this.hasFootnotePlan();
		final double maxArea = Math.max(0, this.pageBox.getInnerLineExtent(this.pageBox.getBlockParams().flow))
				* MAX_FOOT_AREA_RATIO;
		double inset = 0;
		boolean blocked = false;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) {
				blocked = true;
				break;
			}
			final double height = entry.noteBox == null ? entry.measuredHeight : footnoteBandExtent(entry.noteBox);
			if (!Double.isFinite(height)) {
				blocked = true;
				break;
			}
			final double cost = (this.pageFootnoteHost.footnoteReservedCount == 0 ? FOOTNOTE_GAP : 0) + height;
			if (inset + cost > maxArea) {
				// 巨大注はcallの確定を待ち、持ち越しの先頭なら上限まで予約して溢れさせる。
				if (this.pageFootnoteHost.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					inset = maxArea;
					this.pageFootnoteHost.footnoteReservedCount = 1;
					if (planned) this.footnotePlan.put(entry.id, new FootnoteReservation(height, true));
				}
				blocked = true;
				break;
			}
			inset += cost;
			++this.pageFootnoteHost.footnoteReservedCount;
			if (planned) this.footnotePlan.put(entry.id, new FootnoteReservation(height, false));
		}
		final double minimum = this.requestedFootnoteArea(maxArea);
		if (planned) {
			final var report = this.pageGenerator.getFootnotePageProbeReport(this.pageGeneration);
			// 未確定・B正常終端後とも持ち越しだけで固定する。後着報告でHは更新しない。
			final boolean finished = this.pageGenerator.isFootnotePageProbeFinished();
			final boolean usable = report != null && report.generation() == this.pageGeneration && report.emitted()
					&& java.util.Objects.equals(report.pageName(), this.pageGenerator.getPageName())
					&& report.flow() == this.pageBox.getBlockParams().flow
					&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(report.innerWidth(), this.pageBox.getInnerWidth()) == 0
					&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(report.innerHeight(), this.pageBox.getInnerHeight()) == 0;
			if (!blocked && usable) {
				for (final long id : new java.util.TreeSet<>(report.callIds())) {
					if (this.registeredFootnotes.contains(id) || this.bottomFootnotes.containsKey(id)
							|| this.footnotePlan.containsKey(id)) continue;
					final Double height = report.measuredHeights().get(id);
					if (height == null || report.unmeasuredIds().contains(id) || !Double.isFinite(height) || height < 0) continue;
					final double cost = (this.footnotePlan.isEmpty() ? FOOTNOTE_GAP : 0) + height;
					if (inset + cost > maxArea) break;
					inset += cost;
					this.bottomFootnote(id).measuredHeight = height;
					this.footnotePlan.put(id, new FootnoteReservation(height, false));
				}
			}
			if (minimum > 0) inset = Math.max(minimum, inset);
			this.updateFootnotePrefix();
			final var observer = footnotePlanObserver;
			if (observer != null) observer.accept(new FootnotePlanSnapshot(this.pageGeneration, report != null, usable, finished,
					inset, java.util.Set.copyOf(this.footnotePlan.keySet())));
		}
		this.pageBox.reserveFootArea(minimum > 0 ? Math.max(minimum, inset) : inset);
	}

	/** 固定帯は伸ばさず、完成した注にだけFIFOで予約資格を与えます。 */
	private void reserveFixedFootnotes() {
		this.footnotePlan.clear();
		this.pageFootnoteHost.footnoteReservedCount = 0;
		final double capacity = this.isBottomFootnoteArea() ? this.pageBox.getFootInset() : this.pageFootnoteHost.footnoteReservation;
		double used = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (entry.noteBox == null || (entry.deferred && !entry.committed && !this.forceFootnoteAttach)) break;
			final double extent = this.isBottomFootnoteArea() ? footnoteBandExtent(entry.noteBox) : this.footnoteExtent(entry.noteBox);
			final double cost = (this.pageFootnoteHost.footnoteReservedCount == 0 ? FOOTNOTE_GAP : 0) + extent;
			if (used + cost > capacity) {
				// 単独でも入らない注は呼び出しを確定してから次ページで溢れさせる。
				if (this.pageFootnoteHost.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					this.footnotePlan.put(entry.id, new FootnoteReservation(extent, true));
					this.pageFootnoteHost.footnoteReservedCount = 1;
					if (!this.warnedOversizedFootnote) {
						this.warnedOversizedFootnote = true;
						LOG.warning("footnote larger than the fixed band; placing it anyway: " + extent + "pt");
					}
				}
				break;
			}
			used += cost;
			this.footnotePlan.put(entry.id, new FootnoteReservation(extent, false));
			++this.pageFootnoteHost.footnoteReservedCount;
		}
	}

	/** 試験には可変台帳やページ木を渡さず、開始時に固定した計画だけを渡します。 */
	public record FootnotePlanSnapshot(long generation, boolean reported, boolean usable, boolean inputFinished,
			double inset, java.util.Set<Long> reservedIds) { }
	static volatile java.util.function.Consumer<FootnotePlanSnapshot> footnotePlanObserver;

	private void updateFootnotePrefix() {
		this.pageFootnoteHost.footnoteReservedCount = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (entry.noteBox == null || !this.footnotePlan.containsKey(entry.id)) break;
			++this.pageFootnoteHost.footnoteReservedCount;
		}
	}

	/**
	 * 完成した脚注本文を台帳へ加えます({@code DocumentBuilder.endBox}の
	 * FLOAT分岐から)。現ページの容量に入る分だけ予約が伸び、本文容量
	 * ({@link #getPageLimit()})が縮んで以後の溢れ検査・改ページが新しい
	 * 容量で行われる。容量を超えた分は予約されず次ページへ送られる(F4)。
	 */
	public void addFootnote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox) {
		this.addFootnote(noteBox, this, footnoteColumnOwner(this));
	}

	public void addFootnote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox,
			final net.zamasoft.foliojet.layout.builder.LayoutStack parent,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner) {
		if (!this.registeredFootnotes.add(noteBox.getParams().footnoteId)) {
			// 同じ注が二度届いた(two-passの記録と、ソース再生の両方から)。
			// 台帳は1件でよい
			return;
		}
		if (this.isFootnoteProbe()) {
			if (this.probeFootnoteAnchors == null) this.probeFootnoteAnchors = new java.util.HashMap<>();
			this.probeFootnoteAnchors.put(noteBox.getParams().footnoteId, noteBox.getSourceAnchor());
		}
		if (this.footnoteArea().isHeightFixed()) {
			// callが本文より先に改ページした場合も、そのページの番号を保持する。
			this.bottomFootnote(noteBox.getParams().footnoteId).noteBox = noteBox;
			this.reserveFixedFootnotes();
			return;
		}
		if (this.isBottomFootnoteArea()) {
			final double noteExtent = this.footnoteBandExtent(noteBox);
			final double maxArea = Math.max(0, this.pageBox.getInnerHeight() + this.pageBox.getFootInset())
					* MAX_FOOT_AREA_RATIO;
			if (FOOTNOTE_GAP + noteExtent > maxArea && !this.warnedOversizedFootnote) {
				this.warnedOversizedFootnote = true;
				LOG.warning("footnote larger than the bottom band; placing it anyway: " + noteExtent
						+ "pt (max footnote area " + maxArea + "pt)");
			}
			if (this.hasFootnotePlan()) {
				final FootnoteEntry entry = this.bottomFootnote(noteBox.getParams().footnoteId);
				entry.noteBox = noteBox;
				entry.measuredHeight = noteExtent;
				final FootnoteReservation reservation = this.footnotePlan.get(entry.id);
				if (reservation != null && !reservation.oversized() && net.zamasoft.foliojet.layout.util.LayoutUtils.compare(noteExtent, reservation.height()) > 0) {
					// Hは変更しない。実高超過は予約資格だけを外してF4へ送る。
					this.footnotePlan.remove(entry.id);
				}
				this.updateFootnotePrefix();
				return;
			}
			final FootnoteEntry entry = new FootnoteEntry(noteBox.getParams().footnoteId, noteBox);
			if (this.probeCallNumbers != null) {
				final Integer number = this.probeCallNumbers.remove(entry.id);
				if (number != null) {
					entry.committed = true;
					entry.assignedNumber = number;
				}
			}
			this.pageFootnoteHost.pendingFootnotes.addLast(entry);
			return;
		}
		if (this.columnFootnoteHost == null) this.flushColumnFootnoteCarry();
		final FootnoteHost host = this.selectFootnoteHost(parent, owner);
		final double noteExtent = this.footnoteExtent(noteBox);
		final double maxArea = host.capacityBase.getAsDouble() - MIN_PAGE_LIMIT;
		if (FOOTNOTE_GAP + noteExtent > maxArea && !this.warnedOversizedFootnote) {
			// 空ページの最大脚注領域にも収まらない脚注(版面の9割超を占める
			// 単一脚注)。**変換は失敗させない**——ARCHITECTURE.md §5.13
			// (2026-07-26/27のユーザー裁定)が「変換が失敗することは常に
			// エンジンの不具合。版面が破綻した文書の除外は変換の失敗には
			// 適用しない」と定めているため。溢れさせて置き、警告する
			// (2026-08-02。従来はFootnoteOverflowExceptionだった)
			this.warnedOversizedFootnote = true;
			LOG.warning("footnote larger than the page area; placing it anyway: " + noteExtent
					+ "pt (max footnote area " + maxArea + "pt)");
		}
		final FootnoteEntry entry = new FootnoteEntry(noteBox.getParams().footnoteId, noteBox);
		host.pendingFootnotes.addLast(entry);
		this.traceFootnote("arrival", entry, 0, java.util.Set.of());
		if (host == this.pageFootnoteHost) {
			this.reserveFootnotes();
		} else {
			this.columnPageEntries.put(entry.id, entry);
			this.reserveColumnFootnotes(host);
		}
	}

	/**
	 * pendingの先頭prefixのうち現ページの最大脚注領域に収まる分まで
	 * 予約を伸ばします(FIFO——途中を飛ばさない)。配置済みatomic floatの
	 * 終端より後だけを新規予約に使い、既存予約は後から縮めない(2026-09-04)。
	 */
	private void reserveFootnotes() {
		if (this.footnoteArea().isHeightFixed()) {
			this.reserveFixedFootnotes();
			return;
		}
		if (this.isBottomFootnoteArea()) {
			// 地の帯はbeginPageで固定済み。ページ途中の注は予約しない。
			return;
		}
		if (this.footnoteArea().minHeight > 0) {
			this.reserveMinimumFootnotes();
			return;
		}
		final double previousReservation = this.pageFootnoteHost.footnoteReservation;
		final double maxArea = this.pageFootnoteHost.capacityBase.getAsDouble() - Math.max(MIN_PAGE_LIMIT, this.atomicFloatFloor);
		int i = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (i >= this.pageFootnoteHost.footnoteReservedCount) {
				if (entry.holdReservationUntil >= this.pageGeneration) break;
				if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) {
					// 予約を外して呼び出しを待つ注(FIFO なので後続も待つ)
					this.traceFootnote("reserve-stop-deferred", entry, 0, java.util.Set.of());
					break;
				}
				final double cost = (this.pageFootnoteHost.footnoteReservation == 0 ? FOOTNOTE_GAP : 0)
						+ this.footnoteExtent(entry.noteBox);
				if (this.pageFootnoteHost.footnoteReservation + cost > maxArea) {
					// 呼出しページに単独でも収まらない脚注は、そこで最大量を
					// 予約してはならない。本文容量がMIN_PAGE_LIMITまで縮み、
					// callより前の内容(特に空の段組枠)を何百ページも同じ形で
					// 送り続けるためである(seed 7676)。まずcallを現在ページに
					// 確定してcommittedにし、次のnote-onlyページで溢れさせて
					// 置く。既にcarry-in済みなら下の従来経路で必ず予約する。
					if (this.pageFootnoteHost.footnoteReservedCount == 0 && i == 0 && !entry.committed
							&& !this.forceFootnoteAttach) {
						this.traceFootnote("reserve-stop-capacity", entry, 0, java.util.Set.of());
						break;
					}
					// **先頭の1件だけは必ず予約する**(2026-08-02)。
					// 版面より大きい脚注は何ページ送っても入らないため、
					// ここで諦めると前進せず変換が失敗する(§5.13違反)。
					// 予約は版面の上限で頭打ちにし、実体は溢れさせて置く
					if (this.pageFootnoteHost.footnoteReservedCount == 0 && i == 0) {
						final double before = this.pageFootnoteHost.footnoteReservation;
						this.pageFootnoteHost.footnoteReservation = maxArea;
						this.pageFootnoteHost.footnoteReservedCount = 1;
						this.traceFootnote("reserve-oversized", entry, this.pageFootnoteHost.footnoteReservation - before, java.util.Set.of());
					}
					// 入らない分はF4のFIFO送り(次ページで再予約)
					this.traceFootnote("reserve-stop-capacity", entry, 0, java.util.Set.of());
					break;
				}
				this.pageFootnoteHost.footnoteReservation += cost;
				++this.pageFootnoteHost.footnoteReservedCount;
				this.traceFootnote("reserve", entry, cost, java.util.Set.of());
			}
			++i;
		}
		if (this.pageFootnoteHost.footnoteReservation != previousReservation) {
			this.footnoteReservationChangedAfterBottomRegistration();
		}
	}

	/** 下限の予約をまず使い、足りなくなってから従来の容量まで伸ばします。 */
	private void reserveMinimumFootnotes() {
		final double previousReservation = this.pageFootnoteHost.footnoteReservation;
		final double maxArea = Math.max(0, this.blockFootnoteMaxArea()
				- Math.max(0, this.atomicFloatFloor - MIN_PAGE_LIMIT));
		int i = 0;
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
			if (i++ < this.pageFootnoteHost.footnoteReservedCount) continue;
			if (entry.holdReservationUntil >= this.pageGeneration) break;
			if (entry.deferred && !entry.committed && !this.forceFootnoteAttach) {
				this.traceFootnote("reserve-stop-deferred", entry, 0, java.util.Set.of());
				break;
			}
			final double before = this.pageFootnoteHost.footnoteReservation;
			final double cost = (this.pageFootnoteHost.footnoteReservedCount == 0 ? FOOTNOTE_GAP : 0) + this.footnoteExtent(entry.noteBox);
			if (this.pageFootnoteHost.footnoteUsed + cost > maxArea) {
				if (this.pageFootnoteHost.footnoteReservedCount == 0 && (entry.committed || this.forceFootnoteAttach)) {
					this.pageFootnoteHost.footnoteReservation = Math.max(this.pageFootnoteHost.footnoteReservation, maxArea);
					this.pageFootnoteHost.footnoteUsed = maxArea;
					this.pageFootnoteHost.footnoteReservedCount = 1;
					this.traceFootnote("reserve-oversized", entry, this.pageFootnoteHost.footnoteReservation - before, java.util.Set.of());
				}
				this.traceFootnote("reserve-stop-capacity", entry, 0, java.util.Set.of());
				break;
			}
			this.pageFootnoteHost.footnoteUsed += cost;
			this.pageFootnoteHost.footnoteReservation = Math.max(this.pageFootnoteHost.footnoteReservation, this.pageFootnoteHost.footnoteUsed);
			++this.pageFootnoteHost.footnoteReservedCount;
			this.traceFootnote("reserve", entry, this.pageFootnoteHost.footnoteReservation - before, java.util.Set.of());
		}
		if (this.pageFootnoteHost.footnoteReservation != previousReservation) this.footnoteReservationChangedAfterBottomRegistration();
	}

	@Override
	public double getPageLimit() {
		final double pageLimit = this.getPageOwnerLimit();
		return this.columnFootnoteHost == null || this.columnFootnoteHost.footnoteReservation == 0 ? pageLimit
				: pageLimit - this.columnFootnoteHost.footnoteReservation;
	}

	/** 頁所属の予約だけを含む。bottom一次元予約・下限・演算順は従来どおり。 */
	@Override
	public double getPageOwnerLimit() {
		final double base = super.getPageLimit();
		final double reserved = this.pageFootnoteHost.footnoteReservation
				+ (this.bottomFloatOneDimensionalFallback ? this.bottomFloatReservation : 0);
		if (reserved == 0) {
			return base;
		}
		return Math.max(MIN_PAGE_LIMIT, base - reserved);
	}

	@Override
	protected double getUnsplittableFloatPageLimit() {
		final double pageLimit = this.getPageLimit();
		if (!this.hasTwoDimensionalBottomFloatLimit()) {
			return pageLimit;
		}
		// 二次元bottomは本文容量を縮めないが、atomic floatは途中で切れない。
		// 予約帯へ少しでも入る場合は、先頭bottomの実配置開始を終端にする。
		return Math.min(pageLimit, this.firstReservedBottomPlacedStart());
	}

	/** 2-D bottom帯を分割不能floatの実効終端に使う状態ならtrue。 */
	final boolean hasTwoDimensionalBottomFloatLimit() {
		return !this.bottomFloatOneDimensionalFallback && this.bottomFloatReservedCount > 0
				&& this.getMulticolumnBox() == null && this.hasRootWritingModePath();
	}

	// ------------------------------------------------------------------
	// ページフロート(float: top / float: bottom、2026-08-02——PLAN §2の
	// 1位。書籍組版の図表をページ端へ寄せる。脚注の予約・清算機構を
	// そのまま転用する)

	/**
	 * 上端へ置く待ち行列です。頁に本文も配置物もまだ無い場合は現PageBoxへ
	 * 即時配置し、それ以外は<b>次のページの先頭</b>へ置く
	 * (2026-09-04、B-1)。既に組み終えた現ページの内容は組み直さない。
	 */
	private final java.util.ArrayDeque<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> pendingTopFloats =
			new java.util.ArrayDeque<>();

	/** 版面下端(脚注があればその上)へ置く待ち行列です。 */
	private final java.util.ArrayDeque<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> pendingBottomFloats =
			new java.util.ArrayDeque<>();

	/** 現ページで下端フロートへ確保した量です。 */
	private double bottomFloatReservation = 0;

	/** 現ページで予約済みの下端フロート件数(FIFOのprefix長)です。 */
	private int bottomFloatReservedCount = 0;

	/**
	 * 当該ページのbottomを従来の一次元予約で扱う場合はtrue。
	 *
	 * <p>
	 * bottomの実配置はページblock-endなので、登録時点の現在位置が先頭bottomの
	 * 実配置開始位置{@code placedStart}を越えた場合だけ、既配置行との交差を
	 * 遡って解消できない。この場合に限り、そのページの残りを従来の
	 * {@link #getPageLimit()}縮小へ戻す。現在位置が{@code placedStart}以前なら、
	 * 本文が既にあっても二次元排除を使う。二次元登録後に脚注予約が増えた場合も、
	 * 移動後の新しい{@code placedStart}に対して同じ判定を行う。次のPageBoxでは
	 * 解除され、carry-inしたbottomは本文より先に二次元登録される。ページ先頭は
	 * 既配置範囲がないため、oversized bottomの{@code placedStart}が負でも二次元で
	 * 全面排除する。
	 * </p>
	 */
	private boolean bottomFloatOneDimensionalFallback = false;

	// ------------------------------------------------------------------
	// JLREQ 4.2.7 並列注（横組の傍注・縦組の頭注／脚注）。標準CSSに
	// 対応する指定がないため、float:-cssj-note-start/endで版面の
	// 論理行方向外側へ置く。本文領域は作者が@page marginで確保する。

	/** 版面と並列注との既定の空き。 */
	private static final double PAGE_MARGIN_NOTE_GAP = 6.0;

	/** 現ページの各注領域で、次の注を置けるページ軸位置。 */
	private double pageMarginNoteStartCursor = 0, pageMarginNoteEndCursor = 0;

	/** row subgridの遅延配置前に本文をbindしている深さ。 */
	private int rowSubgridBindDepth = 0;

	void beginRowSubgridBind() {
		++this.rowSubgridBindDepth;
	}

	void endRowSubgridBind() {
		if (this.rowSubgridBindDepth <= 0) {
			throw new IllegalStateException("row subgrid bind scopeの不整合");
		}
		--this.rowSubgridBindDepth;
	}

	private void resetPageMarginNoteCursors() {
		this.pageMarginNoteStartCursor = 0;
		this.pageMarginNoteEndCursor = 0;
	}

	/**
	 * 並列注を本文の現在位置に近い版面外へ置く。同じ側の注はFIFOで重ねず、
	 * ページ末に収まる場合は上へ寄せて同一ページ内に保つ。
	 */
	public void addPageMarginNote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox noteBox,
			final boolean start) {
		if (this.rowSubgridBindDepth > 0) {
			final String detailKey = "2823.subgrid-rows-margin-note";
			final net.zamasoft.foliojet.ua.UserAgent ua = this.pageGenerator.getUserAgent();
			if (ua.getUAContext().getReportedIneffectiveCombinationDetails().add(detailKey)) {
				ua.message(net.zamasoft.foliojet.message.MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION,
						"float", net.zamasoft.foliojet.message.MessageCodeUtils.detail(detailKey));
			}
		}
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.pageBox.getBlockParams().flow;
		final double pageLimit = super.getPageLimit();
		final double extent = this.footnoteExtent(noteBox);
		final double cursor = start ? this.pageMarginNoteStartCursor : this.pageMarginNoteEndCursor;
		double pageAxis = Math.max(cursor, Math.max(0, Math.min(this.pageAxis, pageLimit)));
		if (extent <= pageLimit && pageAxis + extent > pageLimit) {
			// 対応する本文位置から必要以上に離さない範囲で、ページ内へ戻す。
			pageAxis = Math.max(cursor, pageLimit - extent);
		}
		final double lineAxis = start
				? -PAGE_MARGIN_NOTE_GAP - noteBox.getLineExtent(flow)
				: this.pageBox.getLineSize() + PAGE_MARGIN_NOTE_GAP;
		this.pageBox.getContainer().addFloating(noteBox, lineAxis, pageAxis);
		final double next = pageAxis + extent + PAGE_MARGIN_NOTE_GAP;
		if (start) {
			this.pageMarginNoteStartCursor = next;
		} else {
			this.pageMarginNoteEndCursor = next;
		}
	}

	/**
	 * ページフロートを台帳へ積みます({@code DocumentBuilder}のFLOAT
	 * 終端から)。
	 */
	public void addPageFloat(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox,
			final boolean top) {
		if (top) {
			// TwoPass/継続のreplayはここを再通過する。同じboxがpending中、
			// または当該ページで配置済みなら二重に積まない。
			final Long placedGeneration = this.placedTopFloatGenerations.get(floatBox);
			if (this.pendingTopFloatGenerations.containsKey(floatBox)
					|| (placedGeneration != null && placedGeneration.longValue() == this.pageGeneration)) {
				return;
			}
			final boolean placeOnCurrentPage = this.isCurrentPageEmptyForTopFloat();
			this.pendingTopFloats.addLast(floatBox);
			this.pendingTopFloatGenerations.put(floatBox, this.pageGeneration);
			if (placeOnCurrentPage) {
				this.placeTopPageFloats(this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
						super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation, true));
			} else {
				this.pendingCurrentTopFloats.add(new CurrentTopFloat(floatBox, this.pageGeneration));
				this.tryTranslateForTopFloats();
			}
		} else {
			final Long placedGeneration = this.placedBottomFloatGenerations.get(floatBox);
			if (this.pendingBottomFloatGenerations.containsKey(floatBox)
					|| (placedGeneration != null && placedGeneration.longValue() == this.pageGeneration)) {
				return;
			}
			this.pendingBottomFloats.addLast(floatBox);
			this.pendingBottomFloatGenerations.put(floatBox, this.pageGeneration);
			this.reserveBottomFloats();
			this.updateBottomFloatFallbackForCurrentPosition();
		}
	}

	/** 版面に未配置のページフロートが残っているか(finish()の駆動条件)。 */
	private boolean hasPendingPageFloats() {
		return !this.pendingTopFloats.isEmpty() || !this.pendingBottomFloats.isEmpty();
	}

	/** 現ページで登録され、まだ現ページ上端への配置資格を持つtop floatがあるか。 */
	public final boolean hasCurrentTopFloats() {
		for (final CurrentTopFloat entry : this.pendingCurrentTopFloats) {
			if (entry.generation() == this.pageGeneration) {
				return true;
			}
		}
		return false;
	}

	/**
	 * flow block終端で子スコープとbreak禁止深さが戻った後、interflowの
	 * overflow検査より先に現ページ上端への平行移動を試します。
	 */
	@Override
	protected void afterFlowBlockClosed() {
		this.tryTranslateForTopFloats();
	}

	/**
	 * 現ページで登録された全行幅topを、配置済み内容と一緒に収まる範囲で
	 * ページ上端へ置きます。判定はすべて変異前に完了し、条件を満たさない
	 * 場合は待ち行列をそのまま次ページへ持ち越します。
	 */
	public final void tryTranslateForTopFloats() {
		if (!this.hasCurrentTopFloats() || !this.canTranslateNow()) {
			return;
		}

		final double used = this.currentTranslateUsedPageEnd();
		final double limit = this.getUnsplittableFloatPageLimit();
		final double maxArea = limit - (used - this.topPageFloatStackEnd);
		final TopFloatPlan plan = this.planTopFloats(this.pendingTopFloats, this.topPageFloatStackEnd,
				maxArea, false);
		if (plan.boxes.isEmpty()) {
			this.logTranslateSkip("capacity: used=" + used + " limit=" + limit);
			return;
		}
		if (!this.hasFullWidthTopFloatPlan(plan)) {
			return;
		}
		this.translateForTopFloats(plan);
	}

	/** 平行移動を安全に行えるRootの静止点かを読み取りだけで判定します。 */
	private boolean canTranslateNow() {
		if (this.textBuilder != null) {
			return this.logTranslateSkip("text builder is open");
		}
		if (this.mode == MODE_NO_BREAK || this.breakDepth != -1) {
			return this.logTranslateSkip("no-break scope: mode=" + this.mode + " breakDepth=" + this.breakDepth);
		}
		if (this.breakAfter != null) {
			// 直前のブロックの page-break-after が保留中: この float は次頁に属する
			return this.logTranslateSkip("forced break pending: " + this.breakAfter);
		}
		if (this.isRestyling()) {
			return this.logTranslateSkip("restyling");
		}
		if (!this.sessions.isEmpty()) {
			return this.logTranslateSkip("resume session");
		}
		if (!this.resumeScopes.isEmpty()) {
			return this.logTranslateSkip("resume scope");
		}
		if (this.rowSubgridBindDepth != 0) {
			return this.logTranslateSkip("row subgrid bind");
		}
		if (this.translateBlockDepth != 0) {
			return this.logTranslateSkip("child builder/replay scope depth=" + this.translateBlockDepth);
		}
		if (this.findColumnBreak() != null) {
			return this.logTranslateSkip("column break path");
		}
		if (this.getMulticolumnBox() != null) {
			return this.logTranslateSkip("multicolumn flow");
		}
		if (!this.hasRootWritingModePath()) {
			return this.logTranslateSkip("mixed writing-mode path");
		}
		if (this.pageFinished) {
			return this.logTranslateSkip("page already finished");
		}
		return true;
	}

	/** FINEでfallback理由を記録し、条件式からそのまま返せるfalseを返します。 */
	private boolean logTranslateSkip(final String reason) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("top float translate skipped: " + reason);
		}
		return false;
	}

	/**
	 * top追加後に必ず占有されるページ軸終端です。開いているflowの終端枠、
	 * 通常/独立BFCのfloat、分割不能float、配置済み並列注を含みます。
	 */
	private double currentTranslateUsedPageEnd() {
		final double normalEnd = this.pageAxis - (this.poLastMargin + this.neLastMargin);
		double used = Math.max(normalEnd, this.maxActiveFloatingPageEnd());
		if (this.atomicFloatFloor > 0) {
			used = Math.max(used, this.atomicFloatFloor);
		}
		final net.zamasoft.foliojet.layout.box.content.FlowContainer pageContainer =
				(net.zamasoft.foliojet.layout.box.content.FlowContainer) this.pageBox.getContainer();
		used = Math.max(used,
				pageContainer.maxPageMarginNotePageEnd(this.pageBox.getBlockParams().flow));
		if (this.hasOpenFlow()) {
			used = Math.max(used, normalEnd + this.lastFrame(this.getFlow(), 1));
		}
		return used;
	}

	/**
	 * 既配置topが、shapeなしの全行幅だけならtrue。
	 *
	 * <p>
	 * 新しく置くtopは幅を問わない: 平行移動は既存の内容を図版のblock寸法だけ
	 * 送るので、狭幅の図版は帯として置かれ、脇には文字が回り込まない
	 * (css-page-floats §3「内容はblock-end側に流れる」。頁先頭の二次元排除とは
	 * 異なる意図した近似。cti.liの縦組み写真のように頁の高さに満たない図版が
	 * 実例の大半なので、全行幅に限ると動機の事例が救えない、2026-09-05)。
	 * 既配置topは脇に文字が入っている可能性があるので全行幅のときだけ許す
	 * (移動した行が新しい帯と重なるため)。
	 * </p>
	 */
	private boolean hasFullWidthTopFloatPlan(final TopFloatPlan plan) {
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox box : plan.boxes) {
			if (box.getFloatPos().shapeOutside != null) {
				return this.logTranslateSkip("shape-outside top float");
			}
		}
		if (this.narrowTopPlacedWithTextBeside) {
			return this.logTranslateSkip("partial-width top placed at page start (text may sit beside it)");
		}
		return true;
	}

	/** 計画済みtopを配置し、既存のページ局所状態を同量だけ平行移動します。 */
	private void translateForTopFloats(final TopFloatPlan plan) {
		final int flowDepth = this.flowStack == null ? 0 : this.flowStack.size();
		this.placingTopByTranslate = true;
		try {
			this.placeTopPageFloats(plan);
		} finally {
			this.placingTopByTranslate = false;
		}
		final double dy = plan.dy;
		final java.util.Set<net.zamasoft.foliojet.layout.box.IBox> keep = java.util.Collections
				.newSetFromMap(new java.util.IdentityHashMap<>());
		keep.addAll(this.placedTopFloatGenerations.keySet());
		final net.zamasoft.foliojet.layout.box.content.FlowContainer pageContainer =
				(net.zamasoft.foliojet.layout.box.content.FlowContainer) this.pageBox.getContainer();
		pageContainer.shiftPageAxis(dy, keep);
		this.shiftFlowStack(dy);
		this.shiftFloatLedgers(dy);
		if (this.atomicFloatFloor > 0) {
			this.atomicFloatFloor += dy;
		}
		if (this.pageMarginNoteStartCursor > 0) {
			this.pageMarginNoteStartCursor += dy;
		}
		if (this.pageMarginNoteEndCursor > 0) {
			this.pageMarginNoteEndCursor += dy;
		}
		this.pageBox.setPageAxis(this.maxNormalFlowPageEnd(pageContainer));
		this.clearBreakProgressHistory();

		final java.util.Set<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> placed = java.util.Collections
				.newSetFromMap(new java.util.IdentityHashMap<>());
		placed.addAll(plan.boxes);
		this.pendingCurrentTopFloats.removeIf(entry -> placed.contains(entry.box()));
		assert flowDepth == (this.flowStack == null ? 0 : this.flowStack.size())
				: "top float translate changed flowStack depth";
	}

	/** 平行移動後のカーソル・終端枠・PageBox直下flowが示す通常フロー終端。 */
	private double maxNormalFlowPageEnd(
			final net.zamasoft.foliojet.layout.box.content.FlowContainer pageContainer) {
		double pageEnd = this.pageAxis - (this.poLastMargin + this.neLastMargin);
		if (this.hasOpenFlow()) {
			pageEnd += this.lastFrame(this.getFlow(), 1);
		}
		return Math.max(pageEnd, pageContainer.maxNormalFlowPageEnd(this.pageBox.getBlockParams().flow));
	}

	/**
	 * pendingの先頭prefixのうち現ページに収まる分まで下端フロートの
	 * 予約を伸ばします(脚注と同じFIFO——途中を飛ばさない)。二次元を
	 * 使えるページでは予約量は配置計画にだけ使い、本文容量を縮めない。
	 * 通常は最初の1件を版面より大きくても予約してEOFドレインを前進させる。
	 * ただしatomic floorがある頁では、そのfloorを破る先頭も次頁へ送る
	 * (2026-09-04)。次頁ではfloor登録前に予約されるため前進性は保たれる。
	 */
	private void reserveBottomFloats() {
		final double bottomMaxArea = super.getPageLimit() - Math.max(MIN_PAGE_LIMIT, this.atomicFloatFloor)
				- this.pageFootnoteHost.footnoteReservation;
		int i = 0;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : this.pendingBottomFloats) {
			if (i++ < this.bottomFloatReservedCount) {
				// このページで既に予約したFIFO prefixは後着floor/脚注でも外さない。
				continue;
			}
			final double cost = this.footnoteExtent(floatBox);
			if (this.bottomFloatReservation + cost > bottomMaxArea) {
				if (this.bottomFloatReservedCount == 0 && i == 1 && this.atomicFloatFloor <= 0) {
					this.bottomFloatReservation = cost;
					this.bottomFloatReservedCount = 1;
					LOG.warning("bottom page float too large for the remaining page area: " + cost + "pt");
				}
				break;
			}
			this.bottomFloatReservation += cost;
			++this.bottomFloatReservedCount;
		}
		this.rebuildBottomPageFloatExclusions();
	}

	/** 本文カーソルと開いている行の実寸のうち、block-end側にある現在位置。 */
	private double currentPagePosition() {
		return this.textBuilder == null ? this.pageAxis
				: Math.max(this.pageAxis, this.textBuilder.getActualPageAxis());
	}

	/** B-1でtopを現PageBoxへ直置きできる、本文も配置物もない状態か(2026-09-04)。 */
	private boolean isCurrentPageEmptyForTopFloat() {
		return this.textBuilder == null
				&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(this.currentPagePosition(), 0) == 0
				// 既に即時配置したtopは空判定から除く。除外対象は型付きの
				// 配置世代台帳に載るbox identityだけで、通常float/本文は除かない。
				&& !this.pageBox.getContainer().hasNonDecorationContentExcludingFloatings(
						this.placedTopFloatGenerations.keySet());
	}

	/** 現在予約した先頭bottomが実際に始まるblock軸位置。 */
	private double firstReservedBottomPlacedStart() {
		return super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation;
	}

	/** 既配置内容が、現在予約した先頭bottomの実配置帯へ達しているか。 */
	private boolean currentPositionPastFirstReservedBottom() {
		if (this.bottomFloatReservedCount == 0) {
			return false;
		}
		final double currentPosition = this.currentPagePosition();
		// oversized bottomのplacedStartは負になり得るが、ページ先頭では
		// まだ巻き戻すべき既配置範囲がないため二次元排除を使える。
		return net.zamasoft.foliojet.layout.util.LayoutUtils.compare(currentPosition, 0) > 0
				&& net.zamasoft.foliojet.layout.util.LayoutUtils
						.compare(currentPosition, this.firstReservedBottomPlacedStart()) > 0;
	}

	/** 現在の既配置範囲と先頭bottomのplacedStartから当該ページの経路を選び直す。 */
	private void updateBottomFloatFallbackForCurrentPosition() {
		final boolean fallback = this.currentPositionPastFirstReservedBottom();
		if (this.bottomFloatOneDimensionalFallback != fallback) {
			this.bottomFloatOneDimensionalFallback = fallback;
			this.rebuildBottomPageFloatExclusions();
		}
	}

	/**
	 * bottom登録後に脚注予約が伸びたとき、実配置矩形を追随させます。
	 * 現在位置が移動後の新しい先頭矩形へ達している場合だけ一次元へ戻し、
	 * それ以前なら新しい矩形で二次元排除を組み直します。
	 */
	private void footnoteReservationChangedAfterBottomRegistration() {
		if (this.bottomFloatReservedCount == 0) {
			return;
		}
		this.reserveBottomFloats();
		this.updateBottomFloatFallbackForCurrentPosition();
	}

	/**
	 * 現在予約済みのbottom prefixを、現時点の脚注予約を使った実配置
	 * {@code [placedStart, placedEnd]}へ登録し直します。
	 *
	 * <p>
	 * 描画は全writing-modeで論理{@code lineAxis=0}に置く。したがって
	 * horizontal-tbは左下、vertical-rlは左上(脚注があればその右)、
	 * vertical-lrは右上(脚注があればその左)であり、論理排除域は
	 * {@code [0, inlineExtent]}の{@link FloatSide#START}になる。設計草案の
	 * END側矩形へ変えると現行描画と交差するため、実描画座標を正とする。
	 * </p>
	 */
	private void rebuildBottomPageFloatExclusions() {
		if (this.bottomFloatOneDimensionalFallback || this.bottomFloatReservedCount == 0) {
			this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
			this.refreshPageFloatExclusionSnapshot();
			return;
		}
		final java.util.List<FloatExclusion> exclusions = new java.util.ArrayList<>(
				this.bottomFloatReservedCount);
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.pageBox.getBlockParams().flow;
		final double fragmentLimit = Math.max(0, super.getPageLimit() - this.pageFootnoteHost.footnoteReservation);
		double pageAxis = this.firstReservedBottomPlacedStart();
		int i = 0;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : this.pendingBottomFloats) {
			if (i++ >= this.bottomFloatReservedCount) {
				break;
			}
			final double placedStart = pageAxis;
			final double placedEnd = placedStart + this.footnoteExtent(floatBox);
			final double exclusionStart = Math.max(0, placedStart);
			final double exclusionEnd = Math.min(fragmentLimit, placedEnd);
			if (exclusionEnd > exclusionStart) {
				Long order = this.bottomFloatOrders.get(floatBox);
				if (order == null) {
					order = Long.valueOf(this.nextPageFloatOrder());
					this.bottomFloatOrders.put(floatBox, order);
				}
				exclusions.add(new FloatExclusion(order.longValue(), FloatSide.START,
						new AxisSpan(exclusionStart, exclusionEnd),
						new AxisSpan(0, floatBox.getLineExtent(flow))));
			}
			pageAxis = placedEnd;
		}
		this.bottomPageFloatExclusionSnapshot = ExclusionSpace.copyOfSorted(exclusions);
		this.refreshPageFloatExclusionSnapshot();
	}

	/**
	 * ページ確定時の下端フロートの清算です(finishLayoutから)。脚注が
	 * あればその上へ、無ければ版面下端へ順に積む。
	 *
	 * @param notesExtent 脚注が実際に占めた量(区切りの空きを含む)
	 */
	private void attachBottomPageFloats(final double notesExtent) {
		if (this.pendingBottomFloats.isEmpty()) {
			this.bottomFloatReservedCount = 0;
			this.bottomFloatReservation = 0;
			this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
			this.refreshPageFloatExclusionSnapshot();
			return;
		}
		double attachedExtent = 0;
		{
			int i = 0;
			for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : this.pendingBottomFloats) {
				if (i >= this.bottomFloatReservedCount) {
					break;
				}
				attachedExtent += this.footnoteExtent(floatBox);
				++i;
			}
		}
		double pageAxis = super.getPageLimit() - notesExtent - attachedExtent;
		for (int i = 0; i < this.bottomFloatReservedCount; ++i) {
			final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox = this.pendingBottomFloats
					.removeFirst();
			this.pendingBottomFloatGenerations.remove(floatBox);
			final Long previous = this.placedBottomFloatGenerations.put(floatBox, this.pageGeneration);
			if (previous != null && previous.longValue() == this.pageGeneration) {
				throw new IllegalStateException(
						"bottom page float repeated on page generation " + this.pageGeneration);
			}
			this.pageBox.getContainer().addFloating(floatBox, 0, pageAxis);
			pageAxis += this.footnoteExtent(floatBox);
			this.pageFloatProgressed = true;
		}
		this.bottomFloatReservedCount = 0;
		this.bottomFloatReservation = 0;
		this.bottomPageFloatExclusionSnapshot = ExclusionSpace.EMPTY;
		this.refreshPageFloatExclusionSnapshot();
	}

	/**
	 * 上端フロート待ち行列を変更せず、先頭から配置できる連続prefixを
	 * 計画します。途中の要素を飛ばさず、{@code atPageStart == false}では
	 * 先頭が収まらなければ空計画を返します。ページ先頭では従来どおり、
	 * {@code stackEnd == 0}の最初の1件を容量にかかわらず採り、その後は
	 * 収まる間だけ採ります。
	 *
	 * @param queue       上端フロートのFIFO待ち行列(読み取り専用)
	 * @param stackEnd    配置済みtop prefixの終端
	 * @param maxArea     topを積めるページ軸終端
	 * @param atPageStart ページ先頭の前進保証を適用するならtrue
	 * @return 採用したprefixと、その占有量の合計
	 */
	final TopFloatPlan planTopFloats(
			final java.util.Deque<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> queue,
			final double stackEnd, final double maxArea, final boolean atPageStart) {
		return planTopFloats(queue, this::footnoteExtent, stackEnd, maxArea, atPageStart);
	}

	/**
	 * {@link #planTopFloats(java.util.Deque, double, double, boolean)}の純粋な核です。
	 * 占有量の測り方を注入できるので、builder を組み立てずに単体で検査できる。
	 */
	static TopFloatPlan planTopFloats(
			final Iterable<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> queue,
			final java.util.function.ToDoubleFunction<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> extentOf,
			final double stackEnd, final double maxArea, final boolean atPageStart) {
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FloatBlockBox> boxes = new java.util.ArrayList<>();
		double pageAxis = stackEnd;
		double dy = 0;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : queue) {
			final double extent = extentOf.applyAsDouble(floatBox);
			if ((!atPageStart || pageAxis > 0) && pageAxis + extent > maxArea) {
				break;
			}
			boxes.add(floatBox);
			pageAxis += extent;
			dy += extent;
			if (atPageStart && pageAxis > maxArea) {
				break;
			}
		}
		return new TopFloatPlan(boxes, dy);
	}

	/**
	 * 計画済みの上端フロートだけをFIFO順に配置し、実配置矩形を当該ページの
	 * 行走査用排除域へ登録します。本文カーソルは進めません。
	 *
	 * @param plan {@link #planTopFloats}が返した配置計画
	 */
	private void placeTopPageFloats(final TopFloatPlan plan) {
		if (plan.boxes.isEmpty() && this.pendingTopFloats.isEmpty()) {
			return;
		}
		// MIN_PAGE_LIMITは基底の改ページ・脚注予約に残す。top配置は本文を
		// 押し下げないため、実際に空いている頁末まで積める。
		final double maxArea = super.getPageLimit() - this.pageFootnoteHost.footnoteReservation - this.bottomFloatReservation;
		final double fragmentLimit = this.getPageOwnerLimit();
		double pageAxis = this.topPageFloatStackEnd;
		for (final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox floatBox : plan.boxes) {
			assert this.pendingTopFloats.peekFirst() == floatBox : "top float plan/queue order mismatch";
			final double extent = this.footnoteExtent(floatBox);
			this.pendingTopFloats.removeFirst();
			this.pendingTopFloatGenerations.remove(floatBox);
			final Long previous = this.placedTopFloatGenerations.put(floatBox, this.pageGeneration);
			// キューは配置時に必ず消費する。同じfloatを同じページへ再配置すると
			// float-onlyページの前進保証が崩れるため、黙って重複させない。
			if (previous != null && previous.longValue() == this.pageGeneration) {
				throw new IllegalStateException("top page float repeated on page generation " + this.pageGeneration);
			}
			final double placedStart = pageAxis;
			final double placedEnd = placedStart + extent;
			// 排除域は当該PageBoxのfragmentainer内だけに限定する。描画上
			// overflowするfloatの実寸をそのまま使うと、行を何ページ分も先へ
			// 移してから通常の改ページで少しずつ戻すため、次ページで集合を
			// 交換しても空ページが何枚も残る。
			final double exclusionEnd = Math.min(placedEnd, fragmentLimit);
			this.pageBox.getContainer().addFloating(floatBox, 0, pageAxis);
			final double lineExtent = floatBox.getLineExtent(this.pageBox.getBlockParams().flow);
			if (!this.placingTopByTranslate && net.zamasoft.foliojet.layout.util.LayoutUtils.compare(lineExtent,
					this.pageBox.getLineSize()) != 0) {
				this.narrowTopPlacedWithTextBeside = true;
			}
			this.topPageFloatExclusions.add(new FloatExclusion(this.nextPageFloatOrder(), FloatSide.START,
					new AxisSpan(placedStart, exclusionEnd), new AxisSpan(0, lineExtent)));
			pageAxis = placedEnd;
			this.topPageFloatStackEnd = pageAxis;
			this.pageFloatProgressed = true;
			if (pageAxis > maxArea) {
				// 単独でページに収まらないフロートは溢れたまま置く
				// (クラッシュ排除方針。警告して続行)
				LOG.warning("page float too large for the page: " + extent + "pt");
			}
		}
		this.topPageFloatExclusionSnapshot = ExclusionSpace.copyOfSorted(this.topPageFloatExclusions);
		this.refreshPageFloatExclusionSnapshot();
	}

	/** 直近のページでフロートの配置が進んだか(finish()の前進性ガード)。 */
	private boolean pageFloatProgressed = false;

	/**
	 * 現ページに、頁先頭(二次元排除)で置いた狭幅 top があるか。脇に本文が
	 * 入り得るので、以後の平行移動は禁止する(移動した行が新しい帯と重なる)。
	 * 平行移動で帯として置いた狭幅 top は脇に本文が無いので数えない。
	 */
	private boolean narrowTopPlacedWithTextBeside = false;

	/** {@link #placeTopPageFloats}が平行移動(帯)から呼ばれている間 true。 */
	private boolean placingTopByTranslate = false;

	/**
	 * ページ確定時の脚注の清算です(finishLayoutから=分割完了後・描画前)。
	 * 確定した箱木に残った::footnote-callのID集合を採取し、pendingの
	 * 先頭から「carry-in済み(committed)またはcallがこのページに残った」
	 * 連続prefixだけを版面下端へ配置する。callがこのページに残ったが
	 * 配置されなかった脚注(容量送り・順序保持)はcommittedにして次ページで
	 * 最優先配置。配置座標は予約高ではなく実配置分の合計高で下端揃え
	 * (call移動で一部を送った場合、予約高のままだと下端に浮く)。
	 * 明示したheight/min-heightの帯では予約領域の本文側から並べます。
	 * 台帳状態は配置ゼロ件でも必ず清算する(次ページへ漏らさない)。
	 */
	private double attachFootnotes() {
		final boolean probe = this.isFootnoteProbe();
		final boolean planned = this.hasFootnotePlan() || this.footnoteArea().isHeightFixed();
		final FootnoteCallScan probeScan = probe ? scanFootnoteCalls(this.pageBox, false)
				: planned ? this.scanFootnoteCalls(this.pageBox) : null;
		if (planned) {
			// callが先にページを閉じても、本文を待つID・所属・採番を失わない。
			for (final long id : new java.util.TreeSet<>(probeScan.ids())) {
				if (!this.registeredFootnotes.contains(id)) this.bottomFootnote(id);
			}
			this.pageHadContent = probeScan.contentful();
			this.updateFootnotePrefix();
		}
		if (probe) {
			if (this.probeCallNumbers == null) this.probeCallNumbers = new java.util.HashMap<>();
			int number = 1;
			for (final long id : new java.util.TreeSet<>(probeScan.ids())) {
				boolean committed = false;
				boolean pending = false;
				for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
					if (entry.id == id) {
						pending = true;
						committed = entry.committed;
					}
				}
				if (!pending && this.registeredFootnotes.contains(id)) continue;
				if (!committed && !this.probeCallNumbers.containsKey(id)) this.probeCallNumbers.put(id, number++);
			}
		}
		final FootnoteCallScan columnPageScan = this.columnPageEntries.isEmpty() ? null : this.scanFootnoteCalls(this.pageBox);
		if (columnPageScan != null) this.numberColumnPageFootnotes(columnPageScan);
		if (this.pageFootnoteHost.pendingFootnotes.isEmpty()) {
			final double emptyArea = !this.isBottomFootnoteArea()
					&& (this.footnoteArea().isHeightFixed() || this.footnoteArea().minHeight > 0)
					? this.pageFootnoteHost.footnoteReservation : 0;
			this.pageFootnoteHost.footnoteReservedCount = 0;
			this.pageFootnoteHost.footnoteReservation = 0;
			return emptyArea;
		}
		final FootnoteCallScan scan = columnPageScan != null ? columnPageScan
				: probe || planned ? probeScan : this.scanFootnoteCalls(this.pageBox);
		this.pageHadContent = scan.contentful();
		final java.util.Set<Long> retained = scan.ids();
		this.traceFootnote("page-plan", null, 0, retained);

		// F5: 採番——このページにcallが残った未採番entryへ、FIFO(文書順)で
		// 1から割り当てる。committed(過去ページで採番済みのcarry-in)は
		// 再採番しない
		if (columnPageScan == null) {
			int nextNumber = 1;
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
				if (!entry.committed && retained.contains(entry.id)) {
					entry.assignedNumber = probe ? this.probeCallNumbers.remove(entry.id) : nextNumber++;
				}
			}
		}
		// 配置計画(変異なしで全件の行き先を確定してから一度だけcommit)
		int attachCount = 0;
		double attachedExtent = 0;
		{
			int i = 0;
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
				final boolean forced = this.forceFootnoteAttach && i == 0;
				if (i >= this.pageFootnoteHost.footnoteReservedCount
						|| (!entry.committed && !retained.contains(entry.id) && !forced)) {
					break;
				}
				if (planned) {
					final FootnoteReservation reservation = this.footnotePlan.get(entry.id);
					if (entry.noteBox == null || reservation == null) break;
					final double height = this.isBottomFootnoteArea() ? footnoteBandExtent(entry.noteBox) : this.footnoteExtent(entry.noteBox);
					final double capacity = this.isBottomFootnoteArea() ? this.pageBox.getFootInset() : this.pageFootnoteHost.footnoteReservation;
					if (!(i == 0 && reservation.oversized())
							&& (net.zamasoft.foliojet.layout.util.LayoutUtils.compare(height, reservation.height()) > 0
									|| net.zamasoft.foliojet.layout.util.LayoutUtils.compare(FOOTNOTE_GAP + attachedExtent + height, capacity) > 0)) break;
				}
				++attachCount;
				if (this.isBottomFootnoteArea()) {
					attachedExtent += this.footnoteBandExtent(entry.noteBox);
				} else {
					attachedExtent += this.footnoteExtent(entry.noteBox);
				}
				++i;
			}
		}
		// 配置されない残りのうち、callがこのページに残ったものはcarry-in
		{
			int i = 0;
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
				this.traceFootnote(i < attachCount ? "attach" : "defer", entry, 0, retained);
				if (i >= attachCount && retained.contains(entry.id)) {
					entry.committed = true;
				}
				++i;
			}
		}
		// F5: このページの確定木に残ったcallラベルを解決する(markerは
		// note側なのでattach時)。pendingに居ないIDのラベル(過去に配置済み
		// のnote内marker等)はスキップ
		if (!probe) {
			final java.util.Map<Long, Integer> numbers = new java.util.HashMap<>();
			for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) {
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
		final double base = this.isBottomFootnoteArea() ? super.getPageLimit() : this.pageFootnoteHost.capacityBase.getAsDouble();
		final boolean sizedBlockArea = !this.isBottomFootnoteArea()
				&& (this.footnoteArea().isHeightFixed() || this.footnoteArea().minHeight > 0);
		final double blockArea = sizedBlockArea ? this.pageFootnoteHost.footnoteReservation : 0;
		double pageAxis = sizedBlockArea ? base - blockArea + FOOTNOTE_GAP : base - attachedExtent;
		// 地の帯は予約領域の上端から並べる。巨大注も本文側へはみ出させない。
		double lineAxis = 0;
		if (this.isBottomFootnoteArea()) {
			lineAxis = this.pageBox.getInnerHeight() + FOOTNOTE_GAP;
		}
		for (int i = 0; i < attachCount; ++i) {
			final FootnoteEntry entry = this.pageFootnoteHost.pendingFootnotes.removeFirst();
			if (planned) this.bottomFootnotes.remove(entry.id);
			if (entry.assignedNumber < 0) {
				// 呼び出しが走査で見つからなかった脚注(表のセル等)。
				// 変換を失敗させず、文書順の通番で採番する(§5.13)
				entry.assignedNumber = (int) (entry.id + 1);
			}
			// note本文先頭の::footnote-markerラベルをcallページの番号で解決
			if (!probe) {
				for (final net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage label : this
						.scanFootnoteCalls(entry.noteBox).labels()) {
					if (label.isMarker()) {
						label.resolve(entry.assignedNumber);
					}
				}
			}
			if (this.isBottomFootnoteArea()) {
				// addFloatingは物理x/yではなく(lineAxis, pageAxis)。縦組みでは行軸がy。
				// 帯の中では注を用紙の左端に揃える。RL では pageAxis の原点が右端
				// なので、注の幅(page 方向の伸び)の分だけ引く——持ち越し先の
				// ページが呼び出しのページより狭くても左端から溢れさせない
				final WritingMode pageFlow = this.pageBox.getBlockParams().flow;
				final double notePageAxis = pageFlow == WritingMode.RL
						? this.pageBox.getInnerPageExtent(pageFlow) - entry.noteBox.getPageExtent(pageFlow)
						: 0;
				this.pageBox.getContainer().addFloating(entry.noteBox, lineAxis, notePageAxis);
				lineAxis += this.footnoteBandExtent(entry.noteBox);
			} else {
				this.pageFootnoteHost.addFloating(entry.noteBox, pageAxis);
				pageAxis += this.footnoteExtent(entry.noteBox);
			}
			this.pageFootnoteHost.footnoteProgressed = true;
		}
		if (this.isBottomFootnoteArea()) {
			if (attachCount > 0) {
				final net.zamasoft.foliojet.ua.FootnoteArea area = this.pageBox.getUserAgent()
						.getUAContext().getFootnoteArea();
				this.pageBox.setFootnoteSeparatorLineAxis(this.pageBox.getInnerHeight() + FOOTNOTE_GAP / 2,
						area.flow == null ? this.pageBox.getBlockParams().flow : area.flow);
			}
			this.pageFootnoteHost.footnoteReservedCount = 0;
			this.pageFootnoteHost.footnoteReservation = 0;
			// 下端ページ浮動体へ渡すblock方向の脚注量は0。
			return 0;
		}
		if (attachCount > 0) {
			// separator罫線(F6/F7答申①): 既存gapの中央に置くため予約は
			// 増えない。描画はPageSequence.drawPageのflow後(artifact)
			this.pageBox.setFootnoteSeparatorAxis(sizedBlockArea ? base - blockArea + FOOTNOTE_GAP / 2
					: base - attachedExtent - FOOTNOTE_GAP / 2);
		}
		this.pageFootnoteHost.footnoteReservedCount = 0;
		this.pageFootnoteHost.footnoteReservation = 0;
		return sizedBlockArea ? blockArea : attachedExtent == 0 ? 0 : attachedExtent + FOOTNOTE_GAP;
	}

	/** 全宿主を論理ID(文書順)で一度だけ採番する。carry-inは新規番号を消費しない。 */
	private void numberColumnPageFootnotes(final FootnoteCallScan scan) {
		final java.util.SortedMap<Long, FootnoteEntry> entries = new java.util.TreeMap<>(this.columnPageEntries);
		for (final FootnoteEntry entry : this.pageFootnoteHost.pendingFootnotes) entries.put(entry.id, entry);
		int nextNumber = 1;
		for (final FootnoteEntry entry : entries.values()) {
			if (!entry.committed && scan.ids().contains(entry.id)) entry.assignedNumber = nextNumber++;
		}
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels = new java.util.ArrayList<>(scan.labels());
		labels.addAll(this.columnPageLabels);
		for (final var label : labels) {
			final FootnoteEntry entry = entries.get(label.getFootnoteId());
			if (entry != null && entry.assignedNumber > 0) label.resolve(entry.assignedNumber);
		}
		for (final FootnoteEntry entry : entries.values()) {
			// 段に call が残ったのに置けなかった注(次段・次頁へ持ち越し)は、
			// 頁の添付と同じく committed にして carry-in として最優先で置く
			// (番号はこの頁のもの。grok レビュー必須2)。
			if (entry.columnCallRetained && entry.attachedColumnHost == null && scan.ids().contains(entry.id)) {
				entry.committed = true;
			}
			entry.columnCallRetained = false;
		}
	}

	/** 版面より大きい脚注の警告は1文書に1回。 */
	private boolean warnedOversizedFootnote = false;

	/**
	 * 呼び出しが見つからない脚注でも先頭から強制的に置くか(finish()の
	 * 前進保証、2026-08-02)。
	 */
	private boolean forceFootnoteAttach = false;

	/** 走査結果: callのID集合と、見つかった脚注ラベル(call/marker両方)、中身(行・置換要素)の有無。 */
	private record FootnoteCallScan(java.util.Set<Long> ids,
			java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels, boolean contentful) {
	}

	/**
	 * 箱木から脚注のcall ID集合とラベル原子を採取します(F4答申の候補A改+
	 * F5のラベル解決)。行跨ぎで同一callのインライン断片が複製されても
	 * 集合なので1件に畳まれる。走査は明示worklistの反復DFS。flow・float・
	 * 行・インラインに加えて、<b>表(行グループ→行→セル)と絶対配置の箱</b>へも
	 * 降りる(2026-09-02)。以前はこの2つが走査の外で、表のセルの中の脚注は
	 * 呼び出しが見つからないまま最後まで保留され、EOFで「諦めて」本文が
	 * 消え、呼び出しの番号も文書通番のままだった(cti.liの報告、2026-09-01:
	 * 2頁目のセルの注が本文なし・番号6)。
	 */
	private FootnoteCallScan scanFootnoteCalls(final net.zamasoft.foliojet.layout.box.AbstractContainerBox root) {
		return scanFootnoteCalls(root, true);
	}

	/** 計測用の読み取り走査。絶対配置のdeferred bind・脚注番号の解決を行いません。 */
	public static java.util.Set<Long> collectFootnoteCalls(
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox root) {
		return java.util.Set.copyOf(scanFootnoteCalls(root, false).ids());
	}

	private static FootnoteCallScan scanFootnoteCalls(
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox root, final boolean bindAbsolute) {
		final java.util.ArrayDeque<Object> work = new java.util.ArrayDeque<>();
		work.push(root);
		return scanFootnoteCalls(work, bindAbsolute);
	}

	private static FootnoteCallScan scanFootnoteCalls(final net.zamasoft.foliojet.layout.box.content.Container source,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner, final boolean bindAbsolute) {
		final java.util.ArrayDeque<Object> work = new java.util.ArrayDeque<>();
		pushFootnoteChildren(source, owner, bindAbsolute, work);
		return scanFootnoteCalls(work, bindAbsolute);
	}

	private static FootnoteCallScan scanFootnoteCalls(final java.util.ArrayDeque<Object> work, final boolean bindAbsolute) {
		final java.util.Set<Long> ids = new java.util.HashSet<>();
		final java.util.List<net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage> labels = new java.util.ArrayList<>();
		boolean contentful = false;
		while (!work.isEmpty()) {
			final Object node = work.pop();
			if (node instanceof net.zamasoft.foliojet.layout.box.AbstractReplacedBox
					|| node instanceof net.zamasoft.foliojet.layout.box.AbstractLineBox) {
				contentful = true;
			}
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
				pushFootnoteChildren(container.getContainer(), container, bindAbsolute, work);
			} else if (node instanceof net.zamasoft.foliojet.layout.box.impl.TableBox table) {
				// 表: ヘッダ→本体→フッタの行グループ、行、元のセル(拡張セルは
				// 同じ箱を指すので飛ばす)
				final java.util.List<net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox> groups = new java.util.ArrayList<>();
				if (table.getTableHeader() != null) {
					groups.add(table.getTableHeader());
				}
				for (int i = 0; i < table.getTableBodyCount(); ++i) {
					groups.add(table.getTableBody(i));
				}
				if (table.getTableFooter() != null) {
					groups.add(table.getTableFooter());
				}
				for (final net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox group : groups) {
					for (int r = 0; r < group.getTableRowCount(); ++r) {
						final net.zamasoft.foliojet.layout.box.impl.TableRowBox row = group.getTableRow(r);
						for (int c = 0; c < row.getCellCount(); ++c) {
							final net.zamasoft.foliojet.layout.box.impl.TableRowBox.Cell cell = row.getCell(c);
							if (cell.isSource() && cell.getCellBox() != null) {
								work.push(cell.getCellBox());
							}
						}
					}
				}
			} else if (node instanceof net.zamasoft.foliojet.layout.box.impl.TextBlockBox textBlock) {
				textBlock.forEachLine(work::push);
			} else if (node instanceof net.zamasoft.foliojet.layout.box.AbstractTextBox textBox) {
				textBox.forEachInlineBox(work::push);
			}
		}
		return new FootnoteCallScan(ids, labels, contentful);
	}

	/** 容器入口を共有する。deferred absoluteはその容器のownerでbindする。 */
	private static void pushFootnoteChildren(final net.zamasoft.foliojet.layout.box.content.Container source,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox owner, final boolean bindAbsolute,
			final java.util.ArrayDeque<Object> work) {
		source.eachFlowBox(work::push);
		source.eachFloatingBox(work::push);
		source.eachAbsoluteBox(box -> {
			if (!bindAbsolute) return;
			if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox absolute) {
				absolute.bindDeferredContent(owner);
			}
			work.push(box);
		});
	}

}
