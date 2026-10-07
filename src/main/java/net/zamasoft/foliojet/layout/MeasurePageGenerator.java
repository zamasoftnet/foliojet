package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * A page generator for measurement (M2c).
 *
 * <p>
 * Generates scratch pages of the specified size without drawing. Replaying a range of source
 * events into this generator via {@link SourceReplayer} allows measurement through actual layout
 * (min/max-content and fit probes) without touching live state. Scratch replay creates fresh
 * boxes, so it avoids the shared-mutable-box and single-consumption restrictions of the old
 * two-pass measurement (the reason M2 was restaged).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class MeasurePageGenerator implements PageGenerator {
	private final UserAgent ua;

	private final BlockParams pageParams;
	private final LayoutSource layoutSource;
	private final boolean countRetainedText;

	private PageBox lastPage;

	private int pageCount = 0;
	private net.zamasoft.foliojet.layout.segment.BlockParamsTemplate probeTemplate;
	private java.util.Map<Long, Double> footnoteMeasurements;
	private String pendingPageName, pageName;
	private double pageInnerWidth, pageInnerHeight;
	private int emittedPages;
	private boolean namedPageGeometry;
	private java.util.function.BiFunction<String, Integer, FootnotePageProbe.PageGeometry> pageGeometry;
	private java.util.function.Consumer<PageMeasurement> pageObserver;
	private java.util.function.LongConsumer compactionObserver;

	/** Values at draw time. Not modified by subsequent pages or bodies arriving later. */
	record PageMeasurement(long generation, String pageName, boolean emitted, double innerWidth, double innerHeight,
			net.zamasoft.foliojet.layout.box.params.WritingMode flow, double h0, java.util.Set<Long> callIds,
			java.util.Map<Long, Double> heights, java.util.Set<Long> unmeasuredIds, boolean lastPage) { }

	void setPageObserver(final java.util.function.Consumer<PageMeasurement> observer) {
		this.pageObserver = observer;
	}

	void setCompactionObserver(final java.util.function.LongConsumer observer) {
		this.compactionObserver = observer;
	}

	@Override
	public void compactLayoutSource(final long watermark) {
		// Use B's watermark only to advance the pin. Compact the main log only at C's request.
		if (this.compactionObserver != null) this.compactionObserver.accept(watermark);
	}

	@Override
	public String getPageName() {
		return this.pendingPageName;
	}

	@Override
	public void setPageName(final String pageName) {
		this.pendingPageName = pageName;
	}

	/**
	 * Creates a page generator for measurement.
	 *
	 * @param ua       the user agent
	 * @param template computed parameters supplying fonts, writing direction, etc.
	 * @param width    the scratch page width
	 * @param height   the scratch page height
	 */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height) {
		this(ua, template, width, height, null);
	}

	/** Borrows the replay source. The scratch side does not append, compact, or close it. */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height, final LayoutSource layoutSource) {
		this(ua, template, width, height, layoutSource, true);
	}

	/** Excludes only margin-box and running-element mini-layouts from character accounting. */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height, final LayoutSource layoutSource, final boolean countRetainedText) {
		this.ua = ua;
		this.layoutSource = layoutSource;
		this.countRetainedText = countRetainedText;
		final BlockParams params = new BlockParams();
		params.fontStyle = template.fontStyle;
		params.fontManager = template.fontManager;
		params.lineBreakRules = template.lineBreakRules;
		params.flow = template.flow;
		params.writingModeVariant = template.writingModeVariant;
		params.direction = template.direction;
		params.unicodeBidi = template.unicodeBidi;
		params.bidiSemanticAlias = template.bidiSemanticAlias;
		params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		this.pageParams = params;
	}

	/** For the raw tee only. Duplicates the type area before reservations, excluding paper margins. */
	MeasurePageGenerator(final UserAgent ua,
			final net.zamasoft.foliojet.layout.segment.BlockParamsTemplate template,
			final double width, final double height, final LayoutSource source) {
		this.ua = ua;
		this.layoutSource = source;
		this.countRetainedText = true;
		final BlockParams params = template.materialize();
		params.frame = net.zamasoft.foliojet.layout.box.params.RectFrame.NULL_FRAME;
		params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		params.minSize = Dimension.ZERO_DIMENSION;
		params.maxSize = Dimension.AUTO_DIMENSION;
		params.boxSizing = net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX;
		this.pageParams = params;
		this.probeTemplate = net.zamasoft.foliojet.layout.segment.BlockParamsTemplate.freeze(params);
		this.footnoteMeasurements = new java.util.LinkedHashMap<>();
	}

	void setPageGeometry(final java.util.function.BiFunction<String, Integer, FootnotePageProbe.PageGeometry> geometry) {
		this.pageGeometry = geometry;
	}

	private java.util.function.LongSupplier deliveredEventEnd;

	public void setDeliveredEventEnd(final java.util.function.LongSupplier deliveredEventEnd) {
		this.deliveredEventEnd = deliveredEventEnd;
	}

	@Override
	public long getDeliveredEventEnd() {
		return this.deliveredEventEnd == null ? Long.MAX_VALUE : this.deliveredEventEnd.getAsLong();
	}

	public boolean isFootnoteProbe() {
		return this.probeTemplate != null;
	}

	/**
	 * Registers only after MEASURE bind completes for a TwoPass body.
	 * Does not resolve numbers or register for actual layout.
	 */
	public void measureFootnote(final net.zamasoft.foliojet.layout.box.impl.FloatBlockBox box) {
		if (this.footnoteMeasurements != null && box.getParams().footnoteId >= 0) {
			this.footnoteMeasurements.putIfAbsent(box.getParams().footnoteId,
					net.zamasoft.foliojet.layout.builder.impl.RootBuilder.footnoteBandExtent(box));
		}
	}

	/** Forgets only notes outside both the replayable input range and the pending ledger. */
	public void forgetFootnote(final long id) {
		if (this.footnoteMeasurements != null) this.footnoteMeasurements.remove(id);
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public boolean isRetainedTextCounted() {
		return this.countRetainedText;
	}

	@Override
	public LayoutSource getLayoutSource() {
		return this.layoutSource;
	}

	public PageBreakMode getPageSide() {
		return PageBreakMode.AUTO;
	}

	public PageBox nextPage() {
		++this.pageCount;
		this.pageName = this.pendingPageName;
		final BlockParams params = this.probeTemplate == null ? this.pageParams : this.probeTemplate.materialize();
		this.namedPageGeometry |= this.pageName != null;
		// Unnamed-only documents keep initial geometry. After a named transition, query even returns to unnamed pages.
		if (this.pageGeometry != null && this.namedPageGeometry) {
			final var geometry = this.pageGeometry.apply(this.pageName, this.emittedPages);
			params.size = Dimension.create(geometry.width(), geometry.height(), LengthType.ABSOLUTE, LengthType.ABSOLUTE);
			params.flow = geometry.flow();
		}
		this.lastPage = new PageBox(params, this.ua);
		this.pageInnerWidth = this.lastPage.getInnerWidth();
		this.pageInnerHeight = this.lastPage.getInnerHeight();
		return this.lastPage;
	}

	public boolean drawPage(final PageBox page, final boolean lastPage, final boolean closedByForcedBreak) {
		if (!this.isFootnoteProbe()) return true;
		// Do not draw. Record B's own output eligibility from body content, forced page breaks, and the final page.
		// B lacks running headers, etc., so its output presence is not guaranteed to match C's.
		final boolean paints = page.paintsAnything();
		final boolean emitted = !(page.isNamedTransitionClosed() && !paints)
				&& (paints || page.isForcedBreakOrigin() || (this.emittedPages == 0 && lastPage));
		if (emitted) ++this.emittedPages;
		final java.util.Set<Long> calls = net.zamasoft.foliojet.layout.builder.impl.RootBuilder.collectFootnoteCalls(page);
		final java.util.Map<Long, Double> heights = new java.util.LinkedHashMap<>();
		final java.util.Set<Long> unmeasured = new java.util.HashSet<>(calls);
		for (final long id : calls) {
			final Double height = this.footnoteMeasurements.get(id);
			if (height != null) {
				heights.put(id, height);
				unmeasured.remove(id);
			}
		}
		if (this.pageObserver != null) this.pageObserver.accept(new PageMeasurement(this.pageCount, this.pageName, emitted,
				this.pageInnerWidth, this.pageInnerHeight, page.getBlockParams().flow, page.getFootInset(),
				java.util.Set.copyOf(calls), java.util.Map.copyOf(heights), java.util.Set.copyOf(unmeasured), lastPage));
		return emitted;
	}

	/** Returns the number of generated pages (for fit probes). */
	public int getPageCount() {
		return this.pageCount;
	}

	/** Returns the last generated page (for actual content measurements). */
	public PageBox getLastPage() {
		return this.lastPage;
	}

	/** B's current width after transitioning to a named page. Before any transition, uses C's width as before. */
	double namedPageWidth() {
		return this.namedPageGeometry && this.lastPage != null ? this.pageInnerWidth : Double.NaN;
	}
}
