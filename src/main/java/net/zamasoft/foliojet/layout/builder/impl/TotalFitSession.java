package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.zamasoft.foliojet.layout.box.content.BreakToken;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;
import net.zamasoft.pdfg2d.gc.text.pipeline.TotalFit;

/**
 * An opt-in session for Knuth-Plass line breaking (CSS {@code text-wrap-style: pretty})
 * (introduced on 2026-07-23, M3c increment 3; consolidated from the proprietary
 * {@code text.line-breaker} property into CSS on 2026-07-25).
 *
 * <p>
 * Buffers shaped events for the TextBuilder construction session (roughly a paragraph) from
 * {@code requireTextBlock()} to {@code endTextBlock()} without delivering them. At session end,
 * {@link TotalFitProjection} selects breakpoints, then the buffered events replay into the existing
 * {@link TextBuilder}. TextBuilder remains solely responsible for physical line construction;
 * this session only supplies "which flush causes a line break" ({@link TotalFitProjection.Plan}).
 * </p>
 *
 * <p>
 * Eligibility is conservative. If any condition fails, replay verbatim with no plan (plan=null),
 * falling back to output identical to legacy:
 * </p>
 * <ul>
 * <li>At start: the paragraph's computed value is {@code text-wrap-style: pretty};
 * this is not a resumption after a page break ({@code BreakToken.midFlow/midLine});
 * horizontal writing ({@code WritingMode.TB}; vertical writing is excluded in the initial version
 * because top alignment in {@code locateLine()} changes the effective width per line);
 * no {@code ::first-line}; no float exclusion area that can affect the paragraph at or below its
 * starting Y; neither {@code white-space: pre/pre-wrap} nor {@code word-wrap: break-word};
 * and a positive, finite line width.</li>
 * <li>During recording (abort immediately, replay buffered events through legacy, then pass through):
 * a tab; an inline replaced element; an inline block (including ruby); inline absolute positioning;
 * an inline boundary with an advance (frame width); {@code addBound} (a float or absolute positioning
 * within the paragraph; it reads TextBuilder's actual state, so finalize before that read);
 * or exceeding the event limit (10000).</li>
 * </ul>
 *
 * <p>
 * If a page break between lines ({@code BreakableBuilder.flush()}) replaces TextBuilder during
 * optimized replay, the plan remains bound to the original TextBuilder instance, so the remaining
 * events naturally use legacy greedy layout (the plan's coordinate system does not get mixed
 * with the replay mechanism after the page break).
 * </p>
 */
final class TotalFitSession {

	/** Maximum number of buffered events (fall back to legacy when exceeded). */
	private static final int EVENT_LIMIT = 10000;

	private enum State {
		RECORDING, REPLAYING, DONE
	}

	/** A recorded delivery event (delivered in the same order during replay). */
	private sealed interface Recorded {
		record Run(FontStyle fontStyle, FontMetrics fontMetrics) implements Recorded {
		}

		record Glyph(int charOffset, char[] cluster, byte clen, int gid) implements Recorded {
		}

		record RunEnd() implements Recorded {
			static final RunEnd INSTANCE = new RunEnd();
		}

		record Ctrl(TextControl quad) implements Recorded {
		}

		record Flush() implements Recorded {
			static final Flush INSTANCE = new Flush();
		}
	}

	private final BlockBuilder builder;

	/** TextBuilder at session start (the plan is bound to this instance). */
	private final TextBuilder textBuilder;

	private final double lineSize;

	private final double textIndent;

	private final TotalFit.LastLinePolicy lastLine;

	private final List<Recorded> events = new ArrayList<>();

	private final List<TotalFitProjection.Piece> pieces = new ArrayList<>();

	private State state = State.RECORDING;

	/**
	 * Number of replayed events (0 during recording). {@link #clampDeliveredCharEnd} uses this
	 * to find the delivery boundary of events that have physically reached TextBuilder.
	 */
	private int replayIndex = 0;

	/**
	 * Maximum source character end of glyphs delivered during replay (-1 if none have been delivered).
	 * Same semantics as legacy {@code deliveredCharEnd} (advances only on glyphs).
	 */
	private int maxDeliveredGlyphEnd = -1;

	// ---- Width measurement mirror (uses TextBuilder's calculation to obtain widths for projection) ----

	/** Inline TextParams stack (equivalent to TextBuilder.textParamStack). */
	private final List<AbstractTextParams> paramsStack = new ArrayList<>();

	private final AbstractTextParams baseParams;

	private double letterSpacing;

	private boolean collapseSpaces;

	private FontStyle fontStyle;

	private FontMetrics fontMetrics;

	private TextImpl mirrorText;

	/**
	 * Japanese spacing T1a: tracks punctuation trimming within a run (paragraphs with autospace
	 * are excluded from pretty, so flags are always 0 and only trim decisions use them).
	 */
	private final net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker spacing = //
			new net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker();

	private double pendingBoxWidth = 0;

	private int flushOrdinal = 0;

	private TotalFitSession(final BlockBuilder builder, final TextBuilder textBuilder, final BlockParams params,
			final double lineSize, final double textIndent) {
		this.builder = builder;
		this.textBuilder = textBuilder;
		this.lineSize = lineSize;
		this.textIndent = textIndent;
		this.lastLine = params.textAlignLast == AbstractLineParams.TEXT_ALIGN_JUSTIFY
				? TotalFit.LastLinePolicy.JUSTIFY
				: TotalFit.LastLinePolicy.RAGGED;
		this.baseParams = params;
		this.applyTextState(params);
		// Japanese spacing T1b: trim policy (flags remain 0 because paragraphs
		// with autospace are excluded from pretty).
		this.spacing.setTrimOff(params.textSpacingTrimOff);
	}

	/**
	 * Checks eligibility at session start and returns a new session if eligible
	 * (null otherwise, for conventional direct delivery).
	 */
	static TotalFitSession tryBegin(final BlockBuilder builder, final TextBuilder textBuilder,
			final BreakToken breakToken) {
		if (breakToken.midFlow()) {
			// Resumption after a page break, a column break, or within the same flow (including midLine).
			return null;
		}
		final LayoutContext.Flow flow = builder.getFlow();
		final BlockParams params = flow.box.getBlockParams();
		if (params.textWrapStyle != AbstractTextParams.TEXT_WRAP_STYLE_PRETTY) {
			// Opt in via CSS text-wrap-style: pretty (2026-07-25).
			// The default (auto) is greedy. K-P applies to a paragraph, so examine only
			// the computed value of the block that establishes the paragraph.
			return null;
		}
		if (params.flow != WritingMode.TB) {
			// Exclude vertical writing in the initial version: top alignment (CL01 adjustment in locateLine)
			// changes the effective width per line.
			return null;
		}
		if (params.firstLineStyle != null) {
			// ::first-line changes the style of the first line only.
			return null;
		}
		if (!textStateSupported(params)) {
			return null;
		}
		// Require no float exclusion area that can affect the paragraph at or below its starting Y (codex review:
		// locateLine() is not a width query; it updates four state values. A float can be involved even
		// when the width is unchanged, e.g., setting maxPageSize for a float further down).
		if (builder.toAddFloatings != null && !builder.toAddFloatings.isEmpty()) {
			return null;
		}
		if (builder.floatings != null && !builder.floatings.isEmpty()) {
			// floatings is sorted by pageEnd ascending, so the last entry has the maximum pageEnd.
			final LayoutContext.Floating last = builder.floatings.get(builder.floatings.size() - 1);
			if (LayoutUtils.compare(last.pageEnd, builder.pageAxis) > 0) {
				return null;
			}
		}
		final double lineSize = flow.box.getLineSize();
		final double indent = flow.box.getTextIndent();
		if (!(lineSize > 0) || Double.isInfinite(lineSize) || !(lineSize - indent > 0)) {
			return null;
		}
		final TotalFitSession session = new TotalFitSession(builder, textBuilder, params, lineSize, indent);
		if (LayoutUtils.isNone(session.letterSpacing)) {
			return null;
		}
		return session;
	}

	/** Checks whether white-space/word-wrap is within the initial version's supported range. */
	private static boolean textStateSupported(final AbstractTextParams params) {
		// Japanese spacing A2 -> enabled by default (2026-08-01): do not reject autospace by property.
		// With text-autospace defaulting to normal, a property check rejected every paragraph, disabling
		// pretty even for purely English or purely Japanese text. Refined this into a content-based check:
		// recordGlyph calls abortToLegacy when a gap actually occurs.
		// (Paragraphs with gaps remain excluded from K-P, as in P1, recommendation Q5.)
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			// Paths that preserve spaces have different collapse rules at the start and end of lines.
			return false;
		case AbstractTextParams.WHITE_SPACE_NORMAL:
		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			// With wrapping enabled: break-word is unsupported because it uses
			// mid-sequence splitting in TextBuilder.glyph().
			if (params.wordWrap == AbstractTextParams.WORD_WRAP_BREAK_WORD) {
				return false;
			}
			return true;
		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			return true;
		default:
			return false;
		}
	}

	private void applyTextState(final AbstractTextParams params) {
		this.collapseSpaces = params.whiteSpace != AbstractTextParams.WHITE_SPACE_PRE
				&& params.whiteSpace != AbstractTextParams.WHITE_SPACE_PRE_WRAP;
		this.letterSpacing = LayoutUtils.computeLength(params.letterSpacing,
				this.builder.getFlowBox().getLineSize());
		// Enabled by default (2026-08-01): give the mirror tracker autospace flags as well, so
		// recordGlyph's gapBefore check (abortToLegacy when a gap actually occurs)
		// tracks the TextBuilder side (changeTextState) at the same granularity.
		this.spacing.setFlags(params.textAutospace);
	}

	/** Returns true while recording. */
	boolean recording() {
		return this.state == State.RECORDING;
	}

	/**
	 * Returns the delivery boundary of source characters that have physically reached TextBuilder.
	 *
	 * <p>
	 * {@code BuilderGlyphHandler.deliveredCharEnd} advances on delivery from upstream (the shaper),
	 * so it advances even while this session buffers events. However, tail replay for a split
	 * paragraph (removed on 2026-10-07) used this value as the boundary beyond which live delivery
	 * would supply content. Without clamping to the first source position of undelivered events
	 * during buffering or replay, a page break between lines during replay caused duplicate delivery
	 * from tail replay and this session's remaining events. The source position of the first
	 * undelivered glyph is that boundary (earlier content predates the session or has been delivered
	 * by replay).
	 * </p>
	 */
	int clampDeliveredCharEnd(final int deliveredCharEnd) {
		if (this.state == State.DONE) {
			return deliveredCharEnd;
		}
		for (int i = this.replayIndex; i < this.events.size(); ++i) {
			if (this.events.get(i) instanceof Recorded.Glyph glyph && glyph.charOffset() >= 0) {
				// Undelivered glyphs remain: as in legacy, the boundary is the end
				// of the delivered glyphs. If no glyph has been replayed yet,
				// clamp to the start of the first undelivered glyph
				// (an upper bound on the value before the session started).
				return Math.min(deliveredCharEnd,
						this.maxDeliveredGlyphEnd >= 0 ? this.maxDeliveredGlyphEnd : glyph.charOffset());
			}
		}
		return deliveredCharEnd;
	}

	/**
	 * Checks the event limit and aborts to legacy if exceeded.
	 *
	 * @return true if recording can continue
	 */
	private boolean checkCapacity() {
		if (this.events.size() < EVENT_LIMIT) {
			return true;
		}
		this.abortToLegacy();
		return false;
	}

	/**
	 * Records the start of a text run.
	 *
	 * @return true if recorded (if false, the caller delivers it directly)
	 */
	boolean recordRun(final FontStyle fontStyle, final FontMetrics fontMetrics) {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		this.events.add(new Recorded.Run(fontStyle, fontMetrics));
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
		this.mirrorText = null;
		return true;
	}

	/**
	 * Records a glyph (defensively copies cluster characters because the upstream buffer is reused).
	 * Computes the width with a mirror TextImpl using the same calculation as TextBuilder.glyph().
	 */
	boolean recordGlyph(final int charOffset, final char[] ch, final int coff, final byte clen, final int gid) {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		if (this.fontStyle == null || this.fontMetrics == null) {
			this.abortToLegacy();
			return false;
		}
		if (this.mirrorText == null) {
			this.mirrorText = new TextImpl(charOffset, this.fontStyle, this.fontMetrics);
			this.mirrorText.setLetterSpacing(this.letterSpacing);
		}
		final char[] cluster = Arrays.copyOfRange(ch, coff, coff + clen);
		// Enabled by default (2026-08-01): exclude only paragraphs where an autospace gap actually occurs
		// from K-P (K-P pair gap discount support remains P1, recommendation Q5).
		// Checking actual occurrences instead of properties preserves pretty for purely English
		// or purely Japanese paragraphs even with text-autospace defaulting to normal.
		if (this.spacing.gapBefore(cluster, 0, this.fontStyle.getSize()) != 0) {
			this.abortToLegacy();
			return false;
		}
		// T1a: include punctuation trimming within a run (moved from the font layer) in candidate widths.
		// (Only trim, since paragraphs with gaps are excluded from pretty. The TextBuilder tracker
		// applies it through xadvance at final bind. As with the old font-layer kern implementation,
		// restoration at a breakpoint is not modeled.)
		final double trim = this.spacing.trimBefore(cluster, 0, gid, this.mirrorText, this.fontMetrics,
				this.fontStyle.getSize(), this.fontStyle);
		// Compute candidate widths with the same CSS width formula (GlyphMeasureStep) as TextBuilder.glyph().
		// This prevents drift among the formulas in the three width accounting paths.
		// gap=0 because gap!=0 has already exited the K-P path above.
		final double advance = new net.zamasoft.foliojet.layout.text.GlyphMeasureStep(
				this.mirrorText.appendGlyph(cluster, 0, clen, gid), this.letterSpacing, 0, trim).totalAdvance();
		this.pendingBoxWidth += advance;
		this.spacing.glyphAdded(this.mirrorText, this.fontStyle.getSize(), cluster, 0, clen, gid);
		this.events.add(new Recorded.Glyph(charOffset, cluster, clen, gid));
		if (this.mirrorText.getGlyphCount() > 10000) {
			// Split the mirror at the same point as TextBuilder.glyph() splits very long runs
			// (so kerning stops at the same split point).
			this.mirrorText = null;
		}
		return true;
	}

	/** Records the end of a text run. */
	boolean recordRunEnd() {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		this.events.add(Recorded.RunEnd.INSTANCE);
		this.mirrorText = null;
		return true;
	}

	/**
	 * Records a control (whitespace, line break, soft hyphen, or inline boundary).
	 * For controls unsupported in the initial version (tabs, replaced elements, inline blocks, ruby,
	 * inline absolute positioning, or inline boundaries with frame width), aborts to legacy and
	 * returns false (the caller delivers directly).
	 */
	boolean recordControl(final TextControl quad) {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		// Enabled by default (2026-08-01): break the mirror tracker's pair state using the same rules
		// as TextBuilder.control (only zero-width inline starts/ends preserve pairs).
		// Without this, Japanese/Latin sequences separated by whitespace, such as "あ text",
		// make gapBefore detect a false gap and abort K-P unnecessarily.
		if (!(quad instanceof InlineQuad inlineQuad
				&& (inlineQuad.getType() == InlineQuad.INLINE_START
						|| inlineQuad.getType() == InlineQuad.INLINE_END)
				&& inlineQuad.getAdvance() == 0)) {
			this.spacing.reset();
		}
		if (quad instanceof Control control) {
			switch (control.getControlChar()) {
			case SoftHyphen.CHAR: {
				final SoftHyphen softHyphen = (SoftHyphen) control;
				this.emitPendingBox();
				this.pieces.add(new TotalFitProjection.Piece.Hyphen(softHyphen.getText().getAdvance()));
				break;
			}

			case '\n':
				this.emitPendingBox();
				this.pieces.add(new TotalFitProjection.Piece.LineFeed());
				break;

			case ' ': {
				if (!this.collapseSpaces) {
					// Eligibility should already have excluded this, but abort defensively.
					this.abortToLegacy();
					return false;
				}
				final WhiteSpace whiteSpace = (WhiteSpace) control;
				this.emitPendingBox();
				this.pieces.add(new TotalFitProjection.Piece.Space(whiteSpace.getAdvance()));
				break;
			}

			case '\t':
			default:
				// Tab width depends on the line position (TextBuilder.tabAdvance(), tab-size).
				this.abortToLegacy();
				return false;
			}
		} else if (quad instanceof InlineQuad inlineQuad) {
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				final InlineQuad.InlineStartQuad startQuad = (InlineQuad.InlineStartQuad) inlineQuad;
				final AbstractTextParams params = startQuad.box.getTextParams();
				if (inlineQuad.getAdvance() != 0 || !textStateSupported(params)) {
					// An inline with frame width changes that width when regenerated at a line break.
					this.abortToLegacy();
					return false;
				}
				this.paramsStack.add(params);
				this.applyTextState(params);
				if (LayoutUtils.isNone(this.letterSpacing)) {
					this.abortToLegacy();
					return false;
				}
				break;
			}

			case InlineQuad.INLINE_END: {
				if (inlineQuad.getAdvance() != 0 || this.paramsStack.isEmpty()) {
					this.abortToLegacy();
					return false;
				}
				this.paramsStack.remove(this.paramsStack.size() - 1);
				final AbstractTextParams params = this.paramsStack.isEmpty() ? this.baseParams
						: this.paramsStack.get(this.paramsStack.size() - 1);
				this.applyTextState(params);
				break;
			}

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_ABSOLUTE:
			default:
				// Replaced elements, inline blocks (including ruby), and absolute positioning.
				this.abortToLegacy();
				return false;
			}
		} else {
			this.abortToLegacy();
			return false;
		}
		this.events.add(new Recorded.Ctrl(quad));
		return true;
	}

	/** Records a flush (breakpoint candidate). */
	boolean recordFlush() {
		if (!this.recording() || !this.checkCapacity()) {
			return false;
		}
		this.emitPendingBox();
		final double stretch = this.fontStyle != null ? this.fontStyle.getSize() * 0.5 : 0;
		this.pieces.add(new TotalFitProjection.Piece.Flush(this.flushOrdinal, stretch));
		this.events.add(Recorded.Flush.INSTANCE);
		++this.flushOrdinal;
		return true;
	}

	private void emitPendingBox() {
		if (this.pendingBoxWidth != 0) {
			this.pieces.add(new TotalFitProjection.Piece.Box(this.pendingBoxWidth));
			this.pendingBoxWidth = 0;
		}
	}

	/**
	 * Stops buffering and replays buffered events through the legacy path. The caller delivers
	 * subsequent events directly (exactly the same behavior as legacy).
	 * Call before external processing that reads TextBuilder's actual state, such as
	 * {@code addBound} for floats or absolute positioning.
	 */
	void abortToLegacy() {
		if (!this.recording()) {
			return;
		}
		this.replay(null);
	}

	/**
	 * Ends the session ({@code endTextBlock()}). If it remained eligible to the end, selects breakpoints
	 * and replays; if selection fails, replays verbatim (= identical to legacy). Does nothing on
	 * reentry during replay ({@code endTextBlock()} caused by a page break between lines).
	 */
	void finishSession() {
		if (!this.recording()) {
			return;
		}
		this.emitPendingBox();
		final TotalFitProjection.Plan plan = TotalFitProjection.plan(this.pieces, this.lineSize - this.textIndent,
				this.lineSize, parameters(this.lastLine));
		this.replay(plan);
	}

	/**
	 * CSS parameters for {@link TotalFit} (conventional values for the second pass of plain TeX).
	 * If tolerance is too loose, almost every breakpoint becomes feasible, exploding the candidate
	 * set in {@code TotalFit}, which does not remove active nodes (see the stretch/shrink model
	 * note in {@link TotalFitProjection}).
	 */
	private static TotalFit.Parameters parameters(final TotalFit.LastLinePolicy lastLine) {
		return new TotalFit.Parameters(200, 10, 10000, 10000, 5000, lastLine);
	}

	/**
	 * Delivers buffered events. If plan is null, replays verbatim (the same call sequence as legacy);
	 * otherwise, binds the plan to the original TextBuilder and delivers. Only flush line-break
	 * decisions follow the plan; all physical line construction uses existing code. If a page break
	 * between lines replaces TextBuilder, the plan stays on the dead instance, so the remaining
	 * events naturally use legacy layout.
	 */
	private void replay(final TotalFitProjection.Plan plan) {
		// Note: keep this.builder.textSession non-null until replay finishes:
		// page-break processing during replay consults the delivery boundary (clampDeliveredCharEnd).
		// State guards prevent reentry into all record methods,
		// abort, and finish.
		this.state = State.REPLAYING;
		if (plan != null) {
			this.textBuilder.totalFitPlan = plan;
		}
		try {
			int ordinal = 0;
			for (int i = 0; i < this.events.size(); ++i) {
				this.replayIndex = i;
				// Read again each time because a page break between lines replaces TextBuilder.
				switch (this.events.get(i)) {
				case Recorded.Run run -> this.builder.textBuilder.startTextRun(run.fontStyle(), run.fontMetrics());
				case Recorded.Glyph glyph -> {
					if (glyph.charOffset() >= 0) {
						this.maxDeliveredGlyphEnd = Math.max(this.maxDeliveredGlyphEnd,
								glyph.charOffset() + glyph.clen());
					}
					this.builder.textBuilder.glyph(glyph.charOffset(), glyph.cluster(), 0, glyph.clen(), glyph.gid());
				}
				case Recorded.RunEnd runEnd -> this.builder.textBuilder.endTextRun();
				case Recorded.Ctrl ctrl -> this.builder.textBuilder.control(ctrl.quad());
				case Recorded.Flush flush -> {
					if (plan != null) {
						plan.arriveFlush(ordinal);
					}
					++ordinal;
					// Go through BreakableBuilder's page-break mechanism between lines
					// (the same path as legacy live delivery).
					this.builder.flush();
				}
				}
			}
			this.replayIndex = this.events.size();
		} finally {
			if (plan != null && this.textBuilder.totalFitPlan == plan) {
				this.textBuilder.totalFitPlan = null;
			}
			this.state = State.DONE;
			this.events.clear();
			this.pieces.clear();
			this.builder.textSession = null;
		}
	}
}
