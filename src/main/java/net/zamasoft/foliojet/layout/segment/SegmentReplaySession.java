package net.zamasoft.foliojet.layout.segment;

import java.util.NoSuchElementException;

/**
 * {@link SegmentRange}を先頭から辿る、呼び出しごとに独立した再生
 * セッションです(2026-07-22新設、M6d-A1)。
 *
 * <p>
 * M6d-Aの最重要契約をここで固定する: 消費位置(現在のordinal)は
 * {@link SegmentRange}や{@link SegmentId}自身ではなく、この
 * セッションのインスタンスだけが所有する。同じ{@link SegmentRange}を
 * 新しい{@code SegmentReplaySession}で何度でも先頭から辿れる(複数回
 * 再生可能)し、複数のセッションを交互に進めても互いに影響しない
 * (MIN/MAX/DEFINITEの呼び出し間で消費状態を共有しない、という
 * サイジング契約の前提)。
 * </p>
 */
public final class SegmentReplaySession {
	private final SegmentRange range;
	private long currentOrdinal;

	public SegmentReplaySession(final SegmentRange range) {
		this.range = range;
		this.currentOrdinal = range.from().ordinal();
	}

	public SegmentId segmentId() {
		return this.range.segmentId();
	}

	public SegmentRange range() {
		return this.range;
	}

	/** 次に{@link #next()}で返す位置を指す、まだ消費していないcursor。 */
	public SegmentCursor current() {
		return new SegmentCursor(this.range.segmentId(), this.currentOrdinal);
	}

	public boolean hasNext() {
		return this.currentOrdinal < this.range.toExclusive().ordinal();
	}

	/**
	 * 現在位置のcursorを返し、1つ進める。
	 *
	 * @throws NoSuchElementException 区間の終端に既に達している場合
	 */
	public SegmentCursor next() {
		if (!this.hasNext()) {
			throw new NoSuchElementException("SegmentReplaySession exhausted: " + this.range);
		}
		final SegmentCursor cursor = this.current();
		++this.currentOrdinal;
		return cursor;
	}
}
