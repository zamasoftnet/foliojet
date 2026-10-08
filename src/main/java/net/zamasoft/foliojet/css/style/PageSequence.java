package net.zamasoft.foliojet.css.style;

import java.awt.geom.AffineTransform;
import java.util.function.Consumer;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.Declaration;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.page.PageBleed;
import net.zamasoft.foliojet.css.impl.property.page.PageMarks;
import net.zamasoft.foliojet.css.impl.property.page.PageSize;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.content.CounterIncrement;
import net.zamasoft.foliojet.css.impl.property.content.CounterReset;
import net.zamasoft.foliojet.css.lang.LanguageProfile;
import net.zamasoft.foliojet.css.lang.LanguageProfileBundle;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CounterSetValue;
import net.zamasoft.foliojet.css.value.PageSizeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.OverflowMode;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.ua.impl.Impositions;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.PassContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputPageLimitAbort;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * Page lifecycle: page creation, {@code @page} styles and counters, blank-page checks
 * and rollback, drawing, and completion of imposition (extracted in increment 2 of
 * StyleBuilder decomposition, 2026-07-30. Method bodies moved verbatim from StyleBuilder;
 * behavior is unchanged).
 *
 * <p>
 * <b>Order is the contract</b>: {@code nextPage} runs
 * segment.trimToOpenElements → imposition.nextPageSide →
 * {@code @page} declaration and counter application; {@code drawPage} runs
 * blank-page check (rollback) → imposition.nextPage → flow → fixed →
 * margin boxes → DisplayListDumper → drawer.draw → closePage.
 * </p>
 */
final class PageSequence {
	private final UserAgent ua;
	private final StyleContext styleContext;
	private final Imposition imposition;
	private final DocumentBuilder doc;
	private final Segment segment;
	private final AbsoluteLengthValue[] margins;

	/** Current body page, used to determine width before constructing footnotes. */
	private PageBox currentPage;

	PageBox getCurrentPage() {
		return this.currentPage;
	}

	/**
	 * Warning for the reserved counter ({@code pages}). Delegates both checking and
	 * warning to StyleBuilder to share the once-per-document flag with author-side
	 * counter processing (StyleBuilder).
	 */
	private final Consumer<String> reservedCounterWarner;

	private CSSElement pageElement = null;
	private CSSElement firstPageElement;
	private int pageNumber = 0;

	int getPageNumber() {
		return this.pageNumber;
	}
	private int maxPageNumber = Integer.MAX_VALUE;

	/**
	 * Number of pages actually output (2026-07-28, css-break-3 §4.4).
	 */
	private int emittedPages = 0;

	/**
	 * Cross-page registry of tagged PDF structure elements (fix for defect ②,
	 * 2026-07-30). One per document (this PageSequence), wired to each page's PageBox.
	 * Harmless for untagged/non-PDF output, where no lookup occurs.
	 */
	private final net.zamasoft.foliojet.layout.box.impl.TaggedStructureContext structContext = new net.zamasoft.foliojet.layout.box.impl.TaggedStructureContext();

	/**
	 * Previous page side, retained to restore the side of a discarded page
	 * ({@link #nextPage()} advances it with {@code imposition.nextPageSide()}).
	 */
	private CSSElement previousPageSide = null;

	/** Amount added by {@code @page} counter-increment on this page. */
	private Value[] appliedPageIncrements = null;

	/** Whether the page counter was automatically incremented for this page (css-page-3 §6.1). */
	private boolean appliedAutoPageIncrement = false;

	/** Root page background (promoted from HTML/BODY). */
	private Background background = null;

	/** Layout direction (determined from HTML/BODY writing-mode). */
	private WritingMode progression = WritingMode.TB;

	PageSequence(final UserAgent ua, final StyleContext styleContext, final Imposition imposition,
			final DocumentBuilder doc, final Segment segment, final Consumer<String> reservedCounterWarner) {
		this.ua = ua;
		this.styleContext = styleContext;
		this.imposition = imposition;
		this.doc = doc;
		this.segment = segment;
		this.reservedCounterWarner = reservedCounterWarner;
		this.pageNumber = ua.getPassContext().getPageNumber();

		// Page width
		{
			String s = UAProps.OUTPUT_PAGE_WIDTH.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false, s);
			if (length != null) {
				double l = length.getLength();
				this.imposition.setPageWidth(l);
				if (this.imposition.getNote() != null) {
					this.imposition.setNote(this.imposition.getNote() + " / width " + s);
				}
			} else {
				this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_WIDTH.name, s);
			}
		}

		// Page height
		{
			String s = UAProps.OUTPUT_PAGE_HEIGHT.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false, s);
			if (length != null) {
				double l = length.getLength();
				this.imposition.setPageHeight(l);
				if (this.imposition.getNote() != null) {
					this.imposition.setNote(this.imposition.getNote() + " / height " + s);
				}
			} else {
				this.ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_HEIGHT.name, s);
			}
		}
		Impositions.setupImposition(this.ua, this.imposition);

		// Margins
		{
			AbsoluteLengthValue[] margins;
			String s = UAProps.OUTPUT_PAGE_MARGINS.getString(ua);
			if (s != null) {
				String[] values = s.split("[\\s]+");
				if (values.length <= 0 || values.length > 4) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_MARGINS.name, s);
					margins = null;
				} else {
					margins = new AbsoluteLengthValue[values.length];
					for (int i = 0; i < values.length; ++i) {
						AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, values[i]);
						if (length != null) {
							margins[i] = length;
						} else {
							ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAGE_MARGINS.name, s);
							margins = null;
							break;
						}
					}
				}
			} else {
				margins = null;
			}
			this.margins = margins;
		}

		// Maximum page count
		this.maxPageNumber = UAProps.OUTPUT_PAGE_LIMIT.getInteger(ua);
	}

	CSSElement getPageElement() {
		return this.pageElement;
	}

	WritingMode getProgression() {
		return this.progression;
	}

	void setProgression(final WritingMode progression) {
		this.progression = progression;
		if (this.ua instanceof net.zamasoft.foliojet.ua.impl.AbstractUserAgent aua) {
			// Allow the output side (page-split SVG manifest) to record page progression direction
			aua.setPageProgression(progression);
		}
	}

	/**
	 * Promotes the HTML/BODY background to the root page background.
	 *
	 * @return true if promoted (the caller sets the element's background to NULL)
	 */
	boolean promoteRootBackground(final Background background) {
		if (this.background == null && background != Background.NULL_BACKGROUND) {
			this.background = background;
			return true;
		}
		return false;
	}

	PageBreakMode getPageSide() {
		if (this.pageElement.isPseudoClass(CSSElement.PC_EVEN)) {
			return PageBreakMode.VERSO;
		}
		if (this.pageElement.isPseudoClass(CSSElement.PC_ODD)) {
			return PageBreakMode.RECTO;
		}
		return PageBreakMode.AUTO;
	}

	/**
	 * Page name from the next generated page onward (named pages N2a; null=unnamed).
	 * Boundary resolution (BreakableBuilder) sets it before a page break. Capture the
	 * finalized value in {@link #pageName} at nextPage() so it does not affect resolution
	 * for a page already generated.
	 */
	private String pendingPageName;

	/** Name of the current (already generated) page (for declarations, running headers, and blank-page checks). */
	private String pageName;

	/** Document default size for size:auto (N3/N4: captured at the first nextPage). */
	private double defaultPageWidth = -1, defaultPageHeight = -1;

	void setPageName(final String pageName) {
		this.pendingPageName = pageName;
	}

	String getPageName() {
		// Boundary comparison target = page name of content about to be placed = pending
		return this.pendingPageName;
	}

	private CSSStyle pageStyle(final CSSElement element, final String name) {
		Declaration declaration = this.styleContext.nextPage(element, name);
		CSSStyle pageStyle = CSSStyle.getCSSStyle(this.ua, null, element);
		pageStyle.setCustomPropertyFallback(this.ua.getDocumentContext().getRootStyle());

		// Default margins
		if (this.margins != null) {
			switch (this.margins.length) {
			case 1:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[0]);
				pageStyle.set(Margin.BOTTOM, this.margins[0]);
				pageStyle.set(Margin.LEFT, this.margins[0]);
				break;
			case 2:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[1]);
				pageStyle.set(Margin.BOTTOM, this.margins[0]);
				pageStyle.set(Margin.LEFT, this.margins[1]);
				break;
			case 3:
				pageStyle.set(Margin.TOP, this.margins[1]);
				pageStyle.set(Margin.RIGHT, this.margins[2]);
				pageStyle.set(Margin.BOTTOM, this.margins[3]);
				pageStyle.set(Margin.LEFT, this.margins[2]);
				break;
			case 4:
				pageStyle.set(Margin.TOP, this.margins[0]);
				pageStyle.set(Margin.RIGHT, this.margins[1]);
				pageStyle.set(Margin.BOTTOM, this.margins[2]);
				pageStyle.set(Margin.LEFT, this.margins[3]);
				break;
			}
		}

		declaration.applyProperties(pageStyle);
		return pageStyle;
	}

	private BlockParams pageParams(final CSSStyle pageStyle) {
		final BlockParams params = new BlockParams();
		params.flow = this.progression;
		params.fontStyle = pageStyle.getFontStyle();
		params.fontManager = this.ua.getFontManager();
		final LanguageProfile lang = LanguageProfileBundle.getLanguageProfile(pageStyle.getCSSElement().lang);
		params.lineBreakRules = lang.getTextBreakingRules(pageStyle);
		return params;
	}

	private Dimension pageSize(final CSSStyle pageStyle) {
		final PageSizeValue pageSize = PageSize.get(pageStyle);
		final double[] resolvedSize = pageSize.resolve(this.defaultPageWidth, this.defaultPageHeight);
		double width = resolvedSize[0];
		double height = resolvedSize[1];

		if ((this.doc.getPageMode() & DocumentBuilder.PAGE_MODE_CONTINUOUS) != 0) {
			if (this.imposition.getBoundSide() == BoundSide.LEFT) {
				// Horizontal writing
				return Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.AUTO);
			} else {
				// Vertical writing
				return Dimension.create(width, height, LengthType.AUTO, LengthType.ABSOLUTE);
			}
		} else {
			return Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		}
	}

	private Insets pageMargin(final CSSStyle pageStyle) {
		// Margins
		Value marginTop = Margin.get(pageStyle, Side.TOP);
		Value marginRight = Margin.get(pageStyle, Side.RIGHT);
		Value marginBottom = Margin.get(pageStyle, Side.BOTTOM);
		Value marginLeft = Margin.get(pageStyle, Side.LEFT);
		return BoxValueUtils.toInsets(marginTop, marginRight, marginBottom, marginLeft);
	}

	private RectFrame pageFrame(final CSSStyle pageStyle, final Insets margin, final Background background) {
		final RectBorder pageBorder = BoxStyleMapper.createRectBorder(pageStyle);
		final Insets pagePadding = BoxValueUtils.toInsets(Padding.get(pageStyle, Side.TOP),
				Padding.get(pageStyle, Side.RIGHT), Padding.get(pageStyle, Side.BOTTOM), Padding.get(pageStyle, Side.LEFT));
		return RectFrame.create(margin, pageBorder, background, pagePadding);
	}

	private CSSElement footnotePageElement(final int emitted) {
		CSSElement element = this.firstPageElement;
		if (emitted > 0) {
			element = this.imposition.getNextPageSide(element);
			// After the first transition, the side is fixed or alternates. Advance virtual sides using actual rules
			// to handle intermediate EPUB sides and single-side starts. Do not rescan all pages for each query.
			if ((emitted & 1) == 0) element = this.imposition.getNextPageSide(element);
		}
		return element;
	}

	/**
	 * Queries the type area before reservation using only B's name and output page count.
	 * Shares style and size resolution with actual processing, but does not advance segment,
	 * counters, imposition, or the current page. If B discards a blank page, only the generation
	 * advances; the side stays the same.
	 */
	net.zamasoft.foliojet.layout.FootnotePageProbe.PageGeometry footnotePageGeometry(final String name, final int emitted) {
		final CSSElement element = this.footnotePageElement(emitted);
		final CSSStyle style = this.pageStyle(element, name);
		final BlockParams params = this.pageParams(style);
		params.size = this.pageSize(style);
		params.frame = this.pageFrame(style, this.pageMargin(style), Background.NULL_BACKGROUND);
		// Share inner-size calculations for percentage margins/border/padding with PageBox too. Do not draw or register.
		final PageBox geometry = new PageBox(params, this.ua);
		return new net.zamasoft.foliojet.layout.FootnotePageProbe.PageGeometry(
				geometry.getInnerWidth(), geometry.getInnerHeight(), params.flow);
	}

	PageBox nextPage() {
		// Trim the segment window: retain only open elements (M6a)
		this.segment.trimToOpenElements();
		// Named pages N2a: finalize this page's name
		this.pageName = this.pendingPageName;
		// Page style
		// nextPageSide() advances the side (recto/verso). Discarded pages do not consume
		// a side, so remember the value before advancing (discardPage restores it).
		this.previousPageSide = this.ua.getPassContext().getPageSide();
		this.pageElement = this.imposition.nextPageSide();
		if (this.firstPageElement == null) this.firstPageElement = this.pageElement;
		CSSStyle pageStyle = this.pageStyle(this.pageElement, this.pageName);

		// Reset page counters
		Value[] resets = CounterReset.get(pageStyle);
		if (resets != null) {
			for (int i = 0; i < resets.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) resets[i];
				String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.reservedCounterWarner.accept(name);
					continue;
				}
				int value = counterSet.getValue();
				this.ua.getPassContext().getCounterScope(0, true).reset(name, value);
			}
		}

		// Increment page counters
		Value[] increments = CounterIncrement.get(pageStyle);
		boolean pageIncremented = false;
		if (increments != null) {
			final PassContext pc = this.ua.getPassContext();
			for (int i = 0; i < increments.length; ++i) {
				CounterSetValue counterSet = (CounterSetValue) increments[i];
				String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					this.reservedCounterWarner.accept(name);
					continue;
				}
				int delta = counterSet.getValue();
				pc.getCounterScope(0, true).increment(name, delta);
				pageIncremented |= "page".equals(name);
			}
		}
		if (!pageIncremented) {
			// The page counter automatically increments for each page (css-page-3 §6.1).
			// An explicit page entry in @page counter-increment takes precedence.
			this.ua.getPassContext().getCounterScope(0, true).increment("page", 1);
		}
		// Discarded pages do not consume a number (discardPage restores it in the same order)
		this.appliedPageIncrements = increments;
		this.appliedAutoPageIncrement = !pageIncremented;

		// Apply the root style
		if (this.background == null) {
			this.background = Background.NULL_BACKGROUND;
		}

		final BlockParams params = this.pageParams(pageStyle);

		// Page size (N3/N4: @page size overrides output defaults). Always restore size:auto
		// to the document defaults captured initially: drawPage rewrites the current imposition
		// values to the previous page's size, so using them would leak state.
		if (this.defaultPageWidth <= 0) {
			this.defaultPageWidth = this.imposition.getPageWidth();
			this.defaultPageHeight = this.imposition.getPageHeight();
		}
		// Crop marks and bleed (2026-08-02): override output.marks / output.trims
		// only when explicitly specified in CSS (same approach as size:auto).
		final net.zamasoft.foliojet.css.value.PageMarksValue marks = PageMarks.get(pageStyle);
		if (marks != net.zamasoft.foliojet.css.value.PageMarksValue.UNSPECIFIED) {
			this.imposition.setCrop(marks.isCrop());
			this.imposition.setCross(marks.isCross());
			if ((marks.isCrop() || marks.isCross()) && this.imposition.getTrimTop() == 0
					&& this.imposition.getTrimRight() == 0 && this.imposition.getTrimBottom() == 0
					&& this.imposition.getTrimLeft() == 0) {
				// If output.marks remains none, the cutting margin is zero, so restore the default
				// (1 cm) when CSS declares crop marks (2026-08-29).
				// Marks are drawn in the cutting margin; zero width places them outside the sheet and hides them.
				final double d = net.zamasoft.pdfg2d.pdf.util.PDFUtils.POINTS_PER_CM;
				this.imposition.setTrims(d, d, d, d);
			}
		}
		final double bleed = PageBleed.get(pageStyle);
		// When output.trim-inset is specified, the bleed already lies within the print area
		// (B-3). Expanding the cutting margin again with CSS bleed would double it.
		if (bleed >= 0 && this.imposition.getTrimInset() == 0) {
			// Declaring bleed in CSS expresses an intent to draw that far beyond the trim line
			// (user report, 2026-08-29). Set the bleed allowance to the same width so content
			// is not clipped at the trim line. Previously it remained zero,
			// so the bleed area stayed white even when bleed was specified.
			// Do not make the cutting margin narrower than bleed. **Do not narrow it from its current size**
			// either: crop marks are drawn within the cutting margin, farther out than the bleed
			// (PrinterMarks uses twice cuttingMargin). Reducing the cutting margin to the bleed
			// width places crop marks outside the sheet and hides them.
			final double trim = Math.max(bleed, Math.max(Math.max(this.imposition.getTrimTop(),
					this.imposition.getTrimRight()),
					Math.max(this.imposition.getTrimBottom(), this.imposition.getTrimLeft())));
			this.imposition.setTrims(trim, trim, trim, trim);
			this.imposition.setCuttingMargin(bleed);
		}

		params.size = this.pageSize(pageStyle);
		params.overflow = OverflowMode.VISIBLE;
		Insets margin = this.pageMargin(pageStyle);

		// Page box background (css-page-3 §3, 2026-09-01). PageBox draws the page-specific
		// background across the whole sheet first, then overlays the canvas background promoted
		// from html/body inside the margins as a normal frame background.
		// Border and padding (css-page-3 §3.1, 2026-09-03): @page border/padding lie inside
		// the margins by the same rules as elements; the type area (page area) lies inside them.
		// Drawn by frames() in PageBox.drawFlow (same path as element frames).
		final Background pageBackground = BoxStyleMapper.createBackground(pageStyle);
		params.frame = this.pageFrame(pageStyle, margin, this.background);

		this.pageNumber++;
		// Negative means unlimited (not just -1; until 2026-10-05, -2 or below aborted immediately)
		if (this.maxPageNumber >= 0 && this.pageNumber > this.maxPageNumber) {
			short code = MessageCodes.ERROR_OUT_OF_PAGE_LIMIT;
			String[] args = new String[] { String.valueOf(this.maxPageNumber) };
			ua.message(code, args);
			if (UAProps.OUTPUT_PAGE_LIMIT_ABORT.get(ua) == OutputPageLimitAbort.NORMAL) {
				throw new AbortException(AbortException.ABORT_NORMAL);
			}
			throw new AbortException(AbortException.ABORT_FORCE);
		}
		this.ua.message(MessageCodes.INFO_PAGE_NUMBER, String.valueOf(this.pageNumber));
		final PageBox pageBox = new PageBox(params, this.ua, pageBackground);
		// Extend the @page background to the bleed. Use the greater of CSS bleed and
		// the bleed allowance determined by imposition (output.cutting-margin, etc.).
		pageBox.setBleed(Math.max(bleed, this.imposition.getCuttingMargin()));
		this.currentPage = pageBox;
		return pageBox;
	}

	/**
	 * Returns whether output can be omitted because this page <b>draws nothing on paper</b>
	 * (added 2026-07-28, css-break-3 §4.4; the full check moved from StyleBuilder).
	 * Checks all five categories: body text, {@code @page} background, fixed positioning,
	 * legacy, and margin box declarations. Does not count crop marks or page numbers.
	 */
	private boolean paintsNothing(final PageBox pageBox, final boolean lastPage, final boolean closedByForcedBreak) {
		if (pageBox.isNamedTransitionClosed() && !pageBox.paintsAnything()) {
			// Blank page closed by a page-name transition at page start (N2b).
			// Discard even with running header declarations or a forced-break origin: equivalent
			// to replacing an unfinalized page with the old name by one with the new name
			// (destination content always follows a transition break, so the PDF cannot have zero pages).
			return true;
		}
		if (this.emittedPages == 0 && lastPage) {
			// **Do not create a zero-page PDF**. However, having output no pages yet is not
			// by itself a reason to retain one (2026-07-29): if subsequent pages have content,
			// the initial blank page can be discarded.
			//
			// Previously the first page was always retained, so **documents whose content began
			// on page 2 output a blank page 1**
			// (sweep seeds 597668 / 1954254; observations showed that only page 1 of 3
			// contained nothing but `drawer z=0`).
			//
			// The caller distinguishes `lastPage`: `RootBuilder.pageBreak` finalizes a page
			// at a break, so **there is a following page** (false);
			// `RootBuilder.finish` is the document end (true).
			//
			// From 2026-07-29 to 09-06, `closedByForcedBreak` was also a reason to retain a page
			// (interpreting {@code page-break-before:always} on the first element as an author's
			// request for a sheet before it). **Withdrawn** (2026-09-06, alongside the user report
			// "character boxes for tate-chu-yoko links"): there is no break point at the start of a document;
			// neither Chrome nor Prince creates a blank page (css-break-3 §3.1: page breaks can
			// occur only between boxes). Book CSS specifying {@code break-before: page} on each
			// article's initial section had produced a blank first page every time.
			// Discard an initial page that draws nothing even if it was closed by a forced break
			// (subsequent content prevents a zero-page document).
			return false;
		}
		if (this.emittedPages == 0 && closedByForcedBreak && !pageBox.isForcedBreakOrigin()
				&& !pageBox.paintsAnything()) {
			// Continuation of the withdrawal above (2026-09-06): discard the page preceding a forced
			// break at document start **even with running header/page number declarations** (2026-10-04).
			// Relying on the rule below to draw whenever margin boxes are declared caused a book
			// with running headers and break-before: right on its first h1 to gain a header-only first page
			// and a blank page for side alignment (found in the 時限暗号 (Time-Lock Cipher) book test).
			return true;
		}
		if (pageBox.isForcedBreakOrigin()) {
			// Author-intended blank page
			return false;
		}
		if (pageBox.paintsAnything()) {
			return false;
		}
		// Treat declared page margin boxes (running headers/page numbers) as drawing content
		return this.styleContext.pageMarginBoxes(this.pageElement, this.pageName).isEmpty();
	}

	/**
	 * Cancels a page that draws nothing (added 2026-07-28).
	 *
	 * <p>
	 * <b>Consumes neither a page number nor a side.</b> Reverses only what
	 * {@link #nextPage()} advanced, exactly and in reverse order.
	 * </p>
	 */
	private void discardPage() {
		final PassContext pc = this.ua.getPassContext();
		if (this.appliedAutoPageIncrement) {
			pc.getCounterScope(0, true).increment("page", -1);
		}
		if (this.appliedPageIncrements != null) {
			for (int i = 0; i < this.appliedPageIncrements.length; ++i) {
				final CounterSetValue counterSet = (CounterSetValue) this.appliedPageIncrements[i];
				final String name = counterSet.getName();
				if (StyleBuilder.isReservedCounterName(name)) {
					// nextPage() did not increment it either
					continue;
				}
				pc.getCounterScope(0, true).increment(name, -counterSet.getValue());
			}
		}
		pc.setPageSide(this.previousPageSide);
		--this.pageNumber;
	}

	boolean drawPage(final PageBox pageBox, final boolean lastPage, final boolean closedByForcedBreak)
			throws GraphicsException {
		// Do not output pages that draw nothing (css-break-3 §4.4). Check before
		// imposition.nextPage() (the point at which the PDF page is created):
		// avoid creating the page instead of creating and then canceling it.
		if (this.paintsNothing(pageBox, lastPage, closedByForcedBreak)) {
			// Discarding a sheet does not lose the assignment source's document order. Finalize empty-element
			// clear/string-set once and inherit it on the next page. Do not draw or register PDF objects.
			final Visitor assignmentVisitor = new net.zamasoft.foliojet.ua.impl.NopVisitor(this.ua);
			for (final var assignment : this.ua.getPassContext().getRunningRegistry().commitPage(pageBox)) {
				assignmentVisitor.visitAssignment(assignment);
			}
			assignmentVisitor.endPage();
			this.discardPage();
			return false;
		}
		// Commit assignment page ownership using the split/transferred tree from RootBuilder.
		final var assignments = this.ua.getPassContext().getRunningRegistry().commitPage(pageBox);
		// Determine page size
		if (UAProps.OUTPUT_EXPAND_WITH_CONTENT.getBoolean(ua)) {
			this.imposition.setPageWidth(pageBox.getVisualWidth());
			this.imposition.setPageHeight(pageBox.getVisualHeight());
		} else {
			this.imposition.setPageWidth(pageBox.getWidth());
			this.imposition.setPageHeight(pageBox.getHeight());
		}
		if (UAProps.OUTPUT_PAPER_WIDTH.getString(ua) == null) {
			this.imposition.fitPaperWidth();
		}
		if (UAProps.OUTPUT_PAPER_HEIGHT.getString(ua) == null) {
			this.imposition.fitPaperHeight();
		}

		if ((this.doc.getPageMode() & DocumentBuilder.PAGE_MODE_CONTINUOUS) != 0) {
			// Report height when it is automatic
			this.ua.message(MessageCodes.INFO_PAGE_HEIGHT, String.valueOf(pageBox.getHeight()));
		}

		// Drawing
		final GC gc = this.imposition.nextPage();

		if (UAProps.OUTPUT_EXPAND_WITH_CONTENT.getBoolean(ua)) {
			if (gc != null && pageBox.getVisualWidth() > pageBox.getWidth()) {
				gc.transform(AffineTransform.getTranslateInstance(pageBox.getVisualWidth() - pageBox.getWidth(), 0));
			}
			this.imposition.setPageWidth(pageBox.getWidth());
			this.imposition.setPageHeight(pageBox.getHeight());
			if (UAProps.OUTPUT_PAPER_WIDTH.getString(ua) == null) {
				this.imposition.fitPaperWidth();
			}
			if (UAProps.OUTPUT_PAPER_HEIGHT.getString(ua) == null) {
				this.imposition.fitPaperHeight();
			}
		}

		final AffineTransform marginT;
		GC.State marginState = null;
		if (gc != null) {
			AbsoluteInsets margin = pageBox.getFrame().margin;
			double xoff = margin.left;
			double yoff = margin.top;
			if (xoff != 0 || yoff != 0) {
				marginT = AffineTransform.getTranslateInstance(xoff, yoff);
			} else {
				marginT = null;
			}
			if (marginT != null) {
				marginState = gc.begin();
				gc.transform(marginT);
			}
		} else {
			marginT = null;
		}

		// B-3 (2026-07-30): wire the structure declaration destination before building the display list
		// (declare during document-order traversal, preserving structure even when drawing follows z-order).
		// Fix for defect ② (2026-07-30): also pass the cross-page registry (this.structContext)
		// so continuation fragments can append content to the StructElem from the first occurrence.
		if (gc instanceof net.zamasoft.pdfg2d.pdf.gc.PDFGC pdfgc
				&& pdfgc.getPDFGraphicsOutput() instanceof net.zamasoft.pdfg2d.pdf.PDFPageOutput structOut) {
			pageBox.setStructOutput(structOut, this.structContext);
		}
		final Visitor visitor = this.ua.getVisitor(gc);
		visitor.startPage();

		final Drawer drawer = new Drawer(0);

		// Flow
		pageBox.drawFlow(drawer, visitor);
		for (final var assignment : assignments) {
			visitor.visitAssignment(assignment);
		}

		if (gc != null) {
			// Footnote separator rule (after flow, before fixed; decorative, so artifact)
			pageBox.drawFootnoteSeparator(drawer);

			// Fixed
			pageBox.drawFixed(drawer, visitor);

			// Page margin boxes (css-page-3: drawn after body text, as specified)
			final var values = new net.zamasoft.foliojet.css.style.running.PageValueSnapshot(
					this.ua, this.pageElement, this.pageName);
			final var running = new net.zamasoft.foliojet.css.style.running.RunningRenderer(this.ua, values);
			// @page :blank (2026-10-04): a page begun by a forced break and closed without
			// drawing anything (e.g. a blank page inserted by left/right page breaks). This is known
			// only now, after content is finalized, so :blank affects only margin boxes.
			final boolean blank = pageBox.isForcedBreakOrigin() && !pageBox.paintsAnything();
			MarginBoxes.draw(this.ua, this.styleContext, this.pageElement, this.pageName, pageBox, drawer, visitor, running,
					blank);

		}
		// NopVisitor also finalizes the page state of string-set/named strings.
		visitor.endPage();

		// Execute drawing asynchronously
		// PDF drawing completes very quickly
		if (gc != null) {
			DisplayListDumper.dumpPage(drawer, this.pageNumber);
			// Draw with the approximate-rendering reporting path (2822) installed (2026-08-29)
			drawer.draw(net.zamasoft.foliojet.layout.util.ApproximationGC.wrap(gc, this.ua), pageBox.getWidth(),
					pageBox.getHeight());
			if (marginState != null) {
				marginState.close();
			}
		}
		// Fix for defect ②: clean up the cross-page registry (discard declarations for elements
		// not continued on this page, bounding retained data by the page's element count).
		this.structContext.endPage();
		this.imposition.closePage();
		++this.emittedPages;
		return true;
	}

	/**
	 * Finishes imposition and saves the page number to the pass context
	 * (called from {@code StyleBuilder.finish()}).
	 */
	void finish() throws GraphicsException {
		net.zamasoft.foliojet.ua.impl.Impositions.finishDocument(this.ua, this.imposition);
		this.ua.getPassContext().setPageNumber(this.pageNumber);
	}
}
