package net.zamasoft.foliojet.layout.segment;

/**
 * {@link SegmentEvent.Barrier}が生じた理由です(2026-07-22新設、
 * M6d-A3a、型契約のみ・未配線)。
 *
 * <p>
 * 旧{@code LayoutSource.Opaque}は理由を一切持たない位置占有マーカーに
 * すぎなかった——それをそのまま正規モデルへ持ち込むと、なぜ再生
 * できなかったのかが後から追えず、silent fallbackの温床になる
 * (codex設計相談で指摘)。この列挙で理由を必ず明示する。
 * </p>
 */
public enum BarrierReason {
	/** 表・置換要素等、まだ{@code SegmentEvent}化されていない内容(旧{@code Opaque}相当)。 */
	NOT_YET_SUPPORTED,
	/** 変換時に未知の型を検出したため、安全側に倒した(fail closed)。 */
	UNKNOWN_TYPE,
	/** 対応する{@code Start}がまだ閉じていない(部分木が未確定)。 */
	UNCLOSED_SUBTREE;
}
