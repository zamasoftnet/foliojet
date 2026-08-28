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
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRules;

/**
 * JLREQ 3.4の短い割注を、半サイズの2段として保持するatomic inline。
 * 行送りへの寄与は本文1行分のままにし、2段の字面はその中へ配置します。
 */
public final class WarichuUnitBox extends InlineBlockBox {
	/** 1断片を2段各4字程度に抑え、狭い本文行でも次行へ送れるようにする。 */
	private static final int MAX_FRAGMENT_CODE_POINTS = 8;
	private final TextImpl[] firstTexts, secondTexts;
	private final Color color;
	private final WritingMode flow;
	private final double baseAscent, baseDescent;
	private final double firstAscent, firstDescent, secondAscent, secondDescent;
	private final String text, first, second;
	private final int sourceStart, sourceEnd;

	private WarichuUnitBox(final BlockParams params, final InlinePos pos, final WarichuContainer container,
			final TextImpl[] firstTexts, final TextImpl[] secondTexts, final Color color, final WritingMode flow,
			final double lineExtent, final double baseAscent, final double baseDescent, final double firstAscent,
			final double firstDescent, final double secondAscent, final double secondDescent, final String text,
			final String first, final String second, final int sourceStart, final int sourceEnd) {
		super(params, pos, Dimension.AUTO_DIMENSION, Dimension.ZERO_DIMENSION,
				new AbsoluteRectFrame(RectFrame.NULL_FRAME), container);
		this.firstTexts = firstTexts;
		this.secondTexts = secondTexts;
		this.color = color;
		this.flow = flow;
		this.baseAscent = baseAscent;
		this.baseDescent = baseDescent;
		this.firstAscent = firstAscent;
		this.firstDescent = firstDescent;
		this.secondAscent = secondAscent;
		this.secondDescent = secondDescent;
		this.text = text;
		this.first = first;
		this.second = second;
		this.sourceStart = sourceStart;
		this.sourceEnd = sourceEnd;
		final double pageExtent = baseAscent + baseDescent;
		if (flow.isVertical()) {
			this.width = pageExtent;
			this.height = lineExtent;
		} else {
			this.width = lineExtent;
			this.height = pageExtent;
		}
		container.setup(baseAscent, baseDescent);
	}

	public boolean isPreMeasured() {
		return true;
	}

	public int getSourceStart() {
		return this.sourceStart;
	}

	public int getSourceEnd() {
		return this.sourceEnd;
	}

	public static WarichuUnitBox create(final InlineParams container, final String text,
			final InlineParams textParams, final int charOffset, final int sourceStart, final int sourceEnd) {
		if (text.isEmpty()) {
			return null;
		}
		final InlineParams tp = textParams == null ? container : textParams;
		final int split = split(text, tp.lineBreakRules);
		final String first = text.substring(0, split);
		final String second = text.substring(split);
		final FontStyle baseFs = container.fontStyle;
		final FontStyle srcFs = tp.fontStyle;
		final FontStyle smallFs = new FontStyleImpl(srcFs.getFamily(), baseFs.getSize() / 2.0, srcFs.getStyle(),
				srcFs.getWeight(), srcFs.getDirection(), srcFs.getPolicy(), srcFs.getFeatures(),
				srcFs.getSynthesisWeight(), srcFs.getSynthesisStyle(), srcFs.getTextOrientation(),
				srcFs.getWidthClass());
		final TextImpl[] firstTexts = shape(tp, smallFs, first, charOffset);
		final TextImpl[] secondTexts = shape(tp, smallFs, second, charOffset < 0 ? -1 : charOffset + split);
		final double firstAdvance = totalAdvance(firstTexts);
		final double secondAdvance = totalAdvance(secondTexts);
		final double lineExtent = Math.max(firstAdvance, secondAdvance);
		if (lineExtent <= 0) {
			return null;
		}
		center(firstTexts, lineExtent - firstAdvance);
		center(secondTexts, lineExtent - secondAdvance);

		final FontListMetrics baseMetrics = container.fontManager.getFontListMetrics(baseFs);
		final double baseAscent = baseMetrics.getMaxAscent();
		final double baseDescent = baseMetrics.getMaxDescent();
		final FontListMetrics smallMetrics = tp.fontManager.getFontListMetrics(smallFs);
		final double firstAscent = firstTexts.length == 0 ? smallMetrics.getMaxAscent() : maxAscent(firstTexts);
		final double firstDescent = firstTexts.length == 0 ? smallMetrics.getMaxDescent() : maxDescent(firstTexts);
		final double secondAscent = secondTexts.length == 0 ? smallMetrics.getMaxAscent() : maxAscent(secondTexts);
		final double secondDescent = secondTexts.length == 0 ? smallMetrics.getMaxDescent() : maxDescent(secondTexts);

		final BlockParams params = new BlockParams();
		params.element = null;
		params.opacity = tp.opacity;
		params.fontStyle = baseFs;
		params.fontManager = container.fontManager;
		params.lineBreakRules = container.lineBreakRules;
		params.direction = container.direction;
		params.flow = container.flow;
		params.color = tp.color;
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.lineHeight = 0;
		final InlinePos pos = new InlinePos();
		pos.lineHeight = 0;
		return new WarichuUnitBox(params, pos, new WarichuContainer(), firstTexts, secondTexts, tp.color,
				container.flow, lineExtent, baseAscent, baseDescent, firstAscent, firstDescent, secondAscent,
				secondDescent, text, first, second, sourceStart, sourceEnd);
	}

	/**
	 * 長い割注を、禁則を破らない複数のatomic二段断片へ分ける。断片間は通常の
	 * インライン分割機会になるため、割注全体が本文の複数行にまたがれる。
	 */
	public static List<WarichuUnitBox> createFragments(final InlineParams container, final String text,
			final InlineParams textParams, final int charOffset, final int sourceStart, final int sourceEnd) {
		if (text.isEmpty()) {
			return List.of();
		}
		final InlineParams tp = textParams == null ? container : textParams;
		final List<WarichuUnitBox> result = new ArrayList<>();
		int from = 0;
		while (from < text.length()) {
			final int to = fragmentEnd(text, from, tp.lineBreakRules);
			final int fragmentSourceStart = sourceStart < 0 ? -1 : Math.min(sourceEnd, sourceStart + from);
			final int fragmentSourceEnd = sourceEnd < 0 ? -1
					: to == text.length() ? sourceEnd : Math.min(sourceEnd, sourceStart + to);
			final WarichuUnitBox box = create(container, text.substring(from, to), tp,
					charOffset < 0 ? -1 : charOffset + from, fragmentSourceStart, fragmentSourceEnd);
			if (box != null) {
				result.add(box);
			}
			from = to;
		}
		return result;
	}

	private static int fragmentEnd(final String text, final int from, final TextBreakingRules rules) {
		final int remaining = text.codePointCount(from, text.length());
		if (remaining <= MAX_FRAGMENT_CODE_POINTS) {
			return text.length();
		}
		final int ideal = text.offsetByCodePoints(from, MAX_FRAGMENT_CODE_POINTS);
		for (int at = ideal; at > from; --at) {
			if (legalBoundary(text, at, rules)) {
				return at;
			}
		}
		// 長い不可分列（欧文単語等）は途中で壊さず、最初の合法境界まで延ばす。
		for (int at = ideal + 1; at < text.length(); ++at) {
			if (legalBoundary(text, at, rules)) {
				return at;
			}
		}
		return text.length();
	}

	private static boolean legalBoundary(final String text, final int at, final TextBreakingRules rules) {
		if (at <= 0 || at >= text.length() || Character.isLowSurrogate(text.charAt(at))) {
			return false;
		}
		return rules == null || !rules.atomic(text.charAt(at - 1), text.charAt(at));
	}

	/** 中央付近で、行頭・行末禁則を破らない境界を選ぶ。 */
	private static int split(final String text, final TextBreakingRules rules) {
		if (text.length() <= 1) {
			return text.length();
		}
		final int middle = text.offsetByCodePoints(0, text.codePointCount(0, text.length()) / 2);
		int best = -1, score = Integer.MAX_VALUE;
		for (int i = 1; i < text.length(); ++i) {
			if (Character.isLowSurrogate(text.charAt(i))) {
				continue;
			}
			if (rules != null && rules.atomic(text.charAt(i - 1), text.charAt(i))) {
				continue;
			}
			final int s = Math.abs(i - middle);
			if (s < score) {
				best = i;
				score = s;
			}
		}
		return best < 0 ? middle : best;
	}

	private static TextImpl[] shape(final InlineParams src, final FontStyle fontStyle, final String text,
			final int charOffset) {
		return text.isEmpty() ? new TextImpl[0]
				: net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns.shape(src.fontManager, fontStyle, text,
						charOffset, src.textSpacingTrimOff);
	}

	private static double totalAdvance(final TextImpl[] texts) {
		double value = 0;
		for (final TextImpl text : texts) {
			value += text.getAdvance();
		}
		return value;
	}

	private static double maxAscent(final TextImpl[] texts) {
		double value = 0;
		for (final TextImpl text : texts) {
			value = Math.max(value, text.getAscent());
		}
		return value;
	}

	private static double maxDescent(final TextImpl[] texts) {
		double value = 0;
		for (final TextImpl text : texts) {
			value = Math.max(value, text.getDescent());
		}
		return value;
	}

	private static void center(final TextImpl[] texts, final double extra) {
		if (extra <= 0.0001) {
			return;
		}
		for (final TextImpl text : texts) {
			text.resetXAdvances();
			if (text.getGlyphCount() > 0) {
				text.addXAdvance(0, extra / 2.0);
				return;
			}
		}
	}

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
		if (this.params.opacity != 0) {
			drawer.visitDrawable(new WarichuDrawable(pageBox, clip, transform, this), x, y);
		}
	}

	private static final class WarichuDrawable extends AbstractDrawable {
		private final WarichuUnitBox box;

		WarichuDrawable(final PageBox pageBox, final Shape clip, final AffineTransform transform,
				final WarichuUnitBox box) {
			super(pageBox, clip, box.params.opacity, transform);
			this.blendMode = box.params.blendMode;
			this.filter = box.params.filter;
			this.box = box;
		}

		public String describe() {
			return String.format(java.util.Locale.ROOT, "Warichu[\"%s\" first=\"%s\" second=\"%s\" w=%.2f h=%.2f]",
					this.box.text, this.box.first, this.box.second, this.box.getWidth(), this.box.getHeight());
		}

		public void innerDraw(final GC gc, final double x, final double y) throws GraphicsException {
			final WarichuUnitBox box = this.box;
			try (final var state = gc.begin()) {
				if (box.color != null) {
					gc.setFillPaint(box.color);
				}
				if (box.flow.isVertical()) {
					drawRun(gc, box.firstTexts, x + box.baseAscent + box.baseDescent - box.firstAscent, y, true);
					drawRun(gc, box.secondTexts, x + box.secondDescent, y, true);
				} else {
					drawRun(gc, box.firstTexts, x, y + box.firstAscent, false);
					drawRun(gc, box.secondTexts, x,
							y + box.baseAscent + box.baseDescent - box.secondDescent, false);
				}
			}
		}

		private static void drawRun(final GC gc, final TextImpl[] texts, double x, double y,
				final boolean vertical) throws GraphicsException {
			for (final TextImpl text : texts) {
				gc.drawText(text, x, y);
				if (vertical) {
					y += text.getAdvance();
				} else {
					x += text.getAdvance();
				}
			}
		}
	}

	private static final class WarichuContainer extends FlowContainer {
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
