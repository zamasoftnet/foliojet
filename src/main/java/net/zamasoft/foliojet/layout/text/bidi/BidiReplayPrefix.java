package net.zamasoft.foliojet.layout.text.bidi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;

/**
 * 段落途中の replay より前にある論理行の永続 chain。
 * 断片ごとに全 prefix を複製せず、段落全体で O(paragraph) に保つ。
 */
public final class BidiReplayPrefix {
	public static final BidiReplayPrefix EMPTY = new BidiReplayPrefix(null, List.of(), 0);

	private final BidiReplayPrefix previous;
	private final List<AbstractLineBox> segment;
	private final int size;

	private BidiReplayPrefix(final BidiReplayPrefix previous, final List<AbstractLineBox> segment,
			final int size) {
		this.previous = previous;
		this.segment = segment;
		this.size = size;
	}

	public boolean isEmpty() {
		return this.size == 0;
	}

	/** 最初の配置時に割り当てた段落 ID。未解決なら 0。 */
	public long paragraphId() {
		BidiReplayPrefix first = this;
		while (first.previous != null && !first.previous.segment.isEmpty()) {
			first = first.previous;
		}
		return first.segment.isEmpty() ? 0 : first.segment.get(0).getBidiParagraphId();
	}

	public BidiReplayPrefix append(final List<AbstractLineBox> lines) {
		if (lines.isEmpty()) {
			return this;
		}
		return new BidiReplayPrefix(this, List.copyOf(lines), this.size + lines.size());
	}

	/** resolver 用に一時的な論理順 list へ展開する。 */
	public List<AbstractLineBox> lines() {
		if (this.isEmpty()) {
			return List.of();
		}
		final ArrayDeque<List<AbstractLineBox>> segments = new ArrayDeque<>();
		for (BidiReplayPrefix prefix = this; prefix != null && !prefix.segment.isEmpty();
				prefix = prefix.previous) {
			segments.push(prefix.segment);
		}
		final List<AbstractLineBox> lines = new ArrayList<>(this.size);
		while (!segments.isEmpty()) {
			lines.addAll(segments.pop());
		}
		return lines;
	}
}
