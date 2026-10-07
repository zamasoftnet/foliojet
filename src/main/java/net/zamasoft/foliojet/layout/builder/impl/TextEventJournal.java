package net.zamasoft.foliojet.layout.builder.impl;

/**
 * Observes normalized events at the WordHyphenator → BuilderGlyphHandler boundary
 * (M3b Phase 0: shadow journal; no behavior change).
 *
 * <p>
 * Assigns a seq exactly once to each delivered event and tracks the source character cursor
 * (including consumption by controls as well as glyphs). The existing deliveredCharEnd
 * advances only for glyphs, so it is incomplete as a normalized event delivery boundary
 * (see the note in BuilderGlyphHandler). This class fixes that distinction in types and tests.
 * TextReplaySlice (C3) and BreakNode projection (M3b) share this seq space.
 * </p>
 */
public final class TextEventJournal {
	private int seq = 0;

	/**
	 * Delivery cursor for normalized events (source character end).
	 * Advances for glyphs and controls with source positions.
	 */
	private int cursor = 0;

	/** Returns the most recent event seq. */
	public int seq() {
		return this.seq;
	}

	/**
	 * Returns the delivery cursor for normalized events. Includes consumption of trailing spaces,
	 * line breaks, and SoftHyphen, so it may be ahead of deliveredCharEnd (glyphs only).
	 */
	public int cursor() {
		return this.cursor;
	}

	/** Start of a text run. */
	public void run(final int charOffset) {
		++this.seq;
	}

	/** Delivery of a glyph. */
	public void glyph(final int charStart, final int charEnd) {
		++this.seq;
		if (charStart >= 0) {
			this.cursor = Math.max(this.cursor, charEnd);
		}
	}

	/** Delivery of a control with a source position (space, line break, or SoftHyphen). */
	public void control(final int charOffset) {
		++this.seq;
		if (charOffset >= 0) {
			this.cursor = Math.max(this.cursor, charOffset + 1);
		}
	}

	/** Delivery of an inline quad (no source position). */
	public void inline() {
		++this.seq;
	}

	/** Line flush. */
	public void flush() {
		++this.seq;
	}
}
