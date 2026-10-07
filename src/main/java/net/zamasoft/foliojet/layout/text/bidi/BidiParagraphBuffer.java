package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Holds logical events for paragraph-level bidirectional resolution (2026-09-04,
 * bidi-isolation-design.md §2-1–§2-3; batch A-1a provided only the model and tests;
 * A-1b connects it to layout, owned by the ordered event queue on the DocumentBuilder side).
 *
 * <p>
 * Accepts characters, inline starts/ends, atomic inlines, forced paragraph breaks, and layout barriers
 * (floats/absolute positioning, etc.) in logical order, building a synthetic UTF-16 sequence for
 * {@link java.text.Bidi}. Ordinary inline boundaries emit nothing and are transparent to UBA;
 * only inlines with {@code unicode-bidi} are enclosed in control characters ({@link BidiResolver}).
 * An atomic inline normally contributes one U+FFFC (only replaced inlines with embed/override use a
 * strong substitute character in the element's direction).
 * After resolution, levels can be retrieved from each event's range in the synthetic sequence.
 * </p>
 */
public final class BidiParagraphBuffer {
	/** Event kinds. */
	public enum Kind {
		TEXT, INLINE_START, INLINE_END, ATOMIC, PARAGRAPH_BREAK, BARRIER
	}

	/**
	 * A logical event. {@code start}/{@code limit} delimit its range in the synthetic sequence
	 * (excluding control characters; zero length except for TEXT/ATOMIC).
	 */
	public static class Event {
		private final Kind kind;
		private final int start, limit;
		private final byte direction, unicodeBidi;
		private final Object payload;

		public Event(final Kind kind, final int start, final int limit, final byte direction,
				final byte unicodeBidi, final Object payload) {
			this.kind = kind;
			this.start = start;
			this.limit = limit;
			this.direction = direction;
			this.unicodeBidi = unicodeBidi;
			this.payload = payload;
		}

		public Kind kind() { return this.kind; }
		public int start() { return this.start; }
		public int limit() { return this.limit; }
		public byte direction() { return this.direction; }
		public byte unicodeBidi() { return this.unicodeBidi; }
		public Object payload() { return this.payload; }

		public int length() {
			return this.limit - this.start;
		}
	}

	/** Immutable recipe for an inline reopened across paragraph boundaries. */
	public record OpenInline(byte direction, byte unicodeBidi, Object payload) {
	}

	/** A forced paragraph boundary and the open-inline snapshot passed to the next buffer. */
	public static final class ParagraphBreak extends Event {
		private final List<OpenInline> openInlines;

		ParagraphBreak(final int start, final int limit, final Object payload, final List<OpenInline> openInlines) {
			super(Kind.PARAGRAPH_BREAK, start, limit, (byte) 0, (byte) 0, payload);
			this.openInlines = List.copyOf(openInlines);
		}

		public Event event() { return this; }
		public List<OpenInline> openInlines() { return this.openInlines; }
	}

	private final StringBuilder synthetic = new StringBuilder();
	private final List<Event> events = new ArrayList<>();
	private final List<OpenInline> openInlines = new ArrayList<>();
	private final BitSet syntheticControls = new BitSet();
	private final int baseDirectionFlag;
	private final byte blockUnicodeBidi;
	private Bidi bidi;
	private boolean broken;

	/**
	 * @param blockDirection   {@code direction} of the block containing the paragraph
	 * @param blockUnicodeBidi {@code unicode-bidi} of that block (automatic detection for plaintext)
	 */
	public BidiParagraphBuffer(final byte blockDirection, final byte blockUnicodeBidi) {
		this.baseDirectionFlag = BidiResolver.baseDirectionFlag(blockDirection, blockUnicodeBidi);
		this.blockUnicodeBidi = blockUnicodeBidi;
		this.appendSynthetic(BidiResolver.rootOpeningControls(blockDirection, blockUnicodeBidi));
	}

	/** Text (after normalization and text-transform). */
	public Event addText(final CharSequence text, final Object payload) {
		this.beforeAdd();
		final int start = this.synthetic.length();
		this.synthetic.append(text);
		return this.add(new Event(Kind.TEXT, start, this.synthetic.length(), (byte) 0, (byte) 0, payload));
	}

	/** Inline start. Inserts control characters according to {@code unicode-bidi}. */
	public Event inlineStart(final byte direction, final byte unicodeBidi, final Object payload) {
		this.beforeAdd();
		this.appendSynthetic(BidiResolver.openingControls(direction, unicodeBidi));
		this.openInlines.add(new OpenInline(direction, unicodeBidi, payload));
		final int at = this.synthetic.length();
		return this.add(new Event(Kind.INLINE_START, at, at, direction, unicodeBidi, payload));
	}

	/** Inline end (paired with {@link #inlineStart}). */
	public Event inlineEnd(final Object payload) {
		this.beforeAdd();
		if (this.openInlines.isEmpty()) {
			throw new IllegalStateException("inlineEnd without inlineStart");
		}
		final OpenInline open = this.openInlines.remove(this.openInlines.size() - 1);
		final int at = this.synthetic.length();
		this.appendSynthetic(BidiResolver.closingControls(open.unicodeBidi()));
		return this.add(new Event(Kind.INLINE_END, at, at, open.direction(), open.unicodeBidi(), payload));
	}

	/** Atomic inline (replaced element, inline-block, ruby, warichu). Normally one U+FFFC. */
	public Event atomic(final Object payload) {
		return this.atomic(payload, (byte) 0, net.zamasoft.foliojet.css.value.UnicodeBidiValue.NORMAL);
	}

	/**
	 * Atomic inline. Replaced inlines with embed/override resolve with a strong substitute character
	 * in the element's direction, per CSS Writing Modes §2.4.3.
	 */
	public Event atomic(final Object payload, final byte direction, final byte unicodeBidi) {
		this.beforeAdd();
		final int start = this.synthetic.length();
		this.synthetic.append(BidiResolver.atomicCharacter(direction, unicodeBidi));
		return this.add(new Event(Kind.ATOMIC, start, start + 1, direction, unicodeBidi, payload));
	}

	/**
	 * Forced paragraph break (bidi type B). Closes controls for open inlines here and reopens them
	 * in the next paragraph (css-writing-modes-3 §2.4). The caller reopens them by passing
	 * the returned snapshot to a new buffer.
	 */
	public ParagraphBreak paragraphBreak(final Object payload) {
		this.beforeAdd();
		final List<OpenInline> snapshot = List.copyOf(this.openInlines);
		for (int i = this.openInlines.size() - 1; i >= 0; --i) {
			this.appendSynthetic(BidiResolver.closingControls(this.openInlines.get(i).unicodeBidi()));
		}
		this.appendSynthetic(BidiResolver.rootClosingControls(this.blockUnicodeBidi));
		final int start = this.synthetic.length();
		this.synthetic.append(BidiResolver.PARAGRAPH_SEPARATOR);
		final ParagraphBreak event = new ParagraphBreak(start, start + 1, payload, snapshot);
		this.add(event);
		this.broken = true;
		return event;
	}

	/** Reopens the previous buffer's {@link ParagraphBreak#openInlines()} in logical order. */
	public void reopen(final List<OpenInline> snapshot) {
		for (final OpenInline open : snapshot) {
			this.inlineStart(open.direction(), open.unicodeBidi(), open.payload());
		}
	}

	/** Layout ordering barrier (float, absolute, bound addition to parent builder, etc.). Emits nothing into the synthetic sequence. */
	public Event barrier(final Object payload) {
		this.beforeAdd();
		final int at = this.synthetic.length();
		return this.add(new Event(Kind.BARRIER, at, at, (byte) 0, (byte) 0, payload));
	}

	private Event add(final Event event) {
		this.events.add(event);
		return event;
	}

	private void beforeAdd() {
		if (this.broken) {
			throw new IllegalStateException("paragraph buffer is closed after paragraphBreak");
		}
		this.bidi = null;
	}

	private void appendSynthetic(final String text) {
		if (text.isEmpty()) {
			return;
		}
		final int start = this.synthetic.length();
		this.synthetic.append(text);
		this.syntheticControls.set(start, this.synthetic.length());
	}

	public List<Event> events() {
		return Collections.unmodifiableList(this.events);
	}

	/** Synthetic sequence (including control characters). For tests and diagnostics. */
	public String synthetic() {
		return this.synthetic.toString();
	}

	public int length() {
		return this.synthetic.length();
	}

	public boolean isEmpty() {
		return this.events.isEmpty();
	}

	/** Whether this index refers to a CSS-synthesized control character. False for the same character from body text. */
	public boolean isSyntheticControl(final int index) {
		if (index < 0 || index >= this.synthetic.length()) {
			throw new IndexOutOfBoundsException(index);
		}
		return this.syntheticControls.get(index);
	}

	/** {@link Bidi} resolved once for the entire paragraph (lazy; recreated after each addition). */
	public Bidi resolve() {
		if (this.bidi == null) {
			final String closeRoot = this.broken ? "" : BidiResolver.rootClosingControls(this.blockUnicodeBidi);
			this.bidi = new Bidi(this.synthetic.toString() + closeRoot, this.baseDirectionFlag);
		}
		return this.bidi;
	}

	/** Paragraph level (0=LTR, 1=RTL). Also exposes plaintext's automatic direction result. */
	public int paragraphLevel() {
		return this.resolve().getBaseLevel();
	}

	/** Embedding level at {@code index} in the synthetic sequence. */
	public int levelAt(final int index) {
		return this.resolve().getLevelAt(index);
	}

	/**
	 * {@link Bidi} for the line {@code [start, limit)}
	 * (UAX #9 L1: reset trailing whitespace to the paragraph level).
	 * Used after line breaking to determine the line's visual order.
	 */
	public Bidi lineBidi(final int start, final int limit) {
		return this.resolve().createLineBidi(start, limit);
	}

	/** Whether the paragraph has RTL characters or a right-to-left base direction (otherwise no reordering is needed). */
	public boolean requiresVisualReordering() {
		return !this.resolve().isLeftToRight();
	}
}
