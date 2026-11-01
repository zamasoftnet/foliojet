package net.zamasoft.foliojet.layout.text.bidi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;

/**
 * Persistent chain of logical lines preceding a replay from the middle of a paragraph.
 * Avoids copying the entire prefix for each fragment, keeping the whole paragraph O(paragraph).
 */
public final class BidiReplayPrefix {
	public static final BidiReplayPrefix EMPTY = new BidiReplayPrefix(null, List.of(), 0, true);

	private final BidiReplayPrefix previous;
	private final List<AbstractLineBox> segment;
	private final int size;
	private final boolean leftToRight;

	private BidiReplayPrefix(final BidiReplayPrefix previous, final List<AbstractLineBox> segment,
			final int size, final boolean leftToRight) {
		this.previous = previous;
		this.segment = segment;
		this.size = size;
		this.leftToRight = leftToRight;
	}

	/** Whether what these lines hold is all that {@link BidiParagraphLayout#staysLeftToRight} allows. */
	boolean staysLeftToRight() {
		return this.leftToRight;
	}

	public boolean isEmpty() {
		return this.size == 0;
	}

	/** Paragraph ID assigned at initial layout. Zero if unresolved. */
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
		boolean leftToRight = this.leftToRight;
		for (int i = 0; leftToRight && i < lines.size(); ++i) {
			leftToRight = BidiParagraphLayout.staysLeftToRight(lines.get(i).getLogicalContents());
		}
		return new BidiReplayPrefix(this, List.copyOf(lines), this.size + lines.size(), leftToRight);
	}

	/** Expands into a temporary list in logical order for the resolver. */
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
