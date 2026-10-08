package net.zamasoft.foliojet.ua;

import java.io.IOException;
import java.util.Map;

import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * UA profile.
 * {@link DeviceStyle} reads device defaults; {@link PageOutput} handles page output.
 *
 * @author MIYABE Tatsuhiko
 */
public interface UserAgent extends SourceResolver, MessageHandler, DeviceStyle, PageOutput {
	/**
	 * Prepares a processing stage.
	 */
	public void prepare(PrepareMode mode);

	public UAContext getUAContext();

	public PassContext getPassContext();

	/** This UA's retained-content accounting, preserved across passes. */
	public net.zamasoft.foliojet.layout.RetainedTextLimit getRetainedTextLimit();

	public DocumentContext getDocumentContext();

	/**
	 * Starts one of several documents laid out into this output in the current pass (an EPUB spine item,
	 * 2026-10-08): a fresh {@link DocumentContext} whose base URI and document URI are {@code documentURI}, and the
	 * document's own footnote area. Pages, counters and running strings continue.
	 */
	public void beginDocument(java.net.URI documentURI);

	/**
	 * Returns a property.
	 */
	public String getProperty(String name);

	/**
	 * Sets a property.
	 */
	public void setProperty(String name, String value);

	public void setProperties(Map<String, String> props);

	public void setSourceResolver(SourceResolver resolver);

	public SourceResolver getSourceResolver();

	public void setMessageHandler(jp.cssj.cti2.message.MessageHandler messageHandler);

	/**
	 * Aborts processing. mode is a CTISession ABORT_* value.
	 */
	public void abort(byte mode);

	/**
	 * <b>Cooperative abort point.</b> Throws {@link AbortException} if {@link #abort(byte)} was called.
	 *
	 * <p>
	 * <b>Why it is needed.</b> {@code abort()} is the only external way to stop conversion, but it
	 * <b>only sets a flag</b>; nothing happens unless the engine reads that flag somewhere.
	 * Previously, reads occurred only at page boundaries, so <b>a document whose single-page processing
	 * never completed could never be stopped</b>
	 * (found 2026-07-27 when a 100,000-document sweep stalled).
	 * </p>
	 *
	 * <p>
	 * Call at the start of long-running loops. Keep granularity coarse: lines, table rows, or pages.
	 * The cost is one volatile read, but it accumulates if placed at glyph level.
	 * </p>
	 */
	public void checkAbort(byte mode);

	/**
	 * Records that <b>one unit of actual work completed</b>.
	 * The deadline uses this as the basis for determining whether processing is stalled.
	 *
	 * <p>
	 * <b>Call where "work completed," not merely where "code ran."</b>
	 * Calling from a loop that spins without progress fabricates progress and defeats the deadline.
	 * Current callers: page output, image load completion, and table row finalization.
	 * </p>
	 */
	public void noteProgress();

	/**
	 * Retrieves an image.
	 */
	public Image getImage(Source source) throws IOException;

	/**
	 * Returns recorded image dimensions <b>before resolving the resource</b>.
	 *
	 * <p>
	 * In a dimension-only pass, an already measured image avoids a call to {@link #resolve(java.net.URI)}.
	 * On paths where resolution itself fetches the resource (requesting it from a client over CTIP),
	 * a transfer occurs unless this is consulted first.
	 * </p>
	 *
	 * @return recorded dimensions if available, otherwise {@code null}.
	 */
	public default Image getImageMetrics(java.net.URI uri) {
		return null;
	}

	/**
	 * Retrieves an image and, in a dimension-only pass, records dimensions <b>under the requested URI</b>.
	 * Preserves relative URIs, so the same EPUB still matches when supplied from a different base.
	 */
	public default Image getImage(java.net.URI uri, Source source) throws IOException {
		return this.getImage(source);
	}

	public boolean isMeasurePass();

	/**
	 * Returns whether the current pass is STRUCTURE_SCAN (lightweight preliminary scan
	 * without actual layout, PrepareMode.STRUCTURE_SCAN).
	 */
	public boolean isStructureScanPass();

	/**
	 * Returns whether the current pass is the final pass (PrepareMode.LAST_PASS).
	 * Used to check convergence of target-counter() values
	 * (whether reference targets are finalized by the final pass).
	 */
	public boolean isLastPass();

	/**
	 * Returns whether the <b>original bytes</b> of a retrieved image can be used (2026-08-28).
	 *
	 * <p>
	 * Outputs that emit images as "resources read by a browser," such as page-split SVG,
	 * return {@code true}. Decoding JPEG and re-encoding as PNG wastes both time and space;
	 * measurements showed resources for one Wikipedia article growing from about 8 MB to 30.5 MB.
	 * Outputs such as PDF that convert images to their own representation leave this {@code false}.
	 * </p>
	 *
	 * <p>
	 * <b>Do not decide from output properties.</b> This once used a string comparison of
	 * {@code output.type}, but it never matched because the UA returned {@code application/pdf}
	 * at image-loading time (discovered by measurement). Ask the UA itself about its capability.
	 * </p>
	 */
	public default boolean keepsEncodedImages() {
		return false;
	}

	/**
	 * Returns whether an unknown page number can be written after drawing (2026-10-04).
	 *
	 * <p>
	 * PDF can reference a component (Form XObject) from a page and write its contents when closing
	 * the document. If this returns {@code true} and there is one pass, {@code target-counter()}
	 * is laid out as a fixed-width slot, and references to later pages also display their numbers
	 * ({@code TargetCounterSlotImage}).
	 * </p>
	 */
	public default boolean paintsPageNumbersLater() {
		return false;
	}
}
