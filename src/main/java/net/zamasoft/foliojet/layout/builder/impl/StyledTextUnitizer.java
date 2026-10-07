package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.text.InlineParamsStack;

import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.impl.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.RubyUnitBox;
import net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.LayoutFontStyle;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.util.TextUtils;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.foliojet.layout.text.Quad;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;
import net.zamasoft.pdfg2d.gc.text.layout.control.LineBreak;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;

// TODO Collapse spaces at the end of a block.
public class StyledTextUnitizer {

	private final Builder builder;

	private final List<AbstractTextParams> textParamsStack = new ArrayList<AbstractTextParams>();

	/**
	 * Stack of InlineEndQuad instances to be used.
	 */
	private final List<Quad> inlineQuadStack = new ArrayList<Quad>();

	private BuilderGlyphHandler gh;

	/**
	 * Space collapsing, LF processing, and wrapping.
	 */
	private boolean collapseSpaces, lineFeed;
	/**
	 * The preceding character.
	 */
	private char followingChar;

	/**
	 * Space width from the word-spacing property.
	 */
	private double wordSpacing;

	private TextShaper textShaper = null;
	private CSSJTextUnitizer textUnitizer;

	/** Character events retained until the tate-chu-yoko character count is known. */
	private record TextCombineChars(int charOffset, char[] chars, boolean lineFeed) {
	}

	private List<TextCombineChars> textCombineChars = null;
	private int textCombineCharCount;

	/**
	 * Ruby unit buffer (annotated-text approach, specification decision on 2026-07-25).
	 * Created at the start of a ruby container (INLINE with {@code rubyRole == RUBY_CONTAINER}); delivers units and is
	 * discarded at container end. Intercepts inline and character events in the ruby range while non-null.
	 */
	private RubyUnitCollector rubyCollector = null;

	/**
	 * Destination for tate-chu-yoko characters inside ruby base text (2026-10-06, jigensha report). Ruby units retain
	 * only characters and discard the tate-chu-yoko inline block, so characters disappeared ("2ちゃんねる" became
	 * "にちゃんねる"). Convert tate-chu-yoko characters to fullwidth and pass them to the parent text processor collecting
	 * base text (an approximation without synthesis that compresses them into 1 em).
	 */
	private StyledTextUnitizer rubyTextCombineTarget = null;

	/** Whether ruby base text is currently being collected. */
	public boolean isCollectingRuby() {
		return this.rubyCollector != null;
	}

	/**
	 * Converts characters from this text processor (the tate-chu-yoko inline block inside ruby base text) to fullwidth
	 * and passes them into {@code parent}'s ruby base text.
	 */
	public void forwardTextCombineToRuby(final StyledTextUnitizer parent) {
		this.rubyTextCombineTarget = parent;
		this.textCombineChars = null;
	}

	private WarichuCollector warichuCollector = null;

	/** Preceding ruby whose overhang decision waits until the adjacent character toward line end is known. */
	private RubyUnitBox pendingRubyEnd = null;
	private InlineQuad pendingRubyQuad = null;

	/**
	 * Controls retained instead of delivered to the line until trailing overhang is determined (the ruby box and
	 * subsequent inline starts/ends; 2026-10-06). Lines count length using the advance at control receipt, so
	 * expanding the box afterward omitted that extent; during line layout, the expansion became extra space for the
	 * next line (jigensha report: a line following a br after ruby longer than its base text became about 2.5 mm
	 * longer when it contained punctuation).
	 */
	private final List<Quad> pendingRubyControls = new ArrayList<Quad>();

	/** Delivers a control to the line. Retains it until the ruby box's overhang is determined if still pending. */
	private void control(final Quad quad) {
		if (this.pendingRubyControls.isEmpty()) {
			this.textShaper.control(quad);
		} else {
			this.pendingRubyControls.add(quad);
		}
	}

	/** Delivers retained controls to the line in order. */
	private void emitPendingRubyControls() {
		if (this.pendingRubyControls.isEmpty()) {
			return;
		}
		this.requireTextShaper();
		for (final Quad quad : this.pendingRubyControls) {
			this.textShaper.control(quad);
		}
		this.pendingRubyControls.clear();
	}

	public StyledTextUnitizer(Builder builder) {
		this.builder = builder;
	}

	private AbstractTextParams getTextParams() {
		return (AbstractTextParams) this.textParamsStack.get(this.textParamsStack.size() - 1);
	}

	public void requireTextShaper() {
		if (this.textShaper != null) {
			return;
		}
		final AbstractTextParams params = this.getTextParams();
		final InlineParamsStack inlineContext = new InlineParamsStack(params);
		final CSSJTextUnitizer textUnitizer = new CSSJTextUnitizer(inlineContext);
		final WordHyphenator wordHyphenator = new WordHyphenator(inlineContext);
		this.textUnitizer = textUnitizer;
		if (this.builder instanceof BlockBuilder blockBuilder) {
			blockBuilder.pendingText = measurement -> wordHyphenator.deliverPending(measurement, textUnitizer::deliverText);
		}
		wordHyphenator.setGlyphHandler(this.gh);
		textUnitizer.setGlyphHandler(wordHyphenator);
		this.textShaper = params.fontManager.getTextShaper();
		this.textShaper.setGlyphHandler(textUnitizer);
		this.textShaper.fontStyle(params.fontStyle);
	}

	private void changeTextState(AbstractTextParams params) {
		this.wordSpacing = params.wordSpacing;
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
			this.collapseSpaces = false;
			this.lineFeed = true;
			break;

		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			this.collapseSpaces = true;
			this.lineFeed = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NORMAL:
			this.collapseSpaces = true;
			this.lineFeed = false;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			this.collapseSpaces = true;
			this.lineFeed = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			this.collapseSpaces = false;
			this.lineFeed = true;
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public void startContainer() {
		this.followingChar = '\u0020';
		final BlockParams params = this.builder.getFlowBox().getBlockParams();
		this.textParamsStack.add(params);
		if (this.gh == null) {
			this.gh = new BuilderGlyphHandler(builder);
		} else {
			if (this.textParamsStack.size() > 1) {
				this.gh.startTextBox(params);
			} else {
				this.gh.updateText();
			}
		}
		this.changeTextState(params);
		if (params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL) {
			// Selecting hwid/twid/qwid requires the character count of the whole run,
			// so retain events across SAX character-event boundaries until the container ends.
			this.textCombineChars = new ArrayList<TextCombineChars>();
			this.textCombineCharCount = 0;
		}
	}

	/**
	 * Returns the end offset of delivered source characters (M6b v3).
	 */
	public int getDeliveredCharEnd() {
		return this.gh == null ? 0 : this.gh.getDeliveredCharEnd();
	}

	public void flushText() {
		if (this.textCombineChars != null) {
			return;
		}
		if (this.textShaper != null) {
			// Determine overhang from the following character, as before. Deliver only the retained controls first.
			this.emitPendingRubyControls();
			this.textShaper.flush();
		}
	}

	public void endContainer() {
		this.emitTextCombineChars();
		if (this.warichuCollector != null) {
			this.warichuCollector.drain();
		}
		if (this.rubyCollector != null) {
			// Defensive: if ruby has not closed before the container closes (malformed,
			// e.g. a block appeared inside ruby), deliver the accumulated content
			// immediately. However, <b>do not discard</b> the collector:
			// without continued depth tracking, an inner inline end would incorrectly
			// pop the normal inline stack and corrupt it.
			this.rubyCollector.drain();
		}
		this.resolvePendingRubyEnd(false);
		final AbstractTextParams params = (AbstractTextParams) this.textParamsStack
				.remove(this.textParamsStack.size() - 1);
		if (this.textShaper != null) {
			this.textShaper.close();
			this.textShaper = null;
			if (this.builder instanceof BlockBuilder blockBuilder) {
				blockBuilder.pendingText = measurement -> { };
			}
			this.gh.builder.endTextBlock();
		}
		if (this.textParamsStack.size() >= 1) {
			this.gh.endTextBox();
		}
	}

	public void startInline(InlineBox inlineBox) {
		this.disableTextCombineWidthVariant();
		final InlineParams inlineParams = inlineBox.getInlineParams();
		if (this.warichuCollector != null) {
			this.warichuCollector.startInline(inlineParams);
			return;
		}
		if (this.rubyCollector != null) {
			// Do not create boxes for markup within ruby; track only depth and style
			// (specification: ruby contains text only).
			this.rubyCollector.startInline(inlineParams);
			return;
		}
		AbstractContainerBox containerBox = this.gh.builder.getFlowBox();
		inlineBox.firstPassLayout(containerBox);
		this.requireTextShaper();

		Quad end = InlineQuad.createInlineBoxEndQuad(inlineBox);
		this.inlineQuadStack.add(end);
		AbstractTextParams params = inlineBox.getInlineParams();
		this.textParamsStack.add(params);
		this.textShaper.fontStyle(params.fontStyle);
		Quad start = InlineQuad.createInlineBoxStartQuad(inlineBox);
		this.control(start);
		this.changeTextState(params);

		if (inlineParams.rubyRole == AbstractTextParams.RUBY_CONTAINER) {
			// Keep the ruby container (ruby element) as a normal inline
			// (this InlineBox holds its identity, such as id and hyperlinks),
			// then intercept subsequent inner characters into the unit buffer. Design decision (d),
			// codex independent review 2026-07-25.
			this.rubyCollector = new RubyUnitCollector(inlineParams,
					(base, ruby) -> this.emitRubyUnit(inlineParams, base, ruby));
		} else if (inlineParams.warichu) {
			this.warichuCollector = new WarichuCollector(inlineParams,
					segment -> this.emitWarichu(inlineParams, segment));
		}
	}

	public void endInline() {
		if (this.warichuCollector != null) {
			if (!this.warichuCollector.endInline()) {
				return;
			}
			this.warichuCollector = null;
		} else if (this.rubyCollector != null) {
			if (!this.rubyCollector.endInline()) {
				return;
			}
			// Ruby container end: remaining units have been delivered. Continue with normal
			// inline-end processing (close the ruby element's InlineBox).
			this.rubyCollector = null;
		}
		// Run this when an inline ends immediately after resuming from interruption by a block.
		this.requireTextShaper();

		Quad end = (InlineEndQuad) this.inlineQuadStack.remove(this.inlineQuadStack.size() - 1);
		this.control(end);
		this.textParamsStack.remove(this.textParamsStack.size() - 1);
		AbstractTextParams params = this.getTextParams();
		this.textShaper.fontStyle(params.fontStyle);
		this.changeTextState(params);
	}

	public void addInlineReplaced(AbstractReplacedBox inlineReplacedBox) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// Ruby units contain text only (specification); discard replaced elements (F-1).
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		Quad quad = InlineQuad.createReplacedBoxQuad(inlineReplacedBox);
		this.textShaper.control(quad);
		this.followingChar = 'x';
	}

	public void addInlineBlock(InlineBlockBox inlineBlockBox) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// Ruby units contain text only (specification); discard inline blocks (F-1).
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		final Quad quad = InlineQuad.createInlineBlockBoxQuad(inlineBlockBox);
		this.textShaper.control(quad);
		this.followingChar = 'x';
	}

	public void addInlineAbsolute(final IAbsoluteBox absoluteBox) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// Ruby units contain text only (specification); discard absolute positioning (F-1).
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		final Quad quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
		this.textShaper.control(quad);
	}

	/**
	 * Delivers {@code leader()} (leader() L1; consult-codex-2026-07-31-leader.txt). Shapes the pattern independently
	 * in the current style (same form as {@code RubyUnitBox.shape}) and emits a variable-width {@link
	 * net.zamasoft.foliojet.layout.text.LeaderQuad} as a control. Creates a new instance on every execution (do not
	 * share allocated widths between recording and replay).
	 */
	public void leader(final String pattern) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// Ruby units contain text only (specification).
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		final AbstractTextParams params = this.getTextParams();
		// Unification (2026-08-01): independent shaping now uses RunCollector+TrimmedRuns.
		final net.zamasoft.pdfg2d.gc.text.TextImpl[] runs = net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns
				.shape(params.fontManager, params.fontStyle, pattern, -1, false);
		if (runs.length == 0) {
			// No font contains a glyph; no filler.
			return;
		}
		this.textShaper.control(new net.zamasoft.foliojet.layout.text.LeaderQuad(runs));
		this.followingChar = 'x';
	}



	/**
	 * Delivers one paired ruby unit downstream as an atomic inline ({@code RubyUnitBox} carried by a quad treated as
	 * an inline block) (2026-07-25, annotated-text approach).
	 */
	private void emitRubyUnit(final InlineParams container, final RubyUnitCollector.Segment base,
			final List<RubyUnitCollector.Annotation> rubies) {
		// Ruby annotations occupy the same interline space, so adjacent ruby units must not overhang one another.
		this.resolvePendingRubyEnd(false);
		final String baseText = base == null ? "" : base.text();
		int sourceStart = -1, sourceEnd = -1;
		if (base != null && base.charOffset() >= 0) {
			sourceStart = base.charOffset();
			sourceEnd = base.charEnd();
		}
		final List<RubyUnitBox.AnnotationInput> annotations = new ArrayList<RubyUnitBox.AnnotationInput>();
		for (final RubyUnitCollector.Annotation ruby : rubies) {
			final RubyUnitCollector.Segment segment = ruby.segment();
			annotations.add(new RubyUnitBox.AnnotationInput(segment.text(), segment.params(), segment.charOffset(),
					ruby.level()));
			if (segment.charOffset() >= 0) {
				sourceStart = sourceStart < 0 ? segment.charOffset() : Math.min(sourceStart, segment.charOffset());
				sourceEnd = Math.max(sourceEnd, segment.charEnd());
			}
		}
		final RubyUnitBox box = RubyUnitBox.create(container, baseText, base == null ? null : base.params(),
				base == null ? -1 : base.charOffset(), annotations, sourceStart, sourceEnd);
		if (box == null) {
			return;
		}
		if (!isSafeRubyOverhangNeighbor(this.followingChar)) {
			box.reserveStartOverhang();
		}
		this.requireTextShaper();
		final InlineQuad quad = InlineQuad.createInlineBlockBoxQuad(box);
		// Do not deliver to the line until trailing overhang is determined (resolvePendingRubyEnd).
		this.pendingRubyControls.add(quad);
		this.pendingRubyEnd = box;
		this.pendingRubyQuad = quad;
		this.followingChar = 'x';
	}

	private void emitWarichu(final InlineParams container, final WarichuCollector.Segment segment) {
		this.resolvePendingRubyEnd(false);
		final List<WarichuUnitBox> boxes = WarichuUnitBox.createFragments(container, segment.text(), segment.params(),
				segment.sourceStart(), segment.sourceStart(), segment.sourceEnd());
		if (boxes.isEmpty()) {
			return;
		}
		this.requireTextShaper();
		for (final WarichuUnitBox box : boxes) {
			this.textShaper.control(InlineQuad.createInlineBlockBoxQuad(box));
		}
		this.followingChar = 'x';
	}

	public void characters(int charOffset, char[] ch, final int off, final int len, boolean lineFeed) {
		assert len > 0;
		if (this.rubyTextCombineTarget != null && this.rubyTextCombineTarget.rubyCollector != null) {
			final char[] wide = new char[len];
			for (int i = 0; i < len; ++i) {
				wide[i] = TextTransforms.fullWidth(ch[off + i]);
			}
			this.rubyTextCombineTarget.rubyCollector.characters(charOffset, wide, 0, len);
			return;
		}
		if (this.textCombineChars != null) {
			final char[] copy = java.util.Arrays.copyOfRange(ch, off, off + len);
			this.textCombineChars.add(new TextCombineChars(charOffset, copy, lineFeed));
			this.textCombineCharCount += Character.codePointCount(copy, 0, copy.length);
			if (this.textCombineCharCount > 4) {
				this.disableTextCombineWidthVariant();
			}
			return;
		}
		if (this.warichuCollector != null) {
			this.warichuCollector.characters(charOffset, ch, off, len);
			return;
		}
		if (this.rubyCollector != null) {
			// Accumulate characters within ruby in the unit buffer (F-1).
			this.rubyCollector.characters(charOffset, ch, off, len);
			return;
		}
		this.resolvePendingRubyEnd(isSafeRubyOverhangNeighbor(Character.codePointAt(ch, off, off + len)));
		final AbstractTextParams params = this.getTextParams();

		// Text processing.
		int ooff = 0;
		FontListMetrics flm = params.getFontListMetrics();
		for (int i = 0; i < len; ++i) {
			char c = ch[i + off];
			if (TextUtils.isControl(c)) {
				TextControl quad = null;
				switch (c) {
				case '\n':
					// Line-break character.
					if (lineFeed || this.lineFeed) {
						quad = new LineBreak(flm, charOffset + i);
					} else if (this.collapseSpaces) {
						UnicodeBlock block = UnicodeBlock.of(this.followingChar);
						if (block == UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
								|| block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || block == UnicodeBlock.HIRAGANA
								|| block == UnicodeBlock.KATAKANA) {
							// Remove one character.
							if (i > ooff) {
								this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
							}
							ooff = i + 1;
							continue;
						}
					}
					break;
				case '\t':
					// Tab character.
					if (!this.collapseSpaces) {
						quad = new Tab(flm, charOffset + i);
					}
					break;
				}
				if (quad != null) {
					// Remove one character.
					if (i > ooff) {
						this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
					}
					ooff = i + 1;
					this.requireTextShaper();
					this.textShaper.control(quad);
					this.followingChar = c;
					continue;
				}
				// Convert to a space.
				c = '\u0020';
			}
			if (c == '\u0020') {
				// Remove one character.
				if (i > ooff) {
					this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
				}
				ooff = i + 1;
				if (this.followingChar != '\u0020' || !this.collapseSpaces) {
					// Output a space.
					WhiteSpace ws = new WhiteSpace(flm, charOffset + i);
					ws.setWordSpacing(this.wordSpacing);
					this.requireTextShaper();
					this.textShaper.control(ws);
				}
				this.followingChar = c;
				continue;
			}
			if (c == '\u00AD') {
				// Convert soft hyphens to break-opportunity markers without shaping them.
				// Remove one character.
				if (i > ooff) {
					this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
				}
				ooff = i + 1;
				if (params.hyphens != AbstractTextParams.HYPHENS_NONE) {
					this.requireTextShaper();
					this.textShaper.control(new WordHyphenator.Marker(charOffset + i));
				}
				this.followingChar = c;
				continue;
			}
			this.followingChar = c;
			ch[i + off] = c;
		}
		if (len > ooff) {
			this._characters(charOffset + ooff, ch, off + ooff, len - ooff);
		}
	}

	private void resolvePendingRubyEnd(final boolean safeNeighbor) {
		if (this.pendingRubyEnd == null) {
			return;
		}
		if (!safeNeighbor) {
			this.pendingRubyEnd.reserveEndOverhang();
			// BuilderGlyphHandler copies advance when accepting a control. Even when restoring box width
			// later, synchronize the same quad's advance; otherwise only drawing expands.
			this.pendingRubyQuad.advance = this.pendingRubyEnd
					.getLineExtent(this.pendingRubyEnd.getBlockParams().flow);
		}
		this.emitPendingRubyControls();
		this.pendingRubyEnd = null;
		this.pendingRubyQuad = null;
	}

	/**
	 * Conservatively restricts JLREQ overhang targets. Kana and kanji have glyph bounds on the base-text side and do
	 * not collide with annotation lines, but reserve space for Latin text, punctuation, and other ruby boxes.
	 */
	private static boolean isSafeRubyOverhangNeighbor(final int codePoint) {
		final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
		return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
				|| script == Character.UnicodeScript.KATAKANA;
	}

	private void _characters(int charOffset, char[] ch, int off, int len) {
		TextTransforms.apply(this.getTextParams().textTransform, ch, off, len);
		this.requireTextShaper();
		this.textUnitizer.characters(this.textShaper, charOffset, ch, off, len);
	}

	/**
	 * Shapes with preference for OpenType width variants matching the tate-chu-yoko character count.
	 * If the font lacks the feature, advance remains unchanged, so the later {@code compressTextCombine} compresses to
	 * 1 em as before.
	 */
	private void emitTextCombineChars() {
		if (this.textCombineChars == null) {
			return;
		}
		final List<TextCombineChars> chars = this.textCombineChars;
		this.textCombineChars = null;
		if (chars.isEmpty()) {
			return;
		}
		this.requireTextShaper();
		this.textShaper.fontStyle(textCombineFontStyle(this.getTextParams().fontStyle, this.textCombineCharCount));
		for (final TextCombineChars text : chars) {
			this.characters(text.charOffset, text.chars, 0, text.chars.length, text.lineFeed);
		}
	}

	/** Falls back to the existing affine compression for tate-chu-yoko with complex child elements. */
	private void disableTextCombineWidthVariant() {
		if (this.textCombineChars == null) {
			return;
		}
		final List<TextCombineChars> chars = this.textCombineChars;
		this.textCombineChars = null;
		for (final TextCombineChars text : chars) {
			this.characters(text.charOffset, text.chars, 0, text.chars.length, text.lineFeed);
		}
	}

	/** Builds a tate-chu-yoko style with width variants matching the character count. */
	static FontStyle textCombineFontStyle(final FontStyle base, final int charCount) {
		final String tag = switch (charCount) {
		case 2 -> "hwid";
		case 3 -> "twid";
		case 4 -> "qwid";
		default -> null;
		};
		if (tag == null) {
			return base;
		}
		final FontFeatureSet width = FontFeatureSet.of(new int[] { FontFeatureSet.packTag(tag) }, new int[] { 1 });
		return LayoutFontStyle.withFeatures(base, base.getFeatures().override(width));
	}

}
