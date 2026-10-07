package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.content.JustificationState;
import net.zamasoft.foliojet.layout.box.impl.LineBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.LinePos;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.SidewaysGeometry;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * A line box implementation.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractLineBox.java 1640 2023-10-04 03:06:26Z miyabe $
 */
public abstract class AbstractLineBox extends AbstractTextBox {
	private static final java.util.concurrent.atomic.AtomicLong NEXT_LINE_ID =
			new java.util.concurrent.atomic.AtomicLong();

	/** Inline-axis alignment. */
	protected double lineAlign = 0;
	/** The physical inline-axis size passed to align. */
	private double inlineExtent;

	/** The end of a line or block. */
	protected boolean last = false;

	/** A drawing-only tree when paragraph UBA is enabled. Logical contents remain unchanged. */
	private List<Object> visualContents;
	private java.util.Map<Object, net.zamasoft.foliojet.layout.text.bidi.BidiSlice> bidiSlices = java.util.Map.of();
	private byte bidiBaseDirection = AbstractTextParams.DIRECTION_LTR;
	private long bidiParagraphId;
	private net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission logicalLineEmission;
	private String logicalLineVisualText;
	/** The logical context of preceding lines when TextReplaySlice resumes midway through a paragraph. */
	private net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix bidiReplayPrefix =
			net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix.EMPTY;

	public abstract AbstractLineParams getLineParams();

	public BoxType getType() {
		return BoxType.LINE;
	}

	public Pos getPos() {
		return LinePos.POS;
	}

	public boolean isLast() {
		return this.last;
	}

	public final void setBidiBaseDirection(final byte baseDirection) {
		this.bidiBaseDirection = baseDirection;
	}

	public final void setVisualContents(final List<Object> visualContents,
			final java.util.Map<Object, net.zamasoft.foliojet.layout.text.bidi.BidiSlice> bidiSlices) {
		this.visualContents = java.util.Collections.unmodifiableList(new ArrayList<Object>(visualContents));
		this.bidiSlices = java.util.Collections.unmodifiableMap(new java.util.IdentityHashMap<>(bidiSlices));
		final StringBuilder visual = new StringBuilder();
		appendVisualText(this.visualContents, visual);
		this.logicalLineVisualText = visual.toString();
		this.prepareLogicalLineEmission();
	}

	public final List<Object> getVisualContents() {
		return this.visualContents == null ? java.util.Collections.emptyList() : this.visualContents;
	}

	@Override
	public final net.zamasoft.foliojet.layout.text.bidi.BidiSlice getBidiSlice(final Object visualContent) {
		return this.bidiSlices.get(visualContent);
	}

	@Override
	public final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission getLogicalLineEmission() {
		return this.logicalLineEmission;
	}

	@Override
	public final String getLogicalLineVisualText() {
		return this.logicalLineVisualText;
	}

	/**
	 * Creates the sidecar before visual inline fragments are built. Atomic inlines
	 * use U+FFFC because alternative text is not uniformly available at this layer.
	 */
	public final net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission prepareLogicalLineEmission() {
		final StringBuilder logical = new StringBuilder();
		appendLogicalText(this.contents, logical);
		final String text = logical.toString();
		if (this.logicalLineEmission == null || !this.logicalLineEmission.logicalText().equals(text)) {
			final long lineId = this.logicalLineEmission == null ? NEXT_LINE_ID.incrementAndGet()
					: this.logicalLineEmission.lineId();
			this.logicalLineEmission = new net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission(lineId, text);
		}
		return this.logicalLineEmission;
	}

	private static void appendLogicalText(final List<Object> contents, final StringBuilder logical) {
		if (contents == null) {
			return;
		}
		for (final Object content : contents) {
			if (content instanceof net.zamasoft.pdfg2d.gc.text.Text text) {
				logical.append(text.getChars(), 0, text.getCharCount());
			} else if (content instanceof net.zamasoft.pdfg2d.gc.text.layout.control.Control control) {
				logical.append(control.getControlChar());
			} else if (content instanceof Inline inline) {
				if (inline.box instanceof net.zamasoft.foliojet.layout.box.impl.InlineBox box) {
					appendLogicalText(box.getLogicalContents(), logical);
				} else {
					logical.append('\uFFFC');
				}
			} else if (content instanceof net.zamasoft.foliojet.layout.text.LeaderQuad) {
				logical.append('\uFFFC');
			}
		}
	}

	private static void appendVisualText(final List<Object> contents, final StringBuilder visual) {
		if (contents == null) {
			return;
		}
		for (final Object content : contents) {
			if (content instanceof net.zamasoft.pdfg2d.gc.text.Text text) {
				visual.append(text.getChars(), 0, text.getCharCount());
			} else if (content instanceof net.zamasoft.pdfg2d.gc.text.layout.control.Control control) {
				visual.append(control.getControlChar());
			} else if (content instanceof Inline inline
					&& inline.box instanceof net.zamasoft.foliojet.layout.box.impl.InlineBox box) {
				appendVisualText(box.getLogicalContents(), visual);
			}
		}
	}

	public final void setBidiReplayPrefix(
			final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix prefix) {
		this.bidiReplayPrefix = prefix;
	}

	public final void setBidiParagraphId(final long paragraphId) {
		this.bidiParagraphId = paragraphId;
	}

	public final long getBidiParagraphId() {
		return this.bidiParagraphId;
	}

	public final net.zamasoft.foliojet.layout.text.bidi.BidiReplayPrefix getBidiReplayPrefix() {
		return this.bidiReplayPrefix;
	}

	@Override
	protected List<Object> getDrawingContents() {
		return this.visualContents == null ? super.getDrawingContents() : this.visualContents;
	}

	public void addAscentDescent(double ascent, double descent) {
		// Expand ascent and descent.
		if (ascent > this.ascent) {
			this.ascent = ascent;
		}
		if (descent > this.descent) {
			this.descent = descent;
		}
		assert !LayoutUtils.isNone(this.ascent + this.descent);
	}

	/**
	 * The amount of end-of-line trimming/hanging (Japanese text spacing T2/H1:
	 * consult-codex-2026-07-31-text-spacing.txt). Line placement and justification use
	 * the effective inline size excluding this amount, while glyphs themselves draw normally
	 * (overflow from hanging punctuation and half-width end-of-line punctuation counts as ink).
	 */
	private double endHangAdvance;

	/**
	 * The ellipsis for {@code text-overflow: ellipsis} (null if absent; 2026-08-29,
	 * TextBuilder.applyTextOverflow). Retained separately from line content; at drawing time,
	 * clips the line end and draws the ellipsis additionally.
	 */
	private net.zamasoft.pdfg2d.gc.text.Text ellipsis;

	/** The inline extent in which to draw content, measured from the line origin before lineAlign. */
	private double ellipsisClipExtent;

	public void setEllipsis(final net.zamasoft.pdfg2d.gc.text.Text ellipsis, final double clipExtent) {
		this.ellipsis = ellipsis;
		this.ellipsisClipExtent = clipExtent;
	}

	public net.zamasoft.pdfg2d.gc.text.Text getEllipsis() {
		return this.ellipsis;
	}

	/** The inline offset from the line origin to the content start (determined by {@link #align}). */
	public double getLineAlign() {
		return this.lineAlign;
	}

	public void setEndHangAdvance(final double endHangAdvance) {
		this.endHangAdvance = endHangAdvance;
	}

	public double getEndHangAdvance() {
		return this.endHangAdvance;
	}

	/**
	 * Applies inline-axis alignment.
	 *
	 * @param textIndent  the indent
	 * @param offset      the offset caused by floats, etc.
	 * @param maxLineAxis the maximum inline size
	 * @param last        a line at the block end or ended by a line break
	 */
	public void align(double textIndent, double offset, double maxLineAxis, boolean last) {
		// Inline-axis alignment.
		assert this.contents != null && !this.contents.isEmpty();
		// Bidi reordering builds separate visualContents at paragraph end, leaving logical contents untouched.
		this.last = last;
		this.inlineExtent = maxLineAxis;
		AbstractLineParams params = this.getLineParams();
		// T2/H1: effective inline size (excluding end-of-line trimming/hanging).
		double lineWidth = this.lineSize - this.endHangAdvance + textIndent;
		textIndent += offset;
		byte textAlign = last ? params.textAlignLast : params.textAlign;
		// sideways uses the same logical offset as LTR, mapped to physical coordinates only once
		// by inlineToPhysical at drawing time. Keep the existing start/end swap only for RTL in normal layout.
		if (this.bidiBaseDirection == AbstractTextParams.DIRECTION_RTL
				&& !TypesettingMode.usesSidewaysInlineAxis(params.flow, params.writingModeVariant)) {
			if (textAlign == AbstractLineParams.TEXT_ALIGN_START) {
				textAlign = AbstractLineParams.TEXT_ALIGN_END;
			} else if (textAlign == AbstractLineParams.TEXT_ALIGN_END) {
				textAlign = AbstractLineParams.TEXT_ALIGN_START;
			}
		}
		switch (textAlign) {
		case AbstractLineParams.TEXT_ALIGN_CENTER:
			// Center.
			this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_END:
			// Align to line end.
			this.lineAlign = maxLineAxis - lineWidth + textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_JUSTIFY: {
			// Justify.
			double remainderAdvance = maxLineAxis - lineWidth;
			if (remainderAdvance > 0) {
				this.justifyByWritingSystem(remainderAdvance);
			}
			this.lineAlign = textIndent;
		}
			break;

		case AbstractLineParams.TEXT_ALIGN_START:
			// Align to line start.
			this.lineAlign = textIndent;
			break;

		case AbstractLineParams.TEXT_ALIGN_X_JUSTIFY_CENTER:
			// Center-justify.
			double remainderAdvance = maxLineAxis - lineWidth;
			if (remainderAdvance <= 0) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}
			double fontSize = this.getTextParams().fontStyle.getSize();
			if (remainderAdvance <= fontSize) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}

			final boolean japanese = this.containsJapaneseComposition();
			final double capacity = japanese
					? this.justificationCapacity(JUSTIFY_LETTERS, new JustificationState())
					: this.countGeneralJustificationPoints(new JustificationState());
			if (capacity <= 0) {
				this.lineAlign = (maxLineAxis - lineWidth) / 2.0 + textIndent;
				break;
			}
			this.justifyByWritingSystem(remainderAdvance - fontSize);
			this.lineAlign = textIndent + fontSize / 2.0;
			break;

		default:
			throw new IllegalStateException();
		}

		// Page-axis alignment.
		super.verticalAlign(this, 0);
	}

	/**
	 * Distributes the line's remaining space according to {@code text-justify} (2026-09-02):
	 * {@code none} distributes nothing; {@code inter-word} uses only word spaces;
	 * {@code inter-character} uses character spaces (JLREQ stages for Japanese lines, separable
	 * boundaries for others). {@code auto} depends on language: JLREQ for Japanese lines,
	 * word spaces only for Korean ({@code lang=ko}; measured in Chrome: only whitespace expands,
	 * syllable advances stay unchanged), and the existing separable boundaries for other languages.
	 */
	private void justifyByWritingSystem(final double remainder) {
		if (remainder <= 0) {
			return;
		}
		final byte mode = this.getTextParams().textJustify;
		if (mode == AbstractTextParams.TEXT_JUSTIFY_NONE) {
			return;
		}
		if (mode == AbstractTextParams.TEXT_JUSTIFY_INTER_WORD
				|| mode == AbstractTextParams.TEXT_JUSTIFY_AUTO && this.isKorean()) {
			final int spaces = this.countWordSpaceJustificationPoints(new JustificationState());
			if (spaces > 0) {
				this.justifyWordSpaces(remainder / spaces, new JustificationState());
				return;
			}
			if (mode == AbstractTextParams.TEXT_JUSTIFY_INTER_WORD) {
				// Leave lines without word spaces unchanged (css-text-3 §7.3).
				return;
			}
			// Fall back to character spacing only for Korean auto lines without word spaces.
		}
		if (this.containsJapaneseComposition()) {
			this.justifyByJlreqPriorities(remainder);
			return;
		}
		final int count = this.countGeneralJustificationPoints(new JustificationState());
		if (count > 0) {
			this.justifyGeneral(remainder / count, new JustificationState());
		}
	}

	/** Whether this line's language is Korean ({@code lang} is {@code ko}). */
	private boolean isKorean() {
		final java.util.Locale lang = this.getTextParams().fontStyle == null ? null
				: this.getTextParams().fontStyle.getLang();
		return lang != null && "ko".equals(lang.getLanguage());
	}

	/** Distributes the line's remaining space in the four stages of JLREQ 3.8.4. */
	private void justifyByJlreqPriorities(double remainder) {
		if (remainder <= 0) {
			return;
		}
		for (int priority = JUSTIFY_WORD_SPACE; priority <= JUSTIFY_GENERAL && remainder > 0.0001;
				++priority) {
			final double capacity = this.justificationCapacity(priority, new JustificationState());
			if (capacity <= 0) {
				continue;
			}
			final double used = Math.min(remainder, capacity);
			this.justify(priority, used / capacity, new JustificationState());
			remainder -= used;
		}
		if (remainder > 0.0001) {
			// For Latin letter spacing, auto distributes only on lines with no other opportunities (JUSTIFY_LETTERS).
			int priority = this.getTextParams().textJustify == AbstractTextParams.TEXT_JUSTIFY_INTER_CHARACTER
					? JUSTIFY_LETTERS : JUSTIFY_FALLBACK;
			double weight = this.justificationCapacity(priority, new JustificationState());
			if (weight <= 0 && priority == JUSTIFY_FALLBACK) {
				priority = JUSTIFY_LETTERS;
				weight = this.justificationCapacity(priority, new JustificationState());
			}
			if (weight > 0) {
				this.justify(priority, remainder / weight, new JustificationState());
			}
		}
	}

	public LineBox splitLine(BlockParams params) {
		LineBox newLine = new LineBox(params);
		return newLine;
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (this.ellipsis != null) {
			// text-overflow: ellipsis (2026-08-29). Clip content at the line end
			// using ellipsisClipExtent, then draw the ellipsis last with the original clip.
			// (The worklist is LIFO, so pushing first executes after the children.)
			final AbstractLineParams lineParams = this.getLineParams();
			final boolean sideways = lineParams.writingModeVariant != WritingModeVariant.NORMAL;
			final boolean vertical = lineParams.flow.isVertical();
			final boolean bottomToTop = vertical && lineParams.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(lineParams.flow, lineParams.writingModeVariant,
							lineParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
			final double keepStart = vertical
					? LayoutUtils.inlineToPhysical(lineParams, this.inlineExtent, this.lineAlign,
							this.lineAlign + this.ellipsisClipExtent)
					: this.lineAlign;
			final double pw = pageBox.getWidth(), ph = pageBox.getHeight();
			final java.awt.geom.Rectangle2D.Double keep;
			if (sideways) {
				final java.awt.geom.Rectangle2D bounds = SidewaysGeometry.bounds(lineParams.writingModeVariant, x,
						y + (bottomToTop ? keepStart : 0),
						this.ascent, this.descent, this.ellipsisClipExtent);
				keep = bottomToTop
						? new java.awt.geom.Rectangle2D.Double(bounds.getX() - pw, bounds.getY(),
								bounds.getWidth() + pw * 2, bounds.getHeight() + ph)
						: new java.awt.geom.Rectangle2D.Double(bounds.getX() - pw, bounds.getY() - ph,
								bounds.getWidth() + pw * 2, bounds.getHeight() + ph);
			} else {
				keep = bottomToTop
						? new java.awt.geom.Rectangle2D.Double(x - pw, y + keepStart, pw * 3,
								ph + this.ellipsisClipExtent)
						: vertical
						? new java.awt.geom.Rectangle2D.Double(x - pw, y - ph, pw * 3, ph + this.ellipsisClipExtent)
						: new java.awt.geom.Rectangle2D.Double(x - pw, y - ph, pw + this.ellipsisClipExtent, ph * 3);
			}
			final Shape outerClip = clip;
			final double ex = vertical ? x : x + this.ellipsisClipExtent;
			final double ey = bottomToTop
					? y + LayoutUtils.inlineToPhysical(lineParams, this.inlineExtent,
							this.lineAlign + this.ellipsisClipExtent,
							this.lineAlign + this.ellipsisClipExtent + this.ellipsis.getAdvance())
					: vertical ? y + this.ellipsisClipExtent : y;
			final List<Object> run = java.util.Collections.singletonList(this.ellipsis);
			worklist.push(w -> drawer.visitDrawable(new TextSequenceDrawable(pageBox, outerClip, transform, run, 0, 1,
					this.getTextParams(), this.ascent, this.descent), ex, ey));
			if (clip == null) {
				clip = keep;
			} else if (clip instanceof java.awt.geom.Rectangle2D rc) {
				clip = rc.createIntersection(keep);
			} else {
				final java.awt.geom.Area area = new java.awt.geom.Area(clip);
				area.intersect(new java.awt.geom.Area(keep));
				clip = area;
			}
		}
		switch (this.getLineParams().flow) {
		case WritingMode.TB:
			// Horizontal writing.
			x += this.lineAlign;
			break;

		case WritingMode.LR:
		case WritingMode.RL:
			// Vertical writing.
			y += LayoutUtils.inlineToPhysical(this.getLineParams(), this.inlineExtent, this.lineAlign,
					this.lineAlign + this.lineSize);
			break;

		default:
			throw new IllegalStateException();
		}

		visitor.visitBox(transform, this, drawer, x, y);
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			java.util.Deque<TextShapeStep> worklist) {
		switch (this.getLineParams().flow) {
		case WritingMode.TB:
			// Horizontal writing.
			x += this.lineAlign;
			break;

		case WritingMode.LR:
		case WritingMode.RL:
			// Vertical writing.
			y += LayoutUtils.inlineToPhysical(this.getLineParams(), this.inlineExtent, this.lineAlign,
					this.lineAlign + this.lineSize);
			break;

		default:
			throw new IllegalStateException();
		}
		super.pushTextShapeSteps(pageBox, path, transform, x, y, worklist);
	}
}
