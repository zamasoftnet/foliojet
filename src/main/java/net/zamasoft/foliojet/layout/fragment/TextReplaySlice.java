package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.builder.impl.BuilderGlyphHandler;
import net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * An immutable slice of shaped-text events (M3b Phase 1 / C3).
 *
 * <p>
 * record captures the sequence delivered to GlyphHandler by restyling remainder lines,
 * and replay reproduces the identical call sequence. Phase 1 uses the box side
 * (TextBlockBox's remainder lines) as the oracle, replacing only the carrier with an event sequence.
 * Capture and replay are identical by construction, so behavior is unchanged.
 * </p>
 */
public final class TextReplaySlice {
	private final List<TextReplayEvent> events;
	private final BidiReplayPrefix bidiReplayPrefix;

	/** consume-once: true if already replayed (same convention as P0 ranges). */
	private boolean consumed;

	private TextReplaySlice(final List<TextReplayEvent> events, final BidiReplayPrefix bidiReplayPrefix) {
		this.events = events;
		this.bidiReplayPrefix = bidiReplayPrefix;
	}

	/** Returns the event count. */
	public int size() {
		return this.events.size();
	}

	/**
	 * Captures the sequence delivered by producer to GlyphHandler (through close).
	 */
	public static TextReplaySlice record(final Consumer<GlyphHandler> producer) {
		return record(producer, BidiReplayPrefix.EMPTY);
	}

	/** For replay from mid-paragraph, also retains preceding, already placed lines as UBA context. */
	public static TextReplaySlice record(final Consumer<GlyphHandler> producer,
			final List<AbstractLineBox> bidiReplayPrefix) {
		return record(producer, BidiReplayPrefix.EMPTY.append(bidiReplayPrefix));
	}

	public static TextReplaySlice record(final Consumer<GlyphHandler> producer,
			final BidiReplayPrefix bidiReplayPrefix) {
		final List<TextReplayEvent> events = new ArrayList<>();
		producer.accept(new GlyphHandler() {
			public void startTextRun(final int charOffset, final FontStyle fontStyle,
					final FontMetrics fontMetrics) {
				events.add(new TextReplayEvent.RunStart(charOffset, fontStyle, fontMetrics));
			}

			public void endTextRun() {
				events.add(new TextReplayEvent.RunEnd());
			}

			public void glyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
				final char[] chars = new char[clen];
				System.arraycopy(ch, coff, chars, 0, clen);
				events.add(new TextReplayEvent.Glyph(charOffset, chars, gid));
			}

			public void control(final TextControl control) {
				events.add(new TextReplayEvent.ControlQuad(control));
			}

			public void flush() {
				events.add(new TextReplayEvent.Flush());
			}

			public void close() {
				// Reproduce termination through close on the replay side.
			}
		});
		return new TextReplaySlice(Collections.unmodifiableList(events), bidiReplayPrefix);
	}

	/**
	 * Replays the captured sequence to GlyphHandler and terminates with close.
	 * consume-once: a second replay would supply glyphs twice, so it is prohibited.
	 * Non-quad Controls (WhiteSpace/Tab/LineBreak/SoftHyphen) are immutable values containing only final
	 * fields,
	 * so carrying references is equivalent to carrying values.
	 * Only InlineQuad (inline continuation) retains mutable references;
	 * OpenChain recipes (Phase 3c) convert these to values.
	 */
	public void replay(final GlyphHandler gh) {
		if (this.consumed) {
			throw new IllegalStateException("TextReplaySlice は consume-once");
		}
		this.consumed = true;
		if (gh instanceof BuilderGlyphHandler builderGlyphHandler) {
			builderGlyphHandler.seedBidiReplayPrefix(this.bidiReplayPrefix);
		}
		for (final TextReplayEvent event : this.events) {
			switch (event) {
			case TextReplayEvent.RunStart(final int charOffset, final FontStyle fontStyle,
					final FontMetrics fontMetrics) -> gh.startTextRun(charOffset, fontStyle, fontMetrics);
			case TextReplayEvent.RunEnd() -> gh.endTextRun();
			case TextReplayEvent.Glyph(final int charOffset, final char[] chars, final int gid) ->
				gh.glyph(charOffset, chars, 0, (byte) chars.length, gid);
			case TextReplayEvent.ControlQuad(final TextControl quad) -> gh.control(quad);
			case TextReplayEvent.Flush() -> gh.flush();
			}
		}
		gh.close();
	}
}
