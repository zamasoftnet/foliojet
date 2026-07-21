package net.zamasoft.foliojet.layout.segment;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * {@link LayoutSource}の各インスタンスへ安定した{@link SegmentId}を
 * 対応付け、その{@code EventId}({@code long})と{@link SegmentCursor}/
 * {@link SegmentRange}の間の座標変換を行う、capture専用のアダプタです
 * (2026-07-22新設、M6d-A2)。
 *
 * <p>
 * 実際の再生駆動({@code SourceReplayer})には一切関与しない——
 * {@code EventId}の世界と{@code SegmentCursor}の世界を安全に往復
 * させるためだけの薄い層。{@link LayoutSource#capture(long, long)}の
 * 契約(両端inclusive、{@code [fromId, toId]})と{@link SegmentRange}
 * の契約(半開、{@code [from, toExclusive)})の違いをここで一箇所に
 * 集約して吸収する——codex設計相談で確認(M6d-A、
 * `docs/history/2026-07-22-m6d-a1-segment-value-types.md`参照)。
 * </p>
 *
 * <p>
 * {@code Start}のmutableな{@code Params}/{@code Pos}、{@code Chars}の
 * 配列、{@code Replaced}のlive boxといったイベント内容そのものは、
 * この段階(M6d-A2)ではまだ正規化しない——それは後続段階
 * (`SegmentEvent`/`BoxRecipe`の設計)の対象。ここではあくまで
 * 「どの範囲を指しているか」という座標の変換だけを行う。
 * </p>
 */
public final class LayoutSourceSegments {
	private static final Map<LayoutSource, SegmentId> IDS = Collections.synchronizedMap(new WeakHashMap<>());

	private LayoutSourceSegments() {
	}

	/**
	 * {@code source}に対応する{@link SegmentId}を返します。初回呼び出し時に
	 * 新規発行し、以後同じインスタンスには常に同じ{@link SegmentId}を返します
	 * ({@link LayoutSource}はidentityで管理、{@code WeakHashMap}のため
	 * {@code source}がGCされれば対応するエントリも自然に消える)。
	 */
	public static SegmentId idFor(final LayoutSource source) {
		synchronized (IDS) {
			return IDS.computeIfAbsent(source, s -> SegmentId.create());
		}
	}

	/** {@code source}上の{@code eventId}を指す{@link SegmentCursor}を返します。 */
	public static SegmentCursor cursorOf(final LayoutSource source, final long eventId) {
		return new SegmentCursor(idFor(source), eventId);
	}

	/**
	 * {@link LayoutSource}の両端inclusive規約{@code [fromId, toId]}を、
	 * {@link SegmentRange}の半開規約{@code [from, toExclusive)}へ変換します。
	 *
	 * @param fromId 先頭イベントの{@code EventId}(含む)
	 * @param toId   末尾イベントの{@code EventId}(含む)。{@code fromId - 1}
	 *               なら空区間({@code toId < fromId}はそれ以外は不正)
	 */
	public static SegmentRange rangeOf(final LayoutSource source, final long fromId, final long toId) {
		return new SegmentRange(cursorOf(source, fromId), cursorOf(source, toId + 1));
	}

	/**
	 * {@link SegmentRange}に対応する{@link LayoutSource.ReplaySlice}を
	 * 取得します。{@code range}が{@code source}のものではない
	 * ({@link SegmentId}が一致しない)場合は例外。空区間
	 * ({@code range.length()==0}相当、退化区間)なら{@code null}
	 * ({@link LayoutSource#capture}が{@code fromId > toId}を受け付けない
	 * ため、ここで明示的に区別する)。
	 */
	public static LayoutSource.ReplaySlice capture(final LayoutSource source, final SegmentRange range) {
		if (!range.segmentId().equals(idFor(source))) {
			throw new IllegalArgumentException(
					"range " + range + " does not belong to this LayoutSource (segmentId="
							+ idFor(source) + ")");
		}
		if (range.length() == 0) {
			return null;
		}
		return source.capture(range.from().ordinal(), range.toExclusive().ordinal() - 1);
	}
}
