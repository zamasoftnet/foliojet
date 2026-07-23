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
	 * (範囲不明・Opaque/フロート/入れ子段組/縦横混在含み・範囲の穴・
	 * {@link SegmentEvent.Barrier}が一件でもある)なら空を返し、呼び出し側は
	 * 既存balanceへフォールバックする——汎用Segment executorは作らない
	 * (codex設計§1.4)。
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
		if (!SourceReplayer.canReplayChildren(log, selfId, owner.getBlockParams().flow)) {
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
}
