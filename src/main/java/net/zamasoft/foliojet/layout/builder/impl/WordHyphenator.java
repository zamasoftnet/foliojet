package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.text.InlineParamsStack;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.pdfg2d.font.Font;
import net.zamasoft.pdfg2d.font.FontMetricsImpl;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.gc.text.pipeline.Hyphenator;

/**
 * Inserts intra-word hyphenation opportunities (CSS hyphens) as SoftHyphen.
 * <p>
 * Sits between CSSJTextUnitizer (kinsoku (line-breaking rules)) and BuilderGlyphHandler, buffering units (words)
 * delimited by flush(). With hyphens:auto and available language hyphenation patterns, computes break points using
 * the Liang algorithm ({@link Hyphenator}) and emits SoftHyphen + flush() during glyph replay. U+00AD in the source
 * ({@link Marker}) creates break points for both manual and auto; when present, it takes precedence over automatic
 * hyphenation (css-text-4).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class WordHyphenator implements FilterGlyphHandler, Cloneable {
	/**
	 * Marker for U+00AD in the source. StyledTextUnitizer emits it before shaping; WordHyphenator converts it to
	 * SoftHyphen and consumes it (it does not flow downstream). Treated as JOIN, so TextAtomizer does not split before
	 * or after it.
	 */
	static final class Marker extends TextControl {
		final int charOffset;

		Marker(int charOffset) {
			this.charOffset = charOffset;
		}

		public String getString() {
			return JOIN;
		}

		public double getAdvance() {
			return 0;
		}

		public String toString() {
			return "[SHY?]";
		}
	}

	private record RunStart(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
	}

	private record Glyph(int charOffset, char[] ch, byte clen, int gid, int wordOffset) {
	}

	private static final Object RUN_END = new Object();

	/** Minimum word length for applying hyphenation patterns (leftMin+rightMin). */
	private static final int MIN_WORD_LENGTH = 5;

	private GlyphHandler out;

	/**
	 * Pipeline-shared inline context (driven by CSSJTextUnitizer).
	 */
	private final InlineParamsStack inlineContext;

	/**
	 * Font of the preceding run (for generating U+00AD glyphs when not buffering).
	 */
	private FontStyle fontStyle;

	private FontMetrics fontMetrics;

	/**
	 * Buffered word: event sequence between flush() calls (RunStart/Glyph/RUN_END/TextControl).
	 */
	private List<Object> events = new ArrayList<Object>();

	private StringBuilder word = new StringBuilder();

	private boolean buffering = false;

	/**
	 * <b>Whether a text run is open downstream</b> (added 2026-08-03).
	 *
	 * <p>
	 * Buffering can start midway through a word: {@link #checkBuffering()} is called from {@link #glyph} as well as
	 * {@link #startTextRun}, so <b>buffering can begin with a run already open downstream</b>. If replay does not know
	 * this state, {@link #processBuffer()} opens the run twice.
	 *
	 * <p>
	 * The observed case was an opening parenthesis immediately after a {@code hyphens:auto} word: with {@code
	 * expanduser(}, a break-opportunity control event arrives while the run is open, and replaying the next buffer
	 * calls {@code startTextRun} twice, hitting a downstream assertion. Periods and commas do not trigger this (they
	 * create no break opportunity).
	 */
	private boolean outRunOpen = false;

	/**
	 * Word eligible for automatic hyphenation (all characters are letters; no replaced elements, etc.).
	 */
	private boolean autoBreaks = true;

	private boolean manualBreaks = false;

	private Hyphenator hyphenator = null;

	/** hyphenate-character at the start of buffering. null means auto. */
	private String hyphenateCharacter;

	/**
	 * Font at the start of buffering (for glyphs when a marker appears at word start).
	 */
	private FontStyle bufFontStyle;

	private FontMetrics bufFontMetrics;

	public WordHyphenator(InlineParamsStack inlineContext) {
		this.inlineContext = inlineContext;
	}

	public void setGlyphHandler(GlyphHandler glyphHandler) {
		this.out = glyphHandler;
	}

	private AbstractTextParams getParams() {
		return this.inlineContext.current();
	}

	public void startTextRun(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) {
		this.checkBuffering();
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
		if (this.buffering) {
			this.events.add(new RunStart(charOffset, fontStyle, fontMetrics));
		} else {
			this.out.startTextRun(charOffset, fontStyle, fontMetrics);
			this.outRunOpen = true;
		}
	}

	public void endTextRun() {
		if (this.buffering) {
			this.events.add(RUN_END);
		} else {
			this.out.endTextRun();
			this.outRunOpen = false;
		}
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		this.checkBuffering();
		if (this.buffering) {
			final char[] chars = new char[clen];
			System.arraycopy(ch, coff, chars, 0, clen);
			this.events.add(new Glyph(charOffset, chars, clen, gid, this.word.length()));
			for (int i = 0; i < clen; ++i) {
				if (!Character.isLetter(chars[i])) {
					this.autoBreaks = false;
				}
			}
			this.word.append(chars);
		} else {
			this.out.glyph(charOffset, ch, coff, clen, gid);
		}
	}

	public void control(TextControl quad) {
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START:
			case InlineQuad.INLINE_END:
				// Track inline context in the shared InlineParamsStack (driven upstream).
				break;

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_ABSOLUTE:
				// Do not hyphenate automatically if a replaced element or similar interrupts the word.
				this.autoBreaks = false;
				break;

			default:
				throw new IllegalStateException();
			}
		} else if (quad instanceof Marker) {
			final Marker marker = (Marker) quad;
			if (this.getParams().hyphens == AbstractTextParams.HYPHENS_NONE) {
				// With hyphens:none, U+00AD neither creates a break point nor draws.
				return;
			}
			if (this.buffering) {
				this.events.add(marker);
				this.manualBreaks = true;
			} else if (this.fontMetrics != null) {
				this.out.control(new SoftHyphen(marker.charOffset,
						hyphenText(marker.charOffset, this.fontStyle, this.fontMetrics,
								this.getParams().hyphenateCharacter)));
				this.out.flush();
			}
			return;
		}
		if (this.buffering) {
			this.events.add(quad);
		} else {
			this.out.control(quad);
		}
	}

	public void flush() {
		this.processBuffer();
		this.out.flush();
	}

	public void close() {
		this.processBuffer();
		this.out.close();
	}

	/**
	 * Sends ordinary glyph/control events to measurement too, leaving CSS width calculation to TextBuilder. Copies the
	 * word, hyphenation candidates, and delivery state before receiving pending clusters; delivers only the final word
	 * without finalizing automatic hyphenation points.
	 */
	void deliverPending(final GlyphHandler measurement,
			final java.util.function.Consumer<GlyphHandler> pending) {
		try {
			final WordHyphenator copy = (WordHyphenator) this.clone();
			copy.events = new ArrayList<>(this.events);
			copy.word = new StringBuilder(this.word);
			copy.out = measurement;
			pending.accept(copy);
			copy.processBuffer(false);
		} catch (final CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Decides when buffering starts. If hyphens:auto applies at the first glyph/run of a word (delimited by flush()),
	 * buffers subsequent events.
	 */
	private void checkBuffering() {
		if (this.buffering) {
			return;
		}
		final AbstractTextParams params = this.getParams();
		if (params.hyphens == AbstractTextParams.HYPHENS_AUTO && params.hyphenator != null) {
			this.buffering = true;
			this.autoBreaks = true;
			this.manualBreaks = false;
			this.hyphenator = params.hyphenator;
			this.hyphenateCharacter = params.hyphenateCharacter;
			this.bufFontStyle = this.fontStyle;
			this.bufFontMetrics = this.fontMetrics;
		}
	}

	private static final int[] NO_BREAKS = new int[0];

	/**
	 * Replays the buffered word, inserting break points (SoftHyphen+flush).
	 */
	private void processBuffer() {
		this.processBuffer(true);
	}

	private void processBuffer(final boolean completeWord) {
		if (!this.buffering) {
			return;
		}
		this.buffering = false;
		if (this.events.isEmpty()) {
			return;
		}
		int[] breaks = NO_BREAKS;
		if (completeWord && !this.manualBreaks && this.autoBreaks && this.word.length() >= MIN_WORD_LENGTH) {
			breaks = this.hyphenator.hyphenate(this.word.toString());
		}
		FontStyle fs = this.bufFontStyle;
		FontMetrics fm = this.bufFontMetrics;
		int runCharOffset = 0;
		// **Keep run-open state in a field, not a local variable** (2026-08-03).
		// When buffering starts midway through a word, a run is already open downstream
		// (see the Javadoc for {@link #outRunOpen}). Starting with a local variable
		// would open that run twice.
		boolean runPending = false;
		int bi = 0;
		for (int i = 0; i < this.events.size(); ++i) {
			final Object ev = this.events.get(i);
			if (ev instanceof RunStart) {
				final RunStart rs = (RunStart) ev;
				fs = rs.fontStyle();
				fm = rs.fontMetrics();
				runCharOffset = rs.charOffset();
				runPending = true;
			} else if (ev == RUN_END) {
				if (this.outRunOpen) {
					this.out.endTextRun();
					this.outRunOpen = false;
				}
				runPending = false;
			} else if (ev instanceof Glyph) {
				final Glyph g = (Glyph) ev;
				// Skip break points inside clusters, since they cannot be used.
				while (bi < breaks.length && breaks[bi] < g.wordOffset()) {
					++bi;
				}
				if (bi < breaks.length && breaks[bi] == g.wordOffset() && g.wordOffset() > 0) {
					++bi;
					if (this.outRunOpen) {
						this.out.endTextRun();
						this.outRunOpen = false;
					}
					this.out.control(new SoftHyphen(g.charOffset(),
							hyphenText(g.charOffset(), fs, fm, this.hyphenateCharacter)));
					this.out.flush();
				}
				if (!this.outRunOpen) {
					this.out.startTextRun(runPending ? runCharOffset : g.charOffset(), fs, fm);
					this.outRunOpen = true;
					runPending = false;
				}
				this.out.glyph(g.charOffset(), g.ch(), 0, g.clen(), g.gid());
			} else if (ev instanceof Marker) {
				final Marker marker = (Marker) ev;
				if (fm != null) {
					this.out.control(new SoftHyphen(marker.charOffset,
							hyphenText(marker.charOffset, fs, fm, this.hyphenateCharacter)));
					this.out.flush();
				}
			} else {
				this.out.control((TextControl) ev);
			}
		}
		// **A run remaining open here is valid** (2026-08-03). Previously,
		// {@code assert !runOpen} appeared at three sites, based on the false assumption
		// that a buffered word always ends with endTextRun.
		//
		// In fact, <b>an opening parenthesis immediately after a word delivers a break-opportunity control
		// event while the run is still open</b>, as in `expanduser(` (not with `.` or `,`).
		// Replay preserves upstream event order,
		// so this state matches upstream and downstream accepts it without trouble.
		// Observed: disabling asserts produces a one-page PDF with correct hyphenation
		// as `ex-pan-du-ser()`.
		//
		// The effect is conversion failure with AssertionError only in environments running with -ea;
		// every development and test layer fails. The first case in wave 0 of importing full-scale
		// documents (official Python documentation) triggered it. A sweep of 20 million documents
		// never did, because the generator does not produce `word + opening parenthesis`.
		// Regression: files/unittest/0450-hyphens/word-then-paren.html.
		this.events.clear();
		this.word.setLength(0);
	}

	/**
	 * Checks whether the break-symbol glyph is fullwidth. Since it goes at a Latin-text line end, an advance exceeding
	 * 0.5 em indicates selection of a CJK fullwidth face.
	 */
	private static boolean isFullWidth(final FontStyle fontStyle, final FontMetrics fontMetrics, final char c) {
		final double size = fontStyle.getSize();
		if (size <= 0) {
			return false;
		}
		final Font font;
		if (fontMetrics instanceof FontMetricsImpl) {
			font = ((FontMetricsImpl) fontMetrics).getFont();
		} else {
			font = fontMetrics.getFontSource().createFont();
		}
		final TextImpl probe = new TextImpl(0, fontStyle, fontMetrics);
		probe.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c));
		return probe.getAdvance() > size * 0.5;
	}

	/**
	 * Creates the hyphen glyph materialized at line end at a break point. TextImpl is mutable (xadvances, etc.), so
	 * returns a new instance for each break point.
	 */
	private static TextImpl hyphenText(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics,
			String hyphenateCharacter) {
		if (hyphenateCharacter == null) {
			char hc = '\u2010'; // HYPHEN
			if (!fontMetrics.getFontSource().canDisplay(hc) || isFullWidth(fontStyle, fontMetrics, hc)) {
				// **Fall back to U+002D in fonts where U+2010 is fullwidth** (2026-08-31).
				// Japanese fonts (such as NotoSerifJP) assign this code point a fullwidth (1 em) glyph,
				// so the default leaves almost 1 em between a Latin word and its line-end hyphen.
				// U+002D is about 0.35 em and fits correctly.
				hc = '-';
			}
			hyphenateCharacter = String.valueOf(hc);
		}
		final Font font;
		if (fontMetrics instanceof FontMetricsImpl) {
			font = ((FontMetricsImpl) fontMetrics).getFont();
		} else {
			font = fontMetrics.getFontSource().createFont();
		}
		final TextImpl text = new TextImpl(charOffset, fontStyle, fontMetrics);
		final char[] chars = hyphenateCharacter.toCharArray();
		for (int i = 0; i < chars.length;) {
			final int codePoint = Character.codePointAt(chars, i);
			final byte length = (byte) Character.charCount(codePoint);
			text.appendGlyph(chars, i, length, font.toGID(codePoint));
			i += length;
		}
		if (text.getGlyphCount() > 0) {
			text.pack();
		}
		text.materializedHyphen = true;
		return text;
	}
}
