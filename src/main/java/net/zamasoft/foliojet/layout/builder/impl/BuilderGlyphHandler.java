package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.RubyUnitBox;
import net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;

/**
 * Handles line breaks and resolves inline box widths.
 *  
 * @author MIYABE Tatsuhiko
 * @version $Id: BuilderGlyphHandler.java 1593 2019-12-03 07:02:17Z miyabe $
 */
public class BuilderGlyphHandler implements GlyphHandler {

	final Builder builder;

	private List<AbstractTextParams> textParamsStack = null;

	/**
	 * Flag indicating a line break on the next inline or text addition.
	 */
	private boolean toLineFeed = false, wrap;

	/**
	 * When an inline boundary lies between the previous and next characters, stores whether
	 * their nearest common ancestor allows wrapping.
	 *
	 * <p>Break opportunities are evaluated when the next character arrives. At that point,
	 * {@link #wrap} holds the value of the child containing that character. Using it directly
	 * would let a sibling's {@code white-space:nowrap} suppress breaks between items as well.
	 * Keep the first ancestor value across consecutive start tags; across consecutive end tags,
	 * update it to the (outer) ancestor value after each pop.</p>
	 */
	private Boolean boundaryWrap = null;
	private WritingMode progression;

	public BuilderGlyphHandler(Builder builder) {
		this.builder = builder;
		this.changeTextState(this.builder.getFlowBox().getBlockParams());
	}

	/** Passes the preceding paragraph lines retained by TextReplaySlice to the replay destination resolver. */
	public void seedBidiReplayPrefix(
			final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix prefix) {
		if (this.builder instanceof BlockBuilder blockBuilder) {
			blockBuilder.seedBidiReplayPrefix(prefix);
		}
	}

	public void startTextBox(AbstractTextParams params) {
		if (this.textParamsStack == null) {
			this.textParamsStack = new ArrayList<AbstractTextParams>();
		}
		this.textParamsStack.add(params);
		this.changeTextState(params);
	}

	public void endTextBox() {
		AbstractTextParams params = (AbstractTextParams) this.textParamsStack.remove(this.textParamsStack.size() - 1);
		if (this.textParamsStack.isEmpty()) {
			params = this.builder.getFlowBox().getBlockParams();
		} else {
			params = (AbstractTextParams) this.textParamsStack.get(this.textParamsStack.size() - 1);
		}
		this.changeTextState(params);
	}

	public void updateText() {
		AbstractTextParams params = this.builder.getFlowBox().getBlockParams();
		this.changeTextState(params);
	}

	private void changeTextState(AbstractTextParams params) {
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NORMAL:
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			this.wrap = true;
			break;
		default:
			throw new IllegalStateException();
		}
		this.progression = params.flow;
	}

	public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.journal.run(charOffset);
		this.builder.startTextRun(charOffset, fontStyle, fontMetrics);
	}

	/**
	 * End offset of the last source character delivered so far (M6b v3).
	 * Characters pending (undelivered) in the shaper lie after this offset. Stop tail replay of
	 * a split paragraph here to prevent duplicate delivery from the live pipeline.
	 *
	 * <p>
	 * Note: only {@link #glyph} advances this value. It does not reflect controls
	 * (spaces, newlines, SoftHyphen) or inline quads delivered after the last glyph.
	 * It is incomplete as a "normalized event delivery boundary" and cannot serve as the
	 * join key for an open paragraph (the complete C3 solution passes the normalized event
	 * sequence by value — ARCHITECTURE §5.9, codex consultation 2026-07-17).
	 * </p>
	 */
	private int deliveredCharEnd = 0;

	/**
	 * Shadow journal of boundary events (M3b Phase 0: observation without behavioral changes).
	 */
	private final TextEventJournal journal = new TextEventJournal();

	/**
	 * Returns the boundary event journal (M3b Phase 0).
	 */
	public TextEventJournal getJournal() {
		return this.journal;
	}

	/**
	 * Returns the end offset of delivered source characters (M6b v3).
	 *
	 * <p>
	 * M3c: while a K-P line-breaking session ({@code text-wrap-style: pretty}) accumulates
	 * and replays events, clamp this to the first source position of undelivered events so
	 * it marks the boundary "physically delivered to TextBuilder" (the default legacy path
	 * has no session, so the value remains unchanged).
	 * </p>
	 */
	public int getDeliveredCharEnd() {
		if (this.builder instanceof BlockBuilder blockBuilder) {
			return blockBuilder.clampDeliveredCharEnd(this.deliveredCharEnd);
		}
		return this.deliveredCharEnd;
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		this.journal.glyph(charOffset, charOffset + clen);
		if (charOffset >= 0) {
			this.deliveredCharEnd = Math.max(this.deliveredCharEnd, charOffset + clen);
		}
		this.builder.glyph(charOffset, ch, coff, clen, gid);
		this.boundaryWrap = null;
	}

	public void endTextRun() {
		this.builder.endTextRun();
	}

	public void control(final TextControl quad) {
		boolean consumesBoundary = true;
		if (quad instanceof InlineQuad) {
			// Inline box
			this.journal.inline();
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				// Inline start
				if (this.boundaryWrap == null) {
					this.boundaryWrap = Boolean.valueOf(this.wrap);
				}
				consumesBoundary = false;
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				final InlineBox inlineBox = inlineStartQuad.box;

				AbstractTextParams params = inlineBox.getTextParams();
				inlineStartQuad.advance = params.flow.isVertical()
						&& params.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
								params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP
							? inlineStartQuad.box.getFrame().getFrameBottom()
							: inlineStartQuad.box.getFrame().getFrameLineStart(this.progression);
				this.startTextBox(params);
			}
				break;

			case InlineQuad.INLINE_END:
				// Inline end
				InlineEndQuad inlineEndQuad = (InlineEndQuad) inlineQuad;
				this.endTextBox();
				this.boundaryWrap = Boolean.valueOf(this.wrap);
				consumesBoundary = false;

				final AbstractTextParams endParams = inlineEndQuad.box.getTextParams();
				inlineEndQuad.advance = endParams.flow.isVertical()
						&& endParams.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(endParams.flow, endParams.writingModeVariant,
								endParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP
							? inlineEndQuad.box.getFrame().getFrameTop()
							: inlineEndQuad.box.getFrame().getFrameLineEnd(this.progression);
				break;

			case InlineQuad.INLINE_REPLACED: {
				// Replaced inline
				InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
				LayoutUtils.calculateReplacedSize(this.builder, inlineReplacedQuad.box);
				inlineReplacedQuad.advance = inlineReplacedQuad.box.getLineExtent(this.progression);
			}
				break;

			case InlineQuad.INLINE_BLOCK:
				// Inline block
				inlineQuad.advance = inlineQuad.getBox().getLineExtent(this.progression);
				if (inlineQuad.getBox() instanceof RubyUnitBox rubyUnit) {
					// The Collector intercepts ruby unit characters, so they do not pass through glyph().
					// Advance the delivered end to the source end of the unit here
					// (2026-07-25: otherwise, tail replay of a split paragraph
					// stops "before the ruby", allowing the ruby range to be delivered
					// by both the live pipeline and replay).
					final int end = rubyUnit.getSourceEnd();
					if (end >= 0) {
						this.deliveredCharEnd = Math.max(this.deliveredCharEnd, end);
					}
				} else if (inlineQuad.getBox() instanceof WarichuUnitBox warichuUnit) {
					// Warichu is also a composite box whose characters the Collector intercepts, so
					// explicitly advance the source end as for ruby.
					final int end = warichuUnit.getSourceEnd();
					if (end >= 0) {
						this.deliveredCharEnd = Math.max(this.deliveredCharEnd, end);
					}
				}
				break;

			case InlineQuad.INLINE_ABSOLUTE:
				// Absolute positioning
				break;
			default:
				throw new IllegalStateException();
			}
		} else if (quad instanceof net.zamasoft.foliojet.layout.text.LeaderQuad) {
			// leader() L1: already shaped and assigned a minimum width; no calculation is needed here.
			this.journal.inline();
		} else {
			// Control code
			Control control = (Control) quad;
			this.journal.control(control.getCharOffset());
			switch (control.getControlChar()) {
			case '\n':
				this.toLineFeed = true;
				break;

			case '\t':
			case '\u0020':
			case SoftHyphen.CHAR:
				break;

			default:
				throw new IllegalStateException();
			}
		}
		this.builder.control(quad);
		if (consumesBoundary) {
			this.boundaryWrap = null;
		}
	}

	public void flush() {
		final boolean boundaryOrCurrentWrap = this.boundaryWrap == null
				? this.wrap : this.boundaryWrap.booleanValue();
		if (!boundaryOrCurrentWrap && !this.toLineFeed) {
			return;
		}
		this.toLineFeed = false;
		this.journal.flush();
		this.builder.flush();
	}
	
	public void close() {
		this.builder.flush();
	}
}
