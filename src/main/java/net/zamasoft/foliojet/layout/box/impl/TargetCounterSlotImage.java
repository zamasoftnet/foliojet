package net.zamasoft.foliojet.layout.box.impl;

import java.awt.geom.Rectangle2D;
import java.net.URI;

import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.BaselineImage;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.PageRef;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;

/**
 * Number slot for {@code target-counter()} laid out in a one-pass PDF (2026-10-04,
 * docs/design/one-pass-target-counter-design.md).
 *
 * <p>
 * The slot width is "digit count × maximum digit advance", independent of the number's value.
 * This allows a table of contents to be laid out before the target pages are known. If the value
 * is known at draw time (a reference to an earlier page), draw it immediately. Otherwise (a reference
 * to a later page), only reference a PDF component from the page and write its content when closing
 * the document ({@link PDFGC#drawDeferredForm}). Numbers are right-aligned within the slot
 * and overflow to the left if they do not fit.
 * </p>
 *
 * <p>
 * The precedent is the fixed footnote-number slot ({@link FootnoteLabelImage}). That slot is filled
 * when its page is finalized, but this one is determined on another (later) page, so drawing is deferred.
 * The image holds only an immutable specification and creates a component on each draw
 * (not shared by repeated table headers or replicas).
 * </p>
 */
public final class TargetCounterSlotImage
		implements net.zamasoft.pdfg2d.gc.image.Image, ReplacedBoxImage, BaselineImage {

	private final UserAgent ua;

	private final URI uri;

	private final String counter;

	private final short numberStyleType;

	private final FontStyle fontStyle;

	private final FontManager fontManager;

	private final Color color;

	private final int digits;

	/** Width of one digit slot (the maximum advance among 0–9). */
	private final double digitAdvance;

	private final double ascent, descent;

	/**
	 * Whether {@code target-counter()} can be laid out as a slot in a one-pass PDF. Otherwise, retain
	 * the existing behavior (with two or more passes, lay out the previous pass's value as text).
	 * Do not use slots with {@code output.pdf.bidi.actual-text}: the slot becomes U+FFFC in the line's
	 * logical text, omitting the number.
	 */
	public static boolean available(final UserAgent ua) {
		return ua.paintsPageNumbersLater() && UAProps.PROCESSING_PASS_COUNT.getInteger(ua) == 1
				&& !UAProps.OUTPUT_PDF_BIDI_ACTUAL_TEXT.getBoolean(ua);
	}

	public TargetCounterSlotImage(final UserAgent ua, final URI uri, final String counter,
			final short numberStyleType, final FontStyle fontStyle, final Color color) {
		this.ua = ua;
		this.uri = uri;
		this.counter = counter;
		this.numberStyleType = numberStyleType;
		this.fontStyle = fontStyle;
		this.fontManager = ua.getFontManager();
		this.color = color;
		this.digits = Math.max(1, Math.min(9, UAProps.PROCESSING_TARGET_COUNTER_DIGITS.getInteger(ua)));
		final FontMetrics fm = this.fontManager.getFontListMetrics(fontStyle).getFontMetrics(0);
		this.ascent = fm.getAscent();
		this.descent = fm.getDescent();
		double digit = 0;
		for (char c = '0'; c <= '9'; ++c) {
			digit = Math.max(digit, this.measure(String.valueOf(c)));
		}
		this.digitAdvance = digit;
		ua.getUAContext().noteTargetCounterSlot();
	}

	/** Whether the target value is already known. */
	public boolean isResolved() {
		return this.resolve() != null;
	}

	/**
	 * Collects slots in recorded drawing operations (including nested group images). Page-split SVG
	 * defers output of pages containing slots with unknown values ({@code PagedSVGUserAgent}).
	 */
	public static java.util.List<TargetCounterSlotImage> slots(final net.zamasoft.pdfg2d.gc.RecorderGC.Page page) {
		final java.util.List<TargetCounterSlotImage> slots = new java.util.ArrayList<>();
		collect(page, slots);
		return slots;
	}

	private static void collect(final net.zamasoft.pdfg2d.gc.RecorderGC.Page page,
			final java.util.List<TargetCounterSlotImage> slots) {
		for (final net.zamasoft.pdfg2d.gc.RecorderGC.Command command : page.commands()) {
			final net.zamasoft.pdfg2d.gc.image.Image image;
			if (command instanceof net.zamasoft.pdfg2d.gc.RecorderGC.DrawImage draw) {
				image = draw.image();
			} else if (command instanceof net.zamasoft.pdfg2d.gc.RecorderGC.DrawImageEffects draw) {
				image = draw.image();
			} else {
				continue;
			}
			if (image instanceof TargetCounterSlotImage slot) {
				slots.add(slot);
			} else if (image instanceof net.zamasoft.pdfg2d.gc.RecorderGC.RecorderImage group) {
				collect(group.getPage(), slots);
			}
		}
	}

	@Override
	public double getWidth() {
		return this.digitAdvance * this.digits;
	}

	@Override
	public double getHeight() {
		return this.ascent + this.descent;
	}

	/** Numbers are text, so align their text baseline with the line baseline. */
	@Override
	public double getDescent() {
		return this.descent;
	}

	/** Target URI (for the display list). */
	public URI getURI() {
		return this.uri;
	}

	@Override
	public String getAltString() {
		final String text = this.resolve();
		return text == null ? "" : text;
	}

	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		try (final var state = gc.begin()) {
			gc.setFillPaint(this.color);
			final String text = this.resolve();
			if (text != null) {
				this.paint(gc, text);
				return;
			}
			if (unwrapApproximation(gc) instanceof PDFGC pdf) {
				pdf.drawDeferredForm(this.getWidth(), this.getHeight(), form -> {
					final String later = this.resolve();
					if (later == null) {
						// No target exists. Leave the slot empty, as in two-pass mode.
						return null;
					}
					form.setFillPaint(this.color);
					return this.paint(form, later);
				});
				return;
			}
			// A drawing target that cannot be written later, such as inside a rasterizing filter. When drawing
			// deferred pages at document end (page-split SVG), no target was ever found, so stay silent (as in PDF).
			if (!this.ua.getUAContext().isDrawingHeldPages()) {
				this.report("2822.target-counter-unresolved");
			}
		}
	}

	/**
	 * Unwraps only wrappers that report approximations. Do not unwrap filters and similar wrappers:
	 * drawing must occur inside them (where deferred writing is unavailable).
	 */
	private static GC unwrapApproximation(GC gc) {
		while (gc instanceof ApproximationGC a) {
			gc = a.delegate();
		}
		return gc;
	}

	/**
	 * Reads only target values registered in this pass. Do not use values from the previous pass
	 * (an intermediate pass in continuous conversion), because they are not final.
	 */
	private String resolve() {
		final PageRef pageRef = this.ua.getUAContext().getPageRef();
		final PageRef.Fragment fragment = pageRef.getFragment(this.uri);
		if (fragment == null || fragment.generation != pageRef.getGeneration()) {
			return null;
		}
		return CounterStyles.of(this.ua).format(fragment.getCounterValue(this.counter), this.numberStyleType);
	}

	/** Draws aligned to the slot's right edge and returns the painted bounds. */
	private Rectangle2D paint(final GC gc, final String text) throws GraphicsException {
		final TextImpl[] runs = this.shape(text);
		double advance = 0;
		for (final TextImpl run : runs) {
			advance += run.getAdvance();
		}
		final double width = this.getWidth();
		if (advance > width + 0.01) {
			this.report("2822.target-counter-digits");
		}
		double x = width - advance;
		final double left = x;
		for (final TextImpl run : runs) {
			gc.drawText(run, x, this.ascent);
			x += run.getAdvance();
		}
		// Leave one character height of space on all four sides to avoid clipping glyph overhangs.
		final double height = this.getHeight();
		return new Rectangle2D.Double(Math.min(0, left) - height, -height, Math.max(width, advance) + height * 2,
				height * 3);
	}

	private void report(final String detailKey) {
		if (this.ua.getUAContext().getReportedApproximations().add("target-counter() " + detailKey)) {
			this.ua.message(MessageCodes.WARN_APPROXIMATED_RENDERING, "target-counter()",
					UAProps.OUTPUT_TYPE.getString(this.ua),
					net.zamasoft.foliojet.message.MessageCodeUtils.detail(detailKey));
		}
	}

	@Override
	public void setReplacedBox(final AbstractReplacedBox box, final double width, final double height) {
		// No back-reference is needed (size is a fixed slot).
	}

	/** Immutable, so the replica can be this instance itself. */
	@Override
	public net.zamasoft.pdfg2d.gc.image.Image duplicate() {
		return this;
	}

	private double measure(final String text) {
		double advance = 0;
		for (final TextImpl run : this.shape(text)) {
			advance += run.getAdvance();
		}
		return advance;
	}

	private TextImpl[] shape(final String text) {
		return net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns.shape(this.fontManager, this.fontStyle, text, -1,
				false);
	}
}
