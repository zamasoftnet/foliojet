package net.zamasoft.foliojet.layout.segment;

/**
 * 単一{@link SegmentId}上の半開区間{@code [from, toExclusive)}です
 * (2026-07-22新設、M6d-A1)。
 *
 * <p>
 * 異なる{@link SegmentId}間の区間・逆転区間(from&gt;to)は構築時に
 * 拒否する——これらはM6d-Aの正規モデルが表現してはならない不正状態
 * (codex設計相談で確認)。
 * </p>
 *
 * @param from        区間の開始(含む)
 * @param toExclusive 区間の終端(含まない)
 */
public record SegmentRange(SegmentCursor from, SegmentCursor toExclusive) {
	public SegmentRange {
		if (!from.segmentId().equals(toExclusive.segmentId())) {
			throw new IllegalArgumentException(
					"from/toExclusive must share the same SegmentId: " + from + ", " + toExclusive);
		}
		if (from.ordinal() > toExclusive.ordinal()) {
			throw new IllegalArgumentException("from must not be after toExclusive: " + from + ", " + toExclusive);
		}
	}

	public SegmentId segmentId() {
		return this.from.segmentId();
	}

	/** この区間に含まれるイベント境界の個数({@code toExclusive.ordinal() - from.ordinal()})。 */
	public long length() {
		return this.toExclusive.ordinal() - this.from.ordinal();
	}

	/** {@code cursor}がこの区間内(同一SegmentId・{@code [from, toExclusive)}範囲内)かどうか。 */
	public boolean contains(final SegmentCursor cursor) {
		return cursor.segmentId().equals(this.segmentId()) && cursor.ordinal() >= this.from.ordinal()
				&& cursor.ordinal() < this.toExclusive.ordinal();
	}
}
