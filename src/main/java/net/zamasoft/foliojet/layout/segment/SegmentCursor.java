package net.zamasoft.foliojet.layout.segment;

/**
 * ある{@link SegmentId}上のイベント境界を指す、安定した継続位置です
 * (2026-07-22新設、M6d-A1)。
 *
 * <p>
 * 「継続位置はobject identityではなく安定したcursorで表す」という
 * M6d-Aの必須条件をここで固定する——{@code IBox}等のlive参照は一切
 * 持たず、{@code equals}可能な値(segmentIdとordinal)だけで完全に
 * 定まる。同じ値を持つ2つのインスタンスは、生成時点やコピー経由か
 * どうかに関わらず常に等価。
 * </p>
 *
 * @param segmentId どの記録ストリームの位置か
 * @param ordinal   そのストリーム内でのイベント境界(0起算、境界数=
 *                  イベント数+1のうちの1つを指す——[from,to)区間の
 *                  端点として使う)
 */
public record SegmentCursor(SegmentId segmentId, long ordinal) {
	public SegmentCursor {
		if (ordinal < 0) {
			throw new IllegalArgumentException("ordinal must be >= 0: " + ordinal);
		}
	}
}
