package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Params;

import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * @author MIYABE Tatsuhiko
 */
public class StyleBuilder implements PageGenerator, StyleBuildContext {

	/**
	 * Total page count counter name. Treated as a UA-reserved counter corresponding to
	 * css-page-3 §6.1 and protected from author {@code counter-reset}/{@code counter-increment}
	 * (see {@link #isReservedCounterName(String)}).
	 */
	private static final String PAGES_COUNTER_NAME = "pages";

	static boolean isReservedCounterName(String name) {
		return PAGES_COUNTER_NAME.equalsIgnoreCase(name);
	}

	private final UserAgent ua;

	private final DocumentBuilder doc;
	private final Imposition imposition;
	private StyleContext styleContext;
	private CSSStyle currentStyle;

	private FlowBlockBox htmlRootBlock = null;
	private boolean rightSide = false;
	private boolean inBody = false;
	private boolean inTextBlock = false;

	/**
	 * Event count of the main-flow style window. Retains no style or character references.
	 * Replay uses the sink's LayoutSource; pseudo-elements and generated content are frozen too.
	 */
	private final Segment segment = new Segment();

	/**
	 * M6b v3 layout source protocol tee (recording + delivery to doc).
	 * See {@link RecordingLayoutSink} for the recording contract and {@code LayoutSource}
	 * lifetime (extracted in increment 1 of StyleBuilder decomposition, 2026-07-30).
	 */
	private final RecordingLayoutSink sink;

	/**
	 * Page lifecycle (creation, @page counters, blank-page checks and rollback, drawing,
	 * and imposition completion). Extracted in increment 2 of StyleBuilder decomposition
	 * (2026-07-30). StyleBuilder still implements PageGenerator, delegating page methods here.
	 */
	private final PageSequence pageSequence;

	/**
	 * Mappings from computed CSS values to Params/Pos/RectFrame. Extracted in increment 3
	 * of StyleBuilder decomposition (2026-07-30).
	 */
	private final BoxStyleMapper mapper;

	/**
	 * Box dispatch by display and anonymous table completion. Extracted in increment 4a
	 * of StyleBuilder decomposition (2026-07-30; moved verbatim, retaining anonymous-table
	 * recursion; iteration follows in increment 4b. State shared through {@link StyleBuildContext}).
	 */
	private final StyleBoxEmitter emitter;

	/**
	 * Style event state machine (counters, string-set, markers, quotes, generated content,
	 * ::first-letter). Extracted in increment 5 of StyleBuilder decomposition
	 * (2026-07-30, moved verbatim).
	 */
	private final StyleEventMachine eventMachine;

	/**
	 * Returns the layout source log (M6b v3).
	 */
	public LayoutSource getLayoutSource() {
		return this.sink.source();
	}

	@Override
	public long getDeliveredEventEnd() {
		return this.sink.deliveredEventEnd();
	}

	@Override
	public boolean isFootnotePageProbeEnabled() {
		return this.ua.getUAContext().getFootnoteArea().isPageBand()
				&& !this.ua.getUAContext().getFootnoteArea().isHeightFixed()
				&& this.pageSequence.getProgression().isVertical();
	}

	@Override
	public net.zamasoft.foliojet.layout.FootnotePageProbeReport getFootnotePageProbeReport(final long generation) {
		return this.sink.report(generation);
	}

	@Override
	public boolean isFootnotePageProbeFinished() {
		return this.sink.probeFinished();
	}

	public void compactLayoutSource(final long watermark) {
		this.sink.compact(watermark);
	}

	/**
	 * Closes the layout source spill store (temporary file) (E-6 increment 3b-2).
	 * Called at conversion end via {@code CSSProcessor.dispose()} from the formatter's
	 * finally, on success or exception. Idempotent.
	 */
	public void closeLayoutSource() {
		this.sink.close();
	}

	public StyleBuilder(StyleContext styleContext, UserAgent ua, Imposition imposition) {
		this.styleContext = styleContext;
		this.ua = ua;
		this.imposition = imposition;
		this.doc = new DocumentBuilder(this);
		// E-6 increment 3b-2: the sink injects the text payload spill budget (bytes)
		this.sink = new RecordingLayoutSink(this.doc, UAProps.PROCESSING_TEXT_SPILL_BUDGET.getLong(ua));
		this.sink.setAssignments(ua.getPassContext().getRunningRegistry());

		byte pageMode = 0;
		// Automatic height
		if (UAProps.OUTPUT_AUTO_HEIGHT.getBoolean(ua)) {
			pageMode |= DocumentBuilder.PAGE_MODE_CONTINUOUS;
		}

		// Prohibit page breaks
		if (UAProps.OUTPUT_NO_PAGE_BREAK.getBoolean(ua)) {
			pageMode |= DocumentBuilder.PAGE_MODE_NO_BREAK;
		}
		this.doc.setPageMode(pageMode);

		// Initialization of page width, height, margins, and maximum page count
		// moved to the PageSequence constructor (increment 2, 2026-07-30;
		// warning message order is also unchanged).
		this.pageSequence = new PageSequence(ua, styleContext, imposition, this.doc, this.segment,
				this::warnReservedCounter);
		this.mapper = new BoxStyleMapper(ua, styleContext);
		this.emitter = new StyleBoxEmitter(this, this.sink, this.mapper, this.pageSequence, ua, imposition);
		this.eventMachine = new StyleEventMachine(this, this.segment, this.sink, this.mapper, this.emitter,
				this.pageSequence, ua, styleContext);
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public CSSElement getPageElement() {
		return this.pageSequence.getPageElement();
	}

	public CSSStyle getCurrentStyle() {
		final CSSStyle captured = this.eventMachine == null ? null : this.eventMachine.capturedStyle();
		return captured == null ? this.currentStyle : captured;
	}

	public void startStyle(final CSSStyle style) {
		this.eventMachine.startStyle(style);
	}

	public void characters(final int charOffset, final char[] ch, final int off, final int len) {
		this.eventMachine.characters(charOffset, ch, off, len);
	}

	public void endStyle() {
		this.eventMachine.endStyle();
	}

	@Override
	public void checkMarker() {
		this.eventMachine.checkMarker();
	}

	/** Delegate for PageSequence reserved-counter warnings (implementation moved to the machine in increment 5). */
	void warnReservedCounter(final String name) {
		this.eventMachine.warnReservedCounter(name);
	}

	public PageBreakMode getPageSide() {
		return this.pageSequence.getPageSide();
	}

	public PageBox nextPage() {
		return this.pageSequence.nextPage();
	}

	@Override
	public void pageStarted(final PageBox page, final double innerWidth, final double innerHeight) {
		this.sink.pageStarted(page, innerWidth, innerHeight, this.pageSequence.getPageName(), this.pageSequence::footnotePageGeometry);
	}

	@Override
	public String getPageName() {
		return this.pageSequence.getPageName();
	}

	@Override
	public void setPageName(String pageName) {
		this.pageSequence.setPageName(pageName);
	}

	public boolean drawPage(final PageBox pageBox, final boolean lastPage, final boolean closedByForcedBreak)
			throws GraphicsException {
		return this.pageSequence.drawPage(pageBox, lastPage, closedByForcedBreak);
	}

	public void finish() throws GraphicsException {
		this.sink.finishProbes();
		this.doc.end();
		this.pageSequence.finish();
		// E-6 increment 3b-2: no source replay occurs after the final page is finalized,
		// so release the spill store temporary file early here (on exceptions,
		// formatter finally→CSSProcessor.dispose→closeLayoutSource cleans up).
		this.sink.close();
	}

	// ---- StyleBuildContext (increment 4a): state remains physically here for now ----

	// Reuse the existing public getCurrentStyle() method (StyleBuildContext implementation)

	@Override
	public void setCurrentStyle(final CSSStyle style) {
		this.currentStyle = style;
	}

	@Override
	public FlowBlockBox getHtmlRootBlock() {
		return this.htmlRootBlock;
	}

	@Override
	public void setHtmlRootBlock(final FlowBlockBox box) {
		this.htmlRootBlock = box;
	}

	@Override
	public boolean isInBody() {
		return this.inBody;
	}

	@Override
	public void setInBody(final boolean inBody) {
		this.inBody = inBody;
	}

	@Override
	public boolean isInTextBlock() {
		return this.inTextBlock;
	}

	@Override
	public void setInTextBlock(final boolean inTextBlock) {
		this.inTextBlock = inTextBlock;
	}

	@Override
	public boolean isRightSide() {
		return this.rightSide;
	}

	@Override
	public void setRightSide(final boolean rightSide) {
		this.rightSide = rightSide;
	}
}
