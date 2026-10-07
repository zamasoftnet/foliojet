package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.LayoutFontStyle;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.SidewaysGeometry;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.css.value.RubyAlignValue;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * One ruby unit (annotated-text approach, added 2026-07-25;
 * see the development log for specification decisions).
 *
 * <p>
 * Lays out a pair of base text and ruby text as an indivisible atomic inline
 * (treated as an inline block). Its width is max(base text width, ruby text width);
 * the narrower text is distributed evenly within the unit. Ruby text uses half the base
 * font size, placed above the line in horizontal writing or to its right in vertical writing.
 * </p>
 *
 * <p>
 * The unit's dimensions (= its contribution to line height) include <b>only the base text</b>.
 * Specification revised 2026-07-25: F-2 quality review judged widening line pitch only on lines
 * with ruby to violate Japanese typesetting principles (constant line pitch, ruby placed
 * between lines). Ruby text is drawn outside the box: above the top in horizontal writing,
 * or to the right of the right edge in vertical writing, in the inter-line space.
 * The designer must provide that space (use a larger line-height in documents with ruby).
 * The {@code TextBuilder} BLOCK path uses {@code getLastDescent()} to align the baseline
 * with the base text, so line pitch exactly matches the surrounding text.
 * </p>
 *
 * <p>
 * The content is not child boxes: this unit draws its own glyph sequences, shaped during
 * construction (base text plus half-size annotations). The container remains empty,
 * avoiding conflicts with existing {@code InlineBlockBox} mechanisms such as splitting
 * and finishLayout.
 * </p>
 *
 * <p>
 * {@code params.element} is <b>null</b>: this box corresponds to no DOM element and is
 * synthesized from characters in the ruby range. The ruby element's identity
 * (id, hyperlink, Tagged PDF role) belongs to the outer {@code InlineBox}, which remains
 * a normal inline (codex independent review 2026-07-25, design decision (d)).
 * Individual rb/rt anchors and PDF Ruby/RB/RT structure types remain future work
 * requiring dedicated metadata.
 * </p>
 */
public class RubyUnitBox extends InlineBlockBox {

	/** The minimum surplus allowed for even distribution within a unit (do not distribute less than this). */
	private static final double DISTRIBUTE_EPSILON = 0.0001;

	private final TextImpl[] baseTexts;

	/** Annotations, including multiple levels and both sides. */
	private final RubyAnnotation[] annotations;

	/** The base text color (null retains the inherited color). */
	private final Color baseColor;

	/**
	 * The writing direction, used to determine the side for ruby text (the line's "over" side).
	 * In vertical writing ({@link WritingMode#RL}/{@link WritingMode#LR}), this engine places
	 * descent to the left of the baseline and ascent to its right (the {@code TextBuilder}
	 * vertical path treats RL/LR alike), so <b>+x</b> is the over side in both cases.
	 * Place ruby text on that over side, toward +x.
	 */
	private final WritingMode flow;

	/** The base text extent above the baseline (to its right in vertical writing). */
	private final double baseAscent;

	/** The base text extent below the baseline (to its left in vertical writing). */
	private final double baseDescent;

	/** The difference between the virtual annotation-alignment width and the actual atomic inline width. */
	private final double annotationOrigin;

	/** Allowed overhang on the left/right (line start/end in vertical writing). */
	private final double startHang, endHang;

	/** The shift that brings base text and annotations back inside the box when reserving the line-start side. */
	private double contentShift = 0;

	private boolean startHangReserved = false, endHangReserved = false;

	/** Text for extraction and kinsoku (line-breaking rules) checks (base text, or ruby text if no base exists). */
	private final String text;

	/**
	 * The source character range consumed by this unit (both -1 when there is no source,
	 * such as generated content). Used to ensure that partial replay at a page break never
	 * resumes in the middle of a unit ({@code AbstractTextBox.lastCharEnd()}/
	 * {@code firstCharOffset()} and advancement of the delivered end).
	 */
	private final int sourceStart, sourceEnd;

	private RubyUnitBox(final BlockParams params, final InlinePos pos, final RubyUnitContainer container,
			final TextImpl[] baseTexts, final RubyAnnotation[] annotations, final Color baseColor,
			final WritingMode flow, final double lineExtent, final double baseAscent, final double baseDescent,
			final double annotationOrigin, final double startHang, final double endHang, final String text,
			final int sourceStart, final int sourceEnd) {
		super(params, pos, Dimension.AUTO_DIMENSION, Dimension.ZERO_DIMENSION,
				new AbsoluteRectFrame(RectFrame.NULL_FRAME), container);
		this.baseTexts = baseTexts;
		this.annotations = annotations;
		this.baseColor = baseColor;
		this.flow = flow;
		this.baseAscent = baseAscent;
		this.baseDescent = baseDescent;
		this.annotationOrigin = annotationOrigin;
		this.startHang = startHang;
		this.endHang = endHang;
		this.text = text;
		this.sourceStart = sourceStart;
		this.sourceEnd = sourceEnd;
		// Page-axis size = base text only (specification revised 2026-07-25: constant line pitch;
		// draw ruby text outside the box in the inter-line space).
		final double pageExtent = baseDescent + baseAscent;
		if (flow.isVertical()) {
			// Vertical writing: line axis = vertical, page axis = horizontal. From left: baseDescent, baseline,
			// baseAscent. The ruby column sits outside the right edge.
			this.width = pageExtent;
			this.height = lineExtent;
		} else {
			// Horizontal writing: from top, base ascent, baseline, base descent.
			// The ruby line sits above the top edge.
			this.width = lineExtent;
			this.height = pageExtent;
		}
		container.setup(baseAscent, baseDescent);
	}

	/**
	 * Reserves overhang inside the box because it may collide with the neighboring glyph
	 * on the line-start side. Intended to be called before passing the quad downstream.
	 */
	public void reserveStartOverhang() {
		if (this.startHangReserved || this.startHang <= 0) {
			return;
		}
		this.startHangReserved = true;
		this.contentShift += this.startHang;
		if (this.flow.isVertical()) {
			this.height += this.startHang;
		} else {
			this.width += this.startHang;
		}
	}

	/** Adds overhang back to the width because it may collide with the neighboring glyph on the line-end side. */
	public void reserveEndOverhang() {
		if (this.endHangReserved || this.endHang <= 0) {
			return;
		}
		this.endHangReserved = true;
		if (this.flow.isVertical()) {
			this.height += this.endHang;
		} else {
			this.width += this.endHang;
		}
	}

	/**
	 * Always true. Ruby units are shaped and sized during construction and do not need
	 * actual shrink-to-fit measurement (a nested builder).
	 */
	public boolean isPreMeasured() {
		return true;
	}

	/**
	 * The exclusive end of source characters consumed by this unit (-1 if none).
	 */
	public int getSourceEnd() {
		return this.sourceEnd;
	}

	/** Annotation input from the collector. level is zero-based. */
	public record AnnotationInput(String text, InlineParams params, int charOffset, int level) {
	}

	private static final class RubyAnnotation {
		final TextImpl[] texts;
		final Color color;
		final boolean over;
		final boolean interCharacter;
		final double ascent, descent, advance;

		RubyAnnotation(final TextImpl[] texts, final Color color, final boolean over, final boolean interCharacter) {
			this.texts = texts;
			this.color = color;
			this.over = over;
			this.interCharacter = interCharacter;
			this.ascent = texts.length == 0 ? 0 : maxAscent(texts);
			this.descent = texts.length == 0 ? 0 : maxDescent(texts);
			this.advance = totalAdvance(texts);
		}
	}

	/**
	 * Builds an atomic inline from base text and zero or more annotation levels.
	 * Reorders each as an independent small paragraph.
	 */
	public static RubyUnitBox create(final InlineParams container, final String baseText, final InlineParams baseParams,
			final int baseOffset, final List<AnnotationInput> annotationInputs, final int sourceStart,
			final int sourceEnd) {
		if (baseText.isEmpty() && annotationInputs.isEmpty()) {
			return null;
		}
		final InlineParams bp = baseParams == null ? container : baseParams;
		final FontStyle baseFs = bp.fontStyle;
		final TextImpl[] baseTexts = shape(bp, baseFs, baseText, baseOffset);
		final double baseAdvance = totalAdvance(baseTexts);

		final List<RubyAnnotation> built = new ArrayList<RubyAnnotation>();
		double visualExtent = baseAdvance;
		String fallbackText = "";
		boolean overhang = container.rubyOverhang;
		for (final AnnotationInput input : annotationInputs) {
			final InlineParams rp = input.params() == null ? container : input.params();
			final FontStyle rubyBaseFs = rp.fontStyle;
			final FontStyle rubyFs = LayoutFontStyle.withSize(rubyBaseFs, baseFs.getSize() / 2.0);
			final TextImpl[] texts = input.text().isEmpty() ? new TextImpl[0]
					: shape(rp, rubyFs, input.text(), input.charOffset());
			final boolean interCharacter = !container.flow.isVertical() && rp.rubyPosition.isInterCharacter();
			final RubyAnnotation annotation = new RubyAnnotation(texts, rp.color,
					interCharacter || rp.rubyPosition.isOver(input.level()), interCharacter);
			built.add(annotation);
			if (!interCharacter) {
				visualExtent = Math.max(visualExtent, annotation.advance);
			}
			overhang &= rp.rubyOverhang;
			if (fallbackText.isEmpty()) {
				fallbackText = input.text();
			}
		}
		if (visualExtent <= 0) {
			return null;
		}

		// CSS Ruby leaves overhang to the UA. Allow up to 0.5ic of the annotation font (=0.25em of the base text)
		// on each side to stay within JLREQ/JIS limits.
		final double desiredHang = Math.max(0, (visualExtent - baseAdvance) / 2.0);
		final double hang = overhang && baseAdvance > 0 ? Math.min(baseFs.getSize() / 4.0, desiredHang) : 0;
		final double lineExtent = Math.max(baseAdvance, visualExtent - hang * 2.0);
		final double annotationOrigin = (lineExtent - visualExtent) / 2.0;

		align(baseTexts, lineExtent - baseAdvance, bp.rubyAlign);
		for (int i = 0; i < built.size(); ++i) {
			final RubyAnnotation annotation = built.get(i);
			if (annotation.interCharacter) {
				continue;
			}
			final InlineParams rp = annotationInputs.get(i).params() == null ? container : annotationInputs.get(i).params();
			align(annotation.texts, visualExtent - annotation.advance, rp.rubyAlign);
		}

		final double baseAscent;
		final double baseDescent;
		if (baseTexts.length > 0) {
			baseAscent = maxAscent(baseTexts);
			baseDescent = maxDescent(baseTexts);
		} else {
			final FontListMetrics baseFlm = bp.fontManager.getFontListMetrics(baseFs);
			baseAscent = baseFlm.getMaxAscent();
			baseDescent = baseFlm.getMaxDescent();
		}

		final BlockParams params = new BlockParams();
		params.element = null;
		params.opacity = bp.opacity;
		params.blendMode = bp.blendMode;
		params.filter = bp.filter;
		params.fontStyle = baseFs;
		params.fontManager = bp.fontManager;
		params.lineBreakRules = bp.lineBreakRules;
		params.direction = bp.direction;
		params.unicodeBidi = bp.unicodeBidi;
		params.bidiSemanticAlias = bp.bidiSemanticAlias;
		params.flow = container.flow;
		params.writingModeVariant = container.writingModeVariant;
		params.color = bp.color;
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.lineHeight = 0;

		final InlinePos pos = new InlinePos();
		pos.lineHeight = 0;
		final String text = baseText.isEmpty() ? fallbackText : baseText;
		return new RubyUnitBox(params, pos, new RubyUnitContainer(), baseTexts,
				built.toArray(RubyAnnotation[]::new), bp.color, container.flow, lineExtent, baseAscent, baseDescent,
				annotationOrigin, hang, hang, text, sourceStart, sourceEnd);
	}

	/** Self-contained shaping (unified on RunCollector+TrimmedRuns on 2026-08-01). */
	private static TextImpl[] shape(final InlineParams src, final FontStyle fontStyle, final String text,
			final int charOffset) {
		final TextImpl[] runs = net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns.shape(src.fontManager, fontStyle,
				text, charOffset, src.textSpacingTrimOff);
		return net.zamasoft.foliojet.layout.text.bidi.BidiParagraphLayout.reorderAtomicRuns(runs, src.direction,
				src.unicodeBidi, src.bidiSemanticAlias);
	}


	private static double totalAdvance(final TextImpl[] texts) {
		double advance = 0;
		for (final TextImpl text : texts) {
			advance += text.getAdvance();
		}
		return advance;
	}

	private static double maxAscent(final TextImpl[] texts) {
		double ascent = 0;
		for (final TextImpl text : texts) {
			ascent = Math.max(ascent, text.getAscent());
		}
		return ascent;
	}

	private static double maxDescent(final TextImpl[] texts) {
		double descent = 0;
		for (final TextImpl text : texts) {
			descent = Math.max(descent, text.getDescent());
		}
		return descent;
	}

	/** Distributes surplus in a text sequence to glyph advances according to CSS {@code ruby-align}. */
	private static void align(final TextImpl[] texts, final double extra, final RubyAlignValue alignment) {
		if (extra <= DISTRIBUTE_EPSILON || alignment == RubyAlignValue.START) {
			return;
		}
		int glyphCount = 0;
		for (final TextImpl text : texts) {
			glyphCount += text.getGlyphCount();
		}
		if (glyphCount <= 0) {
			return;
		}
		final double per;
		if (alignment == RubyAlignValue.CENTER || glyphCount == 1) {
			per = 0;
		} else if (alignment == RubyAlignValue.SPACE_BETWEEN) {
			per = extra / (glyphCount - 1);
		} else {
			per = extra / glyphCount;
		}
		int glyph = 0;
		for (final TextImpl text : texts) {
			text.resetXAdvances();
			for (int i = 0; i < text.getGlyphCount(); ++i) {
				final double before;
				if (alignment == RubyAlignValue.CENTER || glyphCount == 1) {
					before = glyph == 0 ? extra / 2.0 : 0;
				} else if (alignment == RubyAlignValue.SPACE_BETWEEN) {
					before = glyph == 0 ? 0 : per;
				} else {
					before = glyph == 0 ? per / 2.0 : per;
				}
				if (before != 0) {
					text.addXAdvance(i, before);
				}
				++glyph;
			}
		}
	}

	/**
	 * Returns the <b>base text</b> for text extraction. Ruby text is a reading annotation,
	 * not body text, so it is not emitted. This policy is shared by link alternative text,
	 * string-set content(), bookmark headings, and target-text().
	 * Only malformed units with no base text emit ruby text in its place.
	 *
	 * <p>
	 * The container ({@code FlowContainer}) is empty, so this override alone handles extraction.
	 * </p>
	 */
	public void pushGetTextSteps(final StringBuilder textBuff, final java.util.Deque<GetTextStep> worklist) {
		textBuff.append(this.text);
	}

	public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
			AffineTransform transform, final double contextX, final double contextY, double x, double y,
			final java.util.Deque<DrawStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		transform = this.transform(transform, x, y);
		visitor.visitBox(transform, this, drawer, x, y);
		if (this.params.opacity == 0) {
			return;
		}
		drawer.visitDrawable(new RubyUnitDrawable(pageBox, clip, transform, this), x, y);
	}

	/**
	 * Draws the base glyph sequence and half-size annotation glyph sequences.
	 * (x, y) is the unit box's top-left corner.
	 */
	protected static class RubyUnitDrawable extends AbstractDrawable {
		private final RubyUnitBox box;

		RubyUnitDrawable(final PageBox pageBox, final Shape clip, final AffineTransform transform,
				final RubyUnitBox box) {
			super(pageBox, clip, box.params.opacity, transform);
			this.blendMode = box.params.blendMode;
			this.filter = box.params.filter;
			this.box = box;
		}

		public String describe() {
			final StringBuilder base = new StringBuilder();
			for (final TextImpl text : this.box.baseTexts) {
				base.append(text.getChars(), 0, text.getCharCount());
			}
			final StringBuilder ruby = new StringBuilder();
			if (this.box.annotations.length > 0) {
				appendText(ruby, this.box.annotations[0].texts);
			}
			final StringBuilder extra = new StringBuilder();
			for (int i = 1; i < this.box.annotations.length; ++i) {
				final RubyAnnotation annotation = this.box.annotations[i];
				final StringBuilder value = new StringBuilder();
				appendText(value, annotation.texts);
				extra.append(" ruby").append(i + 1).append(annotation.over ? "-over=\"" : "-under=\"")
						.append(value).append('\"');
			}
			return String.format(java.util.Locale.ROOT, "RubyUnit[\"%s\" ruby=\"%s\"%s w=%.2f h=%.2f]", base,
					ruby, extra, this.box.getWidth(), this.box.getHeight());
		}

		@Override
		public String describeGeometry(final double x, final double y) {
			if (!net.zamasoft.foliojet.layout.draw.DisplayListDumper.currentDetailedGeometry()
					|| this.box.params.writingModeVariant == WritingModeVariant.NORMAL) {
				return "";
			}
			final AffineTransform at = SidewaysGeometry.runTransform(this.box.params.writingModeVariant, x, y,
					this.box.baseAscent, this.box.baseDescent, this.box.getHeight());
			final double[] m = new double[6];
			at.getMatrix(m);
			return String.format(java.util.Locale.ROOT, " ruby-tf=[%.2f %.2f %.2f %.2f %.2f %.2f]",
					m[0], m[1], m[2], m[3], m[4], m[5]);
		}

		private static void appendText(final StringBuilder buff, final TextImpl[] texts) {
			for (final TextImpl text : texts) {
				buff.append(text.getChars(), 0, text.getCharCount());
			}
		}

		public void innerDraw(final GC gc, final double x, final double y) throws GraphicsException {
			final RubyUnitBox box = this.box;
			try (final var gcState = gc.begin()) {
				if (box.params.writingModeVariant != WritingModeVariant.NORMAL) {
					gc.transform(SidewaysGeometry.runTransform(box.params.writingModeVariant, x, y,
							box.baseAscent, box.baseDescent, box.getHeight()));
					double over = 0, under = 0;
					for (final RubyAnnotation annotation : box.annotations) {
						if (annotation.over) {
							this.drawRun(gc, annotation.texts, annotation.color,
									box.contentShift + box.annotationOrigin,
									-box.baseAscent - over - annotation.descent, false);
							over += annotation.ascent + annotation.descent;
						} else {
							this.drawRun(gc, annotation.texts, annotation.color,
									box.contentShift + box.annotationOrigin,
									box.baseDescent + under + annotation.ascent, false);
							under += annotation.ascent + annotation.descent;
						}
					}
					this.drawRun(gc, box.baseTexts, box.baseColor, box.contentShift, 0, false);
				} else if (box.flow.isVertical()) {
					// Vertical writing: over=right, under=left. Stack multiple levels outward.
					final double baseX = x + box.baseDescent;
					this.drawRun(gc, box.baseTexts, box.baseColor, baseX, y + box.contentShift, true);
					double over = 0, under = 0;
					for (final RubyAnnotation annotation : box.annotations) {
						final double rubyX;
						if (annotation.over) {
							rubyX = x + box.baseDescent + box.baseAscent + over + annotation.descent;
							over += annotation.ascent + annotation.descent;
						} else {
							rubyX = x - under - annotation.ascent;
							under += annotation.ascent + annotation.descent;
						}
						this.drawRun(gc, annotation.texts, annotation.color, rubyX,
								y + box.contentShift + box.annotationOrigin, true);
					}
				} else {
					// Horizontal writing: over=above, under=below. Place inter-character vertically on the right.
					double over = 0, under = 0;
					for (final RubyAnnotation annotation : box.annotations) {
						if (annotation.interCharacter) {
							final double rubyX = x + box.contentShift + box.getWidth() + annotation.descent;
							final double rubyY = y + Math.max(0,
									(box.baseAscent + box.baseDescent - annotation.advance) / 2.0);
							this.drawRun(gc, annotation.texts, annotation.color, rubyX, rubyY, true);
						} else if (annotation.over) {
							this.drawRun(gc, annotation.texts, annotation.color,
									x + box.contentShift + box.annotationOrigin,
									y - over - annotation.descent, false);
							over += annotation.ascent + annotation.descent;
						} else {
							this.drawRun(gc, annotation.texts, annotation.color,
									x + box.contentShift + box.annotationOrigin,
									y + box.baseAscent + box.baseDescent + under + annotation.ascent, false);
							under += annotation.ascent + annotation.descent;
						}
					}
					this.drawRun(gc, box.baseTexts, box.baseColor, x + box.contentShift, y + box.baseAscent, false);
				}
			}
		}

		private void drawRun(final GC gc, final TextImpl[] texts, final Color color, final double x, final double y,
				final boolean vertical) throws GraphicsException {
			if (texts.length == 0) {
				return;
			}
			try (final var gcState = gc.begin()) {
				if (color != null) {
					gc.setFillPaint(color);
				}
				double xx = x, yy = y;
				for (final TextImpl text : texts) {
					gc.drawText(text, xx, yy);
					if (vertical) {
						yy += text.getAdvance();
					} else {
						xx += text.getAdvance();
					}
				}
			}
		}
	}

	/**
	 * An empty container. The baseline
	 * ({@code getFirstAscent()}/{@code getLastDescent()}) is the unit's base-text baseline,
	 * so the existing inline-block mechanism (the {@code TextBuilder} BLOCK path)
	 * can align it unchanged.
	 */
	protected static class RubyUnitContainer extends FlowContainer {
		private double firstAscent, lastDescent;

		void setup(final double firstAscent, final double lastDescent) {
			this.firstAscent = firstAscent;
			this.lastDescent = lastDescent;
		}

		public double getFirstAscent() {
			return this.firstAscent;
		}

		public double getLastDescent() {
			return this.lastDescent;
		}
	}
}
