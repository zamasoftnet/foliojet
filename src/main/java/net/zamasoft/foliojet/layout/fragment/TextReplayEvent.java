package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * Normalized events for shaped text (M3b Phase 1 / C3).
 *
 * <p>
 * Carries glyph/control/run boundaries finalized at the WordHyphenator output
 * (the BuilderGlyphHandler input) as values. Open paragraphs are handed off through this sequence,
 * not through the shaper's internal state (design from the codex consultation, 2026-07-17).
 * In Phase 1, ControlQuad carries inline quads and controls by reference
 * (value recipes arrive in Phase 3, together with removal of box dependencies from inline continuations).
 * </p>
 */
public sealed interface TextReplayEvent {
	/** Start of a text run. */
	record RunStart(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) implements TextReplayEvent {
	}

	/** A glyph (chars is an exclusively owned copy). */
	record Glyph(int charOffset, char[] chars, int gid) implements TextReplayEvent {
	}

	/** End of a text run. */
	record RunEnd() implements TextReplayEvent {
	}

	/** A control or inline quad (Phase 1: carried by reference). */
	record ControlQuad(TextControl quad) implements TextReplayEvent {
	}

	/** Line flush. */
	record Flush() implements TextReplayEvent {
	}
}
