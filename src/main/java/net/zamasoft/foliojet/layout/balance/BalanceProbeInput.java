package net.zamasoft.foliojet.layout.balance;

import java.util.Optional;

import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * M6cバランスプローブの検証済み入力です(2026-07-24新設、排除域P2の
 * M6c-2——{@code docs/consultations/consult-exclusion-p2-design-codex.txt}
 * §1.2/1.4)。
 *
 * <p>
 * <b>E-6増分3b-5(2026-07-24)</b>: 旧実装はcapture時に範囲全量を
 * {@code List<SegmentEvent>}へ凍結していたが、イベント格納が記録時
 * recipe化(3b-3/3b-4)済みになったため全量Listは不要になった。本recordは
 * range座標+owner geometryだけの軽量入力で、capture時は範囲を1パス
 * streaming走査してBarrier検査(適格判定)のみ行う。各候補試行は
 * {@link #openSlice()}で範囲のstreamingビューを試行ごとに開き直し、
 * {@code SegmentEvent}への変換は駆動時にオンザフライで行う
 * ({@code BalanceProbeSession.build})。
 * </p>
 *
 * <p>
 * <b>試行ごとcaptureの安全性</b>: プローブは改ページ処理中の
 * {@code AbstractContainerBox.balance()}からの同期実行で、実行中に
 * liveの追記・compactは走らない——compactの唯一の呼び出し元は
 * {@code RootBuilder.breakFlow}(live PageGenerator経由)で、その
 * スレッドはプローブ完了までblance()内でブロックしており、候補構築側の
 * {@code ProbePageGenerator.compactLayoutSource}は例外を投げる。
 * よって範囲はプローブ実行中を通して無傷であり、リースを持ち越さない
 * 試行ごとのcapture(O(log N))で複数試行(最大
 * {@code BalanceProbe.MAX_PROBES})の再読が成立する。
 * </p>
 *
 * @param log           ソースログ(範囲の再読に使う。候補構築はここから
 *                      captureした凍結済みrecipe列だけを読み、liveボックスへ
 *                      一切触れない)
 * @param fromId        子イベント範囲の先頭EventId(ソースアンカー再付与の座標)
 * @param toId          子イベント範囲の末尾EventId
 * @param ownerGeometry live ownerの解決済み物理形状
 * @param columnCount   指定段数
 * @param ua            ユーザーエージェント(プロパティ参照のみに使う)
 */
public record BalanceProbeInput(LayoutSource log, long fromId, long toId,
		BalanceBoxSnapshot ownerGeometry, int columnCount, UserAgent ua) {

	/**
	 * live ownerからプローブ入力を検証・構築します。プローブ不適格
	 * (範囲不明・Opaque/入れ子段組/縦横混在含み・範囲の穴・
	 * {@link SegmentEvent.Barrier}化するイベントが一件でもある)なら空を
	 * 返し、呼び出し側は既存balanceへフォールバックする——汎用Segment
	 * executorは作らない(codex設計§1.4)。
	 *
	 * <p>
	 * <b>段組内float(M6c-5、2026-07-24解禁)</b>: 適格判定はプローブ
	 * 専用で、{@link SourceReplayer#canReplayChildren}(live legacy再構築
	 * 経路と共有——そちらは{@code containsFloat}ゲートを維持)とは
	 * 独立している。floatは候補builder内で通常どおり配置され、P1の
	 * {@code commitFloatPlacement}が候補内に閉じる(候補builderの
	 * floatings台帳は新品)。外側floatの排除域は従来どおりコピーしない
	 * (multicol実幅に反映済み・二重回避防止)。
	 * </p>
	 *
	 * @param log         ソースログ
	 * @param selfId      owner自身のStartのEventId(ソースアンカー)
	 * @param owner       バランス対象の段組ボックス
	 * @param columnCount 指定段数
	 * @param ua          ユーザーエージェント
	 * @return 検証済み入力。不適格なら空
	 */
	public static Optional<BalanceProbeInput> capture(final LayoutSource log, final long selfId,
			final MulticolumnBlockBox owner, final int columnCount, final UserAgent ua) {
		if (!canReplayForProbe(log, selfId, owner)) {
			return Optional.empty();
		}
		final long endId = log.endOf(selfId);
		final long fromId = selfId + 1;
		final long toId = endId - 1;
		final LayoutSource.ReplaySlice slice = log.capture(fromId, toId);
		if (slice == null) {
			// 範囲に破棄済みの穴があればフォールバック
			return Optional.empty();
		}
		// E-6増分3b-5: 全量Listへの凍結はしない——範囲を1パスstreaming走査
		// してBarrier化するイベント(replay不能)の有無だけを検査する
		// (payloadのdecodeもしない)。変換自体は各試行の駆動時にオンザフライ
		final boolean[] barrier = { false };
		slice.replay(event -> {
			if (LayoutSourceEventConverter.convertsToBarrier(event)) {
				barrier[0] = true;
			}
		});
		if (barrier[0]) {
			// replay不能イベントが一件でもあればプローブ不適格
			return Optional.empty();
		}
		return Optional.of(new BalanceProbeInput(log, fromId, toId, owner.snapshotBalanceGeometry(), columnCount, ua));
	}

	/**
	 * 検証済み範囲のstreamingビューを開きます(試行ごとに呼ぶ——
	 * {@code ReplaySlice}はconsume-onceのため共有できない。E-6増分3b-5)。
	 * captureが検証した範囲はプローブ実行中のcompactから構造的に無傷
	 * (クラスjavadoc「試行ごとcaptureの安全性」)のため、ここでの失敗は
	 * 契約違反の即時失敗——呼び出し側(BalanceProbe.adopt)のcommit前
	 * 例外処理が既存balanceへフォールバックさせる。
	 */
	public LayoutSource.ReplaySlice openSlice() {
		final LayoutSource.ReplaySlice slice = this.log.capture(this.fromId, this.toId);
		if (slice == null) {
			throw new IllegalStateException(
					"balance probe range lost between trials: [" + this.fromId + ", " + this.toId + "]");
		}
		return slice;
	}

	/**
	 * プローブ専用の子範囲再生可否です(2026-07-24、M6c-5で
	 * {@link SourceReplayer#canReplayChildren}から分離)。判定は
	 * {@code containsFloat}を課さない点だけが異なる——legacy再構築
	 * ({@code SourceReplayer.replayChildren})の適格条件はそのまま
	 * 維持し、float解禁の影響範囲をプローブに限定する。
	 */
	private static boolean canReplayForProbe(final LayoutSource log, final long selfId,
			final MulticolumnBlockBox owner) {
		if (log == null || selfId < 0) {
			return false;
		}
		final long endId = log.endOf(selfId);
		if (endId < 0 || endId <= selfId + 1) {
			// 部分木が開いたまま(column-span分割中)・空内容
			return false;
		}
		// 入れ子段組・縦横混在の再現は未検証のためフォールバック(M6c-5でも維持)。
		// 絶対配置は増分4e以前はOpaque記録でcontainsOpaqueが捕捉していた——
		// 候補内での係留・deferred bindの再現は未検証のため従来どおり不適格
		// 表(G-1、2026-07-25): G-1以前は表がOpaque記録でcontainsOpaqueが
		// 捕捉していた。recipe記録化で「再生できる」ようになっても、段組
		// バランスの候補試行で表を含む範囲を通すと、候補ごとに表全体
		// (auto列幅の再確定を含む)を再構築することになり、バランス結果
		// (=出力)とコストが変わる。挙動不変制約のため従来どおり不適格
		return !(log.containsTable(selfId + 1, endId - 1) || log.containsOpaque(selfId + 1, endId - 1)
				|| log.containsAbsolute(selfId + 1, endId - 1) || log.containsMulticol(selfId + 1, endId - 1)
				|| log.containsMixedFlow(selfId + 1, endId - 1, owner.getBlockParams().flow));
	}
}
