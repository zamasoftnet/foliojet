package net.zamasoft.foliojet.layout.balance;

import java.util.List;
import java.util.Optional;

import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.LayoutSourceEventConverter;
import net.zamasoft.foliojet.layout.segment.SegmentEvent;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * M6cバランスプローブの凍結済み入力です(2026-07-24新設、排除域P2の
 * M6c-2——{@code docs/consultations/consult-exclusion-p2-design-codex.txt}
 * §1.2/1.4)。
 *
 * <p>
 * {@code ReplaySlice}はイベント列をコピーするが{@code Start.params/pos}
 * 自体は参照のままなので、プローブ開始時に一度だけ
 * {@code LayoutSource.capture}→{@link LayoutSourceEventConverter#convert}
 * →frozen template化まで済ませる(ordinal 1:1維持)。各候補は
 * {@code BoxRecipeBoxFactory}からこの凍結列だけを読んで完全な新品を作る
 * ——候補構築中にliveログ・liveボックスへ一切触れない。
 * </p>
 *
 * @param source       子イベント範囲の不変スナップショット(診断・将来の
 *                     shadow比較用。候補構築は{@code frozenEvents}だけを
 *                     読む)
 * @param frozenEvents 凍結済みイベント列({@code source}とordinal 1:1)
 * @param ownerGeometry live ownerの解決済み物理形状
 * @param columnCount  指定段数
 * @param ua           ユーザーエージェント(プロパティ参照のみに使う)
 */
public record BalanceProbeInput(LayoutSource.ReplaySlice source, List<SegmentEvent> frozenEvents,
		BalanceBoxSnapshot ownerGeometry, int columnCount, UserAgent ua) {

	public BalanceProbeInput {
		frozenEvents = List.copyOf(frozenEvents);
	}

	/**
	 * live ownerからプローブ入力を凍結します。プローブ不適格
	 * (範囲不明・Opaque/入れ子段組/縦横混在含み・範囲の穴・
	 * {@link SegmentEvent.Barrier}が一件でもある)なら空を返し、呼び出し側は
	 * 既存balanceへフォールバックする——汎用Segment executorは作らない
	 * (codex設計§1.4)。
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
	 * @return 凍結済み入力。不適格なら空
	 */
	public static Optional<BalanceProbeInput> capture(final LayoutSource log, final long selfId,
			final MulticolumnBlockBox owner, final int columnCount, final UserAgent ua) {
		if (!canReplayForProbe(log, selfId, owner)) {
			return Optional.empty();
		}
		final long endId = log.endOf(selfId);
		final LayoutSource.ReplaySlice slice = log.capture(selfId + 1, endId - 1);
		if (slice == null) {
			// 範囲に破棄済みの穴があればフォールバック
			return Optional.empty();
		}
		final List<SegmentEvent> events = LayoutSourceEventConverter.convert(slice);
		for (final SegmentEvent event : events) {
			if (event instanceof SegmentEvent.Barrier) {
				// replay不能イベントが一件でもあればプローブ不適格
				return Optional.empty();
			}
		}
		return Optional.of(new BalanceProbeInput(slice, events, owner.snapshotBalanceGeometry(), columnCount, ua));
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
		// 入れ子段組・縦横混在の再現は未検証のためフォールバック(M6c-5でも維持)
		return !(log.containsOpaque(selfId + 1, endId - 1) || log.containsMulticol(selfId + 1, endId - 1)
				|| log.containsMixedFlow(selfId + 1, endId - 1, owner.getBlockParams().flow));
	}
}
