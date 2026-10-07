package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public interface PageGenerator {
	
	public UserAgent getUserAgent();

	public PageBreakMode getPageSide();

	public PageBox nextPage();

	/** Reports page-start geometry. Only the first call in C precedes reservations and child input. */
	public default void pageStarted(final PageBox page, final double innerWidth, final double innerHeight) {
	}

	/**
	 * Current page name (named pages N2; null = unnamed).
	 */
	public default String getPageName() {
		return null;
	}

	/**
	 * Sets the page name from the next generated page onward (named pages N2).
	 * A no-op for implementations without a page concept, such as scratch measurement.
	 */
	public default void setPageName(String pageName) {
	}

	/**
	 * Outputs a page.
	 *
	 * <p>
	 * <b>Pages that paint nothing are not output</b> (css-break-3 §4.4, 2026-07-28).
	 * Dropped pages consume neither a page number nor a side (recto/verso), so callers tracking
	 * the page sequence must check the return value.
	 * </p>
	 *
	 * @param page     finalized page
	 * @param lastPage whether this is <b>the document's last page</b> (added 2026-07-29).
	 *                 Used to decide whether a page that paints nothing may be dropped: if it is not
	 *                 last, subsequent content exists, so it may be dropped; dropping the last page
	 *                 could produce a zero-page PDF
	 * @param closedByForcedBreak whether a <b>forced page break</b> closed this page.
	 *                 Preserves blank pages explicitly requested by the author, such as those caused
	 *                 by {@code page-break-before:always} on the first element
	 * @return true if actually output; false if dropped because it paints nothing
	 */
	public boolean drawPage(PageBox page, boolean lastPage, boolean closedByForcedBreak) throws GraphicsException;

	/**
	 * Returns the layout source log (M6b v3). null for implementations without one.
	 */
	public default net.zamasoft.foliojet.layout.fragment.LayoutSource getLayoutSource() {
		return null;
	}

	/** Visible end (exclusive), including the event being delivered as live input. Distinct from the read-ahead log's end. */
	public default long getDeliveredEventEnd() {
		return Long.MAX_VALUE;
	}

	/** Only production bottom + vertical writing uses the queue and reservation plan before initial input. */
	public default boolean isFootnotePageProbeEnabled() {
		return false;
	}

	/** Receives once at page start; consumes the generator's retained value whether accepted or rejected. */
	public default net.zamasoft.foliojet.layout.FootnotePageProbeReport getFootnotePageProbeReport(final long generation) {
		return null;
	}

	/** Even without a report, distinguishes pending from normal completion. Both proceed only by carrying forward. */
	public default boolean isFootnotePageProbeFinished() {
		return true;
	}

	/**
	 * Prunes the layout source log at the watermark (M6b v3).
	 * Long.MAX_VALUE as the watermark means everything except open StartBlock entries may be discarded.
	 */
	public default void compactLayoutSource(long watermark) {
	}
}
