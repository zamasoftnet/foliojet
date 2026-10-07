package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.RunCollector;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * Self-contained shaping plus punctuation trimming within runs (2026-08-01, text pipeline consolidation).
 *
 * <p>
 * Consolidates anonymous collectors duplicated in three places (ruby units, footnote labels,
 * and {@code leader()} patterns) into pdfg2d's {@link RunCollector}
 * (generic shape → run collection) plus this wrapper (foliojet-specific punctuation trimming
 * within runs; Japanese spacing adjustment T1a/T1b).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TrimmedRuns {
	private TrimmedRuns() {
		// utility
	}

	/**
	 * Shapes a string independently in the current style, applies punctuation trimming within runs,
	 * and returns the result.
	 *
	 * @param fontManager font manager
	 * @param fontStyle   style used for shaping
	 * @param text        text (may be empty)
	 * @param charOffset  source character offset of the first character ({@code -1} for generated content)
	 * @param trimOff     whether {@code text-spacing-trim: space-all} (trimming disabled) applies
	 * @return packed run sequence
	 */
	public static TextImpl[] shape(final FontManager fontManager, final FontStyle fontStyle, final String text,
			final int charOffset, final boolean trimOff) {
		final TextImpl[] runs = RunCollector.shape(fontManager, fontStyle, text, charOffset);
		if (!trimOff) {
			for (final TextImpl run : runs) {
				// Japanese spacing adjustment T1a/T1b: within-run punctuation trimming moved from the font layer
				JapaneseSpacingResolver.applyRunTrims(run);
			}
		}
		return runs;
	}
}
