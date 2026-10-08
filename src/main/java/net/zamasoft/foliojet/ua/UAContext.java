package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.font.FontFeatureValues;
import net.zamasoft.foliojet.css.font.FontPaletteValues;
import net.zamasoft.pdfg2d.font.FontSourceManager;

/**
 * State related to processing in the current UA.
 */
public class UAContext {
	private int passCount = 0;

	private final PageRef pageRef = new PageRef();

	private final SelectorFacts selectorFacts = new SelectorFacts();

	private final ContainerFacts containerFacts = new ContainerFacts();

	private final ImageMetricsCache imageMetrics = new ImageMetricsCache();

	/**
	 * The document being laid out when several documents go into one output (the spine items of an EPUB, see
	 * {@code UserAgent.beginDocument}), or {@code null} for a single document. The named definitions below
	 * ({@code @counter-style}, {@code @font-feature-values}, {@code @font-palette-values}) belong to the document that
	 * declares them (2026-10-08): with one registry per conversion, an item that used a name without defining it got
	 * the definition of an earlier item, and with two passes a later item's definition reached an earlier item.
	 */
	private java.net.URI currentDocument = null;

	private final Map<java.net.URI, CounterStyles> counterStyles = new HashMap<>();

	private final Map<java.net.URI, FontFeatureValues> fontFeatureValues = new HashMap<>();

	private final Map<java.net.URI, FontPaletteValues> fontPaletteValues = new HashMap<>();

	public void setCurrentDocument(final java.net.URI document) {
		this.currentDocument = document;
	}

	/**
	 * The number of the conversion into this context's output: 1, and 2, 3, ... for the later conversions of a
	 * continuous session ({@code setContinuous(true)}) that share the output (2026-10-08).
	 */
	private int conversionNumber = 0;

	public int getConversionNumber() {
		return this.conversionNumber;
	}

	/** Starts a conversion into this context's output. */
	public void nextConversion() {
		++this.conversionNumber;
	}

	private FootnoteArea footnoteArea = FootnoteArea.DEFAULT;

	private java.util.function.Consumer<net.zamasoft.foliojet.layout.FootnotePageProbeReport> footnotePageProbeListener;
	private long footnotePageProbeCount;

	/** Enables only observation of B's final report. bottom + vertical writing measurement works even when null. */
	public void setFootnotePageProbeListener(
			final java.util.function.Consumer<net.zamasoft.foliojet.layout.FootnotePageProbeReport> listener) {
		this.footnotePageProbeListener = listener;
	}

	public java.util.function.Consumer<net.zamasoft.foliojet.layout.FootnotePageProbeReport> getFootnotePageProbeListener() {
		return this.footnotePageProbeListener;
	}

	/** Number of probes created during the UA's lifetime. Distinguishes this from a report that has not fired. */
	public long getFootnotePageProbeCount() {
		return this.footnotePageProbeCount;
	}

	public void footnotePageProbeCreated() {
		++this.footnotePageProbeCount;
	}

	public FootnoteArea getFootnoteArea() {
		return this.footnoteArea;
	}

	public void setFootnoteArea(final FootnoteArea footnoteArea) {
		this.footnoteArea = footnoteArea == null ? FootnoteArea.DEFAULT : footnoteArea;
	}

	private net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry pagedSvgFontCarry = new net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry();

	private FontSourceManager fsm;
	
	private Map<Object, ImageMap> maps = new HashMap<Object, ImageMap> ();

	/**
	 * Keys of rendering approximations already reported in this conversion
	 * (2026-08-29; see {@code ApproximationGC}). Prevents repeated warnings for the same
	 * approximation (property × detail) in one document; lives for one conversion (= one UA).
	 */
	private final java.util.Set<String> reportedApproximations = new java.util.HashSet<String>();

	/**
	 * Detail keys for "ineffective CSS combinations" already reported in this conversion.
	 * Notifies the user only once even if the same fallback repeats during layout.
	 */
	private final java.util.Set<String> reportedIneffectiveCombinationDetails = new java.util.HashSet<String>();

	public FontSourceManager getFontSourceManager() {
		return this.fsm;
	}

	public void setFontSourceManager(FontSourceManager fsm) {
		this.fsm = fsm;
	}

	public int getPassCount() {
		return this.passCount;
	}

	public void setPassCount(int passCount) {
		this.passCount = passCount;
	}

	public PageRef getPageRef() {
		return this.pageRef;
	}

	public SelectorFacts getSelectorFacts() {
		return this.selectorFacts;
	}

	/** Element facts for {@code @container} queries (2026-08-15, stage 4). */
	public ContainerFacts getContainerFacts() {
		return this.containerFacts;
	}

	/**
	 * Cache of image intrinsic dimensions (2026-08-16). Avoids reopening resources and rereading
	 * headers for repeated occurrences of an image and repeated passes.
	 */
	public ImageMetricsCache getImageMetrics() {
		return this.imageMetrics;
	}

	/**
	 * Registry of author-defined counter styles ({@code @counter-style}), 2026-08-02.
	 * Placed here rather than in {@code DocumentContext}, which is recreated each pass,
	 * to preserve name-to-code mappings across passes (same lifetime as {@link PageRef}).
	 * One per document of the output (see {@link #setCurrentDocument}).
	 */
	public CounterStyles getCounterStyles() {
		return this.counterStyles.computeIfAbsent(this.currentDocument, k -> new CounterStyles());
	}

	/** {@code @font-feature-values} registry shared across layout passes, one per document of the output. */
	public FontFeatureValues getFontFeatureValues() {
		return this.fontFeatureValues.computeIfAbsent(this.currentDocument, k -> new FontFeatureValues());
	}

	/**
	 * {@code @font-palette-values} registry shared across layout passes, one per document of the output.
	 * Definitions are used only for name resolution and do not affect rendering.
	 */
	public FontPaletteValues getFontPaletteValues() {
		return this.fontPaletteValues.computeIfAbsent(this.currentDocument, k -> new FontPaletteValues());
	}
	
	public Map<Object, ImageMap> getImageMaps() {
		return this.maps;
	}

	/** Isolates image registrations for repeated drawing in a temporary map. Also covers nested image loads. */
	public ImageMapScope isolateImageMaps() {
		return new ImageMapScope();
	}

	public final class ImageMapScope implements AutoCloseable {
		private final Map<Object, ImageMap> previous = maps;
		private boolean closed;

		private ImageMapScope() {
			maps = new HashMap<Object, ImageMap>();
		}

		@Override
		public void close() {
			if (!this.closed) {
				maps = this.previous;
				this.closed = true;
			}
		}
	}

	/** Number of {@code target-counter()} slots created in this conversion (whether to record page-split SVG pages). */
	private int targetCounterSlots = 0;

	/** Records creation of one {@code target-counter()} slot (2026-10-04). */
	public void noteTargetCounterSlot() {
		++this.targetCounterSlots;
	}

	/** Whether this conversion has created a {@code target-counter()} slot. */
	public boolean hasTargetCounterSlots() {
		return this.targetCounterSlots > 0;
	}

	/** Whether deferred pages are being drawn at document end (page-split SVG). */
	private boolean drawingHeldPages = false;

	public boolean isDrawingHeldPages() {
		return this.drawingHeldPages;
	}

	public void setDrawingHeldPages(final boolean drawingHeldPages) {
		this.drawingHeldPages = drawingHeldPages;
	}

	/** Keys of already reported rendering approximations (used by {@code ApproximationGC.report}). */
	public java.util.Set<String> getReportedApproximations() {
		return this.reportedApproximations;
	}

	/** Detail keys of already reported "ineffective CSS combinations". */
	public java.util.Set<String> getReportedIneffectiveCombinationDetails() {
		return this.reportedIneffectiveCombinationDetails;
	}

	/**
	 * CSS declaration warnings (unsupported property, invalid value) already reported in this conversion (2026-10-08).
	 * Cleared at the start of each conversion with the carried style sheets.
	 */
	private final java.util.Set<String> reportedStyleWarnings = new java.util.HashSet<String>();

	/** Whether the declaration warning with this key is reported for the first time in this conversion. */
	public synchronized boolean firstStyleWarning(final String key) {
		return this.reportedStyleWarnings.add(key);
	}

	/** Forgets the reported declaration warnings (at the start of a conversion). */
	public synchronized void clearReportedStyleWarnings() {
		this.reportedStyleWarnings.clear();
	}

	/**
	 * Carryover of Paged SVG font subsets (2026-08-29). Since the UA is recreated for each conversion,
	 * the session ({@code DirectSession}) owns the actual state and passes it here at conversion start.
	 * Allows the preceding subset to be emitted before the first page when laying out the same book
	 * again with only the text size changed.
	 */
	public net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry getPagedSvgFontCarry() {
		return this.pagedSvgFontCarry;
	}

	public void setPagedSvgFontCarry(final net.zamasoft.foliojet.ua.impl.pagedsvg.PagedSvgFontCarry carry) {
		this.pagedSvgFontCarry = carry;
	}

	/**
	 * Stylesheets carried across passes (2026-08-08), one per document (2026-10-08).
	 * <p>
	 * In single-pass streaming, {@code <style>} elements appearing later in the document (inside body)
	 * cannot apply retroactively to earlier elements. SSR such as Nuxt inserts component styles
	 * into body, so earlier content such as headers was laid out as nearly bare HTML
	 * (found on metro.tokyo.lg.jp).
	 * For {@code processing.pass-count>=2}, retain the stylesheet collected in the preceding pass
	 * (including STRUCTURE_SCAN) here so the next pass can apply all rules from the start
	 * (same lifetime management as {@link SelectorFacts}: cleared at STRUCTURE_SCAN start and
	 * at single-pass conversion (DOCUMENT) start). Recollection in later passes adds duplicate rules,
	 * but identical duplicates do not change cascade results (last-wins simply selects the same value).
	 * </p>
	 * <p>
	 * The key is {@link DocumentContext#getDocumentURI()}: the spine items of an EPUB laid out into one output are
	 * separate documents, and one sheet for the whole conversion made the style sheets of an item apply to every later
	 * item (and, with two passes, to the earlier ones too). A single HTML document has a {@code null} key.
	 * </p>
	 */
	private final Map<java.net.URI, net.zamasoft.foliojet.css.CSSStyleSheet> carriedStyleSheets = new HashMap<>();

	public net.zamasoft.foliojet.css.CSSStyleSheet getCarriedStyleSheet(final java.net.URI document) {
		return this.carriedStyleSheets.get(document);
	}

	public void setCarriedStyleSheet(final java.net.URI document, final net.zamasoft.foliojet.css.CSSStyleSheet styleSheet) {
		this.carriedStyleSheets.put(document, styleSheet);
	}

	public void clearCarriedStyleSheets() {
		this.carriedStyleSheets.clear();
	}

	/**
	 * The documents laid out into this one output (the included spine items of an EPUB, 2026-10-08), or {@code null}
	 * for a single document. A link whose target resolves into one of them is an internal link, and element ids
	 * are qualified by their document in the output (see {@code AbstractVisitor}).
	 */
	private java.util.Set<java.net.URI> documentSet = null;

	public java.util.Set<java.net.URI> getDocumentSet() {
		return this.documentSet;
	}

	public void setDocumentSet(final java.util.Set<java.net.URI> documentSet) {
		this.documentSet = documentSet;
		this.normalizedDocuments = null;
	}

	/** The documents of the set by their normalized spelling ({@link #normalizeEscapes}). */
	private Map<String, java.net.URI> normalizedDocuments = null;

	/**
	 * The document of the set that {@code uri} names, or {@code null} (2026-10-08). Percent-escapes are compared as
	 * RFC 3986 §6.2.2 does: an escaped unreserved character is the character ({@code c%68apter.xhtml} names
	 * {@code chapter.xhtml}), and the hex digits of other escapes are compared without regard to case.
	 */
	public synchronized java.net.URI findDocument(final java.net.URI uri) {
		if (this.documentSet == null || uri == null) {
			return null;
		}
		if (this.documentSet.contains(uri)) {
			return uri;
		}
		if (this.normalizedDocuments == null) {
			this.normalizedDocuments = new HashMap<>();
			for (final java.net.URI document : this.documentSet) {
				this.normalizedDocuments.putIfAbsent(normalizeEscapes(document.toString()), document);
			}
		}
		return this.normalizedDocuments.get(normalizeEscapes(uri.toString()));
	}

	/** Decodes escaped unreserved characters and writes the hex digits of the other escapes in upper case. */
	static String normalizeEscapes(final String uri) {
		if (uri.indexOf('%') < 0) {
			return uri;
		}
		final StringBuilder b = new StringBuilder(uri.length());
		for (int i = 0; i < uri.length(); ++i) {
			final char c = uri.charAt(i);
			if (c == '%' && i + 2 < uri.length() && Character.digit(uri.charAt(i + 1), 16) >= 0
					&& Character.digit(uri.charAt(i + 2), 16) >= 0) {
				final char decoded = (char) Integer.parseInt(uri.substring(i + 1, i + 3), 16);
				if ((decoded >= 'A' && decoded <= 'Z') || (decoded >= 'a' && decoded <= 'z')
						|| (decoded >= '0' && decoded <= '9') || decoded == '-' || decoded == '.' || decoded == '_'
						|| decoded == '~') {
					b.append(decoded);
				} else {
					b.append('%').append(uri.substring(i + 1, i + 3).toUpperCase(java.util.Locale.ROOT));
				}
				i += 2;
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}
}
