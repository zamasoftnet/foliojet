package net.zamasoft.foliojet.layout.segment;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 記録ストリーム(1つの継続再生の論理root)を識別する値です
 * (2026-07-22新設、M6d-A1)。
 *
 * <p>
 * M6d(ConstraintSpace/layout()一本化)の正規{@code Segment}モデルの
 * 一部として、既存3種の再生表現({@code LayoutSource}・
 * {@code TwoPassBlockBuilder.Recorded}・{@code TextReplaySlice})を
 * 将来統合するための下地。この段階(M6d-A1)では既存クラスへの配線は
 * 一切行わない——挙動不変な、独立した値型の追加のみ(codex設計相談で
 * 確認、詳細は`docs/history/`参照)。
 * </p>
 *
 * <p>
 * 通常は{@link #create()}で新規発行する。生成順のみを保証し、値
 * そのものに意味を持たせない——比較・識別にのみ使う不透明なトークン
 * として扱う。
 * </p>
 */
public record SegmentId(long value) {
	private static final AtomicLong NEXT = new AtomicLong();

	public static SegmentId create() {
		return new SegmentId(NEXT.getAndIncrement());
	}
}
